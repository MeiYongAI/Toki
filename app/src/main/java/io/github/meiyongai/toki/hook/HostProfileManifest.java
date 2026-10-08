package io.github.meiyongai.toki.hook;

import java.util.Properties;
import java.util.TreeSet;

/** 对发布清单的全部键值做确定性完整性校验，包括多行成员集合。 */
public final class HostProfileManifest {
    /**
     * 计算除 checksum 本身以外的清单摘要；长度前缀消除分隔符歧义。
     * @param properties 发布清单，不修改调用方数据。
     * @return SHA-256 摘要。
     * Callers: HostBundledProfiles.read、GenerateHostProfile.main、HostBundledProfilesTest。
     */
    public static String checksum(Properties properties) {
        StringBuilder encoded = new StringBuilder();
        for (String key : new TreeSet<>(properties.stringPropertyNames())) {
            if (key.equals("profile.checksum")) continue;
            String value = properties.getProperty(key);
            encoded.append(key.length()).append(':').append(key).append(value.length()).append(':').append(value);
        }
        return HostDexIndex.digest(encoded.toString());
    }
}
