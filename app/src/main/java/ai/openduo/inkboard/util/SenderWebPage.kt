package ai.openduo.inkboard.util

/** Static browser client served by the temporary Sender HTTP endpoint. */
internal val SenderUploadPage = """
            <!doctype html>
            <html lang="zh-CN">
            <head>
              <meta charset="utf-8">
              <meta name="viewport" content="width=device-width, initial-scale=1">
              <title>INKBOARD / SENDER</title>
              <style>
                :root { color-scheme: light; font-family: system-ui, -apple-system, sans-serif; }
                body { max-width: 760px; margin: 0 auto; padding: 32px 20px 48px; color: #111; background: #f7f7f5; }
                h1 { margin: 0 0 8px; font-size: 28px; letter-spacing: .08em; }
                p { line-height: 1.6; }
                .rule { border: 0; border-top: 2px solid #111; margin: 24px 0; }
                .destination { display: grid; gap: 8px; margin-bottom: 16px; }
                .destination label { font-size: 13px; font-weight: 800; letter-spacing: .08em; }
                select, .custom { box-sizing: border-box; width: 100%; padding: 14px 12px; border: 2px solid #111; background: #fff; font-size: 16px; }
                .pickers { display: grid; grid-template-columns: 1fr 1fr; gap: 12px; }
                .picker { display: block; padding: 16px 12px; border: 2px solid #111; background: #fff; font-size: 16px; font-weight: 700; text-align: center; cursor: pointer; }
                .picker input { display: none; }
                button { margin-top: 16px; width: 100%; padding: 16px; border: 0; background: #111; color: #fff; font-size: 16px; font-weight: 700; }
                button:disabled { background: #777; }
                #selection { min-height: 1.6em; font-weight: 600; }
                #status { min-height: 1.6em; white-space: pre-wrap; font-weight: 700; }
                .note { font-size: 14px; color: #444; }
                @media (max-width: 560px) { .pickers { grid-template-columns: 1fr; } }
              </style>
            </head>
            <body>
              <h1>INKBOARD / SENDER</h1>
              <p>选择文件，或选择一个文件夹。文件夹的相对目录会保留在平板中。</p>
              <hr class="rule">
              <div class="destination">
                <label for="destination">保存到平板目录</label>
                <select id="destination"><option value="">正在读取目录…</option></select>
                <input id="custom" class="custom" type="text" placeholder="或输入目录，例如 Books/Onyx">
              </div>
              <div class="pickers">
                <label class="picker">选择文件<input id="files" type="file" multiple></label>
                <label class="picker">选择文件夹<input id="folder" type="file" webkitdirectory directory multiple></label>
              </div>
              <p id="selection">未选择文件</p>
              <button id="send" type="button">分片上传</button>
              <p id="status">等待选择文件</p>
              <p class="note">大文件会按 8 MB 分片传输，单片失败会自动重试。保存目录可在上方选择或输入。</p>
              <p class="note">这是一次性局域网服务。传输完成后，请在平板返回；服务会立即关闭。</p>
              <script>
                const filesInput = document.getElementById('files');
                const folderInput = document.getElementById('folder');
                const send = document.getElementById('send');
                const selection = document.getElementById('selection');
                const status = document.getElementById('status');
                const destination = document.getElementById('destination');
                const custom = document.getElementById('custom');
                const chunkSize = 8 * 1024 * 1024;
                let selectedFiles = [];

                async function loadDirectories() {
                  try {
                    const response = await fetch('/directories', { cache: 'no-store' });
                    const result = await response.json();
                    if (!response.ok || !result.ok) throw new Error(result.error || '目录读取失败');
                    destination.innerHTML = '';
                    for (const directory of result.directories) {
                      const option = document.createElement('option');
                      option.value = directory;
                      option.textContent = directory;
                      destination.appendChild(option);
                    }
                  } catch (error) {
                    destination.innerHTML = '<option value="Download/InkBoard">Download/InkBoard</option>';
                    status.textContent = '目录读取失败，已使用 Download/InkBoard';
                  }
                }

                loadDirectories();

                function selectedDestination() {
                  return custom.value.trim() || destination.value || 'Download/InkBoard';
                }

                function setFiles(list, label) {
                  selectedFiles = Array.from(list || []);
                  if (!selectedFiles.length) {
                    selection.textContent = '未选择文件';
                    return;
                  }
                  const total = selectedFiles.reduce((sum, file) => sum + file.size, 0);
                  const megabytes = (total / 1024 / 1024).toFixed(1);
                  selection.textContent = label + ' · ' + selectedFiles.length + ' 个文件 · ' + megabytes + ' MB';
                }

                filesInput.addEventListener('change', () => setFiles(filesInput.files, '文件'));
                folderInput.addEventListener('change', () => setFiles(folderInput.files, '文件夹'));

                function makeUploadId() {
                  if (window.crypto && crypto.randomUUID) return crypto.randomUUID().replaceAll('-', '');
                  return String(Date.now()) + String(Math.random()).slice(2);
                }

                async function sendChunk(url, chunk) {
                  let lastError;
                  for (let attempt = 0; attempt < 3; attempt++) {
                    try {
                      const response = await fetch(url, {
                        method: 'POST',
                        headers: { 'Content-Type': chunk.type || 'application/octet-stream' },
                        body: chunk
                      });
                      const result = await response.json();
                      if (!response.ok || !result.ok) throw new Error(result.error || '上传失败');
                      return result;
                    } catch (error) {
                      lastError = error;
                      if (attempt < 2) await new Promise(resolve => setTimeout(resolve, 700 * (attempt + 1)));
                    }
                  }
                  throw lastError || new Error('上传失败');
                }

                async function uploadFile(file, fileNumber, fileCount, uploadedBefore, totalBytes) {
                  const uploadId = makeUploadId();
                  const totalChunks = Math.max(1, Math.ceil(file.size / chunkSize));
                  const relativePath = file.webkitRelativePath || file.name;
                  for (let chunkIndex = 0; chunkIndex < totalChunks; chunkIndex++) {
                    const start = chunkIndex * chunkSize;
                    const chunk = file.slice(start, Math.min(start + chunkSize, file.size));
                    const query = new URLSearchParams({
                      uploadId: uploadId,
                      chunkIndex: String(chunkIndex),
                      totalChunks: String(totalChunks),
                      totalSize: String(file.size),
                      chunkSize: String(chunkSize),
                      path: relativePath,
                      destination: selectedDestination()
                    });
                    await sendChunk('/upload?' + query.toString(), chunk);
                    const sent = uploadedBefore + start + chunk.size;
                    const percent = totalBytes ? Math.floor(sent / totalBytes * 100) : 100;
                    status.textContent = '上传中 ' + fileNumber + '/' + fileCount + ' · ' + percent + '%\n' + relativePath;
                  }
                }

                send.addEventListener('click', async () => {
                  if (!selectedFiles.length) { status.textContent = '请先选择文件或文件夹'; return; }
                  send.disabled = true;
                  filesInput.disabled = true;
                  folderInput.disabled = true;
                  const totalBytes = selectedFiles.reduce((sum, file) => sum + file.size, 0);
                  let uploadedBefore = 0;
                  try {
                    for (let index = 0; index < selectedFiles.length; index++) {
                      const file = selectedFiles[index];
                      await uploadFile(file, index + 1, selectedFiles.length, uploadedBefore, totalBytes);
                      uploadedBefore += file.size;
                    }
                    status.textContent = '上传完成 · ' + selectedFiles.length + ' 个文件';
                  } catch (error) {
                    status.textContent = '上传失败：' + (error.message || error);
                  } finally {
                    send.disabled = false;
                    filesInput.disabled = false;
                    folderInput.disabled = false;
                  }
                });
              </script>
            </body>
            </html>
        """.trimIndent()

