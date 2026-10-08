package io.github.meiyongai.toki.hook;

import java.nio.file.*;
import java.util.*;

/** 独立 Android 进程基准，不加载 TikTok 类、不操作应用生命周期或数据。 */
public final class NativeParityRunner {
    /**
     * 对同一组代码测量内置结果读取和无缓存原生检索，并逐项核对离线结果。
     * @param args 第一个参数是 APK 路径清单，每行一个完整路径。
     * @return 无；任一属性不同直接抛出异常并输出差异。
     * Callers: 开发者通过 app_process 启动的独立验证进程。
     */
    public static void main(String[] args) throws Exception {
        List<String> paths = Files.readAllLines(Path.of(args[0]));
        String rules;
        try (var input = NativeParityRunner.class.getResourceAsStream("/toki-host-rules.tsv")) {
            if (input == null) throw new IllegalStateException("缺少规则资源");
            rules = new String(input.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
        }
        long start = System.nanoTime();
        String identity = HostDexIndex.identity(paths);
        Properties expected = HostBundledProfiles.INSTANCE.load(identity, rules);
        if (expected == null) throw new IllegalStateException("此完整构建没有离线对照结果：" + identity);
        System.out.println("PROFILE code=" + identity + " ms=" + (System.nanoTime() - start) / 1e6);
        expected.stringPropertyNames().stream().filter(key -> key.startsWith("cache.") || key.startsWith("profile."))
                .forEach(expected::remove);
        start = System.nanoTime();
        final long scanningStarted = start;
        final int[] last = {-1};
        Properties actual = HostNativeIndex.INSTANCE.scan(paths, rules, (done, total) -> {
            int step = done * 10 / total;
            if (step != last[0]) {
                last[0] = step;
                System.out.println("PROGRESS " + step + "/10 ms=" + (System.nanoTime() - scanningStarted) / 1e6);
            }
        });
        System.out.println("NATIVE ms=" + (System.nanoTime() - start) / 1e6 + " properties=" + actual.size());
        if (!expected.equals(actual)) {
            Set<String> keys = new TreeSet<>(expected.stringPropertyNames());
            keys.addAll(actual.stringPropertyNames());
            for (String key : keys) if (!Objects.equals(expected.getProperty(key), actual.getProperty(key)))
                System.out.println("DIFF " + key + " expected=" + expected.getProperty(key) + " actual=" + actual.getProperty(key));
            throw new IllegalStateException("原生结果与完整离线扫描不一致");
        }
        System.out.println("PARITY OK");
    }
}
