package io.github.meiyongai.toki.hook;

import org.jf.dexlib2.Opcodes;
import org.jf.dexlib2.dexbacked.DexBackedDexFile;
import org.jf.dexlib2.iface.ClassDef;
import org.jf.dexlib2.iface.Method;
import org.jf.dexlib2.iface.instruction.ReferenceInstruction;
import org.jf.dexlib2.iface.instruction.WideLiteralInstruction;
import org.jf.dexlib2.iface.reference.StringReference;
import org.jf.dexlib2.iface.reference.TypeReference;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.*;
import java.util.function.Consumer;
import java.util.regex.Pattern;
import java.util.zip.ZipFile;

/** 纯 Java DEX 特征索引；只读取代码，不加载或执行候选宿主类。 */
public final class HostDexIndex {
    private static final Pattern OBFUSCATED = Pattern.compile("LX/[^;]+;|Lkotlin/jvm/internal/[A-Z][^;]+;");
    private static final ProgressListener NO_PROGRESS = (completed, total) -> {};

    /** 各类契约的原生候选互不扩大彼此的指令解析范围。 */
    public record Candidates(Set<String> methods, Set<String> shapes, Set<String> speed, Set<String> comments) {
        /** @return 全部候选类型；无参数。Callers: scan。 */
        public Set<String> allTypes() {
            Set<String> result = new HashSet<>(methods);
            result.addAll(shapes);
            result.addAll(speed);
            result.addAll(comments);
            return result;
        }
    }

    /** 方法候选按类与完整方法描述去重，同类多个匹配方法仍属于歧义。 */
    private record MethodTarget(String owner, String descriptor, String role, Set<String> calls) {}

    /** 一条完整指纹及其预解析成员契约；同符号的不同规则分别参与匹配。 */
    private record Rule(String symbol, String shape, String literals, String linked,
            Map<String, String> bindings) {
        private static Rule parse(String line) {
            String[] columns = line.split("\t", -1);
            if (columns.length != 5) throw new IllegalArgumentException("无效 DEX 特征规则：需要五列");
            for (String column : columns) {
                if (column.isEmpty()) throw new IllegalArgumentException("无效 DEX 特征规则：列不能为空");
            }
            return new Rule(columns[0], columns[1], columns[2], columns[3],
                    Map.copyOf(HostDexIndex.bindings(columns[4])));
        }
    }

    /** 扫描工作进度；每个 DEX 和每个符号各为 1000 单位，不表示预计耗时。 */
    @FunctionalInterface
    public interface ProgressListener {
        /**
         * 接收已经完成的实际扫描工作。
         * @param completed 已完成的工作单位。
         * @param total 本次 DEX 遍历（必要时包含第二轮）与符号校验的总单位。
         * Returns: 无。
         * Callers: visit、scan。
         */
        void onProgress(int completed, int total);
    }

    /**
     * 将混淆类型替换为结构占位符，保留所有未混淆业务类型与数组维度。
     * @param value DEX 类型或成员描述。
     * @return 规范化描述。
     * Callers: shape。
     */
    private static String normalize(String value) {
        return OBFUSCATED.matcher(value).replaceAll("L_obfuscated_;");
    }

    /**
     * 计算字段及方法的完整结构指纹；保留方法名和访问属性以拒绝成员契约变化。
     * @param type 候选 DEX 类。
     * @return SHA-256 结构指纹。
     * Callers: scan、离线规则生成器。
     */
    public static String shape(ClassDef type) {
        List<String> parts = new ArrayList<>();
        parts.add("super:" + normalize(String.valueOf(type.getSuperclass())));
        for (String value : type.getInterfaces()) parts.add("interface:" + normalize(value));
        for (var field : type.getFields()) parts.add("f:" + field.getName() + ":" +
                normalize(field.getType()) + ":" + field.getAccessFlags());
        for (Method method : type.getMethods()) {
            StringBuilder signature = new StringBuilder("m:").append(method.getName()).append('(');
            for (CharSequence value : method.getParameterTypes()) signature.append(normalize(value.toString()));
            signature.append(')').append(normalize(method.getReturnType())).append(':').append(method.getAccessFlags());
            parts.add(signature.toString());
        }
        Collections.sort(parts);
        return digest(String.join("\n", parts));
    }

    /**
     * 计算候选类的规范化指令语义，包含数值常量及成员引用，忽略混淆类型名称。
     * @param type 候选 DEX 类。
     * @return 按方法排序的指令语义 SHA-256。
     * Callers: scan、离线规则生成器。
     */
    public static String literals(ClassDef type) {
        Set<String> values = new TreeSet<>();
        for (Method method : type.getMethods()) {
            if (method.getImplementation() == null) continue;
            StringBuilder body = new StringBuilder(method.getName()).append(':');
            for (var instruction : method.getImplementation().getInstructions()) {
                body.append(instruction.getOpcode().name()).append(':');
                if (instruction instanceof ReferenceInstruction ref && ref.getReference() instanceof StringReference str) {
                    body.append(str.getString().length()).append(':').append(str.getString());
                } else if (instruction instanceof ReferenceInstruction ref) {
                    body.append(normalize(ref.getReference().toString()));
                }
                if (instruction instanceof WideLiteralInstruction literal) body.append(literal.getWideLiteral());
                body.append('\n');
            }
            values.add(body.toString());
        }
        return digest(String.join("\n", values));
    }

    /**
     * 提取静态初始化直接引用的类型，用于区分结构相同的配置开关。
     * @param type 候选类。
     * @return 不包含自身的引用类型集合。
     * Callers: linked、scan。
     */
    public static Set<String> dependencies(ClassDef type) {
        Set<String> result = new TreeSet<>();
        for (Method method : type.getMethods()) {
            if (!method.getName().equals("<clinit>") || method.getImplementation() == null) continue;
            for (var instruction : method.getImplementation().getInstructions()) {
                if (instruction instanceof ReferenceInstruction ref && ref.getReference() instanceof TypeReference target &&
                    !target.getType().equals(type.getType())) result.add(target.getType());
            }
        }
        return result;
    }

    /**
     * 获取候选初始化引用的一层类型字符串指纹，不保留整份 DEX 对象。
     * @param paths 所有代码包。
     * @param dependencies 候选类型到其依赖类型的映射。
     * @return 候选类型到链接语义指纹的映射。
     * @throws IOException 代码读取错误。
     * Callers: scan、离线规则生成器。
     */
    public static Map<String, String> linked(List<String> paths, Map<String, Set<String>> dependencies) throws IOException {
        return linked(paths, dependencies, NO_PROGRESS, 0, 1);
    }

    /**
     * 解析依赖指纹并报告第二轮遍历进度。
     * @param paths 全部代码包。
     * @param dependencies 候选及依赖映射。
     * @param progress 只读进度监听器。
     * @param offset 之前完成的工作单位。
     * @param total 全扫描工作单位。
     * @return 候选的链接语义指纹。
     * @throws IOException DEX读取错误。
     * Callers: linked、scan。
     */
    private static Map<String, String> linked(List<String> paths, Map<String, Set<String>> dependencies,
            ProgressListener progress, int offset, int total) throws IOException {
        Set<String> wanted = new HashSet<>();
        dependencies.values().forEach(wanted::addAll);
        Map<String, String> values = new HashMap<>();
        if (wanted.isEmpty()) {
            Map<String, String> result = new HashMap<>();
            dependencies.keySet().forEach(type -> result.put(type, digest("")));
            return result;
        }
        visit(paths, type -> { if (wanted.contains(type.getType())) values.put(type.getType(), literals(type)); },
                progress, offset, total);
        Map<String, String> result = new HashMap<>();
        dependencies.forEach((type, refs) -> {
            Set<String> hashes = new TreeSet<>();
            for (String ref : refs) if (values.containsKey(ref)) hashes.add(values.get(ref));
            result.put(type, digest(String.join("\n", hashes)));
        });
        return result;
    }

    /**
     * 顺序访问每个 APK 的全部 DEX；每次只保留一个 DEX 的字节缓冲。
     * @param paths 基础包和全部 Split 包路径。
     * @param visitor 每个类的只读访问器，不应保留 ClassDef。
     * @throws IOException APK 或 DEX 无法读取。
     * Callers: scan、离线规则生成器。
     */
    public static void visit(List<String> paths, Consumer<ClassDef> visitor) throws IOException {
        visit(paths, visitor, NO_PROGRESS, 0, 1);
    }

    /**
     * 逐 DEX 遍历并按实际处理的类数报告进度，不增加类加载或额外扫描。
     * @param paths 全部代码包。
     * @param visitor 类访问器。
     * @param progress 进度接收器。
     * @param offset 前序阶段工作单位。
     * @param total 全扫描工作单位。
     * Returns: 无。
     * @throws IOException APK或DEX读取错误。
     * Callers: visit、linked、scan。
     */
    private static void visit(List<String> paths, Consumer<ClassDef> visitor, ProgressListener progress,
            int offset, int total) throws IOException {
        int completedDex = 0;
        for (String path : paths) {
            try (ZipFile apk = new ZipFile(path)) {
                var entries = apk.entries();
                while (entries.hasMoreElements()) {
                    var entry = entries.nextElement();
                    if (!entry.getName().matches("classes(?:[2-9]|[1-9][0-9]+)?\\.dex")) continue;
                    byte[] bytes;
                    try (InputStream stream = apk.getInputStream(entry)) {
                        ByteArrayOutputStream buffer = new ByteArrayOutputStream((int) entry.getSize());
                        byte[] chunk = new byte[65536];
                        int count;
                        while ((count = stream.read(chunk)) != -1) buffer.write(chunk, 0, count);
                        bytes = buffer.toByteArray();
                    }
                    var dex = new DexBackedDexFile(Opcodes.getDefault(), bytes);
                    var classes = dex.getClasses();
                    int done = 0;
                    for (ClassDef type : classes) {
                        visitor.accept(type);
                        done++;
                        if (done % 256 == 0) progress.onProgress(offset + completedDex * 1000 +
                                (int) (done * 1000L / classes.size()), total);
                    }
                    completedDex++;
                    progress.onProgress(offset + completedDex * 1000, total);
                }
            }
        }
    }

    /**
     * 标识完整代码集合，覆盖每个 DEX 的签名头、CRC、长度及所属 APK 的代码分组。
     * @param paths 基础包和全部 Split 路径；不含代码的资源包不影响代码身份。
     * @return SHA-256 代码身份；不是 APK 签名证书验证。
     * @throws IOException 包读取失败。
     * Callers: HostSymbols.initialize、离线验证。
     */
    public static String identity(List<String> paths) throws IOException {
        List<String> containers = new ArrayList<>();
        for (String path : paths) {
            List<String> code = new ArrayList<>();
            try (ZipFile apk = new ZipFile(path)) {
                var entries = apk.entries();
                while (entries.hasMoreElements()) {
                    var entry = entries.nextElement();
                    if (!entry.getName().matches("classes(?:[2-9]|[1-9][0-9]+)?\\.dex")) continue;
                    byte[] header = new byte[32];
                    try (DataInputStream stream = new DataInputStream(apk.getInputStream(entry))) { stream.readFully(header); }
                    code.add(entry.getName() + ":" + entry.getSize() + ":" + entry.getCrc() + ":" + hex(header));
                }
            }
            if (!code.isEmpty()) { Collections.sort(code); containers.add(digest(String.join("\n", code))); }
        }
        if (containers.isEmpty()) throw new IOException("宿主代码集合中没有 DEX");
        Collections.sort(containers);
        return digest(String.join("\n", containers));
    }

    /**
     * 对全部代码扫描规则，只返回去重后唯一的候选；零候选和歧义分别记录。
     * @param paths 完整代码包路径。
     * @param rules 五列类指纹或 method-v1 方法行为规则，以制表符分隔。
     * @return symbol 映射到 binaryName；失败记录在 error.symbol。
     * @throws IOException 输入读取失败。
     * Callers: HostSymbols、离线验证器。
     */
    public static Properties scan(List<String> paths, String rules) throws IOException {
        return scan(paths, rules, NO_PROGRESS);
    }

    /**
     * 扫描全部代码并报告遍历与唯一候选校验的实际进度；无初始化依赖时跳过第二轮遍历。
     * @param paths 基础包和全部Split。
     * @param rules 特征规则文本。
     * @param progress 当前工作接收器。
     * @return 唯一候选及明确失败原因，不包含缓存已保存的声明。
     * @throws IOException 输入读取错误。
     * Callers: HostSymbols、scan、单元测试。
     */
    public static Properties scan(List<String> paths, String rules, ProgressListener progress) throws IOException {
        return scan(paths, rules, progress, null);
    }

    /**
     * 在原生检索的候选集合上执行完整契约校验；空集合表示没有候选，不触发全量扫描。
     * @param paths 完整代码包路径。
     * @param rules 当前所需规则。
     * @param progress 实际校验进度。
     * @param selected 按契约分组的原生 DEX 类型集合；仅离线基准扫描允许 null。
     * @return 经过关系与唯一性验证的结果。
     * @throws IOException 代码包读取失败。
     * Callers: HostNativeIndex.scan、scan 的离线入口。
     */
    public static Properties scan(List<String> paths, String rules, ProgressListener progress,
            Candidates selected) throws IOException {
        Set<Rule> parsedRules = new LinkedHashSet<>();
        Set<HostMethodRule> methodRules = new LinkedHashSet<>();
        Set<String> speedSymbols = new LinkedHashSet<>();
        Set<String> commentSymbols = new LinkedHashSet<>();
        for (String line : rules.split("\\R")) {
            if (line.trim().isEmpty() || line.startsWith("#")) continue;
            String[] columns = line.split("\t", -1);
            if (columns.length == 5 && columns[1].equals("comment-v1")) {
                if (!columns[0].equals("COMMENT_COPY") || !columns[2].equals("relations") ||
                        !columns[3].equals("-") || !columns[4].equals("-"))
                    throw new IllegalArgumentException("无效评论复制关系规则：" + line);
                commentSymbols.add(columns[0]);
            } else if (columns.length == 5 && columns[1].equals("speed-v1")) {
                if (!Set.of("PLAYER_CONTROLLER", "PLAYER_MANAGER", "SPEED_MANAGER", "SPEED_OPTIONS").contains(columns[0]) ||
                        !columns[2].equals("relations") || !columns[3].equals("-") || !columns[4].equals("-"))
                    throw new IllegalArgumentException("无效倍速关系规则：" + line);
                speedSymbols.add(columns[0]);
            } else if (columns.length == 5 && columns[1].equals("method-v1")) methodRules.add(HostMethodRule.parse(columns));
            else parsedRules.add(Rule.parse(line));
        }
        Map<String, Set<MethodTarget>> methodCandidates = new TreeMap<>();
        for (HostMethodRule rule : methodRules) methodCandidates.putIfAbsent(rule.symbol(), new LinkedHashSet<>());
        Set<String> relationSymbols = new HashSet<>(speedSymbols);
        relationSymbols.addAll(commentSymbols);
        if (!Collections.disjoint(relationSymbols, methodCandidates.keySet()))
            throw new IllegalArgumentException("同一符号不能混用关系与方法规则");
        HostSpeedIndex speedIndex = new HostSpeedIndex();
        HostCommentIndex commentIndex = new HostCommentIndex();
        Map<String, List<Rule>> byShape = new HashMap<>();
        Map<String, Map<String, Set<Rule>>> candidates = new TreeMap<>();
        for (Rule rule : parsedRules) {
            if (methodCandidates.containsKey(rule.symbol()) || relationSymbols.contains(rule.symbol()))
                throw new IllegalArgumentException("同一符号不能混用类指纹与方法规则：" + rule.symbol());
            candidates.computeIfAbsent(rule.symbol(), key -> new TreeMap<>());
            byShape.computeIfAbsent(rule.shape(), key -> new ArrayList<>()).add(rule);
        }
        Map<String, Set<Rule>> pending = new HashMap<>();
        Map<String, Set<String>> dependencies = new HashMap<>();
        int dexCount = countDex(paths);
        int total = (dexCount * 2 + candidates.size() + methodCandidates.size() + relationSymbols.size()) * 1000;
        progress.onProgress(0, total);
        HostMethodRule.Index methodIndex = new HostMethodRule.Index(methodRules);
        Set<String> selectedTypes = selected == null ? null : selected.allTypes();
        visit(paths, type -> {
            if (selectedTypes != null && !selectedTypes.contains(type.getType())) return;
            if (!speedSymbols.isEmpty() && (selected == null || selected.speed().contains(type.getType()))) speedIndex.collect(type);
            if (!commentSymbols.isEmpty() && (selected == null || selected.comments().contains(type.getType()))) commentIndex.collect(type);
            if (!methodRules.isEmpty() && (selected == null || selected.methods().contains(type.getType()))) for (Method method : type.getMethods()) {
                for (HostMethodRule rule : methodIndex.match(method)) {
                    Set<String> calls = new HashSet<>();
                    for (var instruction : method.getImplementation().getInstructions())
                        if (instruction instanceof ReferenceInstruction ref &&
                                ref.getReference() instanceof org.jf.dexlib2.iface.reference.MethodReference call &&
                                instruction.getOpcode().name().startsWith("INVOKE_")) calls.add(call.toString());
                    methodCandidates.get(rule.symbol()).add(new MethodTarget(type.getType(),
                            memberDescriptor(method), rule.role(), Set.copyOf(calls)));
                }
            }
            if (byShape.isEmpty()) return;
            if (selected != null && !selected.shapes().contains(type.getType())) return;
            List<Rule> matching = byShape.get(shape(type));
            if (matching == null) return;
            String content = literals(type);
            for (Rule rule : matching) {
                if (content.equals(rule.literals()) && matchesBindings(type, rule.bindings())) {
                    pending.computeIfAbsent(type.getType(), key -> new LinkedHashSet<>()).add(rule);
                    dependencies.put(type.getType(), dependencies(type));
                }
            }
        }, progress, 0, total);
        Map<String, String> linked;
        boolean hasDependencies = dependencies.values().stream().anyMatch(refs -> !refs.isEmpty());
        if (!hasDependencies) {
            linked = new HashMap<>();
            dependencies.keySet().forEach(type -> linked.put(type, digest("")));
            progress.onProgress(dexCount * 2000, total);
        } else {
            linked = linked(paths, dependencies, progress, dexCount * 1000, total);
        }
        pending.forEach((type, matching) -> {
            for (Rule rule : matching) if (rule.linked().equals(linked.get(type))) candidates.get(rule.symbol())
                .computeIfAbsent(type, key -> new LinkedHashSet<>()).add(rule);
        });
        Properties result = new Properties();
        int[] completed = {dexCount * 2000};
        candidates.forEach((symbol, found) -> {
            if (found.size() != 1) result.setProperty("error." + symbol, "候选数量=" + found.size());
            else {
                String descriptor = found.keySet().iterator().next();
                Set<Rule> matches = found.get(descriptor);
                Map<String, String> members = matches.iterator().next().bindings();
                boolean consistent = matches.stream().allMatch(rule -> members.equals(rule.bindings()));
                if (!consistent) result.setProperty("error." + symbol, "成员契约存在冲突");
                else {
                    result.setProperty(symbol, descriptor.substring(1, descriptor.length() - 1).replace('/', '.'));
                    members.forEach((role, member) -> result.setProperty("member." + symbol + "." + role, member));
                }
            }
            completed[0] += 1000;
            progress.onProgress(completed[0], total);
        });
        methodCandidates.forEach((symbol, found) -> {
            Set<String> roles = new HashSet<>();
            Map<String, Set<String>> callers = new HashMap<>();
            for (HostMethodRule rule : methodRules) if (rule.symbol().equals(symbol)) {
                roles.add(rule.role());
                for (String anchor : rule.anchors()) if (anchor.startsWith("called-by:"))
                    callers.computeIfAbsent(rule.role(), key -> new HashSet<>()).add(anchor.substring(10));
            }
            for (Set<String> callerRoles : callers.values()) if (!roles.containsAll(callerRoles))
                throw new IllegalArgumentException("方法规则引用未声明的角色：" + symbol);
            boolean changed;
            do {
                Set<MethodTarget> current = Set.copyOf(found);
                changed = found.removeIf(target -> callers.getOrDefault(target.role(), Set.of()).stream()
                        .anyMatch(role -> current.stream().noneMatch(caller -> caller.owner().equals(target.owner()) &&
                                caller.role().equals(role) && caller.calls().contains(target.owner() + "->" + target.descriptor()))));
            } while (changed);
            Map<String, Map<String, Set<MethodTarget>>> owners = new TreeMap<>();
            for (MethodTarget target : found) owners.computeIfAbsent(target.owner(), key -> new TreeMap<>())
                    .computeIfAbsent(target.role(), key -> new LinkedHashSet<>()).add(target);
            owners.values().removeIf(members -> !members.keySet().containsAll(roles));
            boolean ambiguous = owners.size() != 1 || owners.values().stream()
                    .anyMatch(members -> members.values().stream().anyMatch(matches -> matches.size() != 1));
            if (ambiguous) result.setProperty("error." + symbol, "候选数量=" +
                    (owners.size() == 1 ? owners.values().iterator().next().values().stream().mapToInt(Set::size).max().orElse(0) : owners.size()));
            else {
                String owner = owners.keySet().iterator().next();
                result.setProperty(symbol, owner.substring(1, owner.length() - 1).replace('/', '.'));
                owners.get(owner).forEach((role, matches) -> result.setProperty("member." + symbol + "." + role,
                        matches.iterator().next().descriptor()));
            }
            completed[0] += 1000;
            progress.onProgress(completed[0], total);
        });
        for (String symbol : speedSymbols) {
            result.putAll(speedIndex.resolve(symbol));
            completed[0] += 1000;
            progress.onProgress(completed[0], total);
        }
        if (!commentSymbols.isEmpty()) {
            result.putAll(commentIndex.resolve());
            completed[0] += 1000;
            progress.onProgress(completed[0], total);
        }
        return result;
    }

    /**
     * 解析业务角色与 DEX 成员描述的对应关系；重复角色和无效描述直接报错。
     * @param encoded 以竖线分隔的 role=descriptor，短横线表示没有成员绑定。
     * @return 有序角色映射，方法描述包含参数和返回类型，字段描述包含类型。
     * Callers: scan、matchesBindings。
     */
    private static Map<String, String> bindings(String encoded) {
        Map<String, String> result = new TreeMap<>();
        if (encoded.equals("-")) return result;
        for (String entry : encoded.split("\\|", -1)) {
            String[] pair = entry.split("=", -1);
            if (pair.length != 2 || !pair[0].matches("[A-Za-z][A-Za-z0-9]*") ||
                    !(pair[1].contains("(") || pair[1].contains(":")) ||
                    result.putIfAbsent(pair[0], pair[1]) != null)
                throw new IllegalArgumentException("无效 DEX 成员绑定: " + entry);
        }
        return result;
    }

    /**
     * 校验规则引用的每个方法或字段在候选类内具有完整类型契约。
     * @param type 候选 DEX 类。
     * @param encoded 业务成员绑定，不允许缺少成员后继续发布该符号。
     * @return 所有绑定均存在时返回 true。
     * Callers: scan、离线规则生成器。
     */
    public static boolean matchesBindings(ClassDef type, String encoded) {
        return matchesBindings(type, bindings(encoded));
    }

    /** 校验已解析的完整成员映射；扫描过程中不重复解析规则文本。 */
    private static boolean matchesBindings(ClassDef type, Map<String, String> expected) {
        if (expected.isEmpty()) return true;
        Set<String> actual = new HashSet<>();
        for (var field : type.getFields()) actual.add(field.getName() + ":" + normalize(field.getType()));
        for (Method method : type.getMethods()) actual.add(memberDescriptor(method));
        return actual.containsAll(expected.values());
    }

    /**
     * 构建方法的规范化 DEX 描述，保留名称、参数顺序和返回类型。
     * @param method 只读方法定义。
     * @return name(parameters)returnType 格式的描述。
     * Callers: matchesBindings、离线规则生成器。
     */
    public static String memberDescriptor(Method method) {
        StringBuilder value = new StringBuilder(method.getName()).append('(');
        method.getParameterTypes().forEach(value::append);
        return normalize(value.append(')').append(method.getReturnType()).toString());
    }

    /**
     * 仅读取ZIP目录，计算两轮遍历的DEX工作数量。
     * @param paths 宿主完整代码包集合。
     * @return 正整数DEX数量。
     * @throws IOException 包读取失败或没有DEX。
     * Callers: scan。
     */
    private static int countDex(List<String> paths) throws IOException {
        int count = 0;
        for (String path : paths) {
            try (ZipFile apk = new ZipFile(path)) {
                var entries = apk.entries();
                while (entries.hasMoreElements()) {
                    if (entries.nextElement().getName().matches("classes(?:[2-9]|[1-9][0-9]+)?\\.dex")) count++;
                }
            }
        }
        if (count == 0) throw new IOException("宿主代码集合中没有 DEX");
        return count;
    }

    /**
     * 计算 UTF-8 文本的 SHA-256；算法缺失属于运行环境错误，保留原因。
     * @param value 待计算文本。
     * @return 十六进制摘要。
     * Callers: shape、literals、identity、HostSymbols。
     */
    public static String digest(String value) {
        try { return hex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8))); }
        catch (NoSuchAlgorithmException error) { throw new IllegalStateException("SHA-256 不可用", error); }
    }

    /** 编码字节。@param bytes 原始数据。@return 小写十六进制。Callers: digest、identity。 */
    private static String hex(byte[] bytes) {
        char[] alphabet = "0123456789abcdef".toCharArray();
        char[] result = new char[bytes.length * 2];
        for (int i = 0; i < bytes.length; i++) {
            result[i * 2] = alphabet[(bytes[i] & 255) >>> 4];
            result[i * 2 + 1] = alphabet[bytes[i] & 15];
        }
        return new String(result);
    }
}
