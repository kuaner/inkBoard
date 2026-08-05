package ai.openduo.inkboard.data

import android.content.Context
import org.json.JSONObject

/**
 * Bundled animal SVGs (`assets/home_ornaments`) powering the home mini-game.
 */
object HomeOrnaments {

    private const val ASSET_DIR = "home_ornaments"
    private const val MANIFEST = "$ASSET_DIR/manifest.json"

    /** 找不同：2 行 × 3 列。 */
    const val GRID_SIZE = 6

    @Volatile
    private var cachedPaths: List<String>? = null

    data class OddOneOutRound(
        /** Exactly [GRID_SIZE] asset paths; five share one animal, one is unique. */
        val tiles: List<String>,
        val oddIndex: Int
    ) {
        init {
            require(tiles.size == GRID_SIZE) { "need $GRID_SIZE tiles" }
            require(oddIndex in tiles.indices) { "oddIndex out of range" }
        }
    }

    fun paths(context: Context): List<String> {
        cachedPaths?.let { return it }
        val fromManifest = runCatching {
            context.assets.open(MANIFEST).bufferedReader(Charsets.UTF_8).use { it.readText() }
                .let { body ->
                    val icons = JSONObject(body).optJSONArray("icons") ?: return@let emptyList()
                    buildList(icons.length()) {
                        for (i in 0 until icons.length()) {
                            val file = icons.optJSONObject(i)?.optString("file")?.trim().orEmpty()
                            if (file.endsWith(".svg")) add("$ASSET_DIR/$file")
                        }
                    }
                }
        }.getOrDefault(emptyList())

        val resolved = fromManifest.ifEmpty {
            context.assets.list(ASSET_DIR)
                ?.filter { it.endsWith(".svg") }
                ?.map { "$ASSET_DIR/$it" }
                .orEmpty()
        }
        cachedPaths = resolved
        return resolved
    }

    /**
     * Build a 找不同 board: five copies of animal A, one of animal B.
     * [excludeCommon] avoids reusing the previous majority animal when possible.
     */
    fun newOddOneOut(
        context: Context,
        excludeCommon: String? = null
    ): OddOneOutRound? {
        val all = paths(context)
        if (all.size < 2) return null

        val pool = if (excludeCommon != null && all.any { it != excludeCommon }) {
            all.filter { it != excludeCommon }
        } else {
            all
        }
        val common = pool.random()
        val odd = all.filter { it != common }.random()
        val oddIndex = (0 until GRID_SIZE).random()
        val tiles = List(GRID_SIZE) { index -> if (index == oddIndex) odd else common }
        return OddOneOutRound(tiles = tiles, oddIndex = oddIndex)
    }
}
