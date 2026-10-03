package io.github.meiyongai.toki.hook;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.zip.*;
import org.jf.dexlib2.*;
import org.jf.dexlib2.iface.ClassDef;
import org.jf.dexlib2.iface.instruction.Instruction;
import org.jf.dexlib2.immutable.*;
import org.jf.dexlib2.immutable.instruction.*;
import org.jf.dexlib2.immutable.reference.*;
import org.jf.dexlib2.writer.io.MemoryDataStore;
import org.jf.dexlib2.writer.pool.DexPool;
import org.junit.*;
import org.junit.rules.TemporaryFolder;
import static org.junit.Assert.*;

/** 用真实 DEX 校验成员改名、菜单参数移动和不完整链路拒绝。 */
public class HostCommentIndexTest {
    @Rule public TemporaryFolder temporary = new TemporaryFolder();
    private static final String S="Ljava/lang/String;", L="Ljava/util/List;", C="Landroid/content/ClipData;";
    private static final String A="LX/Action;", I="LX/Item;", B="LX/Binder;", D="LX/Display;", P="LX/Clip;";
    private static final String COMMENT="Lcom/ss/android/ugc/aweme/comment/model/Comment;";
    private static final String MENU="Lcom/ss/android/ugc/aweme/commentv2/commentlist/viewmodel/CommentActionMenuVM;";

    /** @param text 语义字符串。@return 常量指令。Callers: fixture。 */
    private static Instruction string(String text) { return new ImmutableInstruction21c(Opcode.CONST_STRING,0,new ImmutableStringReference(text)); }
    /** @param owner 所属类。@param name 字段名。@param type 字段类型。@return 实例读取。Callers: fixture。 */
    private static Instruction field(String owner,String name,String type) {
        return new ImmutableInstruction22c(type.equals("Z")?Opcode.IGET_BOOLEAN:Opcode.IGET_OBJECT,0,1,new ImmutableFieldReference(owner,name,type));
    }
    /** @param owner 所属类。@param name 方法名。@param result 返回类型。@param params 参数类型。
     * @return 方法引用指令。Callers: fixture。 */
    private static Instruction call(String owner,String name,String result,String... params) {
        return new ImmutableInstruction35c(Opcode.INVOKE_STATIC,0,0,0,0,0,0,new ImmutableMethodReference(owner,name,List.of(params),result));
    }
    /** @param owner 所属类。@param name 名称。@param result 返回类型。@param flags 修饰符。
     * @param params 参数。@param code 指令。@return 测试方法。Callers: fixture。 */
    private static ImmutableMethod method(String owner,String name,String result,int flags,List<String> params,Instruction... code) {
        var parameters=new ArrayList<ImmutableMethodParameter>();
        for(String p:params)parameters.add(new ImmutableMethodParameter(p,Set.of(),null));
        return new ImmutableMethod(owner,name,parameters,result,flags,Set.of(),Set.of(),
                new ImmutableMethodImplementation(20,List.of(code),List.of(),List.of()));
    }
    /** @param owner 所属类。@param methods 方法。@return 测试类。Callers: fixture。 */
    private static ClassDef type(String owner,ImmutableMethod... methods) {
        return new ImmutableClassDef(owner,1,"Ljava/lang/Object;",List.of(),null,Set.of(),List.of(),List.of(methods));
    }
    /** @param broken 要破坏的关联。@return 完整或定向损坏的复制链路。Callers: tests。 */
    private static List<ClassDef> fixture(String broken) {
        var copy=method(A,"renamedCopy","V",1,List.of(),string("copy_comment"),string("clipboard"),string("bpea-221"),
                call(COMMENT,"getText",S),call(COMMENT,"getTextExtra",L),call(I,"readComment",COMMENT),
                field(A,"renamedItem",I),call(broken.equals("clip")?"LX/OtherClip;":P,"build",C,S,S,L));
        var menu=method(MENU,"renamedMenu","V",9,List.of(B,S,I),string("comment_action_menu"),string("long_press"),
                call(broken.equals("action")?"LX/OtherAction;":A,"<init>","V"),field(B,"rendered",D),field(D,"isTranslated","Z"));
        var display=method(D,"toString",S,1,List.of(),
                string("CommentData(commentText="),field(D,"body",S),
                string(", commentTranslatedTextExtra="),field(D,"emojis",L),
                string(", cid="),field(D,"identity",broken.equals("fieldType")?L:S),
                string(", translated="),field(D,"isTranslated","Z"));
        var clip=method(P,"build",C,9,List.of(S,S,L),string("copy_label"),
                call("Landroid/content/ClipData;","newPlainText",C,"Ljava/lang/CharSequence;","Ljava/lang/CharSequence;"));
        var result=new ArrayList<>(List.of(type(A,copy),type(MENU,menu),type(D,display),type(P,clip)));
        if(broken.equals("duplicate")) result.set(1,type(MENU,menu,method(MENU,"anotherMenu","V",9,List.of(B,S,I),
                string("comment_action_menu"),string("long_press"),call(A,"<init>","V"),field(B,"rendered",D),field(D,"isTranslated","Z"))));
        return result;
    }
    /** @param classes DEX 类。@return 发布规则扫描结果。@throws IOException 文件错误。Callers: tests。 */
    private Properties scan(List<ClassDef> classes) throws IOException {
        var pool=new DexPool(Opcodes.getDefault()); for(var type:classes)pool.internClass(type);
        var bytes=new MemoryDataStore(); pool.writeTo(bytes);
        File apk=temporary.newFile("sample"+System.nanoTime()+".apk");
        try(var out=new ZipOutputStream(new FileOutputStream(apk))) {
            out.putNextEntry(new ZipEntry("classes.dex"));out.write(bytes.getData());out.closeEntry();
        }
        bytes.close();
        String rules;
        try(var in=getClass().getResourceAsStream("/toki-host-rules.tsv")) {
            rules=new String(Objects.requireNonNull(in).readAllBytes(),StandardCharsets.UTF_8).lines()
                    .filter(line->line.startsWith("COMMENT_COPY\t")).findFirst().orElseThrow();
        }
        return HostDexIndex.scan(List.of(apk.getAbsolutePath()),rules);
    }
    /** 改名、重排参数后仍返回真实字段和完整调用。无参数，无返回。Callers: JUnit。 */
    @Test public void renamedFieldsAndMovedMenuParametersResolve() throws IOException {
        Properties p=scan(fixture(""));
        assertEquals("X.Action",p.getProperty("COMMENT_COPY"));
        assertEquals(A+"->renamedItem:"+I,p.getProperty("members.COMMENT_COPY.item"));
        assertEquals(D+"->body:"+S,p.getProperty("members.COMMENT_COPY.text"));
        assertEquals(D+"->identity:"+S,p.getProperty("members.COMMENT_COPY.cid"));
        assertEquals(MENU+"->renamedMenu("+B+S+I+")V",p.getProperty("members.COMMENT_COPY.menu"));
        assertEquals(11,p.size());
    }
    /** 不能把无关联方法、错误字段或重复入口拼成有效契约。无参数，无返回。Callers: JUnit。 */
    @Test public void disconnectedWrongTypedAndAmbiguousChainsFailAtomically() throws IOException {
        for(String defect:List.of("clip","action","fieldType","duplicate")) {
            Properties p=scan(fixture(defect));
            assertTrue(defect,p.containsKey("error.COMMENT_COPY"));
            assertEquals(defect,1,p.size());
        }
    }
    /** 专用规则参数及与其他规则混用必须明确拒绝。无参数，无返回。Callers: JUnit。 */
    @Test public void malformedOrMixedRulesAreRejected() {
        assertThrows(IllegalArgumentException.class,()->HostDexIndex.scan(List.of(),"COMMENT_COPY\tcomment-v1\twrong\t-\t-"));
        assertThrows(IllegalArgumentException.class,()->HostDexIndex.scan(List.of(),
                "COMMENT_COPY\tcomment-v1\trelations\t-\t-\nCOMMENT_COPY\tmethod-v1\t()V\tstring:copy_comment\tcopy"));
    }
}
