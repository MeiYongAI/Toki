package com.seepd.toki;

import android.app.Application;
import android.content.Context;
import android.os.SystemClock;

import java.lang.reflect.Method;
import java.util.concurrent.atomic.AtomicBoolean;

import io.github.libxposed.api.XposedModule;
import io.github.libxposed.api.XposedModuleInterface.PackageReadyParam;

/** Modern libxposed entry point. It is loaded only in the selected TikTok process. */
public final class MainHook extends XposedModule {
    private static final String TAG = "Toki";
    private final AtomicBoolean initialized = new AtomicBoolean(false);

    @Override
    public void onPackageReady(PackageReadyParam param) {
        if (!param.isFirstPackage()
                || !ModuleConfig.TARGET_PACKAGE.equals(param.getPackageName())) {
            return;
        }

        try {
            Method attach = Application.class.getDeclaredMethod("attach", Context.class);
            hook(attach)
                    .setId("toki-attach")
                    .intercept(chain -> {
                        Object result = chain.proceed();
                        if (initialized.compareAndSet(false, true)) {
                            Object context = chain.getArg(0);
                            if (context instanceof Context) {
                                install(
                                        param.getClassLoader(),
                                        (Context) context,
                                        SystemClock.elapsedRealtime());
                            }
                        }
                        return result;
                    });
        } catch (Throwable error) {
            logError("Unable to hook Application.attach", error);
        }
    }

    private void install(ClassLoader classLoader, Context context, long processAttachedAt) {
        try {
            String versionName = readTikTokVersion(context);
            ModuleConfig config = loadModuleConfig(context);
            logInfo("Active for " + context.getPackageName());
            logInfo("TikTok version: " + (versionName == null ? "unknown" : versionName));
            if (!ModuleConfig.TESTED_TIKTOK_VERSION.equals(versionName)) {
                logInfo("This TikTok version is outside the supported target");
            }
            logInfo("Implementation and test target: "
                    + ModuleConfig.TESTED_TIKTOK_VERSION);
            logInfo("Hook revision: direct-ui-gates-2-loop-replay-frame-4");
            logInfo("Loop prevention setting: " + config.disableLoop);
            logInfo("Always show video progress bar setting: "
                    + config.alwaysShowVideoProgressBar);
            logInfo("Show author location setting: " + config.showAuthorLocation);
            logInfo("Default playback speed: " + config.defaultPlaybackSpeed);
            logInfo("Comment translation setting: " + config.autoTranslateComments);
            logInfo("Disable offline cold cache with network setting: "
                    + config.disableOfflineColdCacheWithNetwork);
            EnvironmentHooks environmentHooks = new EnvironmentHooks(this);
            if (config.regionSpoof) {
                installFeature("region spoof", () ->
                        environmentHooks.installRegion(classLoader, config.region));
            }
            if (config.languageSpoof || config.timeZoneSpoof) {
                installFeature("system environment spoof", () ->
                        environmentHooks.installSystemEnvironment(config));
            }
            if (config.skipStartupLogin) {
                installFeature("startup login skip", () -> {
                    int installedTargets = new StartupHooks(this, processAttachedAt)
                            .installLoginSkip(classLoader);
                    logInfo("Startup login skip hooks installed: "
                            + installedTargets + " target(s)");
                });
            }
            if (config.gpsSpoof) {
                installFeature("GPS spoof", () -> environmentHooks.installGps(config));
            }
            DownloadHooks downloadHooks = new DownloadHooks(this);
            if (config.removeDownloadRestrictions) {
                installFeature("download restriction removal", () ->
                        downloadHooks.installRestrictionRemoval(classLoader));
            }
            // The save-directory fields are independent from download permission bypassing.
            installFeature("download location", () ->
                    downloadHooks.installLocation(classLoader, config));
            PlaybackHooks playbackHooks = new PlaybackHooks(this);
            installFeature("default playback speed", () ->
                    playbackHooks.installDefaultSpeed(classLoader));
            if (config.alwaysShowVideoProgressBar && !config.hideVideoProgressBar) {
                installFeature("always-visible video progress bar", () -> {
                    int installedTargets = playbackHooks.installAlwaysShowProgressBar(classLoader);
                    logInfo("Always-visible progress-bar hooks installed: "
                            + installedTargets + "/1 target(s)");
                });
            } else if (config.alwaysShowVideoProgressBar) {
                logInfo("Always-visible progress bar skipped because the hide option takes priority");
            }
            if (config.showAuthorLocation) {
                installFeature("author location", () -> {
                    int installedTargets = new AuthorLocationHooks(this).install(classLoader);
                    logInfo("Author-location hooks installed: "
                            + installedTargets + " target(s)");
                });
            }
            if (config.autoTranslateComments) {
                Context applicationContext = context.getApplicationContext();
                Context stateContext = applicationContext == null ? context : applicationContext;
                boolean translationEnabled = ModuleConfig.loadCommentTranslationActive(context);
                logInfo("Persistent comment translation state: " + translationEnabled);
                installFeature("comment translation", () ->
                        new CommentTranslationHooks(this, stateContext, translationEnabled)
                                .install(classLoader));
            }
            PurificationHooks purificationHooks = new PurificationHooks(this);
            if (config.allowDuet || config.allowStitch) {
                installFeature("reuse permissions", () ->
                        new ReusePermissionHooks(this).install(classLoader, config));
            }
            if (config.hasComponentPurificationEnabled()) {
                installFeature("page purification", () -> {
                    int installedTargets = purificationHooks.installComponents(
                            classLoader,
                            config);
                    logInfo("Page purification hooks installed: "
                            + installedTargets + " target(s)");
                });
            }
            if (config.hasFeedOverlayPurificationEnabled()) {
                installFeature("feed overlay purification", () -> {
                    int installedTargets = new FeedOverlayHooks(this).install(
                            classLoader,
                            config);
                    logInfo("Feed overlay purification hooks installed: "
                            + installedTargets + " target(s)");
                });
            }
            if (config.hasGlobalNavigationPurificationEnabled()) {
                installFeature("global navigation purification", () -> {
                    int installedTargets = purificationHooks.installGlobalNavigation(
                            classLoader,
                            config);
                    logInfo("Global navigation purification hooks installed: "
                            + installedTargets + " target(s)");
                });
            }
            if (config.hideFeedAds || config.hideLive || config.hideImages
                    || config.hideAiGenerated || config.forceRegion
                    || config.hideLongPosts || config.filterViewsLikes) {
                installFeature("feed filters", () ->
                        new FeedHooks(this).install(classLoader, config));
            }
            if (config.disableOfflineColdCacheWithNetwork) {
                installFeature("offline cold cache with network restriction", () ->
                        new OfflineCacheHooks(this, context).install(classLoader));
            }
            if (config.disableLoop) {
                installFeature("loop prevention", () -> {
                    logInfo("Installing loop-prevention bridge");
                    int installedTargets = playbackHooks.installLoopPrevention(classLoader);
                    if (installedTargets > 0) {
                        logInfo("Loop-prevention bridge installed: "
                                + installedTargets + " target(s)");
                    } else {
                        logError(
                                "Loop-prevention bridge unavailable: no compatible video engine target",
                                new ClassNotFoundException("TTVideoEngine#setLooping(boolean)"));
                    }
                });
            }
        } catch (Throwable error) {
            logError("Module hook installation aborted", error);
        }
    }

    private static String readTikTokVersion(Context context) {
        try {
            return context.getPackageManager()
                    .getPackageInfo(context.getPackageName(), 0)
                    .versionName;
        } catch (android.content.pm.PackageManager.NameNotFoundException
                 | RuntimeException ignored) {
            return null;
        }
    }

    private ModuleConfig loadModuleConfig(Context context) {
        try {
            ModuleConfig config = ModuleConfig.fromPreferences(
                    getRemotePreferences(ModuleConfig.PREFS));
            logInfo("Loaded module settings through remote preferences");
            return config;
        } catch (Throwable error) {
            logError("Unable to read remote module preferences; using defaults", error);
            return ModuleConfig.defaults();
        }
    }

    private void installFeature(String name, FeatureInstaller installer) {
        try {
            installer.install();
            logInfo("Completed " + name + " hook installation");
        } catch (Throwable error) {
            logError("Unable to install " + name + " hooks", error);
        }
    }

    @FunctionalInterface
    private interface FeatureInstaller {
        void install() throws Throwable;
    }

    private void logInfo(String message) {
        log(4, TAG, message);
    }

    private void logError(String message, Throwable error) {
        log(6, TAG, message, error);
    }
}
