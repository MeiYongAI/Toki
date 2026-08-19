package com.seepd.toki;

import android.content.ContentResolver;
import android.content.ContentValues;
import android.net.Uri;
import android.os.Environment;

import java.io.File;
import java.lang.reflect.Method;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicBoolean;

import io.github.libxposed.api.XposedModule;

/** Handles download permission overrides, no-watermark addresses, and save locations. */
final class DownloadHooks extends HookFeature {
    private final AtomicBoolean locationRewriteLogged = new AtomicBoolean(false);

    DownloadHooks(XposedModule module) {
        super(module);
    }

    void installRestrictionRemoval(ClassLoader classLoader) {
        hookReturnConstant(classLoader, "com.ss.android.ugc.aweme.feed.model.ACLCommonShare", "getCode", 0);
        hookReturnConstant(classLoader, "com.ss.android.ugc.aweme.feed.model.ACLCommonShare", "getShowType", 2);
        hookReturnConstant(classLoader, "com.ss.android.ugc.aweme.feed.model.ACLCommonShare", "getTranscode", 1);
        hookAclTranscode(classLoader, "getDownloadMaskPanel");
        hookAclTranscode(classLoader, "getDownloadGeneral");
        hookAclTranscode(classLoader, "getDownloadSharePanel");
        hookNoWatermarkDownloadAddress(classLoader);
    }

    /** Official TikTok 46.4.3 saves media to DCIM/Camera via MediaStore. */
    void installLocation(ClassLoader classLoader, ModuleConfig config) {
        int bridgeHooks = installMediaInsertBridge(
                classLoader, "X.0yn6", "X.0wj1", config);

        int installed = 0;
        for (Method method : ContentResolver.class.getDeclaredMethods()) {
            Class<?>[] parameterTypes = method.getParameterTypes();
            if (!"insert".equals(method.getName()) || parameterTypes.length < 2
                    || parameterTypes[0] != Uri.class || parameterTypes[1] != ContentValues.class) {
                continue;
            }
            try {
                hook(method)
                        .setId("toki-official-download-location-" + parameterTypes.length)
                        .intercept(chain -> {
                            Object collection = chain.getArg(0);
                            Object values = chain.getArg(1);
                            if (collection instanceof Uri && values instanceof ContentValues) {
                                rewriteLocation((Uri) collection, (ContentValues) values, config);
                            }
                            return chain.proceed();
                        });
                installed++;
            } catch (Throwable error) {
                logError("Unable to hook ContentResolver#insert(" + parameterTypes.length + ")", error);
            }
        }
        logInfo("Official 46.4.3 download location hooks installed (" + bridgeHooks
                + " TikTok bridge, " + installed + " framework insert variants)");
    }

    private int installMediaInsertBridge(
            ClassLoader classLoader,
            String className,
            String metadataClassName,
            ModuleConfig config) {
        try {
            Class<?> type = Class.forName(className, false, classLoader);
            Class<?> metadataType = Class.forName(metadataClassName, false, classLoader);
            Method method = type.getDeclaredMethod(
                    "LJJIJLIJ",
                    ContentResolver.class,
                    Uri.class,
                    ContentValues.class,
                    metadataType);
            hook(method)
                    .setId("toki-464-media-insert-bridge")
                    .intercept(chain -> {
                        Object collection = chain.getArg(1);
                        Object values = chain.getArg(2);
                        if (collection instanceof Uri && values instanceof ContentValues) {
                            rewriteLocation((Uri) collection, (ContentValues) values, config);
                        }
                        return chain.proceed();
                    });
            return 1;
        } catch (ClassNotFoundException ignored) {
            logInfo("TikTok 46.4.3 media insert bridge unavailable");
        } catch (NoSuchMethodException ignored) {
            logInfo("TikTok 46.4.3 media insert bridge signature unavailable");
        } catch (Throwable error) {
            logError("Unable to hook TikTok 46.4.3 media insert bridge", error);
        }
        return 0;
    }

    private void rewriteLocation(Uri collection, ContentValues values, ModuleConfig config) {
        if (!isExternalMediaCollection(collection)) {
            return;
        }
        String originalRelativePath = values.getAsString("relative_path");
        String originalDataPath = values.getAsString("_data");
        boolean scopedStorage = isOfficialDownloadDirectory(originalRelativePath);
        boolean directFileStorage = !scopedStorage && isOfficialDirectFilePath(originalDataPath);
        if (!scopedStorage && !directFileStorage) {
            return;
        }

        String target = targetDirectoryFor(collection, values, config);
        if (target == null) {
            return;
        }
        String rewrittenPath;
        if (scopedStorage) {
            rewrittenPath = target.endsWith("/") ? target : target + "/";
            values.put("relative_path", rewrittenPath);
        } else {
            File targetDirectory = new File(Environment.getExternalStorageDirectory(), target);
            if (!targetDirectory.exists()) {
                targetDirectory.mkdirs();
            }
            rewrittenPath = new File(targetDirectory, new File(originalDataPath).getName()).getPath();
            values.put("_data", rewrittenPath);
        }
        if (locationRewriteLogged.compareAndSet(false, true)) {
            String originalPath = scopedStorage ? originalRelativePath : originalDataPath;
            logInfo("Official download location redirected " + originalPath + " -> "
                    + rewrittenPath);
        }
    }

    private static boolean isExternalMediaCollection(Uri collection) {
        if (collection == null || !"media".equals(collection.getAuthority())) {
            return false;
        }
        String path = collection.getPath();
        return path != null && (path.contains("/images/") || path.contains("/video/")
                || path.contains("/file"));
    }

    private static boolean isOfficialDownloadDirectory(String path) {
        if (path == null) {
            return false;
        }
        String normalized = path.trim().replace('\\', '/');
        while (normalized.startsWith("/")) {
            normalized = normalized.substring(1);
        }
        normalized = normalized.toLowerCase(Locale.ROOT);
        return "dcim/camera".equals(normalized) || normalized.startsWith("dcim/camera/");
    }

    private static boolean isOfficialDirectFilePath(String path) {
        if (path == null) {
            return false;
        }
        String normalized = path.trim().replace('\\', '/').toLowerCase(Locale.ROOT);
        String root = Environment.getExternalStorageDirectory().getAbsolutePath()
                .replace('\\', '/').toLowerCase(Locale.ROOT);
        return normalized.startsWith(root + "/dcim/camera/")
                || normalized.startsWith("/sdcard/dcim/camera/");
    }

    private static String targetDirectoryFor(
            Uri collection, ContentValues values, ModuleConfig config) {
        String mimeType = values.getAsString("mime_type");
        String displayName = values.getAsString("_display_name");
        String type = mimeType == null ? "" : mimeType.toLowerCase(Locale.ROOT);
        String name = displayName == null ? "" : displayName.toLowerCase(Locale.ROOT);
        if (type.contains("gif") || name.endsWith(".gif")) {
            return normalizeRelativeMediaDirectory(config.gifLocation);
        }
        String collectionPath = collection.getPath();
        if (type.startsWith("image/")
                || (collectionPath != null && collectionPath.contains("/images/"))) {
            return normalizeRelativeMediaDirectory(config.picLocation);
        }
        return normalizeRelativeMediaDirectory(config.videoLocation);
    }

    /** MediaStore requires a path relative to the shared-storage root. */
    private static String normalizeRelativeMediaDirectory(String configuredPath) {
        if (configuredPath == null) {
            return null;
        }
        String path = configuredPath.trim().replace('\\', '/');
        if (path.isEmpty() || path.contains("://") || path.startsWith("/")) {
            return null;
        }

        StringBuilder normalized = new StringBuilder();
        for (String segment : path.split("/")) {
            if (segment.isEmpty() || ".".equals(segment)) {
                continue;
            }
            if ("..".equals(segment) || segment.indexOf('\u0000') >= 0) {
                return null;
            }
            if (normalized.length() > 0) {
                normalized.append('/');
            }
            normalized.append(segment);
        }
        return normalized.length() == 0 ? null : normalized.toString();
    }

    private void hookNoWatermarkDownloadAddress(ClassLoader classLoader) {
        try {
            Class<?> type = Class.forName(
                    "com.ss.android.ugc.aweme.feed.model.Video", false, classLoader);
            Method noWatermarkAddress = type.getMethod("getDownloadNoWatermarkAddr");
            for (Method method : type.getDeclaredMethods()) {
                if (!"getDownloadAddr".equals(method.getName()) || method.getParameterCount() != 0) {
                    continue;
                }
                hook(method)
                        .setId("toki-download-no-watermark")
                        .intercept(chain -> {
                            Object normalAddress = chain.proceed();
                            try {
                                Object result = noWatermarkAddress.invoke(chain.getThisObject());
                                return result != null ? result : normalAddress;
                            } catch (ReflectiveOperationException | RuntimeException ignored) {
                                return normalAddress;
                            }
                        });
            }
        } catch (ClassNotFoundException | NoSuchMethodException ignored) {
            // Some TikTok builds do not expose a no-watermark video address.
        } catch (Throwable error) {
            logError("Unable to hook no-watermark download address", error);
        }
    }

    private void hookAclTranscode(ClassLoader classLoader, String getterName) {
        try {
            Class<?> type = Class.forName(
                    "com.ss.android.ugc.aweme.feed.model.AwemeACLShare", false, classLoader);
            for (Method method : type.getDeclaredMethods()) {
                if (!getterName.equals(method.getName()) || method.getParameterCount() != 0) {
                    continue;
                }
                hook(method)
                        .setId("toki-acl-" + getterName)
                        .intercept(chain -> {
                            Object acl = chain.proceed();
                            forceTranscode(acl);
                            return acl;
                        });
            }
        } catch (Throwable error) {
            logError("Unable to hook AwemeACLShare#" + getterName, error);
        }
    }

    private static void forceTranscode(Object acl) {
        if (acl == null) {
            return;
        }
        try {
            acl.getClass().getMethod("setTranscode", int.class).invoke(acl, 1);
        } catch (ReflectiveOperationException | RuntimeException ignored) {
            // The matching TikTok ACL model exposes this setter.
        }
    }

    private void hookReturnConstant(
            ClassLoader classLoader, String className, String methodName, Object value) {
        try {
            Class<?> type = Class.forName(className, false, classLoader);
            for (Method method : type.getDeclaredMethods()) {
                if (methodName.equals(method.getName()) && method.getParameterCount() == 0) {
                    hook(method)
                            .setId("toki-" + methodName)
                            .intercept(chain -> value);
                }
            }
        } catch (Throwable error) {
            logError("Unable to hook " + className + "#" + methodName, error);
        }
    }
}
