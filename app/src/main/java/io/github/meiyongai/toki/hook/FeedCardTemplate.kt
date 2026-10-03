package io.github.meiyongai.toki.hook

import androidx.core.net.toUri

/**
 * 推荐卡的模板身份，不保存查询参数或业务内容。
 * @param channel 模板业务通道。
 * @param path 去除查询参数与片段后的资源路径。
 */
internal data class FeedCardTemplate(val channel: String, val path: String) {
    /**
     * 判断是否为游戏兴趣选择模板，而非整个游戏或直播业务。
     * @return 是否命中已确认的兴趣推荐资源；无入参。
     * Callers: FeedTopicKind.classify。
     */
    fun isGameInterest(): Boolean {
        val resource = "tiktok_live_interaction_game_fyp/pages/interest_card/template.js"
        return path == resource || path.endsWith("/$resource") ||
            (channel == "tiktok_live_interaction_game_fyp" && path == "pages/interest_card/template.js")
    }

    companion object {
        /**
         * 将直接资源地址或携带 url / channel / bundle 的模板路由转换为无查询参数的身份。
         * 只解析指定路由字段，不在任意查询参数、业务 JSON 或显示文案中搜索关键字。
         * @param channel 模型提供的模板通道，可为空。
         * @param address 模型提供的模板地址，可为空。
         * @return 模板身份；空地址、非分层 URI 或缺少资源路径时返回 null。
         * Callers: FeedModelReader.read、FeedCardTemplateTest。
         */
        fun read(channel: String?, address: String?): FeedCardTemplate? {
            if (address.isNullOrBlank()) return null
            val route = address.toUri()
            if (!route.isHierarchical) return null
            val url = route.getQueryParameter("url")
            val resource = (url ?: route.getQueryParameter("bundle") ?: address).toUri()
            if (!resource.isHierarchical) return null
            val path = resource.path?.trimStart('/')?.takeIf { it.isNotEmpty() } ?: return null
            val routeChannel = route.getQueryParameter("channel").orEmpty()
            if (!channel.isNullOrEmpty() && routeChannel.isNotEmpty() && channel != routeChannel) return null
            return FeedCardTemplate(if (channel.isNullOrEmpty()) routeChannel else channel, path)
        }
    }
}

/** 已核实的整页话题推荐类型；不按话题名称、游戏名称或语言分类。 */
internal enum class FeedTopicKind {
    SEARCH_TRENDING, SEARCH_INTEREST, INTEREST_SELECTION, GAME_INTEREST;

    companion object {
        /**
         * 根据原生插卡协议、兴趣卡类型或精确模板资源识别推荐类型。
         * @param entry 从宿主模型提取的条目事实。
         * @return 话题推荐类别；普通内容和未确认类别返回 null。
         * Callers: FeedFilterPolicy.reason、FeedModelReader.read、单元测试。
         */
        fun classify(entry: FeedEntry): FeedTopicKind? = when {
            entry.awemeType in 104..105 && (entry.cardType == 34 || entry.cardType == 35) -> SEARCH_TRENDING
            entry.awemeType in 104..105 && entry.cardType == 38 -> SEARCH_INTEREST
            entry.awemeType == 601 -> INTEREST_SELECTION
            entry.cardTemplates.any { it.isGameInterest() } -> GAME_INTEREST
            else -> null
        }
    }
}
