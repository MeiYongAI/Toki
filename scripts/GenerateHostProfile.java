import io.github.meiyongai.toki.hook.HostDexIndex;
import io.github.meiyongai.toki.hook.HostProfileManifest;
import java.nio.file.*;
import java.io.*;
import java.util.*;

/** 使用完整离线校验生成可审查、可复现的构建适配资源与必要结构条件。 */
public final class GenerateHostProfile {
    /**
     * 为一个完整 APK 集合生成适配结果，同时合并由结构摘要证明的查询条件。
     * @param args 资源目录、格式标识、基础包及全部代码 Split 路径。
     * @return 无；无法读取、结构冲突或发布后代码改变时抛出异常。
     * Callers: 开发者离线命令行。
     */
    public static void main(String[] args) throws Exception {
        if (args.length < 3) throw new IllegalArgumentException("resources format apk [splits...]");
        Path resources = Path.of(args[0]);
        String rules = Files.readString(resources.resolve("toki-host-rules.tsv"));
        List<String> paths = Arrays.asList(args).subList(2, args.length);
        String identity = HostDexIndex.identity(paths), digest = HostDexIndex.digest(rules);
        long start = System.nanoTime();
        Properties result = HostDexIndex.scan(paths, rules);
        TreeMap<String, List<String>> symbols = new TreeMap<>();
        Set<String> shapes = new HashSet<>();
        for (String row : rules.split("\\R")) {
            if (row.isBlank() || row.startsWith("#")) continue;
            String[] columns = row.split("\t");
            symbols.computeIfAbsent(columns[0], key -> new ArrayList<>()).add(row);
            if (columns[1].matches("[0-9a-f]{64}")) shapes.add(columns[1]);
        }
        for (var entry : symbols.entrySet()) {
            String symbol = entry.getKey();
            if (result.containsKey(symbol) == result.containsKey("error." + symbol))
                throw new IllegalStateException("不完整或矛盾的适配结果：" + symbol);
            Collections.sort(entry.getValue());
            result.setProperty("cache.rule." + symbol, HostDexIndex.digest(String.join("\n", entry.getValue())));
        }
        Path hintsFile = resources.resolve("toki-host-shapes.tsv");
        TreeMap<String, String> hints = new TreeMap<>();
        if (Files.exists(hintsFile)) for (String line : Files.readAllLines(hintsFile)) {
            if (!line.isBlank() && !line.startsWith("#")) hints.put(line.split("\t")[0], line);
        }
        HostDexIndex.visit(paths, type -> {
            String shape = HostDexIndex.shape(type);
            if (!shapes.contains(shape)) return;
            int fields = 0, methods = 0;
            Set<String> names = new TreeSet<>();
            for (var field : type.getFields()) fields++;
            for (var method : type.getMethods()) { methods++; names.add(method.getName()); }
            String line = shape + "\t" + fields + "\t" + methods + "\t" +
                    (names.isEmpty() ? "-" : String.join("|", names));
            String previous = hints.putIfAbsent(shape, line);
            if (previous != null && !previous.equals(line)) throw new IllegalStateException("同一结构摘要的必要条件冲突");
        });
        hints.keySet().retainAll(shapes);
        if (!identity.equals(HostDexIndex.identity(paths))) throw new IllegalStateException("生成期间代码发生变化");
        result.setProperty("cache.format", args[1]);
        result.setProperty("cache.identity", identity);
        result.setProperty("cache.rules", digest);
        result.setProperty("cache.key", HostDexIndex.digest(args[1] + "\n" + identity + "\n" + digest));
        result.setProperty("cache.symbols", String.join(",", symbols.keySet()));
        result.setProperty("profile.checksum", HostProfileManifest.checksum(result));
        Path profiles = resources.resolve("toki-host-profiles");
        Files.createDirectories(profiles);
        // Properties 的标准转义保证成员集合中的换行与非 ASCII 错误信息可准确还原。
        ByteArrayOutputStream encoded = new ByteArrayOutputStream();
        result.store(encoded, null);
        List<String> lines = encoded.toString(java.nio.charset.StandardCharsets.ISO_8859_1).lines()
                .filter(line -> !line.startsWith("#")).sorted().toList();
        Files.writeString(profiles.resolve(identity + ".properties"), String.join("\n", lines) + "\n",
                java.nio.charset.StandardCharsets.ISO_8859_1);
        Files.writeString(hintsFile, String.join("\n", hints.values()) + "\n");
        System.out.println("identity=" + identity + " results=" + symbols.size() + " seconds=" +
                (System.nanoTime() - start) / 1e9 + " shapeCoverage=" + hints.size() + "/" + shapes.size());
    }
}
