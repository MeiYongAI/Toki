package io.github.meiyongai.toki.model

import org.json.JSONArray
import org.json.JSONObject

/**
 * 关键词过滤分类枚举。
 *
 * 定义视频过滤支持的三大维度：文案、话题标签与作者。
 */
enum class KeywordCategory(val title: String) {
    DESC("文案"),
    TAG("标签"),
    AUTHOR("作者")
}

/**
 * 关键词多分类过滤配置实体。
 *
 * 将过滤规则按文案、话题标签、作者三个独立维度进行管理与精准匹配。
 *
 * @property desc 视频文案与描述关键词黑名单。
 * @property tag 话题标签黑名单。
 * @property author 作者昵称与唯一用户名黑名单。
 */
data class KeywordFilterConfig(
    val desc: List<String> = emptyList(),
    val tag: List<String> = emptyList(),
    val author: List<String> = emptyList()
) {
    /**
     * 计算全部维度的关键词总数。
     */
    val totalCount: Int
        get() = desc.size + tag.size + author.size

    /**
     * 获取指定分类下的关键词列表。
     *
     * Args:
     *     category (KeywordCategory): 目标分类枚举。
     *
     * Returns:
     *     List<String>: 该分类下的关键词列表。
     *
     * Callers:
     *     - `io.github.meiyongai.toki.ui.screen.DashboardScreen`: 根据选中的选项卡渲染列表。
     */
    fun getList(category: KeywordCategory): List<String> = when (category) {
        KeywordCategory.DESC -> desc
        KeywordCategory.TAG -> tag
        KeywordCategory.AUTHOR -> author
    }

    /**
     * 更新指定分类下的关键词列表，生成新的配置对象。
     *
     * 自动处理项的前后空白字符修剪、去除空串及去重。
     *
     * Args:
     *     category (KeywordCategory): 目标分类枚举。
     *     newList (List<String>): 更新后的关键词列表。
     *
     * Returns:
     *     KeywordFilterConfig: 新的配置实体。
     *
     * Callers:
     *     - `io.github.meiyongai.toki.ui.screen.DashboardScreen`: 增删修改分类下的关键词。
     */
    fun updateCategory(category: KeywordCategory, newList: List<String>): KeywordFilterConfig = when (category) {
        KeywordCategory.DESC -> copy(desc = sanitizeList(newList))
        KeywordCategory.TAG -> copy(tag = sanitizeList(newList))
        KeywordCategory.AUTHOR -> copy(author = sanitizeList(newList))
    }

    /**
     * 将实体序列化为 JSON 格式字符串。
     *
     * Args:
     *     无入参。
     *
     * Returns:
     *     String: 规范化的 JSON 格式文本。
     *
     * Callers:
     *     - `io.github.meiyongai.toki.ui.screen.DashboardScreen`: 保存配置持久化。
     *     - `io.github.meiyongai.toki.hook.FeedFilterHook.serializeKeywords`: 序列化桥接方法。
     */
    fun toJson(): String {
        val root = JSONObject()
        root.put("desc", JSONArray(desc))
        root.put("tag", JSONArray(tag))
        root.put("author", JSONArray(author))
        return root.toString()
    }

    companion object {
        /**
         * 规整字符串列表，去除前后空白、过滤空白项并保留唯一项。
         *
         * Args:
         *     list (List<String>): 待规整的原始列表。
         *
         * Returns:
         *     List<String>: 规范化后的字符串列表。
         *
         * Callers:
         *     - `io.github.meiyongai.toki.model.KeywordFilterConfig.updateCategory`: 分类规则更新时的数据清理。
         */
        private fun sanitizeList(list: List<String>): List<String> =
            list.map { it.trim() }.filter { it.isNotEmpty() }.distinct()

        /**
         * 解析 JSON 字符串为关键词多分类过滤配置实体。
         *
         * 空配置表示没有词条；无效非空文本必须报告解析错误。
         *
         * Args:
         *     jsonStr (String?): 持久化保存的 JSON 文本。
         *
         * Returns:
         *     KeywordFilterConfig: 结构化的关键词过滤配置实体。
         *
         * Callers:
         *     - `io.github.meiyongai.toki.hook.FeedFilterHook`: 拦截执行期间加载规则。
         *     - `io.github.meiyongai.toki.ui.screen.DashboardScreen`: 界面状态回显。
         */
        fun fromJson(jsonStr: String?): KeywordFilterConfig {
            if (jsonStr.isNullOrBlank()) return KeywordFilterConfig()
            val trimmed = jsonStr.trim()
            if (trimmed.startsWith("{")) {
                val jsonObject = JSONObject(trimmed)
                val descList = parseJsonArray(jsonObject.optJSONArray("desc"))
                val tagList = parseJsonArray(jsonObject.optJSONArray("tag"))
                val authorList = parseJsonArray(jsonObject.optJSONArray("author"))
                return KeywordFilterConfig(desc = descList, tag = tagList, author = authorList)
            } else if (trimmed.startsWith("[")) {
                val list = parseJsonArray(JSONArray(trimmed))
                return KeywordFilterConfig(desc = list)
            }
            throw IllegalArgumentException("关键词配置必须是 JSON 对象或数组")
        }

        /**
         * 解析单个 JSON 数组为去重且去空白的字符串列表。
         *
         * Args:
         *     array (JSONArray?): 待解析的 JSON 数组。
         *
         * Returns:
         *     List<String>: 规整后的字符串列表。
         *
         * Callers:
         *     - `io.github.meiyongai.toki.model.KeywordFilterConfig.Companion.fromJson`: 各字段解析提取。
         */
        private fun parseJsonArray(array: JSONArray?): List<String> {
            if (array == null) return emptyList()
            val result = mutableListOf<String>()
            for (i in 0 until array.length()) {
                val item = array.optString(i)?.trim()
                if (!item.isNullOrEmpty()) {
                    result.add(item)
                }
            }
            return result.distinct()
        }
    }
}
