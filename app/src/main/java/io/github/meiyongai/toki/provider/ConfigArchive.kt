package io.github.meiyongai.toki.provider

import org.json.JSONArray
import org.json.JSONObject
import org.json.JSONTokener
import java.io.ByteArrayOutputStream
import java.io.InputStream

/** 带格式版本的功能配置文件；不携带进程诊断、账户、缓存或管理端偏好。 */
object ConfigArchive {
    const val MAX_BYTES = 256 * 1024
    private const val FORMAT = "toki-config"
    private const val VERSION = 1

    /**
     * 从用户选择的文件有界读取配置，读取或校验失败不产生任何配置写入。
     * @param input 调用方负责关闭的输入流。
     * @return 完整验证的配置集合。
     * Callers: ConfigTransferSection、ConfigArchiveTest。
     */
    fun read(input: InputStream): Map<String, Any> {
        val bytes = ByteArrayOutputStream()
        val buffer = ByteArray(8192)
        while (true) {
            val count = input.read(buffer)
            if (count == -1) break
            require(bytes.size() + count <= MAX_BYTES) { "配置文件不能超过 256 KiB" }
            bytes.write(buffer, 0, count)
        }
        val decoder = Charsets.UTF_8.newDecoder()
            .onMalformedInput(java.nio.charset.CodingErrorAction.REPORT)
            .onUnmappableCharacter(java.nio.charset.CodingErrorAction.REPORT)
        return decode(decoder.decode(java.nio.ByteBuffer.wrap(bytes.toByteArray())).toString())
    }

    /**
     * 解析版本化文件并拒绝未知字段、类型和不完整的外层结构。
     * @param text UTF-8 解码后的文件内容。
     * @return 已验证的配置副本。
     * Callers: read、ConfigArchiveTest。
     */
    fun decode(text: String): Map<String, Any> {
        require(text.toByteArray(Charsets.UTF_8).size <= MAX_BYTES) { "配置文件不能超过 256 KiB" }
        val root = parseObject(text)
        require(root.keys().asSequence().toSet() == setOf("format", "version", "values")) { "配置文件结构不正确" }
        require(root.get("format") == FORMAT) { "不是 Toki 配置文件" }
        require(root.get("version") is Int && root.getInt("version") == VERSION) { "不支持的配置文件版本" }
        val data = root.get("values")
        require(data is JSONObject) { "values 必须为配置对象" }
        return validate(data.keys().asSequence().associateWith { data.get(it) })
    }

    /**
     * 验证配置类型、范围及关键词结构，禁止损坏的分类被当成空列表。
     * @param values 原始配置集合。
     * @return 验证完成的副本。
     * Callers: decode、encode、ConfigStore。
     */
    fun validate(values: Map<String, Any>): Map<String, Any> {
        val result = ConfigSchema.validate(values)
        val keywords = result["feed_filter_keywords_json"] as String?
        if (keywords != null) {
            val root = parseObject(keywords)
            for (category in root.keys()) {
                require(category in setOf("desc", "tag", "author")) { "不支持的关键词分类" }
                val list = root.get(category)
                require(list is JSONArray && list.length() <= 2000) { "关键词分类必须为数组，且不超过 2000 条" }
                for (index in 0 until list.length()) {
                    val item = list.get(index)
                    require(item is String && item.isNotBlank() && item.length <= 4096) { "关键词必须为非空文本，且不超过 4096 字符" }
                }
            }
        }
        return result
    }

    /**
     * 编码已验证的功能配置，不序列化任何内部元数据。
     * @param values 功能键值集合。
     * @return 适合直接写入所选文件的 UTF-8 字节。
     * Callers: ConfigTransferSection、ConfigArchiveTest。
     */
    fun encode(values: Map<String, Any>): ByteArray {
        val root = JSONObject().put("format", FORMAT).put("version", VERSION)
            .put("values", JSONObject(validate(values).toSortedMap()))
        val bytes = root.toString(2).toByteArray(Charsets.UTF_8)
        require(bytes.size <= MAX_BYTES) { "配置文件不能超过 256 KiB" }
        return bytes
    }

    /**
     * 读取唯一 JSON 对象并拒绝对象后的附加内容。
     * @param text JSON 文本。
     * @return 解析成功的对象。
     * Callers: decode、validate。
     */
    private fun parseObject(text: String): JSONObject {
        val tokenizer = JSONTokener(text)
        val value = tokenizer.nextValue()
        require(value is JSONObject && tokenizer.nextClean() == '\u0000') { "必须为单个 JSON 对象" }
        return value
    }
}
