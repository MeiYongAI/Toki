package com.seepd.toki;

import android.app.Activity;
import android.app.Application;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.content.res.Configuration;
import android.graphics.drawable.Drawable;
import android.location.Location;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.os.LocaleList;
import android.os.SystemClock;
import android.telephony.TelephonyManager;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewParent;
import android.view.ViewTreeObserver;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.RelativeLayout;

import java.io.File;
import java.lang.ref.WeakReference;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TimeZone;
import java.util.WeakHashMap;
import java.time.ZoneId;
import java.util.concurrent.atomic.AtomicBoolean;

import org.json.JSONException;
import org.json.JSONObject;

import io.github.libxposed.api.XposedModule;
import io.github.libxposed.api.XposedModuleInterface.PackageReadyParam;

/** Modern libxposed entry point. It is loaded only in the selected TikTok process. */
public final class MainHook extends XposedModule {
    private static final String TAG = "Toki";
    private static final String OFFICIAL_TRANSLATION_BUTTON_TAG =
            "toki-official-comment-translation-button";
    private static final int MAX_TRACKED_COMMENTS = 512;
    private final AtomicBoolean initialized = new AtomicBoolean(false);
    private final AtomicBoolean officialTranslationFailureLogged = new AtomicBoolean(false);
    private final AtomicBoolean officialDownloadLocationRewriteLogged = new AtomicBoolean(false);
    private final AtomicBoolean loopPreventionConfigInvocationLogged = new AtomicBoolean(false);
    private final AtomicBoolean loopPreventionEngineInvocationLogged = new AtomicBoolean(false);
    private final AtomicBoolean loopPreventionManualPauseLogged = new AtomicBoolean(false);
    private final AtomicBoolean loopPreventionManualPauseFailureLogged = new AtomicBoolean(false);
    private final AtomicBoolean defaultPlaybackSpeedAppliedLogged = new AtomicBoolean(false);
    private final AtomicBoolean defaultPlaybackSpeedFailureLogged = new AtomicBoolean(false);
    private final AtomicBoolean startupLoginClosedLogged = new AtomicBoolean(false);
    private final AtomicBoolean pagePurificationVisibilityLogged = new AtomicBoolean(false);
    private final AtomicBoolean globalNavigationPurificationVisibilityLogged = new AtomicBoolean(false);
    private final Object officialTranslationLock = new Object();
    private final LinkedHashMap<String, BoundComment> officialBoundComments = new LinkedHashMap<>();
    private final HashSet<String> officialTranslationRequests = new HashSet<>();
    private final WeakHashMap<Object, String> officialTranslatedActions = new WeakHashMap<>();
    private final WeakHashMap<Object, String> defaultPlaybackSpeedSourceIds = new WeakHashMap<>();
    private final WeakHashMap<View, Boolean> globalNavigationObservedRoots = new WeakHashMap<>();
    private volatile Context translationStateContext;
    private volatile boolean officialTranslationEnabled;
    private volatile String officialCommentPageAwemeId;
    private volatile String officialTranslatedAwemeId;
    private volatile long processAttachedAt;

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
                                processAttachedAt = SystemClock.elapsedRealtime();
                                install(param.getClassLoader(), (Context) context);
                            }
                        }
                        return result;
                    });
        } catch (Throwable error) {
            logError("Unable to hook Application.attach", error);
        }
    }

    private void install(ClassLoader classLoader, Context context) {
        try {
            ModuleConfig config = loadModuleConfig(context);
            logInfo("Active for " + context.getPackageName());
            logInfo("Hook revision: direct-ui-gates-2-loop-replay-frame-4");
            logInfo("Loop prevention setting: " + config.disableLoop);
            logInfo("Default playback speed: " + config.defaultPlaybackSpeed);
            logInfo("Comment translation setting: " + config.autoTranslateComments);
            if (config.regionSpoof) {
                installRegionSpoof(classLoader, config.region);
            }
            if (config.languageSpoof || config.timeZoneSpoof) {
                installSystemEnvironmentSpoof(config);
            }
            if (config.skipStartupLogin) {
                int installedTargets = installStartupLoginSkip(classLoader);
                logInfo("Startup login skip hooks installed: " + installedTargets + " target(s)");
            }
            if (config.gpsSpoof) {
                installGpsSpoof(config);
            }
            if (config.removeDownloadRestrictions) {
                installDownloadPatches(classLoader);
            }
            // The save-directory fields are independent from download permission bypassing.
            installOfficialDownloadLocationHook(classLoader, config);
            if (config.disableLoop) {
                logInfo("Installing loop-prevention bridge");
                int installedTargets = installLoopPrevention(classLoader);
                if (installedTargets > 0) {
                    logInfo("Loop-prevention bridge installed: " + installedTargets + " target(s)");
                } else {
                    logError(
                            "Loop-prevention bridge unavailable: no compatible video engine target",
                            new ClassNotFoundException("TTVideoEngine#setLooping(boolean)"));
                }
            }
            installDefaultPlaybackSpeed(classLoader);
            if (config.autoTranslateComments) {
                Context applicationContext = context.getApplicationContext();
                translationStateContext = applicationContext == null ? context : applicationContext;
                officialTranslationEnabled = ModuleConfig.loadCommentTranslationActive(context);
                logInfo("Persistent comment translation state: " + officialTranslationEnabled);
                installCommentTranslationButton(classLoader);
            }
            if (config.allowDuet || config.allowStitch) {
                installReusePermissionPatches(classLoader, config);
            }
            if (config.hasPagePurificationEnabled()) {
                int installedTargets = installPagePurification(classLoader, config);
                logInfo("Page purification hooks installed: " + installedTargets + " target(s)");
            }
            if (config.hasGlobalNavigationPurificationEnabled()) {
                if (installGlobalNavigationPurification(config)) {
                    logInfo("Global navigation purification listener installed");
                }
            }
            if (config.hideTrendingTopics || config.hideContentClassification) {
                int installedTargets = installVideoOverlayPurification(classLoader, config);
                logInfo("Video overlay purification hooks installed: " + installedTargets + " target(s)");
            }
            if (config.hideFeedAds || config.hideLive || config.hideImages
                    || config.hideAiGenerated || config.forceRegion
                    || config.hideLongPosts || config.filterViewsLikes) {
                installFeedFilters(classLoader, config);
            }
        } catch (Throwable error) {
            logError("Module hook installation aborted", error);
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

    private void installRegionSpoof(ClassLoader classLoader, RegionPreset preset) {
        hookTelephony("getSimCountryIso", preset.code.toLowerCase(Locale.ROOT));
        hookTelephony("getNetworkCountryIso", preset.code.toLowerCase(Locale.ROOT));
        hookTelephony("getSimOperator", preset.operator);
        hookTelephony("getNetworkOperator", preset.operator);
        hookTelephony("getSimOperatorName", preset.operatorName);
        hookTelephony("getNetworkOperatorName", preset.operatorName);
        installRegionPayloadPatches(classLoader, preset);
    }

    private void installGpsSpoof(ModuleConfig config) {
        int installed = 0;
        installed += hookLocationCoordinate("getLatitude", config.gpsLatitude);
        installed += hookLocationCoordinate("getLongitude", config.gpsLongitude);
        logInfo("GPS spoof hooks installed: " + installed + " coordinate getter(s)");
    }

    private void installSystemEnvironmentSpoof(ModuleConfig config) {
        Locale targetLocale = localeForRegion(config.region);
        String targetTimeZone = timeZoneForRegion(config.region);
        int installed = 0;
        if (config.languageSpoof) {
            installed += installLocaleSpoof(targetLocale);
        }
        if (config.timeZoneSpoof) {
            installed += installTimeZoneSpoof(targetTimeZone);
        }
        logInfo("System environment spoof hooks installed: " + installed
                + " target(s), locale=" + targetLocale.toLanguageTag()
                + ", timeZone=" + targetTimeZone);
    }

    private int installLocaleSpoof(Locale target) {
        int installed = 0;
        for (Method method : Locale.class.getDeclaredMethods()) {
            if (!"getDefault".equals(method.getName())
                    || !Modifier.isStatic(method.getModifiers())
                    || method.getReturnType() != Locale.class) {
                continue;
            }
            try {
                hook(method)
                        .setId("toki-locale-default-" + method.getParameterCount())
                        .intercept(chain -> target);
                installed++;
            } catch (Throwable error) {
                logError("Unable to hook Locale#getDefault", error);
            }
        }
        installed += hookLocaleListDefaults(target);
        installed += hookConfigurationLocales(target);
        return installed;
    }

    private int hookLocaleListDefaults(Locale target) {
        int installed = 0;
        LocaleList targetList = new LocaleList(target);
        for (Method method : LocaleList.class.getDeclaredMethods()) {
            if (!Modifier.isStatic(method.getModifiers())
                    || method.getParameterCount() != 0
                    || method.getReturnType() != LocaleList.class
                    || !("getDefault".equals(method.getName())
                    || "getAdjustedDefault".equals(method.getName()))) {
                continue;
            }
            try {
                hook(method)
                        .setId("toki-locale-list-" + method.getName())
                        .intercept(chain -> targetList);
                installed++;
            } catch (Throwable error) {
                logError("Unable to hook LocaleList#" + method.getName(), error);
            }
        }
        return installed;
    }

    private int hookConfigurationLocales(Locale target) {
        int installed = 0;
        for (Method method : Configuration.class.getDeclaredMethods()) {
            if (method.getParameterCount() != 0) {
                continue;
            }
            Object replacement = null;
            if ("getLocale".equals(method.getName()) && method.getReturnType() == Locale.class) {
                replacement = target;
            } else if ("getLocales".equals(method.getName())
                    && method.getReturnType() == LocaleList.class) {
                replacement = new LocaleList(target);
            }
            if (replacement == null) {
                continue;
            }
            try {
                Object value = replacement;
                hook(method)
                        .setId("toki-configuration-" + method.getName())
                        .intercept(chain -> value);
                installed++;
            } catch (Throwable error) {
                logError("Unable to hook Configuration#" + method.getName(), error);
            }
        }
        return installed;
    }

    private int installTimeZoneSpoof(String timeZoneId) {
        int installed = 0;
        TimeZone target = TimeZone.getTimeZone(timeZoneId);
        for (Method method : TimeZone.class.getDeclaredMethods()) {
            if (!"getDefault".equals(method.getName())
                    || !Modifier.isStatic(method.getModifiers())
                    || method.getParameterCount() != 0
                    || method.getReturnType() != TimeZone.class) {
                continue;
            }
            try {
                hook(method)
                        .setId("toki-timezone-default")
                        .intercept(chain -> (TimeZone) target.clone());
                installed++;
            } catch (Throwable error) {
                logError("Unable to hook TimeZone#getDefault", error);
            }
        }
        try {
            Method systemDefault = ZoneId.class.getDeclaredMethod("systemDefault");
            ZoneId targetZone = ZoneId.of(timeZoneId);
            hook(systemDefault)
                    .setId("toki-zoneid-system-default")
                    .intercept(chain -> targetZone);
            installed++;
        } catch (Throwable error) {
            logError("Unable to hook ZoneId#systemDefault", error);
        }
        try {
            Class<?> icuTimeZone = Class.forName("android.icu.util.TimeZone");
            Method getDefault = icuTimeZone.getDeclaredMethod("getDefault");
            Method getTimeZone = icuTimeZone.getDeclaredMethod("getTimeZone", String.class);
            Object targetTimeZone = getTimeZone.invoke(null, timeZoneId);
            hook(getDefault)
                    .setId("toki-icu-timezone-default")
                    .intercept(chain -> targetTimeZone);
            installed++;
        } catch (Throwable error) {
            logError("Unable to hook ICU TimeZone#getDefault", error);
        }
        return installed;
    }

    private static Locale localeForRegion(RegionPreset preset) {
        try {
            Class<?> uLocaleType = Class.forName("android.icu.util.ULocale");
            Method forLanguageTag = uLocaleType.getDeclaredMethod("forLanguageTag", String.class);
            Method addLikelySubtags = uLocaleType.getDeclaredMethod("addLikelySubtags", uLocaleType);
            Object regionLocale = forLanguageTag.invoke(null, "und-" + preset.code);
            Object likelyLocale = addLikelySubtags.invoke(null, regionLocale);
            Method getLanguage = uLocaleType.getDeclaredMethod("getLanguage");
            String language = (String) getLanguage.invoke(likelyLocale);
            if (language != null && !language.isEmpty()) {
                return new Locale(language, preset.code);
            }
        } catch (Throwable ignored) {
            // Fall back to English if ICU cannot infer a language for the region.
        }
        return Locale.US;
    }

    private static String timeZoneForRegion(RegionPreset preset) {
        try {
            Class<?> icuTimeZone = Class.forName("android.icu.util.TimeZone");
            Method getAvailableIds = icuTimeZone.getDeclaredMethod("getAvailableIDs", String.class);
            String[] ids = (String[]) getAvailableIds.invoke(null, preset.code);
            if (ids != null && ids.length > 0 && ids[0] != null && !ids[0].isEmpty()) {
                return ids[0];
            }
        } catch (Throwable ignored) {
            // Fall back to UTC for regions without a timezone database entry.
        }
        return "UTC";
    }

    private int installStartupLoginSkip(ClassLoader classLoader) {
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
                            if (startupLoginClosedLogged.compareAndSet(false, true)) {
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

    private int hookLocationCoordinate(String methodName, double value) {
        int installed = 0;
        for (Method method : Location.class.getDeclaredMethods()) {
            if (!methodName.equals(method.getName())
                    || method.getParameterCount() != 0
                    || method.getReturnType() != double.class) {
                continue;
            }
            try {
                hook(method)
                        .setId("toki-gps-" + methodName)
                        .intercept(chain -> value);
                installed++;
            } catch (Throwable error) {
                logError("Unable to hook Location#" + methodName, error);
            }
        }
        return installed;
    }

    /** Restores the comment-page translation control in the supported official TikTok client. */
    private void installCommentTranslationButton(ClassLoader classLoader) {
        if (hasStableCommentTranslationRuntime(classLoader)) {
            // Unknown minor builds must never be sent through an obsolete obfuscated bridge.
            installTikTok464CommentTranslationButton(classLoader);
        } else if (isModernTikTokVersion()) {
            logInfo("Comment translation bridge unavailable for this TikTok build; "
                    + "obsolete translation hooks were skipped");
        } else if (!installOfficialCommentTranslationButton(classLoader)) {
            installTikTok4632CommentTranslationButton(classLoader);
        }
    }

    private boolean isModernTikTokVersion() {
        Context context = translationStateContext;
        if (context == null) {
            return true;
        }
        try {
            String versionName = context.getPackageManager().getPackageInfo(
                    context.getPackageName(), 0).versionName;
            if (versionName == null) {
                return true;
            }
            String[] segments = versionName.split("\\.");
            int major = segments.length > 0 ? Integer.parseInt(segments[0]) : 0;
            int minor = segments.length > 1 ? Integer.parseInt(segments[1]) : 0;
            return major > 46 || (major == 46 && minor >= 4);
        } catch (RuntimeException ignored) {
            return true;
        } catch (android.content.pm.PackageManager.NameNotFoundException ignored) {
            return true;
        }
    }

    private static boolean hasStableCommentTranslationRuntime(ClassLoader classLoader) {
        try {
            Class<?> commentType = Class.forName(
                    "com.ss.android.ugc.aweme.comment.model.Comment", false, classLoader);
            Class<?> baseCommentCell = Class.forName(
                    "com.ss.android.ugc.aweme.commentv2.commentlist.powercell.BaseCommentCell",
                    false,
                    classLoader);
            Class.forName(
                    "com.ss.android.ugc.aweme.translation.service.ITranslationService",
                    false,
                    classLoader);
            Class.forName(
                    "com.ss.android.ugc.aweme.commentv2.actionbar.CommentPageActionBarAssem",
                    false,
                    classLoader);
            findCommentTranslationMembers(baseCommentCell, commentType);
            return true;
        } catch (ClassNotFoundException ignored) {
            return false;
        } catch (NoSuchFieldException ignored) {
            return false;
        }
    }

    /**
     * Uses public translation interfaces and structural discovery for obfuscated comment-cell
     * members. The same path therefore survives ordinary TikTok minor-version renaming.
     */
    private boolean installTikTok464CommentTranslationButton(ClassLoader classLoader) {
        try {
            Class<?> commentType = Class.forName(
                    "com.ss.android.ugc.aweme.comment.model.Comment", false, classLoader);
            Class<?> baseCommentCell = Class.forName(
                    "com.ss.android.ugc.aweme.commentv2.commentlist.powercell.BaseCommentCell",
                    false,
                    classLoader);
            TranslationBindingMembers bindingMembers = findCommentTranslationMembers(
                    baseCommentCell, commentType);

            Class<?> actionBarType = Class.forName(
                    "com.ss.android.ugc.aweme.commentv2.actionbar.CommentPageActionBarAssem",
                    false,
                    classLoader);
            Class<?> contextSourceType = Class.forName(
                    "com.ss.android.ugc.aweme.comment.model.CommentContextSource",
                    false,
                    classLoader);
            Class<?> contextSourceKt = Class.forName(
                    "com.ss.android.ugc.aweme.comment.model.CommentContextSourceKt",
                    false,
                    classLoader);
            Class<?> awemeType = Class.forName(
                    "com.ss.android.ugc.aweme.feed.model.Aweme", false, classLoader);
            Class<?> tuxIconViewType = Class.forName(
                    "com.bytedance.tux.icon.TuxIconView", false, classLoader);
            Class<?> translationServiceInterface = Class.forName(
                    "com.ss.android.ugc.aweme.translation.service.ITranslationService",
                    false,
                    classLoader);
            Class<?> serviceManagerType = Class.forName(
                    "com.ss.android.ugc.aweme.framework.services.ServiceManager",
                    false,
                    classLoader);

            Method getAwemeId = commentType.getMethod("getAwemeId");
            Method getCommentId = commentType.getMethod("getCid");
            Method isTranslated = commentType.getMethod("isTranslated");
            Field translationManager = bindingMembers.manager;
            Field boundComment = bindingMembers.comment;
            Field translationAction = bindingMembers.action;
            Method translate = bindingMembers.translate;
            Method resetTranslate = bindingMembers.reset;
            Method onActionBarCreated = actionBarType.getDeclaredMethod("onViewCreated", View.class);
            Method getCommentContext = findNoArgMethodReturningType(actionBarType, contextSourceType);
            Method getAweme = contextSourceKt.getMethod("aweme", contextSourceType);
            Method getAid = awemeType.getMethod("getAid");
            // A missing close-icon member should never prevent injection into the provided root.
            Field closeButton = findField(actionBarType, "LLJJLIIIJLLLLLLLZ");
            Method isTranslatable = findCommentTranslatabilityMethod(
                    translationServiceInterface, commentType);
            Method setTranslation = findPreferredMethodBySignature(
                    translationServiceInterface,
                    void.class,
                    new Class<?>[]{commentType, boolean.class},
                    "LJJJJZI",
                    "setTranslation",
                    "setCommentTranslation");
            Method getServiceManager = serviceManagerType.getMethod("get");
            Method getService = serviceManagerType.getMethod("getService", Class.class);
            Constructor<?> newTuxIconView = tuxIconViewType.getConstructor(Context.class);
            Method setIconRes = tuxIconViewType.getMethod("setIconRes", int.class);
            Method setIconWidth = tuxIconViewType.getMethod("setIconWidth", int.class);
            Method setIconHeight = tuxIconViewType.getMethod("setIconHeight", int.class);
            Method setTintColor = tuxIconViewType.getMethod("setTintColor", int.class);
            Method setTintColorRes = tuxIconViewType.getMethod("setTintColorRes", int.class);

            translationManager.setAccessible(true);
            boundComment.setAccessible(true);
            translationAction.setAccessible(true);
            if (getCommentContext != null) {
                getCommentContext.setAccessible(true);
            }
            setTranslation.setAccessible(true);

            OfficialTranslationBridge bridge = new OfficialTranslationBridge(
                    translationManager,
                    boundComment,
                    translationAction,
                    getAwemeId,
                    getCommentId,
                    isTranslated,
                    translate,
                    resetTranslate,
                    null,
                    null,
                    getCommentContext,
                    getAweme,
                    getAid,
                    closeButton,
                    newTuxIconView,
                    setIconRes,
                    setIconWidth,
                    setIconHeight,
                    setTintColor,
                    setTintColorRes,
                    null,
                    new DirectTranslationBridge(
                            null,
                            setTranslation,
                            isTranslatable,
                            getServiceManager,
                            getService,
                            translationServiceInterface));

            int bindHooks = hookTikTok464CommentBindingMethods(baseCommentCell, bridge);
            if (bindHooks == 0) {
                throw new NoSuchMethodException("BaseCommentCell#onBindItemView/Q6/K6(*)");
            }
            hook(onActionBarCreated)
                    .setId("toki-464-comment-translation-action-bar")
                    .intercept(chain -> {
                        Object result = chain.proceed();
                        Object root = chain.getArg(0);
                        injectOfficialCommentTranslationButton(
                                chain.getThisObject(), root instanceof View ? (View) root : null, bridge);
                        return result;
                    });
            logInfo("Enabled official TikTok 46.4.3 comment translation button hooks");
            return true;
        } catch (ClassNotFoundException | NoSuchMethodException | NoSuchFieldException error) {
            logInfo("Stable comment translation symbols unavailable: "
                    + error.getClass().getSimpleName() + ": " + error.getMessage());
            return false;
        } catch (Throwable error) {
            logError("Unable to enable TikTok 46.4.3 comment translation button", error);
            return false;
        }
    }

    /**
     * Official 46.3.3 keeps the native comment translation repository without exposing a page-wide
     * control. Track the cell-owned translation actions and expose them through a TuxIconView added
     * beside the existing close control.
     */
    private boolean installOfficialCommentTranslationButton(ClassLoader classLoader) {
        try {
            Class<?> commentType = Class.forName(
                    "com.ss.android.ugc.aweme.comment.model.Comment", false, classLoader);
            Class<?> commentItemType = Class.forName("X.0mYk", false, classLoader);
            Class<?> baseCommentCell = Class.forName(
                    "com.ss.android.ugc.aweme.commentv2.commentlist.powercell.BaseCommentCell",
                    false,
                    classLoader);
            Class<?> translationManagerType = Class.forName("X.0mXN", false, classLoader);
            Class<?> translationActionType = Class.forName("X.0nA8", false, classLoader);
            Class<?> translationServiceType = Class.forName("X.0oLG", false, classLoader);

            Method bindComment = findCommentBindingMethod(baseCommentCell, commentItemType);
            Field translationManager = baseCommentCell.getDeclaredField("LLJJIJI");
            Field boundComment = translationManagerType.getDeclaredField("LJIILJJIL");
            Field translationAction = translationManagerType.getDeclaredField("LIZLLL");
            Method getAwemeId = commentType.getMethod("getAwemeId");
            Method getCommentId = commentType.getMethod("getCid");
            Method isTranslated = commentType.getMethod("isTranslated");
            Method translate = translationActionType.getMethod("translate");
            Method resetTranslate = translationActionType.getMethod("resetTranslate");
            Field translationService = translationServiceType.getDeclaredField("LIZIZ");
            Method isTranslatable = translationServiceType.getMethod("LJIILIIL", commentType);

            Class<?> actionBarType = Class.forName(
                    "com.ss.android.ugc.aweme.commentv2.actionbar.CommentPageActionBarAssem",
                    false,
                    classLoader);
            Class<?> contextSourceType = Class.forName(
                    "com.ss.android.ugc.aweme.comment.model.CommentContextSource",
                    false,
                    classLoader);
            Class<?> contextSourceKt = Class.forName(
                    "com.ss.android.ugc.aweme.comment.model.CommentContextSourceKt",
                    false,
                    classLoader);
            Class<?> awemeType = Class.forName(
                    "com.ss.android.ugc.aweme.feed.model.Aweme", false, classLoader);
            Class<?> tuxIconViewType = Class.forName(
                    "com.bytedance.tux.icon.TuxIconView", false, classLoader);

            Method onActionBarCreated = actionBarType.getDeclaredMethod("onViewCreated", View.class);
            Method getCommentContext = actionBarType.getDeclaredMethod("eq");
            Method getAweme = contextSourceKt.getMethod("aweme", contextSourceType);
            Method getAid = awemeType.getMethod("getAid");
            Field closeButton = actionBarType.getDeclaredField("LLJJLIIIJLLLLLLLZ");
            Constructor<?> newTuxIconView = tuxIconViewType.getConstructor(Context.class);
            Method setIconRes = tuxIconViewType.getMethod("setIconRes", int.class);
            Method setIconWidth = tuxIconViewType.getMethod("setIconWidth", int.class);
            Method setIconHeight = tuxIconViewType.getMethod("setIconHeight", int.class);
            Method setTintColor = tuxIconViewType.getMethod("setTintColor", int.class);
            Method setTintColorRes = tuxIconViewType.getMethod("setTintColorRes", int.class);

            BatchTranslationBridge batchBridge = createOfficialBatchTranslationBridge(
                    classLoader, actionBarType);

            translationManager.setAccessible(true);
            boundComment.setAccessible(true);
            translationAction.setAccessible(true);
            translationService.setAccessible(true);
            getCommentContext.setAccessible(true);
            closeButton.setAccessible(true);

            OfficialTranslationBridge bridge = new OfficialTranslationBridge(
                    translationManager,
                    boundComment,
                    translationAction,
                    getAwemeId,
                    getCommentId,
                    isTranslated,
                    translate,
                    resetTranslate,
                    translationService,
                    isTranslatable,
                    getCommentContext,
                    getAweme,
                    getAid,
                    closeButton,
                    newTuxIconView,
                    setIconRes,
                    setIconWidth,
                    setIconHeight,
                    setTintColor,
                    setTintColorRes,
                    batchBridge,
                    null);

            hook(bindComment)
                    .setId("toki-official-comment-translation-bind")
                    .intercept(chain -> {
                        Object result = chain.proceed();
                        captureOfficialCommentBinding(chain.getThisObject(), bridge);
                        return result;
                    });
            hook(onActionBarCreated)
                    .setId("toki-official-comment-translation-action-bar")
                    .intercept(chain -> {
                        Object result = chain.proceed();
                        Object root = chain.getArg(0);
                        injectOfficialCommentTranslationButton(
                                chain.getThisObject(), root instanceof View ? (View) root : null, bridge);
                        return result;
                    });
            logInfo("Enabled official TikTok 46.3.3 comment translation button hooks");
            return true;
        } catch (ClassNotFoundException | NoSuchMethodException | NoSuchFieldException error) {
            logInfo("Official comment translation symbols unavailable: "
                    + error.getClass().getSimpleName() + ": " + error.getMessage());
            return false;
        } catch (Throwable error) {
            logError("Unable to enable official comment translation button", error);
            return false;
        }
    }

    /**
     * TikTok 46.3.2 binds comment cells with {@code X.0Tus} and exposes the native batch
     * translator directly. It does not retain the 46.3.3 per-comment action object.
     */
    private void installTikTok4632CommentTranslationButton(ClassLoader classLoader) {
        try {
            Class<?> commentType = Class.forName(
                    "com.ss.android.ugc.aweme.comment.model.Comment", false, classLoader);
            Class<?> baseCommentCell = Class.forName(
                    "com.ss.android.ugc.aweme.commentv2.commentlist.powercell.BaseCommentCell",
                    false,
                    classLoader);
            Method getBoundComment = baseCommentCell.getDeclaredMethod("l6");
            Method getAwemeId = commentType.getMethod("getAwemeId");
            Method getCommentId = commentType.getMethod("getCid");
            Method isTranslated = commentType.getMethod("isTranslated");

            Class<?> actionBarType = Class.forName(
                    "com.ss.android.ugc.aweme.commentv2.actionbar.CommentPageActionBarAssem",
                    false,
                    classLoader);
            Class<?> contextSourceType = Class.forName(
                    "com.ss.android.ugc.aweme.comment.model.CommentContextSource",
                    false,
                    classLoader);
            Class<?> contextSourceKt = Class.forName(
                    "com.ss.android.ugc.aweme.comment.model.CommentContextSourceKt",
                    false,
                    classLoader);
            Class<?> awemeType = Class.forName(
                    "com.ss.android.ugc.aweme.feed.model.Aweme", false, classLoader);
            Class<?> tuxIconViewType = Class.forName(
                    "com.bytedance.tux.icon.TuxIconView", false, classLoader);
            Method onActionBarCreated = actionBarType.getDeclaredMethod("onViewCreated", View.class);
            Method onCommentPageReused = actionBarType.getDeclaredMethod("JU", awemeType);
            Method getCommentContext = actionBarType.getDeclaredMethod("cq");
            Method getAweme = contextSourceKt.getMethod("aweme", contextSourceType);
            Method getAid = awemeType.getMethod("getAid");
            Field closeButton = actionBarType.getDeclaredField("LLJJLIIIJLLLLLLLZ");
            Constructor<?> newTuxIconView = tuxIconViewType.getConstructor(Context.class);
            Method setIconRes = tuxIconViewType.getMethod("setIconRes", int.class);
            Method setIconWidth = tuxIconViewType.getMethod("setIconWidth", int.class);
            Method setIconHeight = tuxIconViewType.getMethod("setIconHeight", int.class);
            Method setTintColor = tuxIconViewType.getMethod("setTintColor", int.class);
            Method setTintColorRes = tuxIconViewType.getMethod("setTintColorRes", int.class);

            Class<?> batchTranslatorType = Class.forName("X.0l8b", false, classLoader);
            Class<?> requestType = Class.forName("X.0l59", false, classLoader);
            Method translateBatch = batchTranslatorType.getMethod(
                    "LJFF", List.class, requestType, boolean.class);
            Method resetBatch = batchTranslatorType.getMethod("LIZ", List.class);
            BatchTranslationBridge batchBridge = new BatchTranslationBridge(
                    null, null, null, null, null, translateBatch, resetBatch, null, false, true);

            getBoundComment.setAccessible(true);
            getCommentContext.setAccessible(true);
            closeButton.setAccessible(true);
            OfficialTranslationBridge bridge = new OfficialTranslationBridge(
                    null, null, null, getAwemeId, getCommentId, isTranslated, null, null,
                    null, null, getCommentContext, getAweme, getAid, closeButton,
                    newTuxIconView, setIconRes, setIconWidth, setIconHeight, setTintColor,
                    setTintColorRes, batchBridge, null);

            hook(getBoundComment)
                    .setId("toki-4632-comment-translation-current-comment")
                    .intercept(chain -> {
                        Object result = chain.proceed();
                        captureTikTok4632CommentBinding(chain.getThisObject(), result, bridge);
                        return result;
                    });
            hook(onActionBarCreated)
                    .setId("toki-4632-comment-translation-action-bar")
                    .intercept(chain -> {
                        Object result = chain.proceed();
                        Object root = chain.getArg(0);
                        injectOfficialCommentTranslationButton(
                                chain.getThisObject(), root instanceof View ? (View) root : null, bridge);
                        return result;
                    });
            hook(onCommentPageReused)
                    .setId("toki-4632-comment-translation-page-reused")
                    .intercept(chain -> {
                        Object result = chain.proceed();
                        try {
                            Object aweme = chain.getArg(0);
                            String awemeId = aweme == null ? "" : stringValue(getAid.invoke(aweme));
                            if (awemeId.isEmpty()) {
                                resetOfficialCommentPageState();
                            } else {
                                observeOfficialCommentPage(awemeId);
                            }
                        } catch (ReflectiveOperationException | RuntimeException error) {
                            logOfficialTranslationFailure(
                                    "Unable to observe a reused TikTok 46.3.2 comment page", error);
                        }
                        return result;
                    });
            logInfo("Enabled official TikTok 46.3.2 comment translation button hooks");
        } catch (ClassNotFoundException | NoSuchMethodException | NoSuchFieldException error) {
            logInfo("TikTok 46.3.2 comment translation symbols unavailable: "
                    + error.getClass().getSimpleName() + ": " + error.getMessage());
        } catch (Throwable error) {
            logError("Unable to enable TikTok 46.3.2 comment translation button", error);
        }
    }

    /** Resolves the list-level API used by TikTok's native multi-comment translation flow. */
    private BatchTranslationBridge createOfficialBatchTranslationBridge(
            ClassLoader classLoader,
            Class<?> actionBarType) {
        try {
            Class<?> lifecycleOwnerType = Class.forName(
                    "androidx.lifecycle.LifecycleOwner", false, classLoader);
            Class<?> fragmentType = Class.forName("androidx.fragment.app.Fragment", false, classLoader);
            Class<?> fragmentResolverType = Class.forName("X.0qSg", false, classLoader);
            Class<?> scopeResolverType = Class.forName("X.0qLo", false, classLoader);
            Class<?> listResolverType = Class.forName("X.0nA3", false, classLoader);
            Class<?> listAbilityType = Class.forName(
                    "com.ss.android.ugc.aweme.commentv2.commentlist.ui.ICommentListAssemAbility",
                    false,
                    classLoader);
            Class<?> batchTranslatorType = Class.forName("X.0meW", false, classLoader);
            Class<?> batchRequestType = Class.forName("X.0mXW", false, classLoader);
            Class<?> nativeActionType = Class.forName("X.0mXf", false, classLoader);

            Method getFragment = fragmentResolverType.getDeclaredMethod("LJI", lifecycleOwnerType);
            Method getScope = findCompatibleStaticMethod(scopeResolverType, "LJIIL", actionBarType);
            Method getListAbility = findCompatibleStaticMethod(
                    listResolverType,
                    "LIZLLL",
                    fragmentType,
                    getScope.getReturnType());
            Method getLoadedComments = listAbilityType.getMethod("ZJ2");
            Method translateBatch = batchTranslatorType.getMethod(
                    "LJFF", List.class, batchRequestType, boolean.class);
            Method resetBatch = batchTranslatorType.getMethod("LIZ", List.class);
            Field requestMetadata = nativeActionType.getDeclaredField("LLIZ");

            getFragment.setAccessible(true);
            getScope.setAccessible(true);
            getListAbility.setAccessible(true);
            requestMetadata.setAccessible(true);
            logInfo("Resolved native multi-comment translation bridge");
            return new BatchTranslationBridge(
                    batchRequestType,
                    getFragment,
                    getScope,
                    getListAbility,
                    getLoadedComments,
                    translateBatch,
                    resetBatch,
                    requestMetadata,
                    true,
                    false);
        } catch (ClassNotFoundException | NoSuchMethodException | NoSuchFieldException error) {
            logInfo("Native multi-comment translation bridge unavailable: "
                    + error.getClass().getSimpleName() + ": " + error.getMessage());
            return null;
        } catch (Throwable error) {
            logError("Unable to resolve native multi-comment translation bridge", error);
            return null;
        }
    }

    private static Method findCompatibleStaticMethod(
            Class<?> owner,
            String name,
            Class<?>... argumentTypes) throws NoSuchMethodException {
        for (Method method : owner.getDeclaredMethods()) {
            if (!name.equals(method.getName())
                    || !Modifier.isStatic(method.getModifiers())
                    || method.getParameterCount() != argumentTypes.length) {
                continue;
            }
            Class<?>[] parameterTypes = method.getParameterTypes();
            boolean compatible = true;
            for (int index = 0; index < parameterTypes.length; index++) {
                if (!parameterTypes[index].isAssignableFrom(argumentTypes[index])) {
                    compatible = false;
                    break;
                }
            }
            if (compatible) {
                return method;
            }
        }
        throw new NoSuchMethodException(owner.getName() + '.' + name);
    }

    /** Resolves a renamed instance method without weakening its parameter signature. */
    private static Method findDeclaredMethod(
            Class<?> owner,
            Class<?>[] parameterTypes,
            String... names) throws NoSuchMethodException {
        NoSuchMethodException failure = null;
        for (String name : names) {
            try {
                return owner.getDeclaredMethod(name, parameterTypes);
            } catch (NoSuchMethodException error) {
                failure = error;
            }
        }
        throw failure == null ? new NoSuchMethodException(owner.getName()) : failure;
    }

    /**
     * TikTok renames the comment-cell bind method between releases but retains its unique item
     * parameter. Prefer the verified name and only fall back when the signature is unambiguous.
     */
    private static Method findCommentBindingMethod(Class<?> owner, Class<?> itemType)
            throws NoSuchMethodException {
        Method candidate = null;
        for (Class<?> current = owner; current != null; current = current.getSuperclass()) {
            for (Method method : current.getDeclaredMethods()) {
                if (Modifier.isStatic(method.getModifiers()) || method.getParameterCount() != 1
                        || method.getParameterTypes()[0] != itemType) {
                    continue;
                }
                if ("K6".equals(method.getName())) {
                    return method;
                }
                if (candidate != null) {
                    throw new NoSuchMethodException(
                            "Ambiguous comment binding method on " + owner.getName());
                }
                candidate = method;
            }
        }
        if (candidate == null) {
            throw new NoSuchMethodException(
                    "Comment binding method on " + owner.getName() + " for " + itemType.getName());
        }
        return candidate;
    }

    private int hookTikTok464CommentBindingMethods(
            Class<?> baseCommentCell,
            OfficialTranslationBridge bridge) {
        int bindHooks = 0;
        for (Method method : baseCommentCell.getDeclaredMethods()) {
            String name = method.getName();
            boolean supportedName = "onBindItemView".equals(name)
                    || "Q6".equals(name)
                    || "K6".equals(name);
            if (!supportedName
                    || method.getParameterCount() != 1
                    || Modifier.isStatic(method.getModifiers())
                    || (("Q6".equals(name) || "K6".equals(name))
                    && method.getReturnType() != void.class)) {
                continue;
            }
            method.setAccessible(true);
            final int hookIndex = bindHooks++;
            hook(method)
                    .setId("toki-464-comment-translation-bind-" + hookIndex)
                    .intercept(chain -> {
                        Object result = chain.proceed();
                        captureTikTok464CommentBinding(chain.getThisObject(), bridge);
                        return result;
                    });
        }
        return bindHooks;
    }

    /** Finds the current cell's Comment and native translation action by their stable shape. */
    private static TranslationBindingMembers findCommentTranslationMembers(
            Class<?> baseCommentCell,
            Class<?> commentType) throws NoSuchFieldException {
        // Prefer the verified 46.4.x holder when present, then fall back to structural discovery.
        for (Class<?> current = baseCommentCell; current != null; current = current.getSuperclass()) {
            for (Field managerField : current.getDeclaredFields()) {
                if (!"LLJJIJI".equals(managerField.getName())) {
                    continue;
                }
                TranslationBindingMembers preferred = createTranslationBindingMembers(
                        managerField, commentType);
                if (preferred != null) {
                    return preferred;
                }
            }
        }
        for (Class<?> current = baseCommentCell; current != null; current = current.getSuperclass()) {
            for (Field managerField : current.getDeclaredFields()) {
                if (Modifier.isStatic(managerField.getModifiers()) || managerField.getType().isPrimitive()) {
                    continue;
                }
                TranslationBindingMembers discovered = createTranslationBindingMembers(
                        managerField, commentType);
                if (discovered != null) {
                    return discovered;
                }
            }
        }
        throw new NoSuchFieldException("Comment translation manager/action on "
                + baseCommentCell.getName());
    }

    private static TranslationBindingMembers createTranslationBindingMembers(
            Field managerField,
            Class<?> commentType) {
        if (Modifier.isStatic(managerField.getModifiers()) || managerField.getType().isPrimitive()) {
            return null;
        }
        Field commentField = findFieldByType(managerField.getType(), commentType);
        if (commentField == null) {
            return null;
        }
        for (Class<?> managerType = managerField.getType(); managerType != null;
                managerType = managerType.getSuperclass()) {
            for (Field actionField : managerType.getDeclaredFields()) {
                if (Modifier.isStatic(actionField.getModifiers())
                        || actionField.getType().isPrimitive()) {
                    continue;
                }
                Method translate = findNoArgVoidMethod(actionField.getType(), "translate");
                Method reset = findNoArgVoidMethod(actionField.getType(), "resetTranslate", "LIZJ");
                if (translate == null || reset == null) {
                    continue;
                }
                managerField.setAccessible(true);
                commentField.setAccessible(true);
                actionField.setAccessible(true);
                return new TranslationBindingMembers(
                        managerField, commentField, actionField, translate, reset);
            }
        }
        return null;
    }

    private static Field findFieldByType(Class<?> owner, Class<?> expectedType) {
        for (Class<?> current = owner; current != null; current = current.getSuperclass()) {
            for (Field field : current.getDeclaredFields()) {
                if (!Modifier.isStatic(field.getModifiers())
                        && expectedType.isAssignableFrom(field.getType())) {
                    field.setAccessible(true);
                    return field;
                }
            }
        }
        return null;
    }

    private static Method findNoArgVoidMethod(Class<?> type, String... names) {
        if (type == null) {
            return null;
        }
        for (String name : names) {
            for (Method method : type.getMethods()) {
                if (name.equals(method.getName())
                        && method.getParameterCount() == 0
                        && method.getReturnType() == void.class) {
                    method.setAccessible(true);
                    return method;
                }
            }
            for (Class<?> current = type; current != null; current = current.getSuperclass()) {
                for (Method method : current.getDeclaredMethods()) {
                    if (name.equals(method.getName())
                            && method.getParameterCount() == 0
                            && method.getReturnType() == void.class) {
                        method.setAccessible(true);
                        return method;
                    }
                }
            }
        }
        return null;
    }

    private static Method findNoArgMethodReturningType(Class<?> owner, Class<?> returnType) {
        Method candidate = null;
        for (Class<?> current = owner; current != null; current = current.getSuperclass()) {
            for (Method method : current.getDeclaredMethods()) {
                if (Modifier.isStatic(method.getModifiers())
                        || method.getParameterCount() != 0
                        || method.getReturnType() != returnType) {
                    continue;
                }
                if (candidate != null) {
                    return null;
                }
                method.setAccessible(true);
                candidate = method;
            }
        }
        return candidate;
    }

    private static Method findUniqueMethodBySignature(
            Class<?> owner,
            Class<?> returnType,
            Class<?>... parameterTypes) throws NoSuchMethodException {
        Method candidate = null;
        for (Method method : owner.getMethods()) {
            if (method.getReturnType() != returnType
                    || method.getParameterCount() != parameterTypes.length) {
                continue;
            }
            Class<?>[] actualParameters = method.getParameterTypes();
            boolean matches = true;
            for (int index = 0; index < parameterTypes.length; index++) {
                if (actualParameters[index] != parameterTypes[index]) {
                    matches = false;
                    break;
                }
            }
            if (!matches) {
                continue;
            }
            if (candidate != null) {
                throw new NoSuchMethodException("Ambiguous translation method on "
                        + owner.getName());
            }
            method.setAccessible(true);
            candidate = method;
        }
        if (candidate == null) {
            throw new NoSuchMethodException("Translation method on " + owner.getName());
        }
        return candidate;
    }

    private static Method findCommentTranslatabilityMethod(
            Class<?> owner,
            Class<?> commentType) throws NoSuchMethodException {
        return findPreferredMethodBySignature(
                owner,
                boolean.class,
                new Class<?>[]{commentType},
                "LJIILJJIL",
                "isTranslatable",
                "canTranslate",
                "isCommentTranslatable");
    }

    private static Method findPreferredMethodBySignature(
            Class<?> owner,
            Class<?> returnType,
            Class<?>[] parameterTypes,
            String... preferredNames) throws NoSuchMethodException {
        for (String preferredName : preferredNames) {
            for (Method method : owner.getMethods()) {
                if (preferredName.equals(method.getName())
                        && method.getReturnType() == returnType
                        && hasExactParameters(method, parameterTypes)) {
                    method.setAccessible(true);
                    return method;
                }
            }
        }
        return findUniqueMethodBySignature(owner, returnType, parameterTypes);
    }

    private static boolean hasExactParameters(Method method, Class<?>[] parameterTypes) {
        Class<?>[] actualParameters = method.getParameterTypes();
        if (actualParameters.length != parameterTypes.length) {
            return false;
        }
        for (int index = 0; index < parameterTypes.length; index++) {
            if (actualParameters[index] != parameterTypes[index]) {
                return false;
            }
        }
        return true;
    }

    private void captureOfficialCommentBinding(Object cell, OfficialTranslationBridge bridge) {
        try {
            Object manager = bridge.translationManager.get(cell);
            if (manager == null) {
                return;
            }
            Object comment = bridge.boundComment.get(manager);
            Object action = bridge.translationAction.get(manager);
            if (comment == null || action == null) {
                return;
            }
            String awemeId = stringValue(bridge.getAwemeId.invoke(comment));
            String commentId = stringValue(bridge.getCommentId.invoke(comment));
            if (awemeId.isEmpty() || commentId.isEmpty()) {
                return;
            }

            observeOfficialCommentPage(awemeId);
            String key = commentKey(awemeId, commentId);
            boolean alreadyTranslated = Boolean.TRUE.equals(bridge.isTranslated.invoke(comment));
            boolean translateNow;
            synchronized (officialTranslationLock) {
                officialBoundComments.put(key, new BoundComment(key, awemeId, comment, action));
                pruneOfficialCommentBindingsLocked();
                translateNow = awemeId.equals(officialTranslatedAwemeId)
                        && !alreadyTranslated
                        && markOfficialTranslatedActionLocked(action, key);
            }
            if (translateNow && !translateOfficialComment(comment, action, bridge)) {
                synchronized (officialTranslationLock) {
                    if (key.equals(officialTranslatedActions.get(action))) {
                        officialTranslatedActions.remove(action);
                    }
                }
            }
        } catch (ReflectiveOperationException | RuntimeException error) {
            logOfficialTranslationFailure("Unable to capture a bound comment", error);
        }
    }

    private void captureTikTok4632CommentBinding(
            Object cell,
            Object comment,
            OfficialTranslationBridge bridge) {
        try {
            if (comment == null) {
                return;
            }
            String awemeId = stringValue(bridge.getAwemeId.invoke(comment));
            String commentId = stringValue(bridge.getCommentId.invoke(comment));
            if (awemeId.isEmpty() || commentId.isEmpty()) {
                return;
            }
            // Comment cells can bind before the reused action bar publishes its new context.
            observeOfficialCommentPage(awemeId);
            String key = commentKey(awemeId, commentId);
            boolean translateNow;
            BoundComment binding = new BoundComment(key, awemeId, comment, null);
            synchronized (officialTranslationLock) {
                officialBoundComments.put(key, binding);
                pruneOfficialCommentBindingsLocked();
                translateNow = isOfficialTranslationActive(awemeId)
                        && !Boolean.TRUE.equals(bridge.isTranslated.invoke(comment))
                        && !officialTranslationRequests.contains(key);
            }
            if (translateNow) {
                // A rebind must never redispatch the entire visible list. New cells are queued
                // individually; the native batch call claims the key before publishing loading UI.
                translateOfficialLoadedComments(
                        null, awemeId, Collections.singletonList(binding), bridge);
            }
        } catch (ReflectiveOperationException | RuntimeException error) {
            logOfficialTranslationFailure("Unable to capture a TikTok 46.3.2 bound comment", error);
        }
    }

    private void captureTikTok464CommentBinding(
            Object cell,
            OfficialTranslationBridge bridge) {
        try {
            Object manager = bridge.translationManager.get(cell);
            if (manager == null) {
                return;
            }
            Object comment = bridge.boundComment.get(manager);
            Object action = bridge.translationAction.get(manager);
            if (comment == null || action == null) {
                return;
            }
            String awemeId = stringValue(bridge.getAwemeId.invoke(comment));
            String commentId = stringValue(bridge.getCommentId.invoke(comment));
            if (awemeId.isEmpty() || commentId.isEmpty()) {
                return;
            }
            observeOfficialCommentPage(awemeId);
            String key = commentKey(awemeId, commentId);
            boolean translateNow;
            synchronized (officialTranslationLock) {
                officialBoundComments.put(
                        key, new BoundComment(key, awemeId, comment, action));
                pruneOfficialCommentBindingsLocked();
                translateNow = isOfficialTranslationActive(awemeId)
                        && !Boolean.TRUE.equals(bridge.isTranslated.invoke(comment))
                        && officialTranslationRequests.add(key);
            }
            if (translateNow && !translateOfficialComment(comment, action, bridge)) {
                synchronized (officialTranslationLock) {
                    officialTranslationRequests.remove(key);
                }
            }
        } catch (ReflectiveOperationException | RuntimeException error) {
            logOfficialTranslationFailure("Unable to capture a TikTok 46.4.3 bound comment", error);
        }
    }

    private void observeOfficialCommentPage(String awemeId) {
        if (awemeId == null || awemeId.isEmpty()) {
            return;
        }
        synchronized (officialTranslationLock) {
            if (awemeId.equals(officialCommentPageAwemeId)) {
                return;
            }
            officialCommentPageAwemeId = awemeId;
            officialTranslatedAwemeId = officialTranslationEnabled ? awemeId : null;
            officialTranslationRequests.clear();
            officialTranslatedActions.clear();
            officialBoundComments.entrySet().removeIf(
                    entry -> !awemeId.equals(entry.getValue().awemeId));
        }
        logInfo("Observed comment page for aweme " + awemeId);
    }

    private void resetOfficialCommentPageState() {
        synchronized (officialTranslationLock) {
            officialCommentPageAwemeId = null;
            officialTranslatedAwemeId = null;
            officialTranslationRequests.clear();
            officialTranslatedActions.clear();
            officialBoundComments.clear();
        }
    }

    private boolean markOfficialTranslatedActionLocked(Object action, String key) {
        if (key.equals(officialTranslatedActions.get(action))) {
            return false;
        }
        officialTranslatedActions.put(action, key);
        return true;
    }

    private boolean isOfficialTranslationActive(String awemeId) {
        return officialTranslationEnabled && awemeId != null && !awemeId.isEmpty()
                && awemeId.equals(officialTranslatedAwemeId);
    }

    private void setOfficialTranslationEnabled(boolean enabled) {
        officialTranslationEnabled = enabled;
        Context context = translationStateContext;
        if (context == null || !ModuleConfig.saveCommentTranslationActive(context, enabled)) {
            logInfo("Unable to persist comment translation state: " + enabled);
        }
    }

    private String resolveOfficialCommentPageAwemeId(
            Object actionBar,
            OfficialTranslationBridge bridge) {
        String awemeId = readOfficialCommentPageAwemeId(actionBar, bridge);
        if (!awemeId.isEmpty()) {
            return awemeId;
        }
        synchronized (officialTranslationLock) {
            return officialCommentPageAwemeId == null ? "" : officialCommentPageAwemeId;
        }
    }

    private void injectOfficialCommentTranslationButton(
            Object actionBar,
            View actionBarRoot,
            OfficialTranslationBridge bridge) {
        try {
            View closeButton = bridge.closeButton == null
                    ? null
                    : (View) bridge.closeButton.get(actionBar);
            ViewGroup host = findOfficialTranslationButtonHost(closeButton, actionBarRoot);
            if (host == null) {
                return;
            }
            // TikTok may reuse the action-bar view for the next video's comment sheet.
            // Recreate our control so no icon state or listener closure leaks across videos.
            View previousButton = findTaggedChild(host, OFFICIAL_TRANSLATION_BUTTON_TAG);
            if (previousButton != null) {
                host.removeView(previousButton);
            }

            Context context = host.getContext();
            int normalIcon = context.getResources().getIdentifier(
                    "icon_languages", "raw", context.getPackageName());
            int translatedIcon = context.getResources().getIdentifier(
                    "icon_languages_tick", "raw", context.getPackageName());
            if (normalIcon == 0 || translatedIcon == 0) {
                // These are stable in the verified official 46.3.3 resource table.
                normalIcon = 0x7f010810;
                translatedIcon = 0x7f010812;
            }

            Object iconObject = bridge.newTuxIconView.newInstance(context);
            if (!(iconObject instanceof View)) {
                return;
            }
            View button = (View) iconObject;
            int iconSize = dp(context, 20);
            bridge.setIconWidth.invoke(iconObject, iconSize);
            bridge.setIconHeight.invoke(iconObject, iconSize);
            applyOfficialTranslationButtonTint(iconObject, closeButton, context, bridge);
            button.setTag(OFFICIAL_TRANSLATION_BUTTON_TAG);
            button.setClickable(true);
            button.setFocusable(true);
            button.setPadding(dp(context, 12), dp(context, 12), dp(context, 12), dp(context, 12));
            button.setBackground(null);
            button.setForeground(null);
            button.setStateListAnimator(null);
            addOfficialTranslationButton(host, button, closeButton, context);

            String pageAwemeId = readOfficialCommentPageAwemeId(actionBar, bridge);
            if (pageAwemeId.isEmpty()) {
                resetOfficialCommentPageState();
            } else {
                observeOfficialCommentPage(pageAwemeId);
            }
            boolean translated = officialTranslationEnabled;
            updateOfficialTranslationButton(
                    iconObject, button, translated, normalIcon, translatedIcon, bridge);
            final int finalNormalIcon = normalIcon;
            final int finalTranslatedIcon = translatedIcon;
            button.setOnClickListener(view -> {
                String activeAwemeId = resolveOfficialCommentPageAwemeId(actionBar, bridge);
                boolean nextState = !officialTranslationEnabled;
                setOfficialTranslationEnabled(nextState);
                if (!activeAwemeId.isEmpty()) {
                    observeOfficialCommentPage(activeAwemeId);
                    int affected = setOfficialCommentTranslationState(
                            actionBar, activeAwemeId, nextState, bridge);
                    if (nextState && affected == 0) {
                        logInfo("Official comment translation armed for comments loaded later");
                    }
                }
                try {
                    updateOfficialTranslationButton(
                            iconObject,
                            button,
                            officialTranslationEnabled,
                            finalNormalIcon,
                            finalTranslatedIcon,
                            bridge);
                } catch (ReflectiveOperationException | RuntimeException error) {
                    logOfficialTranslationFailure("Unable to update the translation button", error);
                }
            });
            if (officialTranslationEnabled && !pageAwemeId.isEmpty()) {
                final String initialAwemeId = pageAwemeId;
                button.post(() -> {
                    String activeAwemeId = resolveOfficialCommentPageAwemeId(actionBar, bridge);
                    if (officialTranslationEnabled && initialAwemeId.equals(activeAwemeId)) {
                        setOfficialCommentTranslationState(
                                actionBar, initialAwemeId, true, bridge);
                    }
                });
            }
            logInfo("Injected official comment translation button for aweme " + pageAwemeId);
        } catch (ReflectiveOperationException | RuntimeException error) {
            logOfficialTranslationFailure("Unable to inject the official translation button", error);
        }
    }

    private int setOfficialCommentTranslationState(
            Object actionBar,
            String requestedAwemeId,
            boolean translated,
            OfficialTranslationBridge bridge) {
        String awemeId = requestedAwemeId;
        List<BoundComment> bindings = new ArrayList<>();
        synchronized (officialTranslationLock) {
            pruneOfficialCommentBindingsLocked();
            if (awemeId.isEmpty()) {
                awemeId = officialCommentPageAwemeId == null ? "" : officialCommentPageAwemeId;
            }
            for (BoundComment binding : officialBoundComments.values()) {
                if (awemeId.equals(binding.awemeId)) {
                    bindings.add(binding);
                }
            }
        }
        if (awemeId.isEmpty()) {
            return 0;
        }

        final String targetAwemeId = awemeId;
        synchronized (officialTranslationLock) {
            if (translated) {
                officialTranslatedAwemeId = targetAwemeId;
            } else {
                if (targetAwemeId.equals(officialTranslatedAwemeId)) {
                    officialTranslatedAwemeId = null;
                }
                String keyPrefix = targetAwemeId + '\n';
                officialTranslationRequests.removeIf(key -> key.startsWith(keyPrefix));
                officialTranslatedActions.entrySet().removeIf(
                        entry -> entry.getValue().startsWith(keyPrefix));
            }
        }

        int affected = translated
                ? translateOfficialLoadedComments(actionBar, targetAwemeId, bindings, bridge)
                : resetOfficialLoadedComments(actionBar, targetAwemeId, bindings, bridge);
        for (BoundComment binding : bindings) {
            Object comment = binding.comment.get();
            Object action = binding.action.get();
            if (comment == null || action == null) {
                continue;
            }
            if (translated) {
                boolean shouldInvoke;
                synchronized (officialTranslationLock) {
                    shouldInvoke = officialTranslationRequests.add(binding.key);
                    if (shouldInvoke && !bridge.usesDirectTranslationService()) {
                        markOfficialTranslatedActionLocked(action, binding.key);
                    }
                }
                if (!shouldInvoke) {
                    affected++;
                    continue;
                }
                if (translateOfficialComment(comment, action, bridge)) {
                    affected++;
                } else {
                    synchronized (officialTranslationLock) {
                        officialTranslationRequests.remove(binding.key);
                        if (!bridge.usesDirectTranslationService()
                                && binding.key.equals(officialTranslatedActions.get(action))) {
                            officialTranslatedActions.remove(action);
                        }
                    }
                }
            } else if (resetOfficialComment(comment, action, bridge)) {
                affected++;
            }
        }

        logInfo((translated ? "Requested translation for " : "Restored ")
                + affected + " loaded comments for aweme " + targetAwemeId);
        return affected;
    }

    private int translateOfficialLoadedComments(
            Object actionBar,
            String awemeId,
            List<BoundComment> bindings,
            OfficialTranslationBridge bridge) {
        BatchTranslationBridge batch = bridge.batch;
        if (batch == null) {
            return 0;
        }
        List<Object> comments = new ArrayList<>();
        try {
            comments = batch.useBoundComments
                    ? getOfficialBoundComments(bindings, awemeId)
                    : getOfficialLoadedComments(actionBar, awemeId, bridge);
            if (batch.useBoundComments) {
                comments = claimTikTok4632TranslationRequests(comments, awemeId, bridge);
            }
            if (comments.isEmpty()) {
                return 0;
            }
            Object requestMetadata = batch.requiresRequestMetadata
                    ? findOfficialBatchRequestMetadata(bindings, batch) : null;
            if (batch.requiresRequestMetadata && requestMetadata == null) {
                logInfo("Native multi-comment translation has no request metadata yet");
                return 0;
            }
            batch.translateBatch.invoke(null, comments, requestMetadata, false);
            if (!batch.useBoundComments) {
                rememberOfficialTranslationRequests(comments, awemeId, bridge);
            }
            return comments.size();
        } catch (ReflectiveOperationException | RuntimeException error) {
            if (batch.useBoundComments) {
                releaseTikTok4632TranslationRequests(comments, awemeId, bridge);
            }
            logOfficialTranslationFailure("Unable to request native multi-comment translation", error);
            return 0;
        }
    }

    /** Claims requests before TikTok publishes its per-comment loading state and rebinds the cell. */
    private List<Object> claimTikTok4632TranslationRequests(
            List<Object> comments,
            String awemeId,
            OfficialTranslationBridge bridge) throws ReflectiveOperationException {
        List<Object> claimed = new ArrayList<>();
        synchronized (officialTranslationLock) {
            for (Object comment : comments) {
                String commentId = stringValue(bridge.getCommentId.invoke(comment));
                if (!commentId.isEmpty() && officialTranslationRequests.add(commentKey(awemeId, commentId))) {
                    claimed.add(comment);
                }
            }
        }
        return claimed;
    }

    private void releaseTikTok4632TranslationRequests(
            List<Object> comments,
            String awemeId,
            OfficialTranslationBridge bridge) {
        synchronized (officialTranslationLock) {
            for (Object comment : comments) {
                try {
                    String commentId = stringValue(bridge.getCommentId.invoke(comment));
                    if (!commentId.isEmpty()) {
                        officialTranslationRequests.remove(commentKey(awemeId, commentId));
                    }
                } catch (ReflectiveOperationException | RuntimeException ignored) {
                    // The request could not be identified, so retaining its guard is safer than looping.
                }
            }
        }
    }

    private int resetOfficialLoadedComments(
            Object actionBar,
            String awemeId,
            List<BoundComment> bindings,
            OfficialTranslationBridge bridge) {
        BatchTranslationBridge batch = bridge.batch;
        if (batch == null) {
            return 0;
        }
        try {
            List<Object> comments = batch.useBoundComments
                    ? getOfficialBoundComments(bindings, awemeId)
                    : getOfficialLoadedComments(actionBar, awemeId, bridge);
            if (comments.isEmpty()) {
                return 0;
            }
            batch.resetBatch.invoke(null, comments);
            return comments.size();
        } catch (ReflectiveOperationException | RuntimeException error) {
            logOfficialTranslationFailure("Unable to restore native multi-comment translation", error);
            return 0;
        }
    }

    private List<Object> getOfficialLoadedComments(
            Object actionBar,
            String awemeId,
            OfficialTranslationBridge bridge) throws ReflectiveOperationException {
        BatchTranslationBridge batch = bridge.batch;
        if (batch == null || actionBar == null) {
            return new ArrayList<>();
        }
        Object fragment = batch.getFragment.invoke(null, actionBar);
        Object scope = batch.getScope.invoke(null, actionBar);
        if (fragment == null || scope == null) {
            return new ArrayList<>();
        }
        Object ability = batch.getListAbility.invoke(null, fragment, scope);
        Object result = ability == null ? null : batch.getLoadedComments.invoke(ability);
        if (!(result instanceof List<?>)) {
            return new ArrayList<>();
        }

        List<Object> matchingComments = new ArrayList<>();
        for (Object comment : (List<?>) result) {
            if (comment == null) {
                continue;
            }
            if (awemeId.equals(stringValue(bridge.getAwemeId.invoke(comment)))) {
                matchingComments.add(comment);
            }
        }
        // Never translate another page just because a stale/unknown aweme id had no match.
        return matchingComments;
    }

    private static List<Object> getOfficialBoundComments(
            List<BoundComment> bindings,
            String awemeId) {
        List<Object> comments = new ArrayList<>();
        for (BoundComment binding : bindings) {
            Object comment = binding.comment.get();
            if (comment != null && awemeId.equals(binding.awemeId)) {
                comments.add(comment);
            }
        }
        return comments;
    }

    private Object findOfficialBatchRequestMetadata(
            List<BoundComment> bindings,
            BatchTranslationBridge batch) {
        for (BoundComment binding : bindings) {
            Object action = binding.action.get();
            if (action == null || !batch.requestMetadata.getDeclaringClass().isInstance(action)) {
                continue;
            }
            try {
                Object metadata = batch.requestMetadata.get(action);
                if (batch.requestType.isInstance(metadata)) {
                    return metadata;
                }
            } catch (IllegalAccessException | RuntimeException error) {
                logOfficialTranslationFailure("Unable to read native translation request metadata", error);
            }
        }
        return null;
    }

    private void rememberOfficialTranslationRequests(
            List<Object> comments,
            String awemeId,
            OfficialTranslationBridge bridge) {
        synchronized (officialTranslationLock) {
            for (Object comment : comments) {
                try {
                    String commentId = stringValue(bridge.getCommentId.invoke(comment));
                    if (!commentId.isEmpty()) {
                        officialTranslationRequests.add(commentKey(awemeId, commentId));
                    }
                } catch (ReflectiveOperationException | RuntimeException error) {
                    logOfficialTranslationFailure("Unable to remember translated comments", error);
                    return;
                }
            }
        }
    }

    private boolean translateOfficialComment(
            Object comment,
            Object action,
            OfficialTranslationBridge bridge) {
        try {
            if (bridge.usesDirectTranslationService()) {
                Object service = resolveDirectTranslationService(bridge);
                if (service == null
                        || !Boolean.TRUE.equals(bridge.directIsTranslatable.invoke(service, comment))) {
                    return false;
                }
                bridge.directSetTranslation.invoke(service, comment, Boolean.TRUE);
                return invokeOfficialCommentAction(bridge.translate, action);
            }
            Object service = bridge.translationService.get(null);
            if (service == null
                    || !Boolean.TRUE.equals(bridge.isTranslatable.invoke(service, comment))) {
                return false;
            }
            return invokeOfficialCommentAction(bridge.translate, action);
        } catch (ReflectiveOperationException | RuntimeException error) {
            logOfficialTranslationFailure("Unable to check comment translation eligibility", error);
            return false;
        }
    }

    private boolean resetOfficialComment(
            Object comment,
            Object action,
            OfficialTranslationBridge bridge) {
        try {
            if (bridge.usesDirectTranslationService()) {
                Object service = resolveDirectTranslationService(bridge);
                if (service == null) {
                    return false;
                }
                bridge.directSetTranslation.invoke(service, comment, Boolean.FALSE);
                return invokeOfficialCommentAction(bridge.resetTranslate, action);
            }
            return invokeOfficialCommentAction(bridge.resetTranslate, action);
        } catch (ReflectiveOperationException | RuntimeException error) {
            logOfficialTranslationFailure("Unable to restore native comment translation", error);
            return false;
        }
    }

    private static Object resolveDirectTranslationService(OfficialTranslationBridge bridge)
            throws ReflectiveOperationException {
        if (bridge.directTranslationService != null) {
            return bridge.directTranslationService.get(null);
        }
        if (bridge.directServiceManagerGetter == null
                || bridge.directServiceGetter == null
                || bridge.directServiceInterface == null) {
            return null;
        }
        Object serviceManager = bridge.directServiceManagerGetter.invoke(null);
        return serviceManager == null
                ? null
                : bridge.directServiceGetter.invoke(serviceManager, bridge.directServiceInterface);
    }

    private boolean invokeOfficialCommentAction(Method actionMethod, Object action) {
        try {
            actionMethod.invoke(action);
            return true;
        } catch (ReflectiveOperationException | RuntimeException error) {
            logOfficialTranslationFailure("Unable to invoke the native comment translation action", error);
            return false;
        }
    }

    private String readOfficialCommentPageAwemeId(
            Object actionBar,
            OfficialTranslationBridge bridge) {
        try {
            if (bridge.getCommentContext == null) {
                return "";
            }
            Object contextSource = bridge.getCommentContext.invoke(actionBar);
            if (contextSource == null) {
                return "";
            }
            Object aweme = bridge.getAweme.invoke(null, contextSource);
            return aweme == null ? "" : stringValue(bridge.getAid.invoke(aweme));
        } catch (ReflectiveOperationException | RuntimeException error) {
            logOfficialTranslationFailure("Unable to identify the active comment page", error);
            return "";
        }
    }

    private static ViewGroup findOfficialTranslationButtonHost(View closeButton, View root) {
        if (closeButton != null) {
            ViewParent parent = closeButton.getParent();
            if (parent instanceof ViewGroup) {
                return (ViewGroup) parent;
            }
        }
        return root instanceof ViewGroup ? (ViewGroup) root : null;
    }

    private static View findTaggedChild(ViewGroup parent, Object tag) {
        for (int index = 0; index < parent.getChildCount(); index++) {
            View child = parent.getChildAt(index);
            if (tag.equals(child.getTag())) {
                return child;
            }
        }
        return null;
    }

    private static void addOfficialTranslationButton(
            ViewGroup parent,
            View button,
            View closeButton,
            Context context) {
        int size = dp(context, 44);
        int margin = dp(context, 4);
        if (parent instanceof RelativeLayout) {
            RelativeLayout.LayoutParams params = new RelativeLayout.LayoutParams(size, size);
            params.addRule(RelativeLayout.CENTER_VERTICAL);
            int closeButtonId = ensureViewId(closeButton);
            if (closeButtonId != View.NO_ID && closeButton.getParent() == parent) {
                params.addRule(RelativeLayout.START_OF, closeButtonId);
                params.setMarginEnd(margin);
            } else {
                params.addRule(RelativeLayout.ALIGN_PARENT_END);
                params.setMarginEnd(getCloseButtonEndOffset(closeButton, context) + margin);
            }
            parent.addView(button, params);
        } else if (parent instanceof FrameLayout) {
            FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(size, size);
            params.gravity = Gravity.END | Gravity.CENTER_VERTICAL;
            params.setMarginEnd(getCloseButtonEndOffset(closeButton, context) + margin);
            parent.addView(button, params);
        } else if (parent instanceof LinearLayout) {
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(size, size);
            params.gravity = Gravity.CENTER_VERTICAL;
            params.setMarginEnd(margin);
            int closeIndex = closeButton != null && closeButton.getParent() == parent
                    ? parent.indexOfChild(closeButton)
                    : -1;
            if (closeIndex >= 0) {
                parent.addView(button, closeIndex, params);
            } else {
                parent.addView(button, params);
            }
        } else {
            int closeIndex = closeButton != null && closeButton.getParent() == parent
                    ? parent.indexOfChild(closeButton)
                    : -1;
            ViewGroup.LayoutParams params = new ViewGroup.LayoutParams(size, size);
            if (closeIndex >= 0) {
                parent.addView(button, closeIndex, params);
            } else {
                parent.addView(button, params);
            }
        }
    }

    private static int ensureViewId(View view) {
        if (view == null) {
            return View.NO_ID;
        }
        int id = view.getId();
        if (id == View.NO_ID) {
            id = View.generateViewId();
            view.setId(id);
        }
        return id;
    }

    private static int getCloseButtonEndOffset(View closeButton, Context context) {
        if (closeButton == null) {
            return 0;
        }
        int width = closeButton.getWidth();
        ViewGroup.LayoutParams layoutParams = closeButton.getLayoutParams();
        if (width <= 0 && layoutParams != null && layoutParams.width > 0) {
            width = layoutParams.width;
        }
        if (width <= 0) {
            width = dp(context, 44);
        }
        int endMargin = 0;
        if (layoutParams instanceof ViewGroup.MarginLayoutParams) {
            endMargin = ((ViewGroup.MarginLayoutParams) layoutParams).getMarginEnd();
        }
        return width + Math.max(0, endMargin);
    }

    private static void updateOfficialTranslationButton(
            Object iconObject,
            View button,
            boolean translated,
            int normalIcon,
            int translatedIcon,
            OfficialTranslationBridge bridge)
            throws ReflectiveOperationException {
        bridge.setIconRes.invoke(iconObject, translated ? translatedIcon : normalIcon);
        button.setContentDescription(translated ? "恢复评论原文" : "翻译全部评论");
        button.setSelected(translated);
    }

    private static void applyOfficialTranslationButtonTint(
            Object iconObject,
            View closeButton,
            Context context,
            OfficialTranslationBridge bridge)
            throws ReflectiveOperationException {
        Integer closeButtonTint = readTuxIconTint(closeButton);
        if (closeButtonTint != null) {
            bridge.setTintColor.invoke(iconObject, closeButtonTint);
            return;
        }

        int nativeThemeTint = context.getResources().getIdentifier(
                "a1h", "attr", context.getPackageName());
        if (nativeThemeTint != 0) {
            bridge.setTintColorRes.invoke(iconObject, nativeThemeTint);
            return;
        }

        int uiMode = context.getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK;
        bridge.setTintColor.invoke(iconObject,
                uiMode == Configuration.UI_MODE_NIGHT_YES ? 0xfff2f2f2 : 0xff161823);
    }

    private static Integer readTuxIconTint(View view) {
        if (!(view instanceof ImageView)) {
            return null;
        }
        Drawable drawable = ((ImageView) view).getDrawable();
        if (drawable == null) {
            return null;
        }
        try {
            Field tint = drawable.getClass().getDeclaredField("LJIILL");
            tint.setAccessible(true);
            Object value = tint.get(drawable);
            return value instanceof Integer ? (Integer) value : null;
        } catch (ReflectiveOperationException | RuntimeException ignored) {
            return null;
        }
    }

    private void pruneOfficialCommentBindingsLocked() {
        Iterator<Map.Entry<String, BoundComment>> iterator = officialBoundComments.entrySet().iterator();
        while (iterator.hasNext()) {
            BoundComment binding = iterator.next().getValue();
            // 46.3.2 uses the native batch API, so its bindings intentionally have no action.
            if (binding.comment.get() == null) {
                officialTranslationRequests.remove(binding.key);
                iterator.remove();
            }
        }
        iterator = officialBoundComments.entrySet().iterator();
        while (officialBoundComments.size() > MAX_TRACKED_COMMENTS && iterator.hasNext()) {
            BoundComment binding = iterator.next().getValue();
            officialTranslationRequests.remove(binding.key);
            iterator.remove();
        }
    }

    private void logOfficialTranslationFailure(String message, Throwable error) {
        if (officialTranslationFailureLogged.compareAndSet(false, true)) {
            logError(message, error);
        }
    }

    private static String commentKey(String awemeId, String commentId) {
        return awemeId + '\n' + commentId;
    }

    private static String stringValue(Object value) {
        return value instanceof String ? (String) value : "";
    }

    private static int dp(Context context, int value) {
        return Math.round(value * context.getResources().getDisplayMetrics().density);
    }

    private static final class BoundComment {
        final String key;
        final String awemeId;
        final WeakReference<Object> comment;
        final WeakReference<Object> action;

        BoundComment(String key, String awemeId, Object comment, Object action) {
            this.key = key;
            this.awemeId = awemeId;
            this.comment = new WeakReference<>(comment);
            this.action = new WeakReference<>(action);
        }
    }

    private static final class TranslationBindingMembers {
        final Field manager;
        final Field comment;
        final Field action;
        final Method translate;
        final Method reset;

        TranslationBindingMembers(
                Field manager,
                Field comment,
                Field action,
                Method translate,
                Method reset) {
            this.manager = manager;
            this.comment = comment;
            this.action = action;
            this.translate = translate;
            this.reset = reset;
        }
    }

    private static final class DirectTranslationBridge {
        final Field service;
        final Method setTranslation;
        final Method isTranslatable;
        final Method serviceManagerGetter;
        final Method serviceGetter;
        final Class<?> serviceInterface;

        DirectTranslationBridge(
                Field service,
                Method setTranslation,
                Method isTranslatable,
                Method serviceManagerGetter,
                Method serviceGetter,
                Class<?> serviceInterface) {
            this.service = service;
            this.setTranslation = setTranslation;
            this.isTranslatable = isTranslatable;
            this.serviceManagerGetter = serviceManagerGetter;
            this.serviceGetter = serviceGetter;
            this.serviceInterface = serviceInterface;
        }
    }

    private static final class OfficialTranslationBridge {
        final Field translationManager;
        final Field boundComment;
        final Field translationAction;
        final Method getAwemeId;
        final Method getCommentId;
        final Method isTranslated;
        final Method translate;
        final Method resetTranslate;
        final Field translationService;
        final Method isTranslatable;
        final Method getCommentContext;
        final Method getAweme;
        final Method getAid;
        final Field closeButton;
        final Constructor<?> newTuxIconView;
        final Method setIconRes;
        final Method setIconWidth;
        final Method setIconHeight;
        final Method setTintColor;
        final Method setTintColorRes;
        final BatchTranslationBridge batch;
        final Field directTranslationService;
        final Method directSetTranslation;
        final Method directIsTranslatable;
        final Method directServiceManagerGetter;
        final Method directServiceGetter;
        final Class<?> directServiceInterface;

        OfficialTranslationBridge(
                Field translationManager,
                Field boundComment,
                Field translationAction,
                Method getAwemeId,
                Method getCommentId,
                Method isTranslated,
                Method translate,
                Method resetTranslate,
                Field translationService,
                Method isTranslatable,
                Method getCommentContext,
                Method getAweme,
                Method getAid,
                Field closeButton,
                Constructor<?> newTuxIconView,
                Method setIconRes,
                Method setIconWidth,
                Method setIconHeight,
                Method setTintColor,
                Method setTintColorRes,
                BatchTranslationBridge batch,
                DirectTranslationBridge direct) {
            this.translationManager = translationManager;
            this.boundComment = boundComment;
            this.translationAction = translationAction;
            this.getAwemeId = getAwemeId;
            this.getCommentId = getCommentId;
            this.isTranslated = isTranslated;
            this.translate = translate;
            this.resetTranslate = resetTranslate;
            this.translationService = translationService;
            this.isTranslatable = isTranslatable;
            this.getCommentContext = getCommentContext;
            this.getAweme = getAweme;
            this.getAid = getAid;
            this.closeButton = closeButton;
            this.newTuxIconView = newTuxIconView;
            this.setIconRes = setIconRes;
            this.setIconWidth = setIconWidth;
            this.setIconHeight = setIconHeight;
            this.setTintColor = setTintColor;
            this.setTintColorRes = setTintColorRes;
            this.batch = batch;
            this.directTranslationService = direct == null ? null : direct.service;
            this.directSetTranslation = direct == null ? null : direct.setTranslation;
            this.directIsTranslatable = direct == null ? null : direct.isTranslatable;
            this.directServiceManagerGetter = direct == null ? null : direct.serviceManagerGetter;
            this.directServiceGetter = direct == null ? null : direct.serviceGetter;
            this.directServiceInterface = direct == null ? null : direct.serviceInterface;
        }

        boolean usesDirectTranslationService() {
            return directSetTranslation != null
                    && directIsTranslatable != null
                    && (directTranslationService != null
                    || (directServiceManagerGetter != null
                    && directServiceGetter != null
                    && directServiceInterface != null));
        }
    }

    private static final class BatchTranslationBridge {
        final Class<?> requestType;
        final Method getFragment;
        final Method getScope;
        final Method getListAbility;
        final Method getLoadedComments;
        final Method translateBatch;
        final Method resetBatch;
        final Field requestMetadata;
        final boolean requiresRequestMetadata;
        final boolean useBoundComments;

        BatchTranslationBridge(
                Class<?> requestType,
                Method getFragment,
                Method getScope,
                Method getListAbility,
                Method getLoadedComments,
                Method translateBatch,
                Method resetBatch,
                Field requestMetadata,
                boolean requiresRequestMetadata,
                boolean useBoundComments) {
            this.requestType = requestType;
            this.getFragment = getFragment;
            this.getScope = getScope;
            this.getListAbility = getListAbility;
            this.getLoadedComments = getLoadedComments;
            this.translateBatch = translateBatch;
            this.resetBatch = resetBatch;
            this.requestMetadata = requestMetadata;
            this.requiresRequestMetadata = requiresRequestMetadata;
            this.useBoundComments = useBoundComments;
        }
    }

    private void installRegionPayloadPatches(ClassLoader classLoader, RegionPreset preset) {
        hookRegionJsonPayload(classLoader, preset);
        hookRegionQueryPayload(classLoader, preset);
    }

    private void hookRegionJsonPayload(ClassLoader classLoader, RegionPreset preset) {
        try {
            Class<?> type = Class.forName("X.okl", false, classLoader);
            Method method = type.getDeclaredMethod("LIZ");
            if (method.getReturnType() != JSONObject.class) {
                return;
            }
            hook(method)
                    .setId("toki-region-json")
                    .intercept(chain -> {
                        Object result = chain.proceed();
                        if (result instanceof JSONObject) {
                            applyRegionJson((JSONObject) result, preset);
                        }
                        return result;
                    });
        } catch (ClassNotFoundException ignored) {
            // The class is obfuscated and may change in newer TikTok builds.
        } catch (NoSuchMethodException ignored) {
            // This exact 46.3.3 payload builder is not present in this TikTok build.
        } catch (Throwable error) {
            logError("Unable to hook region JSON payload", error);
        }
    }

    private void hookRegionQueryPayload(ClassLoader classLoader, RegionPreset preset) {
        try {
            Class<?> type = Class.forName("X.i45", false, classLoader);
            Method method = type.getDeclaredMethod("LIZLLL");
            if (method.getReturnType() != String.class) {
                return;
            }
            hook(method)
                    .setId("toki-region-query")
                    .intercept(chain -> replaceRegionParameters((String) chain.proceed(), preset));
        } catch (ClassNotFoundException ignored) {
            // The class is obfuscated and may change in newer TikTok builds.
        } catch (NoSuchMethodException ignored) {
            // This exact 46.3.3 upload-parameter builder is not present in this TikTok build.
        } catch (Throwable error) {
            logError("Unable to hook region query payload", error);
        }
    }

    private static void applyRegionJson(JSONObject payload, RegionPreset preset) {
        try {
            String region = preset.code.toUpperCase(Locale.ROOT);
            payload.put("carrier_region", region);
            payload.put("network_sim_region", region);
            payload.put("system_region", region);
            payload.put("mcc_mnc", preset.operator);
        } catch (JSONException ignored) {
            // Do not interrupt TikTok if a malformed payload cannot be updated.
        }
    }

    private static String replaceRegionParameters(String query, RegionPreset preset) {
        if (query == null || query.isEmpty()) {
            return query;
        }
        String[] parameters = query.split("&", -1);
        String region = preset.code.toUpperCase(Locale.ROOT);
        for (int index = 0; index < parameters.length; index++) {
            String parameter = parameters[index];
            if (parameter.startsWith("carrier_region=")) {
                parameters[index] = "carrier_region=" + region;
            } else if (parameter.startsWith("Region=")) {
                parameters[index] = "Region=" + region;
            } else if (parameter.startsWith("StoreRegion=")) {
                parameters[index] = "StoreRegion=" + region;
            } else if (parameter.startsWith("store_region=")) {
                parameters[index] = "store_region=" + region;
            }
        }
        return String.join("&", parameters);
    }

    private void hookTelephony(String methodName, String value) {
        for (Method method : TelephonyManager.class.getDeclaredMethods()) {
            if (!methodName.equals(method.getName()) || method.getReturnType() != String.class) {
                continue;
            }
            try {
                hook(method)
                        .setId("toki-telephony-" + methodName + "-" + method.getParameterCount())
                        .intercept(chain -> value);
            } catch (Throwable error) {
                logError("Unable to hook TelephonyManager#" + methodName, error);
            }
        }
    }

    private void installDownloadPatches(ClassLoader classLoader) {
        hookReturnConstant(classLoader, "com.ss.android.ugc.aweme.feed.model.ACLCommonShare", "getCode", 0);
        hookReturnConstant(classLoader, "com.ss.android.ugc.aweme.feed.model.ACLCommonShare", "getShowType", 2);
        hookReturnConstant(classLoader, "com.ss.android.ugc.aweme.feed.model.ACLCommonShare", "getTranscode", 1);
        hookAclTranscode(classLoader, "getDownloadMaskPanel");
        hookAclTranscode(classLoader, "getDownloadGeneral");
        hookAclTranscode(classLoader, "getDownloadSharePanel");
        hookNoWatermarkDownloadAddress(classLoader);
    }

    /** Official TikTok 46.3.x and 46.4.x save media to DCIM/Camera via MediaStore. */
    private void installOfficialDownloadLocationHook(
            ClassLoader classLoader, ModuleConfig config) {
        int bridgeHooks = installMediaInsertBridge(
                classLoader, "X.183b", "X.132D", config, "46.3");
        bridgeHooks += installMediaInsertBridge(
                classLoader, "X.0yn6", "X.0wj1", config, "46.4");

        int installed = 0;
        for (Method method : ContentResolver.class.getDeclaredMethods()) {
            Class<?>[] parameterTypes = method.getParameterTypes();
            if (!"insert".equals(method.getName()) || parameterTypes.length < 2
                    || parameterTypes[0] != Uri.class || parameterTypes[1] != ContentValues.class) {
                continue;
            }
            try {
                hook(method)
                        .setId("toki-official-download-location-"
                                + parameterTypes.length)
                        .intercept(chain -> {
                            Object collection = chain.getArg(0);
                            Object values = chain.getArg(1);
                            if (collection instanceof Uri && values instanceof ContentValues) {
                                rewriteOfficialDownloadLocation(
                                        (Uri) collection, (ContentValues) values, config);
                            }
                            return chain.proceed();
                        });
                installed++;
            } catch (Throwable error) {
                logError("Unable to hook ContentResolver#insert(" + parameterTypes.length + ")", error);
            }
        }
        logInfo("Official download location hooks installed (" + bridgeHooks
                + " TikTok bridge, " + installed + " framework insert variants)");
    }

    private int installMediaInsertBridge(
            ClassLoader classLoader,
            String className,
            String metadataClassName,
            ModuleConfig config,
            String version
    ) {
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
                    .setId("toki-media-insert-bridge-" + version)
                    .intercept(chain -> {
                        Object collection = chain.getArg(1);
                        Object values = chain.getArg(2);
                        if (collection instanceof Uri && values instanceof ContentValues) {
                            rewriteOfficialDownloadLocation(
                                    (Uri) collection, (ContentValues) values, config);
                        }
                        return chain.proceed();
                    });
            return 1;
        } catch (ClassNotFoundException ignored) {
            // Only one bridge exists in a supported TikTok version.
        } catch (NoSuchMethodException ignored) {
            // The bridge signature changed in this TikTok version.
        } catch (Throwable error) {
            logError("Unable to hook TikTok " + version + " media insert bridge", error);
        }
        return 0;
    }

    private void rewriteOfficialDownloadLocation(
            Uri collection, ContentValues values, ModuleConfig config) {
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
        if (officialDownloadLocationRewriteLogged.compareAndSet(false, true)) {
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
        if (type.startsWith("image/") || (collectionPath != null && collectionPath.contains("/images/"))) {
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
        if (path.isEmpty() || path.contains("://")) {
            return null;
        }
        if (path.startsWith("/")) {
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

    private static Field findField(Class<?> type, String name) {
        Class<?> current = type;
        while (current != null) {
            try {
                Field field = current.getDeclaredField(name);
                field.setAccessible(true);
                return field;
            } catch (NoSuchFieldException ignored) {
                current = current.getSuperclass();
            } catch (RuntimeException error) {
                return null;
            }
        }
        return null;
    }

    private static Method findMethodByNameAndArity(
            Class<?> type,
            int parameterCount,
            String... names
    ) {
        if (type == null) {
            return null;
        }
        Class<?> current = type;
        while (current != null) {
            Method fallback = null;
            for (String name : names) {
                for (Method method : current.getDeclaredMethods()) {
                    if (!method.getName().equals(name)
                            || method.getParameterCount() != parameterCount) {
                        continue;
                    }
                    if (!method.isSynthetic()) {
                        return method;
                    }
                    fallback = method;
                }
            }
            if (fallback != null) {
                return fallback;
            }
            current = current.getSuperclass();
        }
        return null;
    }

    private static Method findMethodByNameAndArity(Class<?> type, String... names) {
        return findMethodByNameAndArity(type, 0, names);
    }

    private static Method findFloatVoidMethod(Class<?> type, String name) {
        Class<?> current = type;
        while (current != null) {
            for (Method method : current.getDeclaredMethods()) {
                Class<?>[] parameters = method.getParameterTypes();
                if (name.equals(method.getName())
                        && method.getReturnType() == void.class
                        && parameters.length == 1
                        && parameters[0] == float.class) {
                    method.setAccessible(true);
                    return method;
                }
            }
            current = current.getSuperclass();
        }
        return null;
    }

    /** Forces the feed video engine's loop flag off when the user enables prevention. */
    private int installLoopPrevention(ClassLoader classLoader) {
        int installedTargets = 0;
        if (installOfficialLoopConfigHook(classLoader)) {
            installedTargets++;
        }
        if (installLoopCompletionPauseHook(classLoader)) {
            installedTargets++;
        }
        if (installLoopSetter(
                classLoader,
                "com.ss.ttvideoengine.TTVideoEngine",
                "toki-disable-loop-engine")) {
            installedTargets++;
        }
        if (installLoopSetter(
                classLoader,
                "com.ss.ttvideoengine.TTVideoEngineImpl",
                "toki-disable-loop-engine-impl")) {
            installedTargets++;
        }
        return installedTargets;
    }

    /**
     * Applies the configured speed after TikTok finishes initializing a newly rendered video.
     * This deliberately runs once per source ID, so an in-app manual speed change stays intact
     * for the current video while the next video returns to the configured default.
     */
    private void installDefaultPlaybackSpeed(ClassLoader classLoader) {
        try {
            Class<?> type = Class.forName(
                    "com.ss.android.ugc.aweme.feed.controller.PlayerController",
                    false,
                    classLoader);
            int installed = 0;
            for (Method method : type.getDeclaredMethods()) {
                if (!"onRenderReady".equals(method.getName())
                        || method.getParameterCount() != 1
                        || method.getReturnType() != void.class
                        || method.isSynthetic()) {
                    continue;
                }
                method.setAccessible(true);
                hook(method)
                        .setId("toki-default-playback-speed-" + installed)
                        .intercept(chain -> {
                            Object result = chain.proceed();
                            applyDefaultPlaybackSpeed(chain.getThisObject(), chain.getArg(0));
                            return result;
                        });
                installed++;
            }
            if (installed == 0) {
                logError(
                        "Unable to find PlayerController#onRenderReady(*)",
                        new NoSuchMethodException("onRenderReady(*)"));
            } else {
                logInfo("Default playback speed bridge installed: " + installed + " target(s)");
            }
        } catch (ClassNotFoundException ignored) {
            // The player controller is unavailable in this TikTok variant.
        } catch (Throwable error) {
            logError("Unable to install default playback speed bridge", error);
        }
    }

    private void applyDefaultPlaybackSpeed(Object controller, Object renderEvent) {
        String sourceId = extractPlaybackSourceId(renderEvent);
        if (controller == null || sourceId == null || sourceId.isEmpty()) {
            return;
        }
        float speed = loadConfiguredDefaultPlaybackSpeed();
        if (speed == PlaybackSpeed.DEFAULT) {
            return;
        }
        synchronized (defaultPlaybackSpeedSourceIds) {
            if (sourceId.equals(defaultPlaybackSpeedSourceIds.get(controller))) {
                return;
            }
            defaultPlaybackSpeedSourceIds.put(controller, sourceId);
        }
        try {
            Method setSpeed = findFloatVoidMethod(controller.getClass(), "setSpeed");
            if (setSpeed != null) {
                setSpeed.invoke(controller, speed);
            } else {
                Object playerManager = resolvePlayerManager(controller);
                setSpeed = findFloatVoidMethod(
                        playerManager == null ? null : playerManager.getClass(), "setSpeed");
                if (setSpeed == null || playerManager == null) {
                    throw new NoSuchMethodException("PlayerController#setSpeed(float)");
                }
                setSpeed.invoke(playerManager, speed);
            }
            if (defaultPlaybackSpeedAppliedLogged.compareAndSet(false, true)) {
                logInfo("Default playback speed active: " + speed + "x");
            }
        } catch (Throwable error) {
            synchronized (defaultPlaybackSpeedSourceIds) {
                defaultPlaybackSpeedSourceIds.remove(controller);
            }
            if (defaultPlaybackSpeedFailureLogged.compareAndSet(false, true)) {
                logError("Unable to apply default playback speed", error);
            }
        }
    }

    private float loadConfiguredDefaultPlaybackSpeed() {
        try {
            return PlaybackSpeed.sanitize(getRemotePreferences(ModuleConfig.PREFS).getFloat(
                    ModuleConfig.KEY_DEFAULT_PLAYBACK_SPEED,
                    PlaybackSpeed.DEFAULT));
        } catch (Throwable error) {
            if (defaultPlaybackSpeedFailureLogged.compareAndSet(false, true)) {
                logError("Unable to read default playback speed", error);
            }
            return PlaybackSpeed.DEFAULT;
        }
    }

    private static Object resolvePlayerManager(Object controller) throws ReflectiveOperationException {
        Field field = findField(controller.getClass(), "mPlayerManager");
        if (field != null) {
            Object value = field.get(controller);
            if (value != null) {
                return value;
            }
        }
        Method getter = findMethodByNameAndArity(controller.getClass(), "getPlayerManager");
        if (getter == null) {
            throw new NoSuchMethodException("PlayerController#mPlayerManager");
        }
        getter.setAccessible(true);
        return getter.invoke(controller);
    }

    private static String extractPlaybackSourceId(Object renderEvent) {
        if (renderEvent instanceof String) {
            return (String) renderEvent;
        }
        if (renderEvent == null) {
            return null;
        }
        try {
            Field field = findField(renderEvent.getClass(), "LIZ");
            Object value = field == null ? null : field.get(renderEvent);
            return value instanceof String ? (String) value : null;
        } catch (Throwable ignored) {
            return null;
        }
    }

    private boolean installLoopCompletionPauseHook(ClassLoader classLoader) {
        try {
            Class<?> type = Class.forName(
                    "com.ss.android.ugc.aweme.feed.controller.PlayerController",
                    false,
                    classLoader);
            Class<?> awemeType = Class.forName(
                    "com.ss.android.ugc.aweme.feed.model.Aweme",
                    false,
                    classLoader);
            Method completionMethod = type.getDeclaredMethod("onPlayCompleted", String.class);
            Method currentAwemeMethod = findDeclaredMethod(
                    type, new Class<?>[0], "LIZIZ", "LJJIZ", "LLJI");
            Method currentHolderMethod = findDeclaredMethod(
                    type, new Class<?>[0], "LLL", "LLII", "LLJZIJLIL");
            Method holderForSourceMethod = findDeclaredMethod(
                    type, new Class<?>[]{String.class}, "LJJIJL");
            Method manualPauseMethod = findDeclaredMethod(
                    type,
                    new Class<?>[]{awemeType, boolean.class, boolean.class, boolean.class},
                    "jk",
                    "lk",
                    "qk");
            Method pauseStateMethod;
            try {
                pauseStateMethod = type.getDeclaredMethod("LLZLLLL", int.class);
                pauseStateMethod.setAccessible(true);
            } catch (NoSuchMethodException ignored) {
                pauseStateMethod = null;
            }
            final Method markPausedMethod = pauseStateMethod;
            Method seekToReplayFrameMethod = type.getDeclaredMethod("LJIILL", float.class);
            Method getAwemeAidMethod = awemeType.getMethod("getAid");
            if (manualPauseMethod.getReturnType() != void.class) {
                throw new NoSuchMethodException(
                        "PlayerController#qk(Aweme, boolean, boolean, boolean) must return void");
            }
            if (seekToReplayFrameMethod.getReturnType() != void.class) {
                throw new NoSuchMethodException("PlayerController#LJIILL(float) must return void");
            }
            completionMethod.setAccessible(true);
            currentAwemeMethod.setAccessible(true);
            currentHolderMethod.setAccessible(true);
            holderForSourceMethod.setAccessible(true);
            manualPauseMethod.setAccessible(true);
            seekToReplayFrameMethod.setAccessible(true);
            getAwemeAidMethod.setAccessible(true);
            hook(completionMethod)
                    .setId("toki-disable-loop-completion-replay-frame")
                    .intercept(chain -> {
                        Object result = chain.proceed();
                        Object controller = chain.getThisObject();
                        try {
                            Object completedSourceId = chain.getArg(0);
                            Object currentAweme = currentAwemeMethod.invoke(controller);
                            if (!awemeType.isInstance(currentAweme)) {
                                throw new IllegalStateException("PlayerController#LLJI() did not return the current Aweme");
                            }
                            boolean currentCompletion = isCurrentPlaybackCompletion(
                                    controller,
                                    completedSourceId,
                                    currentAweme,
                                    getAwemeAidMethod,
                                    currentHolderMethod,
                                    holderForSourceMethod);
                            if (!currentCompletion) {
                                return result;
                            }
                            // qk() is TikTok's own single-tap play/pause path. Its first flag makes the
                            // pause UI visible; the remaining flags preserve ordinary user-tap behavior.
                            manualPauseMethod.invoke(
                                    controller,
                                    currentAweme,
                                    Boolean.TRUE,
                                    Boolean.FALSE,
                                    Boolean.FALSE);
                            // Stream completion does not always dispatch onPausePlay(), leaving the
                            // tap handler in its playing state. State value 2 maps to paused (3).
                            if (markPausedMethod != null) {
                                markPausedMethod.invoke(controller, 2);
                            }
                            // Keep TikTok's real paused state, then render the frame from which replay starts.
                            seekToReplayFrameMethod.invoke(controller, 0.0f);
                            if (loopPreventionManualPauseLogged.compareAndSet(false, true)) {
                                logInfo("Loop-prevention paused and rewound to the replay frame");
                            }
                        } catch (Throwable error) {
                            if (loopPreventionManualPauseFailureLogged.compareAndSet(false, true)) {
                                logError("Unable to pause and rewind TikTok playback", error);
                            }
                        }
                        return result;
                    });
            return true;
        } catch (ClassNotFoundException ignored) {
            return false;
        } catch (NoSuchMethodException error) {
            logError("Unable to find TikTok loop-completion playback controls", error);
            return false;
        } catch (Throwable error) {
            logError("Unable to hook PlayerController completion pause and rewind", error);
            return false;
        }
    }

    private static boolean isCurrentPlaybackCompletion(
            Object controller,
            Object completedSourceId,
            Object currentAweme,
            Method getAwemeAidMethod,
            Method currentHolderMethod,
            Method holderForSourceMethod) {
        if (controller == null || !(completedSourceId instanceof String)) {
            return false;
        }
        String completedId = (String) completedSourceId;
        try {
            Object currentHolder = currentHolderMethod.invoke(controller);
            Object completedHolder = holderForSourceMethod.invoke(controller, completedId);
            if (currentHolder != null && currentHolder == completedHolder) {
                return true;
            }
        } catch (Throwable ignored) {
            // Continue with model and controller IDs when a holder is being rebound.
        }
        try {
            Object currentAid = getAwemeAidMethod.invoke(currentAweme);
            if (completedId.equals(currentAid)) {
                return true;
            }
        } catch (Throwable ignored) {
            // Continue with controller IDs.
        }
        try {
            Field currentSourceIdField = findField(controller.getClass(), "mCurrentSourceId");
            Object currentSourceId = currentSourceIdField == null
                    ? null
                    : currentSourceIdField.get(controller);
            if (completedId.equals(currentSourceId)) {
                return true;
            }
            Field currentAidField = findField(controller.getClass(), "mCurrentAid");
            Object currentAid = currentAidField == null ? null : currentAidField.get(controller);
            return completedId.equals(currentAid);
        } catch (Throwable ignored) {
            return false;
        }
    }

    private boolean installOfficialLoopConfigHook(ClassLoader classLoader) {
        try {
            Class<?> type = Class.forName("X.0tNO", false, classLoader);
            for (Method method : type.getDeclaredMethods()) {
                Class<?>[] parameters = method.getParameterTypes();
                if (!"LJIJJ".equals(method.getName())
                        || parameters.length != 3
                        || parameters[1] != Map.class
                        || parameters[2] != boolean.class
                        || method.getReturnType() != void.class) {
                    continue;
                }
                method.setAccessible(true);
                hook(method)
                        .setId("toki-disable-loop-config-4633")
                        .intercept(chain -> {
                            Object result = chain.proceed();
                            Object mapArgument = chain.getArg(1);
                            Object bypassArgument = chain.getArg(2);
                            if (Boolean.FALSE.equals(bypassArgument)
                                    && mapArgument instanceof Map<?, ?>) {
                                Object requested = forceLoopFlagOff((Map<?, ?>) mapArgument);
                                if (loopPreventionConfigInvocationLogged.compareAndSet(false, true)) {
                                    logInfo("Loop-prevention config active via X.0tNO#LJIJJ"
                                            + ", requested=" + requested);
                                }
                            }
                            return result;
                        });
                return true;
            }
            logError(
                    "Unable to find official loop config builder",
                    new NoSuchMethodException("X.0tNO#LJIJJ(*, Map, boolean)"));
            return false;
        } catch (ClassNotFoundException ignored) {
            return false;
        } catch (Throwable error) {
            logError("Unable to hook official loop config builder", error);
            return false;
        }
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private static Object forceLoopFlagOff(Map<?, ?> settings) {
        return ((Map) settings).put("is_play_loop", Boolean.FALSE);
    }

    private boolean installLoopSetter(ClassLoader classLoader, String className, String hookId) {
        try {
            Class<?> type = Class.forName(className, false, classLoader);
            Method method = type.getDeclaredMethod("setLooping", boolean.class);
            if (method.getReturnType() != void.class) {
                logError(
                        "Unable to hook loop setter on " + className,
                        new NoSuchMethodException("setLooping(boolean) must return void"));
                return false;
            }
            method.setAccessible(true);
            hook(method)
                    .setId(hookId)
                    .intercept(chain -> {
                        if (loopPreventionEngineInvocationLogged.compareAndSet(false, true)) {
                            logInfo("Loop-prevention bridge active via " + className
                                    + ", requested=" + chain.getArg(0));
                        }
                        return chain.proceed(new Object[]{Boolean.FALSE});
                    });
            return true;
        } catch (ClassNotFoundException ignored) {
            return false;
        } catch (NoSuchMethodException error) {
            logError("Unable to find loop setter on " + className, error);
            return false;
        } catch (Throwable error) {
            logError("Unable to hook loop setter on " + className, error);
            return false;
        }
    }

    private int installPagePurification(ClassLoader classLoader, ModuleConfig config) {
        int installed = 0;
        if (config.hideAuthorInfo) {
            installed += installComponentVisibilityHooks(classLoader, "author-info",
                    "com.ss.android.ugc.aweme.feed.assem.avatar.FeedAvatarAssemWrap",
                    "com.ss.android.ugc.aweme.feed.assem.avatar.FeedAvatarDefaultAssem",
                    "com.ss.android.ugc.aweme.feed.assem.videoauthorinfo.VideoAuthorInfoRelationAssem");
        }
        if (config.hideFollowButton) {
            installed += installComponentVisibilityHooks(classLoader, "follow-button",
                    "com.ss.android.ugc.aweme.feed.assem.relationbtn.VideoRelationBtnAssem",
                    "com.ss.android.ugc.aweme.feed.assem.relationbtn.VideoRelationBtnAssemV2");
        }
        if (config.hideVideoDescription) {
            installed += installComponentVisibilityHooks(classLoader, "video-description",
                    "com.ss.android.ugc.aweme.feed.assem.desc.VideoDescAssem");
        }
        if (config.hideVideoTags) {
            installed += installComponentVisibilityHooks(classLoader, "video-tags",
                    "com.ss.android.ugc.aweme.feed.assem.desc.VideoDescTagAssem");
        }
        if (config.hideMusicTitle) {
            installed += installComponentVisibilityHooks(classLoader, "music-title",
                    "com.ss.android.ugc.aweme.feed.assem.music.VideoMusicTitleAssem");
        }
        if (config.hideMusicCover) {
            installed += installComponentVisibilityHooks(classLoader, "music-cover",
                    "com.ss.android.ugc.aweme.feed.assem.music.VideoMusicCoverAssem");
        }
        if (config.hideLikeButton) {
            installed += installComponentVisibilityHooks(classLoader, "like-button",
                    "com.ss.android.ugc.aweme.feed.assem.digg.VideoDiggAssem");
        }
        if (config.hideCommentButton) {
            installed += installComponentVisibilityHooks(classLoader, "comment-button",
                    "com.ss.android.ugc.aweme.feed.assem.videocomment.VideoCommentAssem");
        }
        if (config.hideFavoriteButton) {
            installed += installComponentVisibilityHooks(classLoader, "favorite-button",
                    "com.ss.android.ugc.aweme.feed.favorite.VideoFavoriteAssem");
        }
        if (config.hideShareButton) {
            installed += installComponentVisibilityHooks(classLoader, "share-button",
                    "com.ss.android.ugc.aweme.feed.assem.share.VideoShareAssem");
        }
        if (config.hideDuetButton) {
            installed += installComponentVisibilityHooks(classLoader, "duet-button",
                    "com.ss.android.ugc.aweme.feed.assem.duetbutton.VideoDuetButtonAssem");
        }
        if (config.hideStitchButton) {
            installed += installComponentVisibilityHooks(classLoader, "stitch-button",
                    "com.ss.android.ugc.aweme.feed.assem.stitchbutton.VideoStitchButtonAssem");
        }
        if (config.hideQuickDm) {
            installed += installComponentVisibilityHooks(classLoader, "quick-dm",
                    "com.ss.android.ugc.aweme.feed.assem.quickreply.MUFQuickDMBoxAssem",
                    "com.ss.android.ugc.aweme.feed.assem.quickreply.MUFQuickDMBoxAssemV2",
                    "com.ss.android.ugc.aweme.feed.assem.story.QuickDMEntranceAssem",
                    "com.ss.android.ugc.aweme.feed.assem.story.QuickDMEntranceAssemV2");
        }
        if (config.hideStoryTags) {
            installed += installComponentVisibilityHooks(classLoader, "story-tags",
                    "com.ss.android.ugc.aweme.feed.assem.story.FeedStoryTagAssem",
                    "com.ss.android.ugc.aweme.feed.assem.story.FeedStoryTagAssemV2");
        }
        if (config.hideCollabLabel) {
            installed += installComponentVisibilityHooks(classLoader, "collab-label",
                    "com.ss.android.ugc.aweme.feed.assem.collab.CollabInFeedLabelAssem");
        }
        if (config.hideTako) {
            installed += installComponentVisibilityHooks(classLoader, "tako",
                    "com.ss.android.ugc.aweme.feed.assem.tikbot.TakoAssem");
        }
        if (config.hideContentSearch) {
            installed += installContentSearchVisibilityHooks(classLoader);
        }
        if (config.hideTranslationControls) {
            installed += installComponentVisibilityHooks(classLoader, "translation-controls",
                    "com.ss.android.ugc.aweme.translation.ui.TranslationControlsAssem");
        }
        return installed;
    }

    /** Removes the feed payloads that cause TikTok to render visual and similar-content search. */
    private int installContentSearchVisibilityHooks(ClassLoader classLoader) {
        return installAwemePayloadRemovalHooks(
                classLoader,
                "content-search",
                "getSmartSearchInfo",
                "getVisualSearchInfo"
        );
    }

    private int installVideoOverlayPurification(ClassLoader classLoader, ModuleConfig config) {
        int installed = 0;
        if (config.hideTrendingTopics) {
            installed += installAwemePayloadRemovalHooks(
                    classLoader,
                    "trending-topics",
                    "getTrendingBar",
                    "getTrendingBarFYP",
                    "getHotSearchInfo",
                    "getDouDiscountMixInfo"
            );
        }
        if (config.hideContentClassification) {
            installed += installAwemePayloadRemovalHooks(
                    classLoader,
                    "content-classification",
                    "getContentClassificationMaskInfo"
            );
        }
        return installed;
    }

    /** Removes optional Aweme payloads before TikTok creates their corresponding overlay. */
    private int installAwemePayloadRemovalHooks(
            ClassLoader classLoader,
            String targetName,
            String... getterNames
    ) {
        try {
            Class<?> awemeType = Class.forName(
                    "com.ss.android.ugc.aweme.feed.model.Aweme", false, classLoader);
            int installed = 0;
            for (String getterName : getterNames) {
                try {
                    Method getter = awemeType.getMethod(getterName);
                    hook(getter)
                            .setId("toki-purify-" + targetName + "-" + getterName)
                            .intercept(chain -> null);
                    installed++;
                } catch (NoSuchMethodException ignored) {
                    // The payload has been removed or renamed in this TikTok version.
                }
            }
            return installed;
        } catch (ClassNotFoundException error) {
            logError("Unable to resolve Aweme payloads for " + targetName, error);
            return 0;
        } catch (Throwable error) {
            logError("Unable to remove Aweme payloads for " + targetName, error);
            return 0;
        }
    }

    private int installComponentVisibilityHooks(
            ClassLoader classLoader,
            String targetName,
            String... classNames
    ) {
        int installed = 0;
        for (String className : classNames) {
            try {
                Class<?> type = Class.forName(className, false, classLoader);
                Method contentViewMethod = findComponentContentViewMethod(type);
                for (Method method : type.getDeclaredMethods()) {
                    boolean viewCreated = "onViewCreated".equals(method.getName())
                            && method.getParameterCount() == 1
                            && View.class.isAssignableFrom(method.getParameterTypes()[0]);
                    boolean binding = "onBind".equals(method.getName())
                            && method.getParameterCount() == 1
                            && !method.isBridge();
                    if (!viewCreated && !binding) {
                        continue;
                    }
                    method.setAccessible(true);
                    final Method viewMethod = contentViewMethod;
                    final String source = className + "#" + method.getName();
                    hook(method)
                            .setId("toki-purify-" + targetName + "-" + installed)
                            .intercept(chain -> {
                                Object result = chain.proceed();
                                hideComponentView(chain.getThisObject(), chain.getArg(0), viewMethod);
                                if (pagePurificationVisibilityLogged.compareAndSet(false, true)) {
                                    logInfo("Page purification active via " + source);
                                }
                                return result;
                            });
                    installed++;
                }
            } catch (ClassNotFoundException ignored) {
                // TikTok changes some optional component variants between releases.
            } catch (Throwable error) {
                logError("Unable to hide page purification target " + className, error);
            }
        }
        return installed;
    }

    private static void hideComponentView(
            Object component,
            Object lifecycleArgument,
            Method contentViewMethod
    ) {
        View lifecycleView = lifecycleArgument instanceof View ? (View) lifecycleArgument : null;
        View contentView = null;
        if (contentViewMethod != null) {
            try {
                Object value = contentViewMethod.invoke(component);
                if (value instanceof View) {
                    contentView = (View) value;
                }
            } catch (ReflectiveOperationException | RuntimeException ignored) {
                // The lifecycle view remains a safe fallback when the base component changes.
            }
        }
        if (contentView != null) {
            contentView.setVisibility(View.GONE);
        }
        if (lifecycleView != null && lifecycleView != contentView) {
            lifecycleView.setVisibility(View.GONE);
        }
    }

    private static Method findComponentContentViewMethod(Class<?> type) {
        for (String name : new String[]{"getContentView", "LJJIJLIJ"}) {
            Method method = findInheritedNoArgMethod(type, name);
            if (method != null && View.class.isAssignableFrom(method.getReturnType())) {
                return method;
            }
        }
        for (Class<?> current = type; current != null; current = current.getSuperclass()) {
            for (Method method : current.getDeclaredMethods()) {
                if (method.getParameterCount() == 0
                        && !Modifier.isStatic(method.getModifiers())
                        && View.class.isAssignableFrom(method.getReturnType())) {
                    method.setAccessible(true);
                    return method;
                }
            }
        }
        return null;
    }

    private static Method findInheritedNoArgMethod(Class<?> type, String name) {
        for (Class<?> current = type; current != null; current = current.getSuperclass()) {
            try {
                Method method = current.getDeclaredMethod(name);
                method.setAccessible(true);
                return method;
            } catch (NoSuchMethodException ignored) {
                // Continue to the base component where getContentView is declared.
            }
        }
        return null;
    }

    private boolean installGlobalNavigationPurification(ModuleConfig config) {
        try {
            Method onResume = Activity.class.getDeclaredMethod("onResume");
            onResume.setAccessible(true);
            hook(onResume)
                    .setId("toki-global-navigation-purification")
                    .intercept(chain -> {
                        Object result = chain.proceed();
                        Object activity = chain.getThisObject();
                        if (activity instanceof Activity) {
                            observeGlobalNavigation((Activity) activity, config);
                        }
                        return result;
                    });
            return true;
        } catch (Throwable error) {
            logError("Unable to install global navigation purification", error);
            return false;
        }
    }

    private void observeGlobalNavigation(Activity activity, ModuleConfig config) {
        View decorView;
        try {
            decorView = activity.getWindow().getDecorView();
        } catch (RuntimeException ignored) {
            return;
        }
        if (decorView == null || globalNavigationObservedRoots.put(decorView, Boolean.TRUE) != null) {
            return;
        }

        GlobalNavigationViewIds viewIds = GlobalNavigationViewIds.from(decorView);
        applyGlobalNavigationPurification(decorView, config, viewIds);
        ViewTreeObserver observer = decorView.getViewTreeObserver();
        if (observer.isAlive()) {
            observer.addOnGlobalLayoutListener(
                    () -> applyGlobalNavigationPurification(decorView, config, viewIds));
        }
    }

    private void applyGlobalNavigationPurification(
            View root,
            ModuleConfig config,
            GlobalNavigationViewIds viewIds
    ) {
        if (config.hideTopNavigation) {
            hideViewById(root, viewIds.topNavigation);
        }
        if (config.hideSearchEntry) {
            hideViewById(root, viewIds.searchEntry);
        }
        if (config.hideBottomNavigation) {
            hideViewById(root, viewIds.bottomNavigation);
        }
        if (config.hideVideoProgressBar) {
            hideViewById(root, viewIds.videoProgressBar);
        }
        if (globalNavigationPurificationVisibilityLogged.compareAndSet(false, true)) {
            logInfo("Global navigation purification active");
        }
    }

    private static void hideViewById(View root, int resourceId) {
        if (resourceId == 0) {
            return;
        }
        View target = root.findViewById(resourceId);
        if (target != null) {
            target.setVisibility(View.GONE);
        }
    }

    private static final class GlobalNavigationViewIds {
        final int topNavigation;
        final int searchEntry;
        final int bottomNavigation;
        final int videoProgressBar;

        private GlobalNavigationViewIds(
                int topNavigation,
                int searchEntry,
                int bottomNavigation,
                int videoProgressBar
        ) {
            this.topNavigation = topNavigation;
            this.searchEntry = searchEntry;
            this.bottomNavigation = bottomNavigation;
            this.videoProgressBar = videoProgressBar;
        }

        static GlobalNavigationViewIds from(View root) {
            return new GlobalNavigationViewIds(
                    viewId(root, "tyu"),
                    viewId(root, "jvu"),
                    viewId(root, "o3o"),
                    viewId(root, "video_seek_bar"));
        }

        private static int viewId(View root, String resourceName) {
            return root.getResources().getIdentifier(
                    resourceName, "id", ModuleConfig.TARGET_PACKAGE);
        }
    }

    private void installReusePermissionPatches(ClassLoader classLoader, ModuleConfig config) {
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

    private void hookNoWatermarkDownloadAddress(ClassLoader classLoader) {
        try {
            Class<?> type = Class.forName("com.ss.android.ugc.aweme.feed.model.Video", false, classLoader);
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
                                Object noWatermarkAddressResult = noWatermarkAddress.invoke(chain.getThisObject());
                                return noWatermarkAddressResult != null
                                        ? noWatermarkAddressResult
                                        : normalAddress;
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
            Class<?> type = Class.forName("com.ss.android.ugc.aweme.feed.model.AwemeACLShare", false, classLoader);
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

    private void hookReturnConstant(ClassLoader classLoader, String className, String methodName, Object value) {
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

    private void installFeedFilters(ClassLoader classLoader, ModuleConfig config) {
        FeedFilter filter = new FeedFilter(config);
        hookFeedResults(classLoader, "com.ss.android.ugc.aweme.feed.FeedApiService", filter);
        hookFeedResults(classLoader, "com.ss.android.ugc.aweme.feed.api.FeedApi", filter);
        hookFollowingFeedResults(classLoader, filter);
        if (config.hideFeedAds) {
            hookDiscoverResults(classLoader, filter);
            hookProfileAdResponses(classLoader, filter);
        }
    }


    private void hookFollowingFeedResults(ClassLoader classLoader, FeedFilter filter) {
        try {
            Class<?> type = Class.forName("X.dFg", false, classLoader);
            for (Method method : type.getDeclaredMethods()) {
                if (!"LJIIIZ".equals(method.getName()) || method.getParameterCount() != 1
                        || !java.util.List.class.isAssignableFrom(method.getReturnType())) {
                    continue;
                }
                hook(method)
                        .setId("toki-following-feed")
                        .intercept(chain -> filter.filterListResult(chain.proceed()));
            }
        } catch (ClassNotFoundException ignored) {
            // The known 46.3.3 path is obfuscated differently in other versions.
        } catch (Throwable error) {
            logError("Unable to hook following feed transformation", error);
        }
    }

    private void hookDiscoverResults(ClassLoader classLoader, FeedFilter filter) {
        try {
            Class<?> type = Class.forName("X.0U2", false, classLoader);
            for (Method method : type.getDeclaredMethods()) {
                if (!"apply".equals(method.getName()) || method.getParameterCount() != 1) {
                    continue;
                }
                hook(method)
                        .setId("toki-discover-" + method.getReturnType().getName())
                        .intercept(chain -> {
                            Object result = chain.proceed();
                            filter.applyBanners(chain.getArg(0));
                            return result;
                        });
            }
        } catch (ClassNotFoundException ignored) {
            // Obfuscated class names change between TikTok releases.
        } catch (Throwable error) {
            logError("Unable to hook Discover banner transformation", error);
        }
    }

    private void hookProfileAdResponses(ClassLoader classLoader, FeedFilter filter) {
        try {
            Class<?> type = Class.forName("X.U83", false, classLoader);
            for (Method method : type.getDeclaredMethods()) {
                Class<?>[] parameterTypes = method.getParameterTypes();
                if (!"LIZLLL".equals(method.getName()) || parameterTypes.length != 6
                        || !java.util.List.class.isAssignableFrom(parameterTypes[0])
                        || method.getReturnType() != Object.class) {
                    continue;
                }
                hook(method)
                        .setId("toki-profile-ads")
                        .intercept(chain -> {
                            Object result = chain.proceed();
                            filter.clearProfileAds(result);
                            return result;
                        });
            }
        } catch (ClassNotFoundException ignored) {
            // This is an obfuscated 46.3.3 implementation detail.
        } catch (Throwable error) {
            logError("Unable to hook profile ad response", error);
        }
    }

    private void hookFeedResults(ClassLoader classLoader, String className, FeedFilter filter) {
        try {
            Class<?> type = Class.forName(className, false, classLoader);
            for (Method method : type.getDeclaredMethods()) {
                if ("com.ss.android.ugc.aweme.feed.model.FeedItemList"
                        .equals(method.getReturnType().getName())) {
                    hook(method)
                            .setId("toki-feed-" + className + "-" + method.getName()
                                    + "-" + method.getParameterCount())
                            .intercept(chain -> {
                                Object result = chain.proceed();
                                filter.apply(result);
                                return result;
                            });
                }
            }
        } catch (Throwable error) {
            logError("Unable to hook feed results from " + className, error);
        }
    }

    private void logInfo(String message) {
        log(4, TAG, message);
    }

    private void logError(String message, Throwable error) {
        log(6, TAG, message, error);
    }
}
