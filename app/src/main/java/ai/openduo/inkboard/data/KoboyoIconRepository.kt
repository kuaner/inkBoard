package ai.openduo.inkboard.data

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.IOException

data class KoboyoIconCategory(
    val group: String,
    val subgroup: String? = null,
    val label: String = subgroup ?: group
) {
    /** A direct group has no visible subgroup in the picker. */
    val key: String get() = subgroup?.let { "$group/$it" } ?: group

    /** Bundled catalog file stem: group--subgroup (or group--group). */
    val endpointKey: String get() = "${group}--${subgroup ?: group}"
}

data class KoboyoIconGroup(
    val key: String,
    val label: String
)

data class KoboyoIcon(
    val slug: String,
    val name: String,
    val localPath: String? = null
)

data class IconPickerState(
    val category: KoboyoIconCategory = KoboyoIconRepository.CATEGORIES.first {
        it.key == "object/misc"
    },
    val page: Int = 0,
    val pageCount: Int = 1,
    val icons: List<KoboyoIcon> = emptyList(),
    val loading: Boolean = false,
    val error: String? = null
)

/**
 * Offline Koboyo icon browser.
 *
 * Catalogs and SVGs are pre-bundled under `assets/koboyo/` by
 * `scripts/download_koboyo_icons.py`. No network access at runtime.
 */
class KoboyoIconRepository(private val context: Context) {

    companion object {
        // A 4 × 4 manual page makes better use of the landscape e-ink panel
        // and avoids unnecessary page flashes while choosing an icon.
        private const val PAGE_SIZE = 16
        private const val ASSET_ROOT = "koboyo"
        private const val CATALOG_DIR = "$ASSET_ROOT/catalogs"
        const val SVG_DIR = "$ASSET_ROOT/svg"

        val GROUPS = listOf(
            KoboyoIconGroup("face", "FACE"),
            KoboyoIconGroup("mark", "MARK"),
            KoboyoIconGroup("object", "OBJECT"),
            KoboyoIconGroup("people", "PEOPLE"),
            KoboyoIconGroup("scene", "SCENE"),
            KoboyoIconGroup("solid", "SOLID")
        )

        val CATEGORIES = buildList {
            add(KoboyoIconCategory(group = "face", label = "FACE"))
            addAll(categories("mark", listOf(
                "icon", "mark", "math", "solid", "status", "symbol", "texture"
            )))
            addAll(categories("object", listOf(
                "agriculture", "ai", "animal", "aviation", "beauty", "business",
                "civic", "collage", "commerce", "communication", "compsci", "concept",
                "content", "craft", "culture", "data", "dev", "document", "education",
                "entertainment", "environment", "event", "everyday", "family", "fantasy",
                "fashion", "feature", "file", "food", "gaming", "hand", "health", "history",
                "hobby", "home", "hospitality", "incident", "industry", "infographic",
                "interface", "iso", "legal", "logistics", "maritime", "mark", "marketing",
                "mascot", "math", "media", "military", "misc", "nature", "place", "plan",
                "playful", "print", "property", "rail", "safety", "science", "security",
                "social", "sport", "stationery", "symbol", "sysdesign", "tech", "telecom",
                "time", "tool", "toy", "travel", "vehicle", "workplace", "workshop"
            )))
            addAll(categories("people", listOf(
                "action", "business", "character", "creative", "culture", "education",
                "emotion", "event", "famous", "figure", "gesture", "group", "health",
                "home", "interface", "misc", "outdoor", "person", "pose", "present",
                "profession", "sport", "tech", "vehicle", "workplace"
            )))
            addAll(categories("scene", listOf("uistate", "vignette")))
            add(KoboyoIconCategory(group = "solid", label = "SOLID"))
        }

        const val DEFAULT_PAGE_SIZE = PAGE_SIZE

        private fun categories(
            group: String,
            subgroups: List<String>
        ): List<KoboyoIconCategory> = subgroups.map { subgroup ->
            KoboyoIconCategory(group = group, subgroup = subgroup)
        }

        fun assetSvgPath(slug: String): String? {
            val safe = slug.replace(Regex("[^a-zA-Z0-9._-]"), "_").ifBlank { return null }
            return "$SVG_DIR/$safe.svg"
        }
    }

    private val categoryCache = mutableMapOf<String, List<KoboyoIcon>>()

    suspend fun loadPage(
        category: KoboyoIconCategory,
        requestedPage: Int
    ): IconPickerState = withContext(Dispatchers.IO) {
        val allIcons = runCatching { loadCategory(category) }.getOrElse {
            return@withContext IconPickerState(
                category = category,
                page = 0,
                pageCount = 1,
                icons = emptyList(),
                error = "本地图标目录不可用"
            )
        }
        val pageCount = maxOf(1, (allIcons.size + PAGE_SIZE - 1) / PAGE_SIZE)
        val page = requestedPage.coerceIn(0, pageCount - 1)
        val pageIcons = allIcons
            .drop(page * PAGE_SIZE)
            .take(PAGE_SIZE)
            .map { icon ->
                icon.copy(localPath = resolveIconPath(icon))
            }

        IconPickerState(
            category = category,
            page = page,
            pageCount = pageCount,
            icons = pageIcons
        )
    }

    /** Asset path for a bundled SVG, e.g. `koboyo/svg/fox.svg`. No I/O download. */
    fun resolveIconPath(icon: KoboyoIcon): String? {
        val path = assetSvgPath(icon.slug) ?: return null
        return if (assetExists(path)) path else null
    }

    private fun loadCategory(category: KoboyoIconCategory): List<KoboyoIcon> {
        categoryCache[category.endpointKey]?.let { return it }

        val body = readAssetText("$CATALOG_DIR/${category.endpointKey}.json")
            ?: throw IOException("missing catalog ${category.endpointKey}")

        val entries = JSONObject(body).optJSONArray("entries") ?: return emptyList()
        val icons = buildList(entries.length()) {
            for (index in 0 until entries.length()) {
                val entry = entries.optJSONArray(index) ?: continue
                val slug = entry.optString(0).trim()
                if (slug.isBlank()) continue
                val name = entry.optString(1).trim().ifBlank { slug }
                val path = assetSvgPath(slug)
                // Skip catalog rows whose SVG was not bundled.
                if (path == null || !assetExists(path)) continue
                add(KoboyoIcon(slug = slug, name = name, localPath = path))
            }
        }
        categoryCache[category.endpointKey] = icons
        return icons
    }

    private fun readAssetText(path: String): String? = runCatching {
        context.assets.open(path).bufferedReader(Charsets.UTF_8).use { it.readText() }
    }.getOrNull()

    private fun assetExists(path: String): Boolean = runCatching {
        context.assets.open(path).close()
        true
    }.getOrDefault(false)
}
