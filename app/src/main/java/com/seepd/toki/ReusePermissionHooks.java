package com.seepd.toki;

import java.lang.reflect.Method;

import io.github.libxposed.api.XposedModule;

/** Overrides duet and stitch permission getters selected by the user. */
final class ReusePermissionHooks extends HookFeature {
    ReusePermissionHooks(XposedModule module) {
        super(module);
    }

    void install(ClassLoader classLoader, ModuleConfig config) {
        if (config.allowDuet) {
            hookReuseGetter(classLoader, "com.ss.android.ugc.aweme.feed.model.Aweme", "getDuetSetting");
            hookReuseGetter(classLoader, "com.ss.android.ugc.aweme.profile.model.User", "getDuetSetting");
        }
        if (config.allowStitch) {
            hookReuseGetter(classLoader, "com.ss.android.ugc.aweme.feed.model.Aweme", "getStitchSetting");
            hookReuseGetter(classLoader, "com.ss.android.ugc.aweme.profile.model.User", "getStitchSetting");
        }
    }

    private void hookReuseGetter(ClassLoader classLoader, String className, String methodName) {
        try {
            Class<?> type = Class.forName(className, false, classLoader);
            Method method = type.getMethod(methodName);
            if (method.getReturnType() != int.class) {
                return;
            }
            hook(method)
                    .setId("toki-" + methodName + "-" + className)
                    .intercept(chain -> 0);
        } catch (ClassNotFoundException | NoSuchMethodException ignored) {
            // The model is version-specific; leave missing variants untouched.
        } catch (Throwable error) {
            logError("Unable to hook " + className + "#" + methodName, error);
        }
    }
}
