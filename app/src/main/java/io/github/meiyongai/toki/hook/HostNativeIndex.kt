package io.github.meiyongai.toki.hook

import org.luckypray.dexkit.DexKitBridge
import org.luckypray.dexkit.query.FindClass
import org.luckypray.dexkit.query.FindMethod
import org.luckypray.dexkit.query.matchers.ClassMatcher
import org.luckypray.dexkit.query.matchers.MethodMatcher
import org.luckypray.dexkit.query.matchers.MethodsMatcher
import org.luckypray.dexkit.query.matchers.UsingFieldMatcher
import org.luckypray.dexkit.util.DexSignUtil
import java.util.Properties
import java.util.zip.ZipFile

/** 原生检索必要条件，完整成员、语义、关系和唯一性统一由 HostDexIndex 验证。 */
internal object HostNativeIndex {
    private val library by lazy {
        try {
            System.loadLibrary("dexkit")
        } catch (error: LinkageError) {
            // 将原生链接错误交给适配任务的失败发布流程，保留根因且不启动备用扫描。
            throw IllegalStateException("原生 DEX 引擎加载失败", error)
        }
    }

    /**
     * 对所有代码包进行原生候选检索，再对候选类执行完整规则验证。
     * @param paths 基础包及全部 Split；资源包不创建原生索引。
     * @param rules 所需规则。
     * @param progress 原生查询及契约校验的进度。
     * @return 唯一结果或明确的未匹配原因；原生失败直接传播。
     * Callers: HostSymbols.prepare。
     */
    fun scan(paths: List<String>, rules: String, progress: HostDexIndex.ProgressListener): Properties {
        library
        val selected = HostDexIndex.Candidates(linkedSetOf(), linkedSetOf(), linkedSetOf(), linkedSetOf())
        val methods = methodQueries(rules).map { selected.methods() to it } + relationQueries(rules, selected)
        val classes = classQueries(rules)
        val totalQueries = paths.size * (methods.size + classes.size)
        var completed = 0
        for (path in paths) {
            val hasCode = ZipFile(path).use { apk -> apk.entries().asSequence().any {
                it.name.matches(Regex("classes(?:[2-9]|[1-9][0-9]+)?\\.dex"))
            } }
            if (hasCode) DexKitBridge.create(path).use { bridge ->
                for ((types, query) in methods) {
                    bridge.findMethod(FindMethod().matcher(query)).forEach {
                        types.add(it.descriptor.substringBefore("->"))
                    }
                    progress.onProgress(++completed, totalQueries * 2)
                }
                for (query in classes) {
                    bridge.findClass(FindClass().matcher(query)).forEach { selected.shapes().add(it.descriptor) }
                    progress.onProgress(++completed, totalQueries * 2)
                }
            } else completed += methods.size + classes.size
        }
        android.util.Log.i("TokiNativeIndex", "候选类：methods=${selected.methods().size} shapes=${selected.shapes().size} speed=${selected.speed().size} comments=${selected.comments().size}")
        return HostDexIndex.scan(paths, rules, { done, total ->
            progress.onProgress(totalQueries + (done.toLong() * totalQueries / total).toInt(), totalQueries * 2)
        }, selected)
    }

    /**
     * 每个符号从首个角色进入，保留该角色的全部替代规则；其余同类角色交由统一验证。
     * 完整匹配必然包含入口角色，避免通用关联角色扩大候选集合。
     * @param rules 五列规则文本。
     * @return 所有可能参与最终契约的原生方法查询。
     * Callers: scan、HostNativeQueryTest。
     */
    internal fun methodQueries(rules: String): List<MethodMatcher> {
        val rows = rows(rules)
        val entries = rows.filter { it[1] == "method-v1" }.groupBy { it[0] }.values.flatMap { symbol ->
            symbol.filter { it[4] == symbol.first()[4] }
        }
        val queries = entries.map { row ->
            signature(row[2]).apply {
                val anchors = row[3].split('|')
                val strings = anchors.filter { it.startsWith("string:") }.map { it.removePrefix("string:") }
                if (strings.isNotEmpty()) usingEqStrings(strings)
                val calls = anchors.filter { it.startsWith("call:") }.map {
                    MethodMatcher().descriptor(it.removePrefix("call:"))
                }
                if (calls.isNotEmpty()) invokeMethods(MethodsMatcher().methods(calls))
                anchors.filter { it.startsWith("field:") }.forEach {
                    addUsingField(UsingFieldMatcher(it.removePrefix("field:")))
                }
                if (anchors.any { it.startsWith("class:") }) opNames(listOf("const-class"))
                if ("static" in anchors) modifiers(java.lang.reflect.Modifier.STATIC)
                anchors.firstOrNull { it.startsWith("name:") }?.let { name(it.removePrefix("name:")) }
                anchors.firstOrNull { it.startsWith("owner:") }?.let {
                    declaredClass(ClassMatcher().descriptor(it.removePrefix("owner:")))
                }
            }
        }
        return queries
    }

    /**
     * 为跨类关系分别收集候选，避免通用实验开关触发其他契约的指令解析。
     * @param rules 所需规则。
     * @param selected 各关系的候选接收集合。
     * @return 候选集合与原生必要条件。
     * Callers: scan。
     */
    private fun relationQueries(rules: String, selected: HostDexIndex.Candidates): List<Pair<MutableSet<String>, MethodMatcher>> {
        val rows = rows(rules)
        val queries = mutableListOf<Pair<MutableSet<String>, MethodMatcher>>()
        if (rows.any { it[1] == "speed-v1" }) {
            queries += selected.speed() to MethodMatcher().declaredClass("com.ss.android.ugc.aweme.feed.controller.PlayerController")
            queries += selected.speed() to MethodMatcher().usingEqStrings("swipe_up_lock_persist")
            queries += selected.speed() to MethodMatcher().usingEqStrings("feed_support_3x_speed")
            queries += selected.speed() to signature("()Z").invokeMethods(MethodsMatcher().add(
                MethodMatcher().descriptor("Ljava/lang/Boolean;->booleanValue()Z")))
            for (returns in listOf("java.util.List", "java.lang.Object")) {
                queries += selected.speed() to MethodMatcher().returnType(returns).invokeMethods(MethodsMatcher().add(
                    MethodMatcher().descriptor("Ljava/lang/Float;->valueOf(F)Ljava/lang/Float;")))
            }
        }
        if (rows.any { it[1] == "comment-v1" }) {
            for (anchor in listOf("comment_action_menu", "copy_comment", "copy_label", "CommentData(commentText=")) {
                queries += selected.comments() to MethodMatcher().usingEqStrings(anchor)
            }
        }
        return queries
    }

    /**
     * 按已验证结构摘要读取其必要结构条件；缺少条件是发布错误，不能转为全量扫描。
     * @param rules 当前规则。
     * @return 类字段数量、方法数量与方法名查询。
     * Callers: scan、HostNativeQueryTest。
     */
    internal fun classQueries(rules: String): List<ClassMatcher> {
        val shapes = checkNotNull(javaClass.getResourceAsStream("/toki-host-shapes.tsv")) {
            "模块缺少类结构检索条件"
        }.bufferedReader().useLines { lines -> lines.filter { it.isNotBlank() && !it.startsWith('#') }
            .associate { line -> line.split('\t').let { it[0] to it } } }
        return rows(rules).filter { it[1].matches(Regex("[0-9a-f]{64}")) }.map { it[1] }.distinct().map { shape ->
            val row = checkNotNull(shapes[shape]) { "类结构缺少原生检索条件：$shape" }
            require(row.size == 4) { "类结构检索条件格式错误" }
            ClassMatcher().fieldCount(row[1].toInt()).methodCount(row[2].toInt()).apply {
                if (row[3] != "-") row[3].split('|').forEach { addMethod(MethodMatcher().name(it)) }
            }
        }
    }

    /** @param rules 五列规则。@return 有效规则行。Callers: methodQueries、classQueries。 */
    private fun rows(rules: String): List<List<String>> = rules.lineSequence()
        .filter { it.isNotBlank() && !it.startsWith('#') }.map {
            it.split('\t').also { row -> require(row.size == 5) { "规则必须有五列" } }
        }.toList()

    /**
     * 将 DEX 参数与返回类型转换为原生查询；混淆通配符仅放宽候选阶段。
     * @param descriptor 不含方法名的 DEX 签名。
     * @return 精确参数顺序和返回类型的必要条件。
     * Callers: methodQueries、HostNativeQueryTest。
     */
    internal fun signature(descriptor: String): MethodMatcher {
        val close = descriptor.indexOf(')')
        require(descriptor.startsWith('(') && close > 0) { "无效方法签名：$descriptor" }
        val parameters = descriptor.substring(1, close)
        val types = Regex("\\[*([ZBSCIFJD]|L[^;]+;)").findAll(parameters).map { it.value }.toList()
        require(types.joinToString("") == parameters) { "无效参数签名：$descriptor" }
        val result = MethodMatcher().paramTypes(types.map { if ('*' in it) null else DexSignUtil.getTypeName(it) })
        val returns = descriptor.substring(close + 1)
        if ('*' !in returns) result.returnType(DexSignUtil.getTypeName(returns))
        return result
    }
}
