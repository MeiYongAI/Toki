package com.seepd.toki;

import android.content.SharedPreferences;

import java.lang.reflect.Executable;

import io.github.libxposed.api.XposedInterface;
import io.github.libxposed.api.XposedModule;

/** Shared access to the single libxposed module instance used by every feature group. */
abstract class HookFeature {
    private static final String TAG = "Toki";
    private final XposedModule module;

    HookFeature(XposedModule module) {
        this.module = module;
    }

    protected final XposedInterface.HookBuilder hook(Executable executable) {
        return module.hook(executable);
    }

    protected final SharedPreferences getRemotePreferences(String name) {
        return module.getRemotePreferences(name);
    }

    protected final void logInfo(String message) {
        module.log(4, TAG, message);
    }

    protected final void logError(String message, Throwable error) {
        module.log(6, TAG, message, error);
    }
}
