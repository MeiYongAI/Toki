package com.seepd.toki;

import android.content.Context;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.concurrent.atomic.AtomicBoolean;

import io.github.libxposed.api.XposedModule;

/** Prevents TikTok from serving offline-mode feed items while Android has validated Internet. */
final class OfflineCacheHooks extends HookFeature {
    private static final String CACHE_PROVIDER_REGISTRY = "X.0MPt";
    private static final String CACHE_REQUEST_SCENE = "X.0MPs";
    private static final String CACHE_SOURCE_TYPE = "X.0MPT";
    private static final String OFFLINE_SWITCH =
            "com.ss.android.ugc.aweme.offlinemode.ui.popup.OfflineModeSwitchComponent";
    private final Context context;
    private final AtomicBoolean blockedLogged = new AtomicBoolean(false);

    OfflineCacheHooks(XposedModule module, Context context) {
        super(module);
        Context applicationContext = context.getApplicationContext();
        this.context = applicationContext == null ? context : applicationContext;
    }

    void install(ClassLoader classLoader) throws ReflectiveOperationException {
        Class<?> registryClass = Class.forName(CACHE_PROVIDER_REGISTRY, false, classLoader);
        Class<?> sceneClass = Class.forName(CACHE_REQUEST_SCENE, false, classLoader);
        Class<?> sourceTypeClass = Class.forName(CACHE_SOURCE_TYPE, false, classLoader);
        Method findProvider = registryClass.getDeclaredMethod("LIZ", sceneClass, sourceTypeClass);
        if (Modifier.isStatic(findProvider.getModifiers())) {
            throw new NoSuchMethodException(CACHE_PROVIDER_REGISTRY + "#LIZ(scene, sourceType)");
        }
        hook(findProvider)
                .setId("toki-disable-online-offline-cache-source")
                .intercept(chain -> {
                    Object sourceType = chain.getArg(1);
                    if (!hasValidatedInternet()
                            || !(sourceType instanceof Enum)
                            || !"OFFLINE_MODE".equals(((Enum<?>) sourceType).name())) {
                        return chain.proceed();
                    }
                    logBlockedOnce();
                    return null;
                });

        Class<?> switchClass = Class.forName(OFFLINE_SWITCH, false, classLoader);
        Method checkNetworkStatus = switchClass.getDeclaredMethod("Jo");
        if (Modifier.isStatic(checkNetworkStatus.getModifiers())
                || checkNetworkStatus.getReturnType() != void.class) {
            throw new NoSuchMethodException(OFFLINE_SWITCH + "#Jo(): void");
        }
        hook(checkNetworkStatus)
                .setId("toki-disable-online-offline-mode-switch")
                .intercept(chain -> {
                    if (!hasValidatedInternet()) {
                        return chain.proceed();
                    }
                    logBlockedOnce();
                    return null;
                });

        Method showOfflineToast = switchClass.getDeclaredMethod("Yo", boolean.class);
        if (Modifier.isStatic(showOfflineToast.getModifiers())
                || showOfflineToast.getReturnType() != void.class) {
            throw new NoSuchMethodException(OFFLINE_SWITCH + "#Yo(boolean): void");
        }
        hook(showOfflineToast)
                .setId("toki-disable-online-offline-cache-toast")
                .intercept(chain -> {
                    if (!hasValidatedInternet()) {
                        return chain.proceed();
                    }
                    logBlockedOnce();
                    return null;
                });
    }

    private boolean hasValidatedInternet() {
        ConnectivityManager manager =
                (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
        if (manager == null) {
            return false;
        }
        for (Network network : manager.getAllNetworks()) {
            NetworkCapabilities capabilities = manager.getNetworkCapabilities(network);
            if (capabilities != null
                    && capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                    && capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)) {
                return true;
            }
        }
        return false;
    }

    private void logBlockedOnce() {
        if (blockedLogged.compareAndSet(false, true)) {
            logInfo("Blocked TikTok offline mode while Android network is validated");
        }
    }
}
