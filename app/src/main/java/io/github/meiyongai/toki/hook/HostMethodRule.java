package io.github.meiyongai.toki.hook;

import java.util.HashSet;
import java.util.Set;
import java.util.List;
import java.util.Map;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Collection;
import java.util.regex.Pattern;
import org.jf.dexlib2.Opcode;
import org.jf.dexlib2.iface.Method;
import org.jf.dexlib2.iface.instruction.ReferenceInstruction;
import org.jf.dexlib2.iface.instruction.TwoRegisterInstruction;
import org.jf.dexlib2.iface.reference.FieldReference;
import org.jf.dexlib2.iface.reference.MethodReference;
import org.jf.dexlib2.iface.reference.StringReference;
import org.jf.dexlib2.iface.reference.TypeReference;

/** 方法行为契约：签名、调用方式、字段访问和角色关系，不依赖混淆成员名称。 */
record HostMethodRule(String symbol, String signature, Set<String> anchors, String role, Pattern signaturePattern) {
    /**
     * 解析五列方法规则；未知条件直接拒绝，不能静默放宽契约。
     * @param columns symbol、method-v1、参数与返回类型、以竖线分隔的行为条件、成员角色。
     * @return 不可变规则。
     * Callers: HostDexIndex.scan。
     */
    static HostMethodRule parse(String[] columns) {
        if (columns.length != 5 || !columns[1].equals("method-v1") ||
                columns[0].isBlank() || !columns[2].matches("\\([^)]*\\).+") ||
                !columns[4].matches("[A-Za-z][A-Za-z0-9]*"))
            throw new IllegalArgumentException("无效方法特征规则");
        Set<String> anchors = new HashSet<>();
        for (String anchor : columns[3].split("\\|", -1)) {
            if (!(anchor.startsWith("string:") && anchor.length() > 7 ||
                    anchor.startsWith("call:L") && anchor.contains(";->") && anchor.contains("(") ||
                    anchor.startsWith("write:L") && anchor.endsWith(";") ||
                    anchor.matches("invoke:INVOKE_(VIRTUAL|INTERFACE|STATIC|SUPER|DIRECT):(?:L[^;]+;->[^()]+|obfuscated:|self:)\\([^)]*\\).+") ||
                    anchor.matches("field:L[^;]+;->[^:]+:.+") ||
                    anchor.matches("name:(?:<init>|[A-Za-z_$][A-Za-z0-9_$]*)") ||
                    anchor.matches("called-by:[A-Za-z][A-Za-z0-9]*") ||
                    anchor.matches("class:L[^;]+;") || anchor.matches("owner:L[^;]+;") || anchor.equals("static") || anchor.equals("instance")))
                throw new IllegalArgumentException("无效方法行为条件：" + anchor);
            anchors.add(anchor);
        }
        Pattern signaturePattern = columns[2].contains("LX/*;")
                ? Pattern.compile(Pattern.quote(columns[2]).replace("LX/*;", "\\ELX/[^;\\[()]+;\\Q")) : null;
        return new HostMethodRule(columns[0], columns[2], Set.copyOf(anchors), columns[4], signaturePattern);
    }

    /**
     * 检查同一方法内的局部条件；called-by 关系由扫描器在完整候选集合中验证。
     * @param method DEX 方法；默认要求实例方法，static 条件显式选择静态方法。
     * @return 签名与全部局部条件成立时为 true；无方法体或未显式声明名称的初始化方法为 false。
     * Callers: HostDexIndex.scan。
     */
    boolean matches(Method method) {
        return matchesHeader(method, descriptor(method)) && matchesEvidence(evidence(method));
    }

    /** @param method DEX 方法。@return 精确签名，每个方法只构造一次。Callers: matches、Index.match。 */
    private static String descriptor(Method method) {
        StringBuilder actual = new StringBuilder("(");
        for (CharSequence parameter : method.getParameterTypes()) actual.append(parameter);
        return actual.append(')').append(method.getReturnType()).toString();
    }

    /** @param method 候选方法。@param actual 已计算签名。@return 是否满足头部契约。Callers: matches、Index.match。 */
    private boolean matchesHeader(Method method, String actual) {
        var body = method.getImplementation();
        if (body == null || method.getName().startsWith("<") && !anchors.contains("name:" + method.getName()) ||
                ((method.getAccessFlags() & 8) != 0) != anchors.contains("static")) return false;
        return signaturePattern == null ? signature.equals(actual) : signaturePattern.matcher(actual).matches();
    }

    /** @param method 已通过头部筛选的方法。@return 一次指令遍历产生的行为集合。Callers: matches、Index.match。 */
    private static Set<String> evidence(Method method) {
        var body = method.getImplementation();
        int parameterWords = 0;
        for (CharSequence parameter : method.getParameterTypes()) {
            parameterWords += parameter.toString().equals("J") || parameter.toString().equals("D") ? 2 : 1;
        }
        int receiver = body.getRegisterCount() - parameterWords - 1;
        Set<String> found = new HashSet<>();
        found.add((method.getAccessFlags() & 8) != 0 ? "static" : "instance");
        found.add("name:" + method.getName());
        found.add("owner:" + method.getDefiningClass());
        for (var instruction : body.getInstructions()) {
            if (!(instruction instanceof ReferenceInstruction reference)) continue;
            var target = reference.getReference();
            if (instruction.getOpcode() == Opcode.CONST_CLASS && target instanceof TypeReference type)
                found.add("class:" + type.getType());
            if (target instanceof StringReference string) found.add("string:" + string.getString());
            if (target instanceof MethodReference call && instruction.getOpcode().name().startsWith("INVOKE_"))
                found.add("call:" + call);
            if (target instanceof MethodReference call && instruction.getOpcode().name().startsWith("INVOKE_")) {
                String descriptor = call.toString();
                found.add("invoke:" + instruction.getOpcode().name().replace("_RANGE", "") + ":" + descriptor);
                if (call.getDefiningClass().startsWith("LX/")) {
                    String signature = descriptor.substring(descriptor.indexOf('('));
                    found.add("invoke:" + instruction.getOpcode().name().replace("_RANGE", "") + ":obfuscated:" + signature);
                }
                if (call.getDefiningClass().equals(method.getDefiningClass()))
                    found.add("invoke:" + instruction.getOpcode().name().replace("_RANGE", "") + ":self:" +
                            descriptor.substring(descriptor.indexOf('(')));
            }
            if (target instanceof FieldReference field && (instruction.getOpcode().name().startsWith("IGET") ||
                    instruction.getOpcode().name().startsWith("SGET")))
                found.add("field:" + field);
            if (instruction.getOpcode() == Opcode.IPUT_OBJECT && target instanceof FieldReference field &&
                    field.getDefiningClass().equals(method.getDefiningClass()) &&
                    ((TwoRegisterInstruction) instruction).getRegisterB() == receiver)
                found.add("write:" + field.getType());
        }
        return found;
    }

    /** @param found 当前方法行为集合。@return 是否包含本规则全部局部条件。Callers: matches、Index.match。 */
    private boolean matchesEvidence(Set<String> found) {
        for (String anchor : anchors) if (!anchor.startsWith("called-by:") && !found.contains(anchor)) return false;
        return true;
    }

    /**
     * 仅解码字符串常量，用于在解析所有成员引用前排除不相关方法。
     * @param method 有方法体的候选。
     * @return 精确字符串集合，不跳过任何字符串指令。
     * Callers: Index.match、HostCommentIndex.collect。
     */
    static Set<String> strings(Method method) {
        Set<String> strings = new HashSet<>();
        for (var instruction : method.getImplementation().getInstructions()) {
            if (instruction.getOpcode() == Opcode.CONST_STRING || instruction.getOpcode() == Opcode.CONST_STRING_JUMBO)
                strings.add(((StringReference) ((ReferenceInstruction) instruction).getReference()).getString());
        }
        return strings;
    }

    /** 按精确签名索引规则；通配签名单独筛选，匹配候选共享一次行为提取。 */
    static final class Index {
        private final Map<String, List<HostMethodRule>> exact = new HashMap<>();
        private final List<HostMethodRule> patterns = new ArrayList<>();

        /** @param rules 完整方法规则集合。Callers: HostDexIndex.scan、HostMethodRuleTest。 */
        Index(Collection<HostMethodRule> rules) {
            for (HostMethodRule rule : rules) {
                if (rule.signaturePattern == null) exact.computeIfAbsent(rule.signature, key -> new ArrayList<>()).add(rule);
                else patterns.add(rule);
            }
        }

        /** @param method 当前方法。@return 全部匹配规则，保留同签名多角色及歧义。Callers: HostDexIndex.scan、HostMethodRuleTest。 */
        List<HostMethodRule> match(Method method) {
            if (method.getImplementation() == null) return List.of();
            String actual = descriptor(method);
            List<HostMethodRule> candidates = new ArrayList<>();
            for (HostMethodRule rule : exact.getOrDefault(actual, List.of()))
                if (rule.matchesHeader(method, actual)) candidates.add(rule);
            for (HostMethodRule rule : patterns)
                if (rule.matchesHeader(method, actual)) candidates.add(rule);
            if (candidates.isEmpty()) return List.of();
            if (candidates.stream().anyMatch(rule -> rule.anchors.stream().anyMatch(anchor -> anchor.startsWith("string:")))) {
                Set<String> strings = strings(method);
                candidates.removeIf(rule -> rule.anchors.stream().anyMatch(anchor ->
                        anchor.startsWith("string:") && !strings.contains(anchor.substring(7))));
                if (candidates.isEmpty()) return List.of();
            }
            Set<String> found = evidence(method);
            candidates.removeIf(rule -> !rule.matchesEvidence(found));
            return candidates;
        }
    }
}
