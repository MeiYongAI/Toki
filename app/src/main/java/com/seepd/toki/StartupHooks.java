package com.seepd.toki;

import android.app.Activity;
import android.os.Bundle;
import android.os.SystemClock;

import java.lang.reflect.Method;
import java.util.concurrent.atomic.AtomicBoolean;

import io.github.libxposed.api.XposedModule;

/** Handles startup-only UI hooks that depend on the process attachment time. */
final class StartupHooks extends HookFeature {
    private final AtomicBoolean loginClosedLogged = new AtomicBoolean(false);
    private final long processAttachedAt;

    StartupHooks(XposedModule module, long processAttachedAt) {
        super(module);
        this.processAttachedAt = processAttachedAt;
    }

    int installLoginSkip(ClassLoader classLoader) {
        try {
            Class<?> loginActivityClass = Class.forName(
                    "com.ss.android.ugc.aweme.account.login.auth."
                            + "I18nSignUpActivityWithNoAnimation",
                    false,
                    classLoader);
            Method onCreate = loginActivityClass.getDeclaredMethod("onCreate", Bundle.class);
            hook(onCreate)
                    .setId("toki-skip-startup-login")
                    .intercept(chain -> {
                        Object result = chain.proceed();
                        Object target = chain.getThisObject();
                        long processAge = SystemClock.elapsedRealtime() - processAttachedAt;
                        if (processAge >= 0L && processAge <= 20_000L
                                && target instanceof Activity) {
                            Activity activity = (Activity) target;
                            activity.finish();
                            activity.overridePendingTransition(0, 0);
                            if (loginClosedLogged.compareAndSet(false, true)) {
                                logInfo("Closed startup login prompt");
                            }
                        }
                        return result;
                    });
            return 1;
        } catch (ClassNotFoundException ignored) {
            logInfo("Startup login activity is unavailable in this TikTok build");
            return 0;
        } catch (NoSuchMethodException error) {
            logError("Unable to find startup login Activity#onCreate(Bundle)", error);
            return 0;
        } catch (Throwable error) {
            logError("Unable to install startup login skip hook", error);
            return 0;
        }
    }
}
