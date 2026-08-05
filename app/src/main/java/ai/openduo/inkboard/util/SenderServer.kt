package ai.openduo.inkboard.util

import android.content.ContentValues
import android.content.Context
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.webkit.MimeTypeMap
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.io.RandomAccessFile
import java.net.Inet4Address
import java.net.InetSocketAddress
import java.net.NetworkInterface
import java.net.ServerSocket
import java.net.Socket
import java.net.SocketException
import java.net.URLDecoder
import java.nio.charset.StandardCharsets
import java.util.Collections
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

data class SenderServerInfo(
    val port: Int,
    val url: String?
)

/**
 * A deliberately short-lived, single-purpose file drop server.
 *
 * It is owned by LauncherViewModel rather than an Android Service. The server
 * therefore exists only while the SENDER page is open and is also stopped by
 * the ViewModel as a final safety net.
 */
class SenderServer(private val context: Context) {

    private var serverSocket: ServerSocket? = null
    private var acceptExecutor: ExecutorService? = null
    private var clientExecutor: ExecutorService? = null
    private var serverInfo: SenderServerInfo? = null
    private val clients = ConcurrentHashMap.newKeySet<Socket>()
    private val uploads = ConcurrentHashMap<String, UploadSession>()
    private val completedUploads = ConcurrentHashMap<String, Long>()

    @Volatile
    private var running = false

    @Synchronized
    fun start(): SenderServerInfo {
        serverInfo?.let { info ->
            if (running && serverSocket?.isClosed == false) return info
        }

        stopLocked()

        var bound: ServerSocket? = null
        var boundPort = 0
        for (port in PORT_START..PORT_END) {
            val candidate = runCatching {
                ServerSocket().apply {
                    reuseAddress = true
                    bind(InetSocketAddress("0.0.0.0", port), 8)
                }
            }.getOrNull()
            if (candidate != null) {
                bound = candidate
                boundPort = port
                break
            }
        }

        val socket = bound ?: throw IOException("没有可用的传输端口")
        val info = SenderServerInfo(
            port = boundPort,
            url = localIpv4Address()?.let { "http://$it:$boundPort" }
        )

        serverSocket = socket
        serverInfo = info
        running = true
        acceptExecutor = Executors.newSingleThreadExecutor { runnable ->
            Thread(runnable, "inkboard-sender-accept").apply { isDaemon = true }
        }
        clientExecutor = Executors.newCachedThreadPool { runnable ->
            Thread(runnable, "inkboard-sender-client").apply { isDaemon = true }
        }
        acceptExecutor?.execute { acceptLoop(socket) }
        return info
    }

    @Synchronized
    fun stop() {
        stopLocked()
    }

    private fun stopLocked() {
        running = false
        runCatching { serverSocket?.close() }
        serverSocket = null
        clients.forEach { socket -> runCatching { socket.close() } }
        clients.clear()
        uploads.values.forEach { session -> runCatching { session.tempFile.delete() } }
        uploads.clear()
        completedUploads.clear()
        acceptExecutor?.shutdownNow()
        clientExecutor?.shutdownNow()
        acceptExecutor = null
        clientExecutor = null
        serverInfo = null
    }

    private fun acceptLoop(socket: ServerSocket) {
        while (running && !socket.isClosed) {
            val client = try {
                socket.accept()
            } catch (_: SocketException) {
                break
            } catch (_: IOException) {
                if (!running) break
                continue
            }

            if (!running) {
                runCatching { client.close() }
                break
            }

            clients += client
            clientExecutor?.execute {
                try {
                    handle(client)
                } finally {
                    clients -= client
                    runCatching { client.close() }
                }
            } ?: runCatching { client.close() }
        }
    }

    private fun handle(socket: Socket) {
        socket.soTimeout = CLIENT_TIMEOUT_MS
        val input = BufferedInputStream(socket.getInputStream())
        val output = BufferedOutputStream(socket.getOutputStream())
        val request = readRequest(input) ?: return

        if (request.headers["expect"]?.equals("100-continue", ignoreCase = true) == true) {
            output.write("HTTP/1.1 100 Continue\r\n\r\n".toByteArray(StandardCharsets.ISO_8859_1))
            output.flush()
        }

        when {
            request.method == "GET" && request.path == "/directories" -> {
                sendDirectories(output)
            }

            request.method == "GET" && request.path == "/" -> {
                sendJsonOrHtml(
                    output = output,
                    status = "200 OK",
                    contentType = "text/html; charset=utf-8",
                    body = SenderUploadPage.toByteArray(StandardCharsets.UTF_8)
                )
            }

            request.method == "POST" && request.path == "/upload" -> {
                handleUpload(request, input, output)
            }

            else -> sendError(output, "404 Not Found", "找不到这个地址")
        }
    }

    private fun handleUpload(
        request: HttpRequest,
        input: InputStream,
        output: OutputStream
    ) {
        val contentLength = request.headers["content-length"]?.toLongOrNull()
        if (contentLength == null || contentLength < 0L) {
            sendError(output, "411 Length Required", "上传请求缺少分片大小")
            return
        }

        val uploadId = queryParameter(request.target, "uploadId")
        val chunkIndex = queryParameter(request.target, "chunkIndex")?.toIntOrNull()
        val totalChunks = queryParameter(request.target, "totalChunks")?.toIntOrNull()
        val totalSize = queryParameter(request.target, "totalSize")?.toLongOrNull()
        val chunkSize = queryParameter(request.target, "chunkSize")?.toIntOrNull()
        val requestedPath = queryParameter(request.target, "path")
            ?: queryParameter(request.target, "name")
        val requestedDestination = queryParameter(request.target, "destination")

        if (
            uploadId.isNullOrBlank() || !isValidUploadId(uploadId) ||
            chunkIndex == null || totalChunks == null || totalSize == null ||
            chunkSize == null || requestedPath.isNullOrBlank()
        ) {
            sendError(output, "400 Bad Request", "分片参数不完整")
            return
        }

        if (
            totalSize < 0L || totalSize > MAX_UPLOAD_BYTES ||
            totalChunks !in 1..MAX_CHUNKS ||
            chunkSize !in MIN_CHUNK_SIZE..MAX_CHUNK_SIZE ||
            chunkIndex !in 0 until totalChunks
        ) {
            sendError(output, "400 Bad Request", "分片参数无效")
            return
        }

        val expectedTotalChunks = maxOf(
            1L,
            (totalSize + chunkSize.toLong() - 1L) / chunkSize.toLong()
        )
        if (totalChunks.toLong() != expectedTotalChunks) {
            sendError(output, "400 Bad Request", "分片总数与文件大小不匹配")
            return
        }

        val expectedChunkLength = if (chunkIndex == totalChunks - 1) {
            totalSize - chunkIndex.toLong() * chunkSize.toLong()
        } else {
            chunkSize.toLong()
        }
        if (contentLength != expectedChunkLength) {
            sendError(output, "400 Bad Request", "分片大小不匹配")
            return
        }

        val path = sanitizeRelativePath(requestedPath)
        val destination = try {
            sanitizeDirectoryPath(requestedDestination)
        } catch (error: IllegalArgumentException) {
            sendError(output, "400 Bad Request", error.message ?: "保存目录无效")
            return
        }
        val mimeType = request.headers["content-type"]
            ?.substringBefore(';')
            ?.trim()
            ?.takeIf { it.isNotBlank() }
            ?: guessMimeType(path.fileName)

        try {
            completedUploads[uploadId]?.let {
                // The final response may have been lost after the file was
                // published. Accepting the retry makes the last chunk
                // idempotent instead of creating a new incomplete session.
                drainExactly(input, contentLength)
                sendJson(
                    output = output,
                    status = "200 OK",
                    body = "{\"ok\":true,\"complete\":true,\"receivedBytes\":$totalSize,\"totalSize\":$totalSize}"
                )
                return
            }
            val session = getOrCreateUploadSession(
                uploadId = uploadId,
                path = path,
                mimeType = mimeType,
                destination = destination,
                totalSize = totalSize,
                totalChunks = totalChunks,
                chunkSize = chunkSize
            )

            var complete = false
            var receivedBytes = 0L
            synchronized(session) {
                if (!session.received[chunkIndex]) {
                    writeChunk(session, chunkIndex, input, contentLength)
                    session.received[chunkIndex] = true
                    session.receivedBytes += contentLength
                } else {
                    // A browser retry is safe: consume the duplicate body but
                    // do not count it twice or rewrite the finished chunk.
                    drainExactly(input, contentLength)
                }

                receivedBytes = session.receivedBytes
                if (session.received.all { it } && !session.published) {
                    session.published = true
                    try {
                        publishUpload(session)
                        completedUploads[uploadId] = System.currentTimeMillis()
                        uploads.remove(uploadId, session)
                        runCatching { session.tempFile.delete() }
                        complete = true
                    } catch (error: Exception) {
                        session.published = false
                        throw error
                    }
                }
            }

            sendJson(
                output = output,
                status = "200 OK",
                body = "{\"ok\":true,\"complete\":$complete,\"receivedBytes\":$receivedBytes,\"totalSize\":$totalSize}"
            )
        } catch (error: Exception) {
            sendError(output, "500 Internal Server Error", "保存失败：${error.message ?: "未知错误"}")
        }
    }

    private fun sendDirectories(output: OutputStream) {
        val body = buildString {
            append("{\"ok\":true,\"directories\":[")
            availableDirectories().forEachIndexed { index, directory ->
                if (index > 0) append(',')
                append(jsonString(directory))
            }
            append("]}")
        }
        sendJson(output, "200 OK", body)
    }

    private fun availableDirectories(): List<String> {
        // MediaStore on this platform only accepts certain top-level trees for
        // app inserts (commonly Download / Documents). Do not seed directories
        // that insert() will reject (e.g. Books).
        val directories = linkedSetOf(
            DEFAULT_DIRECTORY,
            "Download",
            "Documents",
            "Pictures",
            "Movies",
            "Music",
            "DCIM"
        )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            runCatching {
                val uri = MediaStore.Files.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
                context.contentResolver.query(
                    uri,
                    arrayOf(MediaStore.MediaColumns.RELATIVE_PATH),
                    null,
                    null,
                    null
                )?.use { cursor ->
                    val column = cursor.getColumnIndex(MediaStore.MediaColumns.RELATIVE_PATH)
                    if (column >= 0) {
                        while (cursor.moveToNext()) {
                            addDirectoryAndParents(directories, cursor.getString(column))
                        }
                    }
                }
            }
        }
        // Drop trees MediaStore will reject on insert (device error:
        // "Primary directory Books not allowed … allowed [Download, Documents]").
        return directories
            .filterNot { path -> isMediaStoreBlockedPrimary(path) }
            .sortedWith(
                compareBy<String> { if (it == DEFAULT_DIRECTORY) 0 else 1 }.thenBy { it }
            )
    }

    /** Top-level folders that MediaStore.Files will not accept for app inserts. */
    private fun isMediaStoreBlockedPrimary(path: String): Boolean {
        val primary = path.substringBefore('/').trim()
        return primary.equals("Books", ignoreCase = true) ||
            primary.equals("Audiobooks", ignoreCase = true) ||
            primary.equals("Podcasts", ignoreCase = true) ||
            primary.equals("Ringtones", ignoreCase = true) ||
            primary.equals("Alarms", ignoreCase = true) ||
            primary.equals("Notifications", ignoreCase = true)
    }

    private fun addDirectoryAndParents(target: MutableSet<String>, value: String?) {
        var current = value?.trim()?.trimEnd('/').orEmpty()
        while (current.isNotBlank()) {
            val sanitized = runCatching { sanitizeDirectoryPath(current) }.getOrNull()
            if (!sanitized.isNullOrBlank()) target += sanitized
            current = current.substringBeforeLast('/', "")
        }
    }

    private fun getOrCreateUploadSession(
        uploadId: String,
        path: RelativeFilePath,
        mimeType: String,
        destination: String,
        totalSize: Long,
        totalChunks: Int,
        chunkSize: Int
    ): UploadSession {
        uploads[uploadId]?.let { existing ->
            validateSession(existing, path, mimeType, destination, totalSize, totalChunks, chunkSize)
            return existing
        }

        return synchronized(uploads) {
            uploads[uploadId]?.let { existing ->
                validateSession(existing, path, mimeType, destination, totalSize, totalChunks, chunkSize)
                return@synchronized existing
            }
            val created = UploadSession(
                uploadId = uploadId,
                path = path,
                mimeType = mimeType,
                destination = destination,
                totalSize = totalSize,
                totalChunks = totalChunks,
                chunkSize = chunkSize,
                tempFile = createTempFile(uploadId, totalSize)
            )
            uploads[uploadId] = created
            created
        }
    }

    private fun validateSession(
        session: UploadSession,
        path: RelativeFilePath,
        mimeType: String,
        destination: String,
        totalSize: Long,
        totalChunks: Int,
        chunkSize: Int
    ) {
        if (
            session.path != path || session.mimeType != mimeType ||
            session.destination != destination ||
            session.totalSize != totalSize || session.totalChunks != totalChunks ||
            session.chunkSize != chunkSize
        ) {
            throw IOException("同一个上传任务的分片参数不一致")
        }
    }

    private fun createTempFile(uploadId: String, totalSize: Long): File {
        val directory = context.cacheDir.resolve("sender")
        if (!directory.exists() && !directory.mkdirs()) {
            throw IOException("无法创建临时上传目录")
        }
        val file = directory.resolve("$uploadId.part")
        if (file.exists() && !file.delete()) {
            throw IOException("无法覆盖上一次未完成的上传")
        }
        RandomAccessFile(file, "rw").use { random ->
            random.setLength(totalSize)
        }
        return file
    }

    private fun writeChunk(
        session: UploadSession,
        chunkIndex: Int,
        input: InputStream,
        length: Long
    ) {
        RandomAccessFile(session.tempFile, "rw").use { random ->
            random.seek(chunkIndex.toLong() * session.chunkSize.toLong())
            val buffer = ByteArray(BUFFER_SIZE)
            var remaining = length
            while (remaining > 0L) {
                val read = input.read(buffer, 0, minOf(buffer.size.toLong(), remaining).toInt())
                if (read < 0) throw IOException("上传中断")
                if (read == 0) continue
                random.write(buffer, 0, read)
                remaining -= read
            }
        }
    }

    private fun publishUpload(session: UploadSession) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            publishToMediaStore(session)
        } else {
            publishToLegacyStorage(session)
        }
    }

    private fun publishToMediaStore(session: UploadSession) {
        val relativePath = listOf(session.destination, session.path.directory)
            .filter { it.isNotBlank() }
            .joinToString("/") + "/"
        val collection = if (
            session.destination == Environment.DIRECTORY_DOWNLOADS ||
            session.destination.startsWith("${Environment.DIRECTORY_DOWNLOADS}/")
        ) {
            MediaStore.Downloads.EXTERNAL_CONTENT_URI
        } else {
            MediaStore.Files.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
        }
        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, session.path.fileName)
            put(MediaStore.MediaColumns.MIME_TYPE, session.mimeType)
            put(MediaStore.MediaColumns.RELATIVE_PATH, relativePath)
            put(MediaStore.MediaColumns.IS_PENDING, 1)
        }
        val resolver = context.contentResolver
        val uri = resolver.insert(collection, values)
            ?: throw IOException("无法创建目标文件")
        try {
            resolver.openOutputStream(uri)?.use { output ->
                FileInputStream(session.tempFile).use { input ->
                    copyExactly(input, output, session.totalSize)
                }
            } ?: throw IOException("无法打开目标文件")

            resolver.update(
                uri,
                ContentValues().apply { put(MediaStore.MediaColumns.IS_PENDING, 0) },
                null,
                null
            )
        } catch (error: Exception) {
            runCatching { resolver.delete(uri, null, null) }
            throw error
        }
    }

    private fun publishToLegacyStorage(session: UploadSession) {
        val directory = context
            .getExternalFilesDir(null)
            ?.resolve(session.destination)
            ?.resolve(session.path.directory)
            ?: throw IOException("无法访问 Download 目录")
        if (!directory.exists() && !directory.mkdirs()) {
            throw IOException("无法创建目标目录")
        }
        val target = File(directory, session.path.fileName)
        FileInputStream(session.tempFile).use { input ->
            target.outputStream().use { output ->
                copyExactly(input, output, session.totalSize)
            }
        }
    }

    private fun copyExactly(input: InputStream, output: OutputStream, length: Long) {
        val buffer = ByteArray(BUFFER_SIZE)
        var remaining = length
        while (remaining > 0L) {
            val read = input.read(buffer, 0, minOf(buffer.size.toLong(), remaining).toInt())
            if (read < 0) throw IOException("上传中断")
            if (read == 0) continue
            output.write(buffer, 0, read)
            remaining -= read
        }
        output.flush()
    }

    private fun drainExactly(input: InputStream, length: Long) {
        val buffer = ByteArray(BUFFER_SIZE)
        var remaining = length
        while (remaining > 0L) {
            val read = input.read(buffer, 0, minOf(buffer.size.toLong(), remaining).toInt())
            if (read < 0) throw IOException("上传中断")
            if (read == 0) continue
            remaining -= read
        }
    }

    private fun readRequest(input: InputStream): HttpRequest? {
        val bytes = ByteArrayOutputStream()
        var previous = 0
        var previousPrevious = 0
        var previousPreviousPrevious = 0

        while (bytes.size() < MAX_HEADER_BYTES) {
            val value = input.read()
            if (value < 0) return null
            bytes.write(value)
            if (
                previousPreviousPrevious == '\r'.code &&
                previousPrevious == '\n'.code &&
                previous == '\r'.code &&
                value == '\n'.code
            ) {
                break
            }
            previousPreviousPrevious = previousPrevious
            previousPrevious = previous
            previous = value
        }

        if (bytes.size() >= MAX_HEADER_BYTES) {
            throw IOException("请求头过大")
        }

        val lines = String(bytes.toByteArray(), StandardCharsets.ISO_8859_1)
            .removeSuffix("\r\n\r\n")
            .split("\r\n")
        val requestLine = lines.firstOrNull()?.split(' ', limit = 3) ?: return null
        if (requestLine.size < 2) return null

        val headers = buildMap {
            lines.drop(1).forEach { line ->
                val separator = line.indexOf(':')
                if (separator > 0) {
                    put(
                        line.substring(0, separator).trim().lowercase(Locale.US),
                        line.substring(separator + 1).trim()
                    )
                }
            }
        }
        return HttpRequest(
            method = requestLine[0].uppercase(Locale.US),
            target = requestLine[1],
            path = requestLine[1].substringBefore('?').substringBefore('#'),
            headers = headers
        )
    }

    private fun sendError(output: OutputStream, status: String, message: String) {
        sendJson(
            output = output,
            status = status,
            body = "{\"ok\":false,\"error\":${jsonString(message)}}"
        )
    }

    private fun sendJson(output: OutputStream, status: String, body: String) {
        sendJsonOrHtml(
            output = output,
            status = status,
            contentType = "application/json; charset=utf-8",
            body = body.toByteArray(StandardCharsets.UTF_8)
        )
    }

    private fun sendJsonOrHtml(
        output: OutputStream,
        status: String,
        contentType: String,
        body: ByteArray
    ) {
        val header = buildString {
            append("HTTP/1.1 ").append(status).append("\r\n")
            append("Content-Type: ").append(contentType).append("\r\n")
            append("Content-Length: ").append(body.size).append("\r\n")
            append("Cache-Control: no-store\r\n")
            append("Connection: close\r\n")
            append("\r\n")
        }
        output.write(header.toByteArray(StandardCharsets.ISO_8859_1))
        output.write(body)
        output.flush()
    }

    private data class HttpRequest(
        val method: String,
        val target: String,
        val path: String,
        val headers: Map<String, String>
    )

    private data class RelativeFilePath(
        val directory: String,
        val fileName: String
    )

    private class UploadSession(
        val uploadId: String,
        val path: RelativeFilePath,
        val mimeType: String,
        val destination: String,
        val totalSize: Long,
        val totalChunks: Int,
        val chunkSize: Int,
        val tempFile: File,
        val received: BooleanArray = BooleanArray(totalChunks),
        var receivedBytes: Long = 0L,
        var published: Boolean = false
    )

    private companion object {

        fun queryParameter(target: String, name: String): String? {
            val query = target.substringAfter('?', missingDelimiterValue = "")
                .substringBefore('#')
            return query.split('&')
                .asSequence()
                .mapNotNull { item ->
                    val separator = item.indexOf('=')
                    if (separator < 0) return@mapNotNull null
                    val key = runCatching {
                        URLDecoder.decode(item.substring(0, separator), "UTF-8")
                    }.getOrNull() ?: return@mapNotNull null
                    if (key != name) return@mapNotNull null
                    runCatching {
                        URLDecoder.decode(item.substring(separator + 1), "UTF-8")
                    }.getOrNull()
                }
                .firstOrNull()
        }

        fun isValidUploadId(value: String): Boolean =
            value.length in 8..128 && value.all { it.isLetterOrDigit() || it == '_' || it == '-' }

        fun sanitizeRelativePath(value: String): RelativeFilePath {
            val segments = value
                .replace('\\', '/')
                .split('/')
                .filter { it.isNotBlank() && it != "." && it != ".." }
                .map { sanitizePathSegment(it) }
                .filter { it.isNotBlank() }
            val fileName = segments.lastOrNull()?.let(::sanitizeFileName)
                ?: "inkboard-${System.currentTimeMillis()}.bin"
            val directory = segments.dropLast(1).joinToString("/").take(MAX_DIRECTORY_LENGTH)
            return RelativeFilePath(directory = directory, fileName = fileName)
        }

        fun sanitizeDirectoryPath(value: String?): String {
            val requested = value
                ?.replace('\\', '/')
                ?.trim()
                .orEmpty()
            if (requested.isBlank()) return DEFAULT_DIRECTORY

            val rawSegments = requested.split('/')
            if (rawSegments.any { it == ".." }) {
                throw IllegalArgumentException("保存目录不能包含 ..")
            }
            val segments = rawSegments
                .filter { it.isNotBlank() && it != "." }
                .map { sanitizePathSegment(it) }
                .filter { it.isNotBlank() }
            if (segments.firstOrNull()?.equals("Android", ignoreCase = true) == true) {
                throw IllegalArgumentException("不能写入 Android 受保护目录")
            }
            return segments.joinToString("/")
                .take(MAX_DIRECTORY_LENGTH)
                .ifBlank { DEFAULT_DIRECTORY }
        }

        fun sanitizePathSegment(value: String): String = value
            .replace(Regex("[\\u0000-\\u001f\\u007f]"), "_")
            .replace('/', '_')
            .replace('\\', '_')
            .trim()
            .take(MAX_PATH_SEGMENT_LENGTH)

        fun sanitizeFileName(value: String): String {
            val candidate = value
                .substringAfterLast('/')
                .substringAfterLast('\\')
                .replace(Regex("[\\u0000-\\u001f\\u007f]"), "_")
                .trim()
                .take(MAX_FILE_NAME_LENGTH)
            return candidate.ifBlank { "inkboard-${System.currentTimeMillis()}.bin" }
        }

        fun guessMimeType(fileName: String): String {
            val extension = fileName.substringAfterLast('.', "").lowercase(Locale.US)
            return MimeTypeMap.getSingleton().getMimeTypeFromExtension(extension)
                ?: "application/octet-stream"
        }

        fun jsonString(value: String): String = buildString {
            append('"')
            value.forEach { char ->
                when (char) {
                    '\\' -> append("\\\\")
                    '"' -> append("\\\"")
                    '\n' -> append("\\n")
                    '\r' -> append("\\r")
                    '\t' -> append("\\t")
                    else -> append(char)
                }
            }
            append('"')
        }

        fun localIpv4Address(): String? {
            val interfaces = runCatching {
                Collections.list(NetworkInterface.getNetworkInterfaces())
            }.getOrDefault(emptyList())

            data class Candidate(val interfaceName: String, val address: String)
            val candidates = interfaces
                .filter { networkInterface ->
                    runCatching {
                        networkInterface.isUp &&
                            !networkInterface.isLoopback &&
                            !networkInterface.isVirtual
                    }.getOrDefault(false)
                }
                .flatMap { networkInterface ->
                    Collections.list(networkInterface.inetAddresses)
                        .filterIsInstance<Inet4Address>()
                        .filterNot { it.isLoopbackAddress }
                        .mapNotNull { address ->
                            address.hostAddress?.let {
                                Candidate(networkInterface.name, it)
                            }
                        }
                }

            return candidates
                .sortedWith(
                    compareBy<Candidate> {
                        when {
                            it.interfaceName.startsWith("wlan", ignoreCase = true) -> 0
                            it.interfaceName.startsWith("eth", ignoreCase = true) -> 1
                            else -> 2
                        }
                    }.thenBy { it.address }
                )
                .firstOrNull()
                ?.address
        }

        const val PORT_START = 8765
        const val PORT_END = 8785
        const val BUFFER_SIZE = 64 * 1024
        const val MAX_HEADER_BYTES = 64 * 1024
        const val MAX_UPLOAD_BYTES = 8L * 1024L * 1024L * 1024L
        const val MIN_CHUNK_SIZE = 64 * 1024
        const val MAX_CHUNK_SIZE = 16 * 1024 * 1024
        const val MAX_CHUNKS = 1_000_000
        const val CLIENT_TIMEOUT_MS = 120_000
        const val MAX_DIRECTORY_LENGTH = 512
        const val MAX_PATH_SEGMENT_LENGTH = 120
        const val MAX_FILE_NAME_LENGTH = 160
        const val DEFAULT_DIRECTORY = "Download/InkBoard"
    }
}

