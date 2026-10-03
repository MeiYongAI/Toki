package io.github.meiyongai.toki.hook;

import java.util.*;
import java.util.stream.Collectors;
import org.jf.dexlib2.iface.ClassDef;
import org.jf.dexlib2.iface.Method;
import org.jf.dexlib2.iface.instruction.ReferenceInstruction;
import org.jf.dexlib2.iface.reference.*;

/** 将评论菜单、复制动作、显示快照与剪贴板构造按真实引用连接成一个完整契约。 */
final class HostCommentIndex {
    private static final String COMMENT = "Lcom/ss/android/ugc/aweme/comment/model/Comment;";
    private static final String MENU = "Lcom/ss/android/ugc/aweme/commentv2/commentlist/viewmodel/CommentActionMenuVM;";
    private static final String CLIP = "(Ljava/lang/String;Ljava/lang/String;Ljava/util/List;)Landroid/content/ClipData;";
    private static final Map<String, String> LABELS = Map.of("CommentData(commentText=", "text",
            ", commentTranslatedTextExtra=", "extra", ", cid=", "cid", ", translated=", "translated");
    private record Body(String owner, String name, String signature, List<String> parameters,
            Set<String> strings, Set<String> calls, Set<String> reads) {
        /** @return 完整 DEX 方法引用。无参数。Callers: resolve、collect。 */
        String reference() { return owner + "->" + name + signature; }
    }
    private final Map<String, Body> copies = new TreeMap<>(), clips = new TreeMap<>(), menus = new TreeMap<>();
    private final Map<String, Map<String, String>> displays = new TreeMap<>();

    /** @param type 当前 DEX 类。@return 无；只保存目标方法摘要。Callers: HostDexIndex.scan。 */
    void collect(ClassDef type) {
        for (Method method : type.getMethods()) {
            if (method.getImplementation() == null) continue;
            String sig = signature(method);
            boolean isStatic = (method.getAccessFlags() & 8) != 0;
            boolean menu = type.getType().equals(MENU) && isStatic && method.getReturnType().equals("V");
            boolean copy = !isStatic && sig.equals("()V") && !method.getName().startsWith("<");
            boolean clip = isStatic && sig.equals(CLIP);
            boolean display = !isStatic && method.getName().equals("toString") && sig.equals("()Ljava/lang/String;");
            if (!menu && !copy && !clip && !display) continue;
            Set<String> strings = HostMethodRule.strings(method);
            if (!(menu && strings.containsAll(Set.of("comment_action_menu", "long_press")) ||
                    copy && strings.containsAll(Set.of("copy_comment", "clipboard", "bpea-221")) ||
                    clip && strings.contains("copy_label") || display && strings.containsAll(LABELS.keySet()))) continue;
            Body body = body(method);
            if (copy && body.strings.containsAll(Set.of("copy_comment", "clipboard", "bpea-221")) &&
                    body.calls.containsAll(Set.of(COMMENT + "->getText()Ljava/lang/String;", COMMENT + "->getTextExtra()Ljava/util/List;")))
                copies.put(body.reference(), body);
            if (clip && body.strings.contains("copy_label") && body.calls.contains(
                    "Landroid/content/ClipData;->newPlainText(Ljava/lang/CharSequence;Ljava/lang/CharSequence;)Landroid/content/ClipData;"))
                clips.put(body.reference(), body);
            if (menu && body.strings.containsAll(Set.of("comment_action_menu", "long_press"))) menus.put(body.reference(), body);
            if (display && body.strings.containsAll(LABELS.keySet())) displays.put(type.getType(), displayFields(method));
        }
    }

    /** @param method 方法引用。@return 精确参数及返回描述。Callers: collect、body。 */
    private static String signature(MethodReference method) {
        return "(" + method.getParameterTypes().stream().map(Object::toString).collect(Collectors.joining()) + ")" + method.getReturnType();
    }

    /** @param method 候选方法。@return 字符串、调用及实例字段读取摘要。Callers: collect。 */
    private static Body body(Method method) {
        Set<String> strings = new HashSet<>(), calls = new HashSet<>(), reads = new HashSet<>();
        for (var instruction : method.getImplementation().getInstructions()) {
            if (!(instruction instanceof ReferenceInstruction ref)) continue;
            var target = ref.getReference();
            String op = instruction.getOpcode().name();
            if (target instanceof StringReference text) strings.add(text.getString());
            if (target instanceof MethodReference call && op.startsWith("INVOKE_")) calls.add(call.toString());
            if (target instanceof FieldReference field && op.startsWith("IGET")) reads.add(field.toString());
        }
        return new Body(method.getDefiningClass(), method.getName(), signature(method),
                method.getParameterTypes().stream().map(Object::toString).collect(Collectors.toList()), strings, calls, reads);
    }

    /**
     * 由数据类 toString 的字段标签与紧随其后的自身字段读取绑定语义。
     * @param method 显示数据的 toString。
     * @return 全部四个字段；缺失、重复或类型不符则为空。
     * Callers: collect。
     */
    private static Map<String, String> displayFields(Method method) {
        Map<String, String> fields = new HashMap<>();
        String pending = null;
        for (var instruction : method.getImplementation().getInstructions()) {
            if (!(instruction instanceof ReferenceInstruction ref)) continue;
            if (ref.getReference() instanceof StringReference text) pending = LABELS.get(text.getString());
            if (pending != null && ref.getReference() instanceof FieldReference field &&
                    instruction.getOpcode().name().startsWith("IGET") && field.getDefiningClass().equals(method.getDefiningClass())) {
                String expected = pending.equals("extra") ? "Ljava/util/List;" : pending.equals("translated") ? "Z" : "Ljava/lang/String;";
                if (!field.getType().equals(expected) || fields.put(pending, field.toString()) != null) return Map.of();
                pending = null;
            }
        }
        return fields.size() == 4 && new HashSet<>(fields.values()).size() == 4 ? fields : Map.of();
    }

    /** @return 唯一完整复制链路，缺失或歧义明确失败。无参数。Callers: HostDexIndex.scan。 */
    Properties resolve() {
        List<Properties> candidates = new ArrayList<>();
        for (Body copy : copies.values()) for (Body menu : menus.values()) for (var display : displays.entrySet()) {
            if (display.getValue().isEmpty() || menu.calls.stream().noneMatch(call -> call.startsWith(copy.owner + "-><init>("))) continue;
            var clip = clips.values().stream().filter(value -> copy.calls.contains(value.reference())).collect(Collectors.toList());
            var getters = copy.calls.stream().filter(call -> call.endsWith("()" + COMMENT)).collect(Collectors.toList());
            var items = copy.reads.stream().filter(field -> owner(field).equals(copy.owner) &&
                    menu.parameters.contains(fieldType(field)) && fieldType(field).startsWith("LX/")).collect(Collectors.toList());
            var data = menu.reads.stream().filter(field -> fieldType(field).equals(display.getKey()) &&
                    menu.parameters.contains(owner(field))).collect(Collectors.toList());
            if (clip.size() != 1 || getters.size() != 1 || data.size() != 1 ||
                    !menu.reads.contains(display.getValue().get("translated"))) continue;
            // 同一菜单可能读取其他控制器字段；评论项由 getter 所属基类关系在注册时进一步核验。
            for (String item : items) {
                if (Collections.frequency(menu.parameters, fieldType(item)) != 1 ||
                        Collections.frequency(menu.parameters, owner(data.get(0))) != 1) continue;
                Properties result = new Properties();
                result.setProperty("COMMENT_COPY", binary(copy.owner));
                bind(result, "copy", copy.reference()); bind(result, "item", item);
                bind(result, "getComment", getters.get(0)); bind(result, "clip", clip.get(0).reference());
                bind(result, "menu", menu.reference()); bind(result, "display", data.get(0));
                display.getValue().forEach((role, ref) -> bind(result, role, ref));
                candidates.add(result);
            }
        }
        if (candidates.size() == 1) return candidates.get(0);
        Properties error = new Properties();
        error.setProperty("error.COMMENT_COPY", "完整评论复制链路候选数量=" + candidates.size());
        return error;
    }

    /** @param ref 字段或方法引用。@return 所属 DEX 类。Callers: resolve。 */
    private static String owner(String ref) { return ref.substring(0, ref.indexOf("->")); }
    /** @param ref 字段引用。@return 字段类型。Callers: resolve。 */
    private static String fieldType(String ref) { return ref.substring(ref.indexOf(':') + 1); }
    /** @param type DEX 类。@return JVM 类名。Callers: resolve。 */
    private static String binary(String type) { return type.substring(1, type.length() - 1).replace('/', '.'); }
    /** @param out 输出契约。@param role 成员语义。@param ref 完整引用。@return 无。Callers: resolve。 */
    private static void bind(Properties out, String role, String ref) { out.setProperty("members.COMMENT_COPY." + role, ref); }
}
