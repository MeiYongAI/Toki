package io.github.meiyongai.toki.hook;

import android.util.AtomicFile;
import android.util.Log;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import org.robolectric.annotation.Implementation;
import org.robolectric.annotation.Implements;

/** 让主机测试保留 Android/POSIX rename 替换既有文件的语义；其余 AtomicFile 代码仍实际执行。 */
@Implements(AtomicFile.class)
public class PosixAtomicFileShadow {
    /**
     * 执行原子替换，失败时按 Android AtomicFile 的契约记录错误，由提交后读取校验发现失败。
     * @param source 待提交文件。
     * @param target 正式文件。
     * @return 无返回值。
     * Callers: Robolectric 对 AtomicFile.rename(File, File) 的测试替身分发。
     */
    @Implementation
    protected static void rename(File source, File target) {
        try {
            if (target.isDirectory()) Files.delete(target.toPath());
            Files.move(source.toPath(), target.toPath(), StandardCopyOption.ATOMIC_MOVE,
                    StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException error) {
            Log.e("AtomicFile", "Failed to rename " + source + " to " + target, error);
        }
    }
}
