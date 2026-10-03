package io.github.meiyongai.toki.hook;

import java.util.*;
import org.jf.dexlib2.iface.*;
import org.jf.dexlib2.iface.instruction.*;
import org.jf.dexlib2.iface.reference.*;

/** 倍速关系契约：沿状态读写、播放器接口调用和实验开关关系解析，仅读取当前 DEX。 */
final class HostSpeedIndex {
    private static final String AWEME = "Lcom/ss/android/ugc/aweme/feed/model/Aweme;";
    private static final String CONTROLLER = "Lcom/ss/android/ugc/aweme/feed/controller/PlayerController;";
    private static final Set<String> SIGNATURES = Set.of("()Ljava/lang/Object;", "()Ljava/lang/Boolean;", "()Z",
            "(" + AWEME + ")Z", "(" + AWEME + ")F", "(" + AWEME + "Ljava/lang/String;)V",
            "(" + AWEME + "Ljava/lang/String;ZZ)Z", "(F" + AWEME + "Ljava/lang/String;Ljava/lang/String;)V");
    private final List<Properties> controllers = new ArrayList<>(), managers = new ArrayList<>();
    private final Set<String> experiments = new HashSet<>();
    private final Map<String, Set<String>> initializers = new HashMap<>();
    private final Map<String, String> gates = new HashMap<>();
    private final Map<String, Set<String>> sources = new TreeMap<>();

    /** 方法摘要只在访问该类期间保留，不持有 DEX 对象。 */
    private record Body(String owner, String name, String signature, int flags, Set<String> strings,
                        Set<String> calls, Set<String> interfaceCalls, Set<String> types, Set<Long> numbers,
                        Map<String, Set<String>> fields) {
        /** @return 完整调用描述。无参数。Callers: collect。 */
        String reference() { return owner + "->" + name + signature; }
        /** @param opcode 字段访问指令。@param type 字段类型。@return 本类匹配字段集合。Callers: manager。 */
        Set<String> fields(String opcode, String type) {
            Set<String> result = new TreeSet<>();
            for (String field : fields.getOrDefault(opcode, Set.of()))
                if (field.startsWith(owner + "->") && field.endsWith(":" + type)) result.add(field);
            return result;
        }
    }

    /** @param method DEX 方法。@return 独立行为摘要。Callers: collect。 */
    private static Body body(Method method) {
        Set<String> strings = new HashSet<>(), calls = new HashSet<>(), interfaceCalls = new HashSet<>(), types = new HashSet<>();
        Set<Long> numbers = new HashSet<>();
        Map<String, Set<String>> fields = new HashMap<>();
        if (method.getImplementation() != null) for (var instruction : method.getImplementation().getInstructions()) {
            String opcode = instruction.getOpcode().name();
            if (instruction instanceof WideLiteralInstruction number) numbers.add(number.getWideLiteral());
            if (!(instruction instanceof ReferenceInstruction ref)) continue;
            var target = ref.getReference();
            if (target instanceof StringReference string) strings.add(string.getString());
            if (target instanceof MethodReference call && opcode.startsWith("INVOKE_")) calls.add(call.toString());
            if (target instanceof MethodReference call && opcode.startsWith("INVOKE_INTERFACE")) interfaceCalls.add(call.toString());
            if (target instanceof TypeReference type) types.add(type.getType());
            if (target instanceof FieldReference field) fields.computeIfAbsent(opcode, key -> new HashSet<>()).add(field.toString());
        }
        return new Body(method.getDefiningClass(), method.getName(), signature(method), method.getAccessFlags(),
                strings, calls, interfaceCalls, types, numbers, fields);
    }

    /**
     * 收集本类的倍速关系，菜单仅保留构建浮点档位表的静态方法与开关关联。
     * @param type 当前 DEX 类；调用结束后不保留 DEX 引用。
     * @return 无。Callers: HostDexIndex.scan 的共享遍历。
     */
    void collect(ClassDef type) {
        List<Body> bodies = new ArrayList<>();
        // 单类摘要在本次访问结束后释放；只保存有关联的候选。
        boolean manager = false;
        Set<String> initializer = Set.of();
        boolean hasGate = false;
        for (Method method : type.getMethods()) {
            if (method.getImplementation() == null) continue;
            String signature = signature(method);
            boolean shape = signature.equals("()Ljava/util/List;") || signature.equals("(" + type.getType() + ")Ljava/lang/Object;");
            if (!type.getType().equals(CONTROLLER) && !method.getName().equals("<clinit>") && !shape &&
                    !SIGNATURES.contains(signature)) continue;
            Body value = body(method);
            bodies.add(value);
            if (value.strings.contains("swipe_up_lock_persist") &&
                    value.signature.equals("(F" + AWEME + "Ljava/lang/String;Ljava/lang/String;)V")) manager = true;
            if (value.strings.contains("feed_support_3x_speed") &&
                    value.calls.contains("Ljava/lang/Boolean;->valueOf(Z)Ljava/lang/Boolean;")) experiments.add(type.getType());
            if (value.name.equals("<clinit>")) initializer = value.types;
            if ((value.flags & 8) != 0 && value.signature.equals("()Z") &&
                    value.calls.contains("Ljava/lang/Boolean;->booleanValue()Z")) {
                gates.put(value.reference(), type.getType());
                hasGate = true;
            }
            if ((value.flags & 8) != 0 && shape && value.types.contains("[Ljava/lang/Float;") &&
                    value.calls.contains("Ljava/lang/Float;->valueOf(F)Ljava/lang/Float;") &&
                    value.numbers.containsAll(Set.of(1056964608L, 1065353216L, 1069547520L, 1073741824L, 1077936128L)) &&
                    value.calls.stream().anyMatch(call -> call.endsWith("([Ljava/lang/Object;)Ljava/util/List;"))) {
                Set<String> gateCalls = new HashSet<>();
                for (String call : value.calls) if (call.endsWith("()Z")) gateCalls.add(call);
                sources.put(value.reference(), gateCalls);
            }
        }
        if (hasGate) initializers.put(type.getType(), initializer);
        if (manager) {
            Properties candidate = manager(bodies);
            if (!managers.contains(candidate)) managers.add(candidate);
        }
        if (type.getType().equals(CONTROLLER)) {
            Properties candidate = controller(bodies);
            if (!controllers.contains(candidate)) controllers.add(candidate);
        }
    }

    /** @param method 方法引用。@return 完整参数与返回签名。Callers: collect、body。 */
    private static String signature(MethodReference method) {
        StringBuilder result = new StringBuilder("(");
        method.getParameterTypes().forEach(result::append);
        return result.append(')').append(method.getReturnType()).toString();
    }

    /** @param values 方法摘要。@param signature 精确签名。@return 唯一静态方法，歧义为空。Callers: manager。 */
    private static Body unique(List<Body> values, String signature) {
        var matches = values.stream().filter(value -> value.signature.equals(signature) && (value.flags & 8) != 0).collect(java.util.stream.Collectors.toList());
        return matches.size() == 1 ? matches.get(0) : null;
    }

    /** @param values 已识别中枢的方法集合。@return 完整契约或明确失败。Callers: collect。 */
    private static Properties manager(List<Body> values) {
        Properties result = new Properties();
        Body select = unique(values, "(F" + AWEME + "Ljava/lang/String;Ljava/lang/String;)V");
        Body restore = unique(values, "(" + AWEME + "Ljava/lang/String;)V");
        Body gate = unique(values, "(" + AWEME + ")Z");
        Body reset = unique(values, "(" + AWEME + "Ljava/lang/String;ZZ)Z");
        var queries = values.stream().filter(value -> value.signature.equals("(" + AWEME + ")F") && (value.flags & 8) != 0).collect(java.util.stream.Collectors.toList());
        if (select == null || restore == null || gate == null || reset == null || queries.size() != 2 ||
                !select.strings.containsAll(Set.of("long_press", "click_share_button", "swipe_up_lock_persist")) ||
                !restore.calls.containsAll(Set.of(select.reference(), gate.reference())) ||
                !gate.calls.contains("Lcom/ss/android/ugc/aweme/feed/model/Aweme;->isCanPlay()Z")) return result;
        Set<String> floats = select.fields("SPUT", "F"), persist = restore.fields("SGET", "F"), enabled = restore.fields("SGET_BOOLEAN", "Z");
        Set<String> queryFields = new TreeSet<>();
        for (Body query : queries) {
            var fields = query.fields("SGET", "F");
            if (fields.size() != 1 || !query.calls.contains("Landroid/text/TextUtils;->equals(Ljava/lang/CharSequence;Ljava/lang/CharSequence;)Z")) return result;
            queryFields.addAll(fields);
        }
        if (floats.size() != 3 || persist.size() != 1 || enabled.size() != 1 || queryFields.size() != 2 ||
                !Collections.disjoint(queryFields, persist) || !floats.containsAll(queryFields) || !floats.containsAll(persist) ||
                !reset.fields("SPUT", "F").equals(queryFields)) return result;
        result.setProperty("SPEED_MANAGER", binary(select.owner));
        bind(result, "SPEED_MANAGER", "select", select);
        bind(result, "SPEED_MANAGER", "restore", restore);
        bind(result, "SPEED_MANAGER", "gate", gate);
        bind(result, "SPEED_MANAGER", "reset", reset);
        for (int i = 0; i < queries.size(); i++) {
            bind(result, "SPEED_MANAGER", "query" + i, queries.get(i));
            result.setProperty("member.SPEED_MANAGER.current" + i, member(queries.get(i).fields("SGET", "F").iterator().next()));
        }
        result.setProperty("member.SPEED_MANAGER.persist", member(persist.iterator().next()));
        result.setProperty("member.SPEED_MANAGER.enabled", member(enabled.iterator().next()));
        return result;
    }

    /** @param values 控制器方法集合。@return 通过真实接口调速调用确定的播放器契约。Callers: collect。 */
    private static Properties controller(List<Body> values) {
        Properties result = new Properties();
        var speeds = values.stream().filter(value -> value.signature.equals("(F)V") && (value.flags & 8) == 0 &&
                value.strings.containsAll(Set.of("begin_speed", "speed_begin"))).collect(java.util.stream.Collectors.toList());
        if (speeds.size() != 1) return result;
        Body speed = speeds.get(0);
        var calls = speed.interfaceCalls.stream().filter(call -> call.startsWith("LX/") && call.endsWith("(F)V")).collect(java.util.stream.Collectors.toList());
        if (calls.size() != 1) return result;
        String manager = calls.get(0).substring(0, calls.get(0).indexOf("->"));
        var getters = values.stream().filter(value -> value.name.equals("getPlayerManager") && value.signature.equals("()" + manager)).collect(java.util.stream.Collectors.toList());
        var setters = values.stream().filter(value -> value.name.equals("setPlayerManager") && value.signature.equals("(" + manager + ")V")).collect(java.util.stream.Collectors.toList());
        var render = values.stream().filter(value -> value.name.equals("onRenderReady") && value.signature.matches("\\(L[^;]+;\\)V")).collect(java.util.stream.Collectors.toList());
        if (getters.size() != 1 || setters.size() != 1 || render.size() != 1 || !speed.calls.contains(getters.get(0).reference())) return result;
        result.setProperty("PLAYER_CONTROLLER", binary(CONTROLLER));
        result.setProperty("PLAYER_MANAGER", binary(manager));
        result.setProperty("member.PLAYER_MANAGER.setSpeed", member(calls.get(0)));
        bind(result, "PLAYER_CONTROLLER", "setSpeed", speed);
        bind(result, "PLAYER_CONTROLLER", "getManager", getters.get(0));
        bind(result, "PLAYER_CONTROLLER", "setManager", setters.get(0));
        bind(result, "PLAYER_CONTROLLER", "renderReady", render.get(0));
        return result;
    }

    /** @param symbol 请求符号。@return 已验证属性或明确错误，不返回部分契约。Callers: HostDexIndex.scan。 */
    Properties resolve(String symbol) {
        Properties result = new Properties();
        if (symbol.equals("SPEED_MANAGER") || symbol.equals("PLAYER_CONTROLLER") || symbol.equals("PLAYER_MANAGER")) {
            List<Properties> candidates = symbol.equals("SPEED_MANAGER") ? managers : controllers;
            if (candidates.size() == 1 && candidates.get(0).containsKey(symbol)) {
                var candidate = candidates.get(0);
                for (String key : candidate.stringPropertyNames()) if (key.equals(symbol) || key.startsWith("member." + symbol + "."))
                    result.setProperty(key, candidate.getProperty(key));
            }
        } else if (symbol.equals("SPEED_OPTIONS")) {
            var gateCandidates = gates.entrySet().stream().filter(entry ->
                    !Collections.disjoint(initializers.getOrDefault(entry.getValue(), Set.of()), experiments)).collect(java.util.stream.Collectors.toList());
            if (gateCandidates.size() == 1) {
                String gate = gateCandidates.get(0).getKey();
                var matching = sources.entrySet().stream().filter(entry -> entry.getValue().contains(gate)).map(Map.Entry::getKey).collect(java.util.stream.Collectors.toList());
                if (!matching.isEmpty() && matching.stream().anyMatch(method -> method.endsWith("()Ljava/util/List;"))) {
                    result.setProperty(symbol, binary(gateCandidates.get(0).getValue()));
                    result.setProperty("member.SPEED_OPTIONS.gate", member(gate));
                    result.setProperty("members.SPEED_OPTIONS.sources", String.join("\n", matching));
                }
            }
        } else throw new IllegalArgumentException("未声明的倍速关系契约：" + symbol);
        if (!result.containsKey(symbol)) result.setProperty("error." + symbol, "倍速行为或关联契约不唯一/不完整");
        return result;
    }

    /** @param type DEX 类型。@return JVM 类名。Callers: controller、manager、resolve。 */
    private static String binary(String type) { return type.substring(1, type.length() - 1).replace('/', '.'); }
    /** @param reference DEX 字段或方法引用。@return 成员描述。Callers: controller、manager、resolve。 */
    private static String member(String reference) { return reference.substring(reference.indexOf("->") + 2); }
    /** @param result 输出。@param symbol 符号。@param role 角色。@param method 实际方法。@return 无。Callers: controller、manager。 */
    private static void bind(Properties result, String symbol, String role, Body method) {
        result.setProperty("member." + symbol + "." + role, method.name + method.signature);
    }
}
