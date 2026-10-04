package io.github.meiyongai.toki.hook;

import java.io.*;
import java.nio.charset.StandardCharsets;
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
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import static org.junit.Assert.*;

/** 使用真实 DEX 和发布规则验证改名容忍与错误候选拒绝。 */
public class HostMethodRuleTest {
    /** 相同展示行为的临时组件不能混入正式组件候选，成员改名不影响定位。无参数、无返回。Callers: JUnit。 */
    @Test public void ownerAnchorSeparatesEquivalentComponents() {
        var rule = HostMethodRule.parse(new String[]{"BUTTON", "method-v1", "()V", "owner:Lhost/Button;", "show"});
        for (String owner : List.of("Lhost/Button;", "Lhost/ButtonTemp;")) {
            var method = new ImmutableMethod(owner, "renamed", List.of(), "V", 1, Set.of(), Set.of(),
                    new ImmutableMethodImplementation(1, List.of(new ImmutableInstruction10x(Opcode.RETURN_VOID)), List.of(), List.of()));
            assertEquals(owner.equals("Lhost/Button;"), rule.matches(method));
            assertEquals(rule.matches(method), !new HostMethodRule.Index(List.of(rule)).match(method).isEmpty());
        }
    }
    /** 类常量契约必须精确匹配类型及指令，不能将类型转换误认成组件声明。无参数、无返回。Callers: JUnit。 */
    @Test public void classAnchorRequiresExactConstClassInstruction() {
        var rule = HostMethodRule.parse(new String[]{"PANEL", "method-v1", "()V", "class:Lhost/Controller;", "declaration"});
        for (var opcode : List.of(Opcode.CONST_CLASS, Opcode.CHECK_CAST)) {
            for (String type : List.of("Lhost/Controller;", "Lhost/Other;")) {
                var method = new ImmutableMethod("LX/Owner;", "renamed", List.of(), "V", 1, Set.of(), Set.of(),
                        new ImmutableMethodImplementation(2, List.of(
                                new ImmutableInstruction21c(opcode, 0, new ImmutableTypeReference(type)),
                                new ImmutableInstruction10x(Opcode.RETURN_VOID)), List.of(), List.of()));
                assertEquals(opcode == Opcode.CONST_CLASS && type.equals("Lhost/Controller;"), rule.matches(method));
                assertEquals(rule.matches(method), !new HostMethodRule.Index(List.of(rule)).match(method).isEmpty());
            }
        }
    }
    /** 索引必须保留通配与精确规则的全部匹配角色，并严格区分构造器和静态方法。无参数、无返回。Callers: JUnit。 */
    @Test public void indexedRulesPreserveAllMatchingRolesAndInvocationKinds() {
        var rules = List.of(
                HostMethodRule.parse(new String[]{"A", "method-v1", "(LX/Arg;)V", "static", "exact"}),
                HostMethodRule.parse(new String[]{"A", "method-v1", "(LX/*;)V", "static", "pattern"}),
                HostMethodRule.parse(new String[]{"B", "method-v1", "(LX/Arg;)V", "instance", "instance"}),
                HostMethodRule.parse(new String[]{"C", "method-v1", "(LX/Arg;)V", "name:<init>", "construct"}),
                HostMethodRule.parse(new String[]{"D", "method-v1", "(LX/*;)V", "static|string:missing", "reject"}));
        var index = new HostMethodRule.Index(rules);
        for (String name : List.of("renamed", "<init>")) for (int flags : List.of(1, 9)) {
            var method = new ImmutableMethod("LX/Owner;", name,
                    List.of(new ImmutableMethodParameter("LX/Arg;", Set.of(), null)), "V", flags, Set.of(), Set.of(),
                    new ImmutableMethodImplementation(2, List.of(new ImmutableInstruction10x(Opcode.RETURN_VOID)), List.of(), List.of()));
            var expected = new HashSet<HostMethodRule>();
            for (var rule : rules) if (rule.matches(method)) expected.add(rule);
            assertEquals(expected, new HashSet<>(index.match(method)));
            if (name.equals("renamed") && flags == 9) assertEquals(2, index.match(method).size());
        }
    }
    /** 混淆类型占位只允许一个对象，不应吞掉额外参数或接受数组。无参数，无返回。Callers: JUnit。 */
    @Test public void obfuscatedParameterMatchesExactlyOneObject() {
        var rule = HostMethodRule.parse(new String[]{"TEST", "method-v1", "(LX/*;)V", "static", "apply"});
        for (String type : List.of("LX/Old;", "LX/New;", "[LX/New;", "Ljava/lang/String;", "I")) {
            var method = new ImmutableMethod("LX/Owner;", "renamed",
                    List.of(new ImmutableMethodParameter(type, Set.of(), null)), "V", 9, Set.of(), Set.of(),
                    new ImmutableMethodImplementation(2, List.of(new ImmutableInstruction10x(Opcode.RETURN_VOID)), List.of(), List.of()));
            assertEquals(type.startsWith("LX/"), rule.matches(method));
        }
        var extra = new ImmutableMethod("LX/Owner;", "renamed", List.of(
                new ImmutableMethodParameter("LX/New;", Set.of(), null), new ImmutableMethodParameter("I", Set.of(), null)),
                "V", 9, Set.of(), Set.of(), new ImmutableMethodImplementation(3,
                List.of(new ImmutableInstruction10x(Opcode.RETURN_VOID)), List.of(), List.of()));
        assertFalse(rule.matches(extra));
    }
    @Rule public TemporaryFolder temporary = new TemporaryFolder();
    private static final String AWEME = "Lcom/ss/android/ugc/aweme/feed/model/Aweme;";
    private static final String VIDEO = "Lcom/ss/android/ugc/aweme/feed/model/Video;";
    private static final String URL = "Lcom/ss/android/ugc/aweme/base/model/UrlModel;";
    private static final String VIDEO_URL = "Lcom/ss/android/ugc/aweme/feed/model/VideoUrlModel;";
    private static final String STRING = "Ljava/lang/String;";

    /** 改名不影响展示入口匹配，缺少业务调用或重复入口必须拒绝。无参数，无返回。Callers: JUnit。 */
    @Test public void authorDisplayMatchesBehaviorAndRejectsIncompleteOrAmbiguousMethods() throws IOException {
        String rule;
        try (var stream = getClass().getResourceAsStream("/toki-host-rules.tsv")) {
            rule = new String(Objects.requireNonNull(stream).readAllBytes(), StandardCharsets.UTF_8)
                    .lines().filter(line -> line.startsWith("AUTHOR_LOCATION\t")).findFirst().orElseThrow();
        }
        String owner = "LX/RenamedAuthor;";
        var valid = authorMethod(owner, "renamedBuild", false);
        var result = HostDexIndex.scan(List.of(apk(type(owner, valid))), rule);
        assertTrue(result.getProperty("member.AUTHOR_LOCATION.build").startsWith("renamedBuild("));
        var missing = HostDexIndex.scan(List.of(apk(type(owner, authorMethod(owner, "LIZIZ", true)))), rule);
        assertEquals("候选数量=0", missing.getProperty("error.AUTHOR_LOCATION"));
        var duplicate = HostDexIndex.scan(List.of(apk(type(owner, valid, authorMethod(owner, "secondBuild", false)))), rule);
        assertEquals("候选数量=2", duplicate.getProperty("error.AUTHOR_LOCATION"));
    }

    /** @param owner 所属类。@param name 方法名。@param omit 是否省略广告作者判断。
     * @return 作者展示候选。Callers: authorDisplayMatchesBehaviorAndRejectsIncompleteOrAmbiguousMethods。 */
    private ImmutableMethod authorMethod(String owner, String name, boolean omit) {
        String user = "Lcom/ss/android/ugc/aweme/profile/model/User;";
        List<Instruction> code = new ArrayList<>();
        code.add(call(AWEME, "getAuthor", user, 2));
        code.add(call(user, "getNickname", STRING, 1));
        if (!omit) code.add(call(user, "isAdFake", "Z", 1));
        code.add(call("Lcom/ss/android/ugc/aweme/feed/model/AwemeRawAd;", "getOmVast",
                "Lcom/ss/android/ugc/aweme/commercialize/model/OmVast;", 3));
        code.add(new ImmutableInstruction11x(Opcode.RETURN_OBJECT, 0));
        return new ImmutableMethod(owner, name, List.of(new ImmutableMethodParameter(STRING, Set.of(), null),
                new ImmutableMethodParameter(user, Set.of(), null), new ImmutableMethodParameter(AWEME, Set.of(), null)),
                STRING, 9, Set.of(), Set.of(), new ImmutableMethodImplementation(4, code, List.of(), List.of()));
    }

    /** 正文规则必须忽略同名的标题资格方法，并拒绝两个正文候选。无参数，无返回。Callers: JUnit。 */
    @Test public void descriptionRuleDistinguishesPhotoTitleDespiteMethodName() throws IOException {
        String rule;
        try (var stream = getClass().getResourceAsStream("/toki-host-rules.tsv")) {
            rule = new String(Objects.requireNonNull(stream).readAllBytes(), StandardCharsets.UTF_8)
                    .lines().filter(line -> line.startsWith("DESCRIPTION_TRANSLATION\t")).findFirst().orElseThrow();
        }
        String owner = "LX/TranslationService;";
        var title = translationMethod(owner, "LIZLLL", "isPhotoTitleTranslatable");
        var desc = translationMethod(owner, "renamedDescription", "isDescTranslatable");
        var result = HostDexIndex.scan(List.of(apk(type(owner, title, desc))), rule);
        assertEquals("renamedDescription(" + AWEME + ")Z", result.getProperty("member.DESCRIPTION_TRANSLATION.eligible"));
        var absent = HostDexIndex.scan(List.of(apk(type(owner, title))), rule);
        assertEquals("候选数量=0", absent.getProperty("error.DESCRIPTION_TRANSLATION"));
        var ambiguous = HostDexIndex.scan(List.of(apk(type(owner, desc,
                translationMethod(owner, "duplicate", "isDescTranslatable")))), rule);
        assertEquals("候选数量=2", ambiguous.getProperty("error.DESCRIPTION_TRANSLATION"));
    }

    /** @param owner 所属类。@param name 混淆方法名。@param getter 正文或标题标记。
     * @return 用于检查语义匹配的 DEX 方法。Callers: descriptionRuleDistinguishesPhotoTitleDespiteMethodName。 */
    private ImmutableMethod translationMethod(String owner, String name, String getter) {
        String text = "Ljava/lang/CharSequence;";
        List<Instruction> code = new ArrayList<>();
        code.add(call(AWEME, getter, "Z", 2));
        code.add(new ImmutableInstruction35c(Opcode.INVOKE_STATIC, 1, 0, 0, 0, 0, 0,
                new ImmutableMethodReference("Landroid/text/TextUtils;", "isEmpty", List.of(text), "Z")));
        code.add(new ImmutableInstruction35c(Opcode.INVOKE_STATIC, 2, 0, 0, 0, 0, 0,
                new ImmutableMethodReference("Landroid/text/TextUtils;", "equals", List.of(text, text), "Z")));
        code.add(new ImmutableInstruction11x(Opcode.RETURN, 0));
        return new ImmutableMethod(owner, name, List.of(new ImmutableMethodParameter(AWEME, Set.of(), null)),
                "Z", 1, Set.of(), Set.of(), new ImmutableMethodImplementation(3, code, List.of(), List.of()));
    }

    /** 显式构造器规则按资源字段读取识别，字段改写或缺少名称不匹配。无参数，无返回。Callers: JUnit。 */
    @Test public void constructorRequiresExplicitNameAndResourceRead() throws IOException {
        String owner = "LX/RenamedMask;";
        String resource = "Lcom/ss/android/ugc/aweme/app/R$styleable;";
        String rule = "DARK_LAYER\tmethod-v1\t()V\tname:<init>|field:" + resource + "->TuxDarkLayerView:[I\tconstruct";
        var reference = new ImmutableFieldReference(resource, "TuxDarkLayerView", "[I");
        for (Opcode opcode : List.of(Opcode.SGET_OBJECT, Opcode.SPUT_OBJECT)) {
            var constructor = new ImmutableMethod(owner, "<init>", List.of(), "V", 1, Set.of(), Set.of(),
                    new ImmutableMethodImplementation(1, List.of(new ImmutableInstruction21c(opcode, 0, reference)), List.of(), List.of()));
            var target = apk(type(owner, constructor));
            var result = HostDexIndex.scan(List.of(target), rule);
            assertEquals(opcode == Opcode.SGET_OBJECT, result.containsKey("DARK_LAYER"));
            var implicit = HostDexIndex.scan(List.of(target), rule.replace("name:<init>|", ""));
            assertFalse(implicit.containsKey("DARK_LAYER"));
        }
    }

    /** @return 发布的下载规则。无参数。Callers: 本类测试。 */
    private String rule() throws IOException {
        try (var stream = getClass().getResourceAsStream("/toki-host-rules.tsv")) {
            return new String(Objects.requireNonNull(stream).readAllBytes(), StandardCharsets.UTF_8)
                    .lines().filter(line -> line.startsWith("DOWNLOAD_SOURCE\t")).findFirst().orElseThrow();
        }
    }

    /** @param owner 接收类型。@param name 方法名。@param result 返回类型。@param register 接收寄存器。
     * @return 无参数虚方法调用。Callers: method。 */
    private Instruction call(String owner, String name, String result, int register) {
        return new ImmutableInstruction35c(Opcode.INVOKE_VIRTUAL, 1, register, 0, 0, 0, 0,
                new ImmutableMethodReference(owner, name, List.of(), result));
    }

    /** @param owner 类描述。@param name 任意混淆方法名。@param omit 省略的关键条件。
     * @param staticMethod 是否构造错误的静态候选。@param booleanParameter 第二参数是否保持布尔类型。
     * @return 可写入 DEX 的候选方法。Callers: 本类测试。 */
    private ImmutableMethod method(String owner, String name, String omit, boolean staticMethod, boolean booleanParameter) {
        List<Instruction> code = new ArrayList<>();
        code.add(call(AWEME, "getVideo", VIDEO, 4));
        code.add(new ImmutableInstruction11x(Opcode.MOVE_RESULT_OBJECT, 0));
        if (!omit.equals("download")) {
            code.add(call(VIDEO, "getDownloadNoWatermarkAddr", URL, 0));
            code.add(new ImmutableInstruction11x(Opcode.MOVE_RESULT_OBJECT, 1));
        }
        code.add(call(VIDEO, "getPlayAddrH264", VIDEO_URL, 0));
        code.add(new ImmutableInstruction11x(Opcode.MOVE_RESULT_OBJECT, 1));
        if (!omit.equals("write")) code.add(new ImmutableInstruction22c(Opcode.IPUT_OBJECT, 1,
                omit.equals("receiver") ? 0 : 3, new ImmutableFieldReference(owner, "renamedAddress", URL)));
        code.add(call(URL, "getUri", STRING, 1));
        code.add(new ImmutableInstruction11x(Opcode.MOVE_RESULT_OBJECT, 1));
        if (!omit.equals("tag")) code.add(new ImmutableInstruction21c(Opcode.CONST_STRING, 2,
                new ImmutableStringReference("tag_no_water")));
        code.add(new ImmutableInstruction22c(Opcode.IPUT_OBJECT, 1, 3,
                new ImmutableFieldReference(owner, "renamedFilename", STRING)));
        code.add(new ImmutableInstruction10x(Opcode.RETURN_VOID));
        return new ImmutableMethod(owner, name, List.of(new ImmutableMethodParameter(AWEME, Set.of(), null),
                new ImmutableMethodParameter(booleanParameter ? "Z" : "I", Set.of(), null)), "V",
                staticMethod ? 9 : 1, Set.of(), Set.of(), new ImmutableMethodImplementation(6, code, List.of(), List.of()));
    }

    /** @param owner 类描述。@param methods 包括无关方法的候选列表。@return 声明实际写入字段的类。Callers: 本类测试。 */
    private ClassDef type(String owner, ImmutableMethod... methods) {
        return new ImmutableClassDef(owner, 1, "Ljava/lang/Object;", List.of(), null, Set.of(),
                List.of(new ImmutableField(owner, "renamedAddress", URL, 1, null, Set.of(), Set.of()),
                        new ImmutableField(owner, "renamedFilename", STRING, 1, null, Set.of(), Set.of()),
                        new ImmutableField(owner, "unrelatedNewField", "I", 1, null, Set.of(), Set.of())),
                List.of(methods));
    }

    /** @param types 实际类集合。@return 临时 APK 路径。Callers: 本类测试。 */
    private String apk(ClassDef... types) throws IOException {
        var pool = new DexPool(Opcodes.getDefault());
        for (ClassDef type : types) pool.internClass(type);
        var store = new MemoryDataStore();
        pool.writeTo(store);
        File target = temporary.newFile();
        try (var zip = new ZipOutputStream(new FileOutputStream(target))) {
            zip.putNextEntry(new ZipEntry("classes.dex"));
            zip.write(store.getData());
            zip.closeEntry();
        }
        store.close();
        return target.getAbsolutePath();
    }

    /** 改名及无关类成员不影响定位，实际方法名必须发布。无参数，无返回。Callers: JUnit。 */
    @Test public void renamedMembersAndUnrelatedMethodsStillResolve() throws IOException {
        String owner = "LX/CompletelyRenamed;";
        var host = type(owner, method(owner, "newName", "", false, true),
                method(owner, "unrelated", "tag", false, true));
        var result = HostDexIndex.scan(List.of(apk(host)), rule());
        assertEquals("X.CompletelyRenamed", result.getProperty("DOWNLOAD_SOURCE"));
        assertEquals("newName(" + AWEME + "Z)V", result.getProperty("member.DOWNLOAD_SOURCE.select"));
    }

    /** 关键调用、标记和 this 写入缺失均拒绝匹配。无参数，无返回。Callers: JUnit。 */
    @Test public void missingBehaviorAndWrongReceiverAreRejected() throws IOException {
        String owner = "LX/FalsePositive;";
        for (String missing : List.of("download", "tag", "write", "receiver")) {
            var result = HostDexIndex.scan(List.of(apk(type(owner, method(owner, "select", missing, false, true)))), rule());
            assertEquals(missing, "候选数量=0", result.getProperty("error.DOWNLOAD_SOURCE"));
            assertFalse(result.containsKey("member.DOWNLOAD_SOURCE.select"));
        }
    }

    /** 参数契约、实例调用契约变化不启用 Hook。无参数，无返回。Callers: JUnit。 */
    @Test public void wrongSignatureAndStaticMethodAreRejected() throws IOException {
        String owner = "LX/Contract;";
        for (var candidate : List.of(method(owner, "select", "", true, true), method(owner, "select", "", false, false))) {
            var result = HostDexIndex.scan(List.of(apk(type(owner, candidate))), rule());
            assertEquals("候选数量=0", result.getProperty("error.DOWNLOAD_SOURCE"));
        }
    }

    /** 同类两个方法都符合条件时不能取第一个。无参数，无返回。Callers: JUnit。 */
    @Test public void twoMatchingMethodsInOneClassAreAmbiguous() throws IOException {
        String owner = "LX/Ambiguous;";
        var result = HostDexIndex.scan(List.of(apk(type(owner, method(owner, "a", "", false, true),
                method(owner, "b", "", false, true)))), rule());
        assertEquals("候选数量=2", result.getProperty("error.DOWNLOAD_SOURCE"));
        assertFalse(result.containsKey("DOWNLOAD_SOURCE"));
    }

    /** 全部分包参与唯一性验证，扫描进度必须结束于总数。无参数，无返回。Callers: JUnit。 */
    @Test public void splitCandidatesAreCountedAndProgressCompletes() throws IOException {
        String a = "LX/A;", b = "LX/B;";
        int[] progress = new int[2];
        var result = HostDexIndex.scan(List.of(apk(type(a, method(a, "a", "", false, true))),
                apk(type(b, method(b, "b", "", false, true)))), rule(), (done, total) -> {
                    assertTrue(done >= progress[0]);
                    progress[0] = done; progress[1] = total;
                });
        assertEquals("候选数量=2", result.getProperty("error.DOWNLOAD_SOURCE"));
        assertEquals(progress[1], progress[0]);
        assertTrue(progress[1] > 0);
    }

    /** 规则拼写错误必须阻止扫描，不能减少实际校验条件。无参数，无返回。Callers: JUnit。 */
    @Test public void unknownAnchorIsRejected() {
        assertThrows(IllegalArgumentException.class, () -> HostMethodRule.parse(new String[] {
                "DOWNLOAD_SOURCE", "method-v1", "(" + AWEME + "Z)V", "unknown:tag_no_water", "select"}));
    }

    /** @param owner 类描述。@param name 方法名。@param marker 语义标记。@param target 被调用方法名。
     * @param isStatic 静态调用约束。@return 返回 void 的测试方法。Callers: 角色及调用关系测试。 */
    private ImmutableMethod roleMethod(String owner, String name, String marker, String target, boolean isStatic) {
        List<Instruction> code = new ArrayList<>();
        code.add(new ImmutableInstruction21c(Opcode.CONST_STRING, 0, new ImmutableStringReference(marker)));
        if (target != null) code.add(new ImmutableInstruction35c(Opcode.INVOKE_VIRTUAL, 1, 1, 0, 0, 0, 0,
                new ImmutableMethodReference(owner, target, List.of(), "V")));
        code.add(new ImmutableInstruction10x(Opcode.RETURN_VOID));
        return new ImmutableMethod(owner, name, List.of(), "V", isStatic ? 9 : 1, Set.of(), Set.of(),
                new ImmutableMethodImplementation(2, code, List.of(), List.of()));
    }

    /** @return 同类双角色规则。无参数。Callers: 角色测试。 */
    private String roleRules() {
        return "TEST\tmethod-v1\t()V\tstring:entry\tentry\n" +
                "TEST\tmethod-v1\t()V\tstring:read|called-by:entry\tread\n";
    }

    /** 仅发布被已匹配入口调用的成员，忽略同类未调用的同义方法。无参数，无返回。Callers: JUnit。 */
    @Test public void callerRelationshipResolvesRenamedMember() throws IOException {
        String owner = "LX/Related;";
        var host = type(owner, roleMethod(owner, "renamedEntry", "entry", "renamedRead", false),
                roleMethod(owner, "renamedRead", "read", null, false),
                roleMethod(owner, "decoy", "read", null, false));
        var result = HostDexIndex.scan(List.of(apk(host)), roleRules());
        assertEquals("X.Related", result.getProperty("TEST"));
        assertEquals("renamedRead()V", result.getProperty("member.TEST.read"));
        assertEquals("renamedEntry()V", result.getProperty("member.TEST.entry"));
    }

    /** 不同类分别满足角色时不能拼成虚假的完整类契约。无参数，无返回。Callers: JUnit。 */
    @Test public void rolesCannotBeCombinedAcrossClasses() throws IOException {
        String a = "LX/EntryOnly;", b = "LX/ReaderOnly;";
        var result = HostDexIndex.scan(List.of(apk(type(a, roleMethod(a, "entry", "entry", "read", false)),
                type(b, roleMethod(b, "read", "read", null, false)))), roleRules());
        assertEquals("候选数量=0", result.getProperty("error.TEST"));
        assertFalse(result.containsKey("member.TEST.entry"));
    }

    /** 同类双角色仍须存在实际调用关系。无参数，无返回。Callers: JUnit。 */
    @Test public void absentCallerRelationshipRejectsOwner() throws IOException {
        String owner = "LX/Disconnected;";
        var result = HostDexIndex.scan(List.of(apk(type(owner, roleMethod(owner, "a", "entry", null, false),
                roleMethod(owner, "b", "read", null, false)))), roleRules());
        assertEquals("候选数量=0", result.getProperty("error.TEST"));
    }

    /** 两个完整类契约不能按遍历顺序选择。无参数，无返回。Callers: JUnit。 */
    @Test public void multipleCompleteOwnersAreRejected() throws IOException {
        String a = "LX/First;", b = "LX/Second;";
        var result = HostDexIndex.scan(List.of(apk(
                type(a, roleMethod(a, "a", "entry", "b", false), roleMethod(a, "b", "read", null, false)),
                type(b, roleMethod(b, "a", "entry", "b", false), roleMethod(b, "b", "read", null, false)))), roleRules());
        assertEquals("候选数量=2", result.getProperty("error.TEST"));
    }

    /** 静态匹配必须显式声明，不复用实例方法规则。无参数，无返回。Callers: JUnit。 */
    @Test public void staticMethodRequiresExplicitContract() throws IOException {
        String owner = "LX/Static;";
        String path = apk(type(owner, roleMethod(owner, "renamed", "read", null, true)));
        String base = "TEST\tmethod-v1\t()V\t";
        assertEquals("X.Static", HostDexIndex.scan(List.of(path), base + "static|string:read\tread").getProperty("TEST"));
        assertEquals("候选数量=0", HostDexIndex.scan(List.of(path), base + "string:read\tread").getProperty("error.TEST"));
    }

    /** 拼错角色引用不能悄悄变成零候选。无参数，无返回。Callers: JUnit。 */
    @Test public void undeclaredCallerRoleIsRejected() throws IOException {
        String owner = "LX/Typo;";
        String path = apk(type(owner, roleMethod(owner, "a", "read", null, false)));
        assertThrows(IllegalArgumentException.class, () -> HostDexIndex.scan(List.of(path),
                "TEST\tmethod-v1\t()V\tstring:read|called-by:missing\tread"));
    }

    /** 指令方式与调用所属类都是契约，不能只按方法签名匹配。无参数，无返回。Callers: JUnit。 */
    @Test public void invocationKindAndSelfOwnerAreEnforced() {
        String owner = "LX/Owner;";
        var method = roleMethod(owner, "a", "entry", "b", false);
        assertTrue(HostMethodRule.parse(new String[] { "TEST", "method-v1", "()V",
                "invoke:INVOKE_VIRTUAL:self:()V", "entry" }).matches(method));
        assertFalse(HostMethodRule.parse(new String[] { "TEST", "method-v1", "()V",
                "invoke:INVOKE_SUPER:self:()V", "entry" }).matches(method));
        assertFalse(HostMethodRule.parse(new String[] { "TEST", "method-v1", "()V",
                "invoke:INVOKE_VIRTUAL:LX/Other;->b()V", "entry" }).matches(method));
    }

    /** 已知字段读取使用完整定义与类型；同名异类字段不能代替。无参数，无返回。Callers: JUnit。 */
    @Test public void fieldReadUsesExactOwnerAndType() {
        String owner = "LX/Switch;", config = "Lexample/Config;";
        var code = List.of(new ImmutableInstruction22c(Opcode.IGET_BOOLEAN, 0, 1,
                new ImmutableFieldReference(config, "enabled", "Z")), new ImmutableInstruction11x(Opcode.RETURN, 0));
        var method = new ImmutableMethod(owner, "a", List.of(), "Z", 9, Set.of(), Set.of(),
                new ImmutableMethodImplementation(2, code, List.of(), List.of()));
        assertTrue(HostMethodRule.parse(new String[] {"TEST", "method-v1", "()Z",
                "static|field:Lexample/Config;->enabled:Z", "enabled"}).matches(method));
        assertFalse(HostMethodRule.parse(new String[] {"TEST", "method-v1", "()Z",
                "static|field:Lexample/Other;->enabled:Z", "enabled"}).matches(method));
    }
}
