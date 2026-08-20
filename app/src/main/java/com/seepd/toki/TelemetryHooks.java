package com.seepd.toki;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;

import io.github.libxposed.api.XposedModule;

/** Blocks ByteDance AppLog, MobClick, and Tracker telemetry and event logging. */
final class TelemetryHooks extends HookFeature {
    TelemetryHooks(XposedModule module) {
        super(module);
    }

    int install(ClassLoader classLoader) {
        int installed = 0;
        installed += hookAppLog(classLoader, "com.ss.android.common.applog.AppLog");
        installed += hookAppLog(classLoader, "com.bytedance.applog.AppLog");
        installed += hookTracker(classLoader, "com.ss.android.ugc.aweme.common.MobClick");
        installed += hookTracker(classLoader, "com.ss.android.ugc.aweme.common.Tracker");
        return installed;
    }

    private int hookAppLog(ClassLoader classLoader, String className) {
        int count = 0;
        try {
            Class<?> type = Class.forName(className, false, classLoader);
            for (Method method : type.getDeclaredMethods()) {
                String name = method.getName();
                if (Modifier.isStatic(method.getModifiers())
                        && (name.startsWith("onEvent") || name.startsWith("onMiscEvent")
                        || name.startsWith("reportEvent"))) {
                    try {
                        hook(method)
                                .setId("toki-telemetry-" + className + "-" + name + "-" + method.getParameterCount())
                                .intercept(chain -> {
                                    // Block sending the event to ByteDance servers
                                    Class<?> returnType = method.getReturnType();
                                    if (returnType == boolean.class) {
                                        return true;
                                    }
                                    if (returnType == int.class || returnType == long.class) {
                                        return 0;
                                    }
                                    return null;
                                });
                        count++;
                    } catch (Throwable ignored) {
                    }
                }
            }
        } catch (ClassNotFoundException ignored) {
        } catch (Throwable error) {
            logError("Unable to hook " + className, error);
        }
        return count;
    }

    private int hookTracker(ClassLoader classLoader, String className) {
        int count = 0;
        try {
            Class<?> type = Class.forName(className, false, classLoader);
            for (Method method : type.getDeclaredMethods()) {
                String name = method.getName();
                if (Modifier.isStatic(method.getModifiers())
                        && (name.equals("onEvent") || name.equals("onEventV3") || name.equals("event"))) {
                    try {
                        hook(method)
                                .setId("toki-tracker-" + className + "-" + name + "-" + method.getParameterCount())
                                .intercept(chain -> null);
                        count++;
                    } catch (Throwable ignored) {
                    }
                }
            }
        } catch (ClassNotFoundException ignored) {
        } catch (Throwable error) {
            logError("Unable to hook tracker " + className, error);
        }
        return count;
    }
}
