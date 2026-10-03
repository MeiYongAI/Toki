package io.github.meiyongai.toki.hook;

import java.io.*;
import java.util.*;
import java.util.zip.*;
import org.jf.dexlib2.Opcode;
import org.jf.dexlib2.Opcodes;
import org.jf.dexlib2.iface.ClassDef;
import org.jf.dexlib2.iface.instruction.Instruction;
import org.jf.dexlib2.immutable.*;
import org.jf.dexlib2.immutable.instruction.*;
import org.jf.dexlib2.immutable.reference.*;
import org.jf.dexlib2.writer.io.MemoryDataStore;
import org.jf.dexlib2.writer.pool.DexPool;
import org.junit.Test;
import static org.junit.Assert.*;

/** 真实 DEX 指令验证关系匹配、重命名容忍、歧义拒绝与统一扫描进度。 */
public class HostSpeedIndexTest {
    private static final String A = "Lcom/ss/android/ugc/aweme/feed/model/Aweme;";
    private static final String S = "Ljava/lang/String;";
    private static final String C = "Lcom/ss/android/ugc/aweme/feed/controller/PlayerController;";
    private static final String B = "Ljava/lang/Boolean;";

    /** @param value 常量。@return 字符串指令。Callers: 本类候选构造器。 */
    private static Instruction string(String value) {
        return new ImmutableInstruction21c(Opcode.CONST_STRING, 0, new ImmutableStringReference(value));
    }
    /** @param op 调用方式。@param owner 类型。@param name 名称。@param result 返回类型。
     * @param args 参数。@return 调用指令。Callers: 本类候选构造器。 */
    private static Instruction call(Opcode op, String owner, String name, String result, String... args) {
        return new ImmutableInstruction35c(op, 5, 0, 1, 2, 3, 4,
                new ImmutableMethodReference(owner, name, List.of(args), result));
    }
    /** @param op 访问方式。@param owner 类型。@param name 名称。@param type 字段类型。
     * @return 字段指令。Callers: manager。 */
    private static Instruction field(Opcode op, String owner, String name, String type) {
        return new ImmutableInstruction21c(op, 0, new ImmutableFieldReference(owner, name, type));
    }
    /** @param owner 类型。@param name 名称。@param args 参数。@param result 返回类型。
     * @param flags 标志。@param code 指令。@return DEX 方法。Callers: 本类候选构造器。 */
    private static ImmutableMethod method(String owner, String name, List<String> args, String result, int flags, Instruction... code) {
        return new ImmutableMethod(owner, name, args.stream().map(t -> new ImmutableMethodParameter(t, Set.of(), null)).toList(),
                result, flags, Set.of(), Set.of(), new ImmutableMethodImplementation(8, List.of(code), List.of(), List.of()));
    }
    /** @param owner 类型。@param methods 方法。@return DEX 类。Callers: 本类候选构造器。 */
    private static ClassDef type(String owner, ImmutableMethod... methods) {
        return new ImmutableClassDef(owner, 1, "Ljava/lang/Object;", List.of(), null, Set.of(), List.of(), List.of(methods));
    }
    /** @param owner 中枢类型。@param broken 破坏的关联。@return 重命名后的中枢。Callers: 中枢测试。 */
    private static ClassDef manager(String owner, String broken) {
        return type(owner,
            method(owner, "chooseRenamed", List.of("F", A, S, S), "V", 9,
                string("long_press"), string("click_share_button"), string("swipe_up_lock_persist"),
                field(Opcode.SPUT, owner, "first", "F"), field(Opcode.SPUT, owner, "second", "F"), field(Opcode.SPUT, owner, "stored", "F")),
            method(owner, "allowRenamed", List.of(A), "Z", 9, call(Opcode.INVOKE_VIRTUAL, A, "isCanPlay", "Z")),
            method(owner, "readFirst", List.of(A), "F", 9, field(Opcode.SGET, owner, "first", "F"),
                call(Opcode.INVOKE_STATIC, "Landroid/text/TextUtils;", "equals", "Z", "Ljava/lang/CharSequence;", "Ljava/lang/CharSequence;")),
            method(owner, "readSecond", List.of(A), "F", 9, field(Opcode.SGET, owner, broken.equals("query") ? "first" : "second", "F"),
                call(Opcode.INVOKE_STATIC, "Landroid/text/TextUtils;", "equals", "Z", "Ljava/lang/CharSequence;", "Ljava/lang/CharSequence;")),
            method(owner, "restoreRenamed", List.of(A, S), "V", 9,
                call(Opcode.INVOKE_STATIC, owner, broken.equals("call") ? "unrelated" : "chooseRenamed", "V", "F", A, S, S),
                call(Opcode.INVOKE_STATIC, owner, "allowRenamed", "Z", A),
                field(Opcode.SGET, owner, broken.equals("persist") ? "first" : "stored", "F"), field(Opcode.SGET_BOOLEAN, owner, "enabledRenamed", "Z")),
            method(owner, "resetRenamed", List.of(A, S, "Z", "Z"), "Z", 9,
                field(Opcode.SPUT, owner, "first", "F"), field(Opcode.SPUT, owner, broken.equals("reset") ? "stored" : "second", "F")));
    }
    /** @param opcode 播放器调用类型。@param extra 是否额外调用同形接口。
     * @return 控制器类。Callers: 控制器测试。 */
    private static ClassDef controller(Opcode opcode, boolean extra) {
        var instructions = new ArrayList<Instruction>(List.of(string("begin_speed"), string("speed_begin"),
                call(Opcode.INVOKE_VIRTUAL, C, "getPlayerManager", "LX/RenamedPlayer;"),
                call(opcode, "LX/RenamedPlayer;", "renamedSpeed", "V", "F")));
        if (extra) instructions.add(call(opcode, "LX/Other;", "otherSpeed", "V", "F"));
        return type(C, method(C, "renamedControllerSpeed", List.of("F"), "V", 1, instructions.toArray(Instruction[]::new)),
                method(C, "getPlayerManager", List.of(), "LX/RenamedPlayer;", 1),
                method(C, "setPlayerManager", List.of("LX/RenamedPlayer;"), "V", 1),
                method(C, "onRenderReady", List.of("LX/Event;"), "V", 1));
    }
    /** @param owner 实验类。@param key 实验键。@return 实验读取类。Callers: 菜单测试。 */
    private static ClassDef experiment(String owner, String key) {
        return type(owner, method(owner, "invoke", List.of(), "Ljava/lang/Object;", 1, string(key),
                call(Opcode.INVOKE_STATIC, B, "valueOf", B, "Z")));
    }
    /** @param owner 开关类。@param experiment 实验类型。@return 开关类。Callers: 菜单测试。 */
    private static ClassDef gate(String owner, String experiment) {
        return type(owner, method(owner, "<clinit>", List.of(), "V", 8,
                new ImmutableInstruction21c(Opcode.NEW_INSTANCE, 0, new ImmutableTypeReference(experiment))),
                method(owner, "gateRenamed", List.of(), "Z", 9, call(Opcode.INVOKE_VIRTUAL, B, "booleanValue", "Z")));
    }
    /** @param owner 数据源。@param gate 开关。@param name 方法名称。@param lambda 自参数 Lambda。
     * @return 档位列表数据源。Callers: 菜单测试。 */
    private static ClassDef source(String owner, String gate, String name, boolean lambda) {
        List<Instruction> code = new ArrayList<>(List.of(call(Opcode.INVOKE_STATIC, gate, "gateRenamed", "Z"),
                new ImmutableInstruction22c(Opcode.NEW_ARRAY, 0, 1, new ImmutableTypeReference("[Ljava/lang/Float;")),
                call(Opcode.INVOKE_STATIC, "Ljava/lang/Float;", "valueOf", "Ljava/lang/Float;", "F"),
                call(Opcode.INVOKE_STATIC, "LX/Lists;", "list", "Ljava/util/List;", "[Ljava/lang/Object;")));
        for (int number : List.of(1056964608, 1065353216, 1069547520, 1073741824, 1077936128))
            code.add(new ImmutableInstruction31i(Opcode.CONST, 0, number));
        return type(owner, method(owner, name, lambda ? List.of(owner) : List.of(),
                lambda ? "Ljava/lang/Object;" : "Ljava/util/List;", 9, code.toArray(Instruction[]::new)));
    }
    /** @param types 任意顺序的类。@return 已收集索引。Callers: 本类测试。 */
    private static HostSpeedIndex index(ClassDef... types) {
        var index = new HostSpeedIndex();
        for (var type : types) index.collect(type);
        return index;
    }

    /** 重命名状态字段和全部方法仍按调用读写关联解析。无参数，无返回。Callers: JUnit。 */
    @Test public void renamedManagerPublishesActualRoles() {
        var result = index(manager("LX/NewManager;", "")).resolve("SPEED_MANAGER");
        assertEquals("X.NewManager", result.getProperty("SPEED_MANAGER"));
        assertEquals("stored:F", result.getProperty("member.SPEED_MANAGER.persist"));
        assertEquals("enabledRenamed:Z", result.getProperty("member.SPEED_MANAGER.enabled"));
        assertTrue(result.getProperty("member.SPEED_MANAGER.select").startsWith("chooseRenamed("));
    }
    /** 状态读写关系或恢复调用破坏时不发布部分结果。无参数，无返回。Callers: JUnit。 */
    @Test public void brokenManagerRelationsAreRejected() {
        for (String broken : List.of("query", "persist", "reset", "call")) {
            var result = index(manager("LX/Broken;", broken)).resolve("SPEED_MANAGER");
            assertEquals(broken, Set.of("error.SPEED_MANAGER"), result.stringPropertyNames());
        }
    }
    /** 两个完整中枢必须拒绝；同一类的重复收集不构成歧义。无参数，无返回。Callers: JUnit。 */
    @Test public void ambiguityAndDuplicateCollectionAreDistinguished() {
        var first = manager("LX/First;", "");
        assertEquals("X.First", index(first, first).resolve("SPEED_MANAGER").getProperty("SPEED_MANAGER"));
        assertEquals(Set.of("error.SPEED_MANAGER"), index(first, manager("LX/Second;", "")).resolve("SPEED_MANAGER").stringPropertyNames());
    }
    /** 控制器必须真实调用唯一播放器接口，普通虚调用不满足契约。无参数，无返回。Callers: JUnit。 */
    @Test public void controllerRequiresUniqueInterfaceSpeedCall() {
        var good = index(controller(Opcode.INVOKE_INTERFACE, false));
        assertEquals("renamedSpeed(F)V", good.resolve("PLAYER_MANAGER").getProperty("member.PLAYER_MANAGER.setSpeed"));
        assertTrue(good.resolve("PLAYER_CONTROLLER").getProperty("member.PLAYER_CONTROLLER.setSpeed").startsWith("renamedControllerSpeed("));
        for (var bad : List.of(controller(Opcode.INVOKE_VIRTUAL, false), controller(Opcode.INVOKE_INTERFACE, true)))
            assertEquals(Set.of("error.PLAYER_MANAGER"), index(bad).resolve("PLAYER_MANAGER").stringPropertyNames());
    }
    /** Lambda 编号、类归属和数量可变；相同浮点列表但实验无关时排除。无参数，无返回。Callers: JUnit。 */
    @Test public void menuUsesExperimentRelationAndVariableSourceSet() {
        var index = index(source("LX/Menu;", "LX/Gate;", "listRenamed", false),
                source("Lkotlin/jvm/internal/Repartitioned;", "LX/Gate;", "invoke$901", true),
                source("LX/Unrelated;", "LX/OtherGate;", "sameFloats", false),
                gate("LX/Gate;", "LX/Experiment;"), experiment("LX/Experiment;", "feed_support_3x_speed"));
        var result = index.resolve("SPEED_OPTIONS");
        assertEquals("X.Gate", result.getProperty("SPEED_OPTIONS"));
        assertEquals(2, result.getProperty("members.SPEED_OPTIONS.sources").split("\n").length);
        assertTrue(result.getProperty("members.SPEED_OPTIONS.sources").contains("invoke$901"));
        assertFalse(result.getProperty("members.SPEED_OPTIONS.sources").contains("Unrelated"));
    }
    /** 无实验关联、重复实验开关或缺少普通列表入口都不能发布菜单。无参数，无返回。Callers: JUnit。 */
    @Test public void missingAndAmbiguousMenuRelationsAreRejected() {
        var gate = gate("LX/Gate;", "LX/Experiment;");
        var experiment = experiment("LX/Experiment;", "feed_support_3x_speed");
        var source = source("LX/Menu;", "LX/Gate;", "options", false);
        for (var index : List.of(index(gate, source), index(gate, experiment),
                index(gate, experiment, source, gate("LX/OtherGate;", "LX/Experiment;")),
                index(gate, experiment, source("LX/Lambda;", "LX/Gate;", "options", true))))
            assertEquals(Set.of("error.SPEED_OPTIONS"), index.resolve("SPEED_OPTIONS").stringPropertyNames());
    }
    /** 真实 APK 输入走统一扫描发布，完成进度覆盖关系契约。无参数，无返回。Callers: JUnit。 */
    @Test public void sharedScannerPublishesContractsAndCompletesProgress() throws Exception {
        File apk = File.createTempFile("speed-contract", ".apk");
        try {
            var pool = new DexPool(Opcodes.getDefault());
            pool.internClass(manager("LX/Actual;", ""));
            var data = new MemoryDataStore();
            try (var zip = new ZipOutputStream(new FileOutputStream(apk))) {
                pool.writeTo(data);
                zip.putNextEntry(new ZipEntry("classes.dex"));
                zip.write(data.getData());
                zip.closeEntry();
            } finally { data.close(); }
            int[] last = {0, 0};
            var result = HostDexIndex.scan(List.of(apk.getPath()), "SPEED_MANAGER\tspeed-v1\trelations\t-\t-", (done, total) -> {
                assertTrue(done >= last[0]);
                last[0] = done; last[1] = total;
            });
            assertEquals("X.Actual", result.getProperty("SPEED_MANAGER"));
            assertTrue(last[0] > 0);
            assertEquals(last[1], last[0]);
            assertThrows(IllegalArgumentException.class, () -> HostDexIndex.scan(List.of(apk.getPath()),
                    "SPEED_MANAGER\tspeed-v1\tunknown\t-\t-"));
        } finally { java.nio.file.Files.delete(apk.toPath()); }
    }
}
