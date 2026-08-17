package com.seepd.toki;

import android.animation.Animator;
import android.app.Application;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.content.res.Configuration;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.telephony.TelephonyManager;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewParent;
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
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.WeakHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import org.json.JSONException;
import org.json.JSONObject;

import io.github.libxposed.api.XposedInterface;
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
    private final AtomicBoolean antiBurnInPhotoTitleFailureLogged = new AtomicBoolean(false);
    private final AtomicInteger antiBurnInTraceBudget = new AtomicInteger(0);
    private final Object officialTranslationLock = new Object();
    private final Object antiBurnInLock = new Object();
    private final LinkedHashMap<String, BoundComment> officialBoundComments = new LinkedHashMap<>();
    private final HashSet<String> officialTranslationRequests = new HashSet<>();
    private final WeakHashMap<Object, String> officialTranslatedActions = new WeakHashMap<>();
    private final WeakHashMap<Object, AntiBurnInGestureTracker> antiBurnInGestures =
            new WeakHashMap<>();
    private final WeakHashMap<Object, PhotoUiRestoreTarget> antiBurnInPhotoUiRestoreTargets =
            new WeakHashMap<>();
    private final WeakHashMap<Object, PhotoStateRestoreTarget>
            antiBurnInPhotoStateRestoreTargets = new WeakHashMap<>();
    private final WeakHashMap<Object, Boolean> antiBurnInPhotoIntercepts =
            new WeakHashMap<>();
    private final WeakHashMap<Object, Boolean> antiBurnInHiddenVideoCells =
            new WeakHashMap<>();
    private final WeakHashMap<Object, Boolean> antiBurnInPausePanels = new WeakHashMap<>();
    private final WeakHashMap<Object, String> defaultPlaybackSpeedSourceIds = new WeakHashMap<>();
    private final WeakHashMap<Object, Runnable> antiBurnInLongPressTasks = new WeakHashMap<>();
    private final Handler antiBurnInHandler = new Handler(Looper.getMainLooper());
    private volatile Context translationStateContext;
    private volatile boolean officialTranslationEnabled;
    private volatile String officialCommentPageAwemeId;
    private volatile String officialTranslatedAwemeId;
    private volatile Field antiBurnInDetectorField;
    private volatile boolean antiBurnInDesiredState;
    private volatile PhotoTitleVisibilityBridge antiBurnInPhotoTitleVisibilityBridge;
    private volatile boolean antiBurnInPhotoTitleForcedHidden;
    private volatile Method antiBurnInPhotoStateMethod;
    private volatile Method antiBurnInVideoVisibilityMethod;
    private volatile Method antiBurnInPauseResumeMethod;
    private volatile Method antiBurnInCellCleanMethod;
    private volatile int antiBurnInCellCleanModeArgument = 2;
    private volatile Method antiBurnInPanelCleanMethod;
    private volatile int antiBurnInPanelCleanModeArgument = 2;
    private volatile Constructor<?> antiBurnInToastBuilderConstructor;
    private volatile Method antiBurnInToastMessageMethod;
    private volatile Method antiBurnInToastLegacyMethod;
    private volatile Method antiBurnInToastShowMethod;
    private volatile float antiBurnInCenterTolerancePx = 24f;
    private volatile float antiBurnInSpanTolerancePx = 8f;

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
            logInfo("Hook revision: direct-ui-gates-2-loop-replay-frame-4-anti-burn-in-11-photo-gates");
            logInfo("Loop prevention setting: " + config.disableLoop);
            logInfo("Default playback speed: " + config.defaultPlaybackSpeed);
            logInfo("Anti-burn-in setting: " + config.antiBurnIn);
            logInfo("Comment translation setting: " + config.autoTranslateComments);
            if (config.antiBurnIn) {
                logInfo("Installing anti-burn-in bridges");
                installAntiBurnIn(classLoader, context);
                logInfo("Anti-burn-in bridge installation complete");
            }
            if (config.regionSpoof) {
                installRegionSpoof(classLoader, config.region);
            }
            if (config.removeDownloadRestrictions) {
                installDownloadPatches(classLoader);
            }
            // The save-directory fields are independent from download permission bypassing.
            installOfficialDownloadLocationHook(config);
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
            if (config.hideFeedAds || config.hideLive || config.hideImages || config.forceRegion
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

    /** Restores the comment-page translation control in the supported official TikTok client. */
    private void installCommentTranslationButton(ClassLoader classLoader) {
        installOfficialCommentTranslationButton(classLoader);
    }

    /**
     * Official 46.3.3 keeps the native comment translation repository without exposing a page-wide
     * control. Track the cell-owned translation actions and expose them through a TuxIconView added
     * beside the existing close control.
     */
    private void installOfficialCommentTranslationButton(ClassLoader classLoader) {
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

            Method bindComment = baseCommentCell.getDeclaredMethod("K6", commentItemType);
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
                    batchBridge);

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
        } catch (ClassNotFoundException | NoSuchMethodException | NoSuchFieldException error) {
            logInfo("Official comment translation symbols unavailable: "
                    + error.getClass().getSimpleName() + ": " + error.getMessage());
        } catch (Throwable error) {
            logError("Unable to enable official comment translation button", error);
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
                    requestMetadata);
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
            View closeButton = (View) bridge.closeButton.get(actionBar);
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
                : resetOfficialLoadedComments(actionBar, targetAwemeId, bridge);
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
                    if (shouldInvoke) {
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
                        if (binding.key.equals(officialTranslatedActions.get(action))) {
                            officialTranslatedActions.remove(action);
                        }
                    }
                }
            } else if (invokeOfficialCommentAction(bridge.resetTranslate, action)) {
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
        try {
            List<Object> comments = getOfficialLoadedComments(actionBar, awemeId, bridge);
            if (comments.isEmpty()) {
                return 0;
            }
            Object requestMetadata = findOfficialBatchRequestMetadata(bindings, batch);
            if (requestMetadata == null) {
                logInfo("Native multi-comment translation has no request metadata yet");
                return 0;
            }
            batch.translateBatch.invoke(null, comments, requestMetadata, false);
            rememberOfficialTranslationRequests(comments, awemeId, bridge);
            return comments.size();
        } catch (ReflectiveOperationException | RuntimeException error) {
            logOfficialTranslationFailure("Unable to request native multi-comment translation", error);
            return 0;
        }
    }

    private int resetOfficialLoadedComments(
            Object actionBar,
            String awemeId,
            OfficialTranslationBridge bridge) {
        BatchTranslationBridge batch = bridge.batch;
        if (batch == null) {
            return 0;
        }
        try {
            List<Object> comments = getOfficialLoadedComments(actionBar, awemeId, bridge);
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
            if (binding.comment.get() == null || binding.action.get() == null) {
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

    private static final class PhotoUiRestoreTarget {
        final WeakReference<Object> owner;
        final PhotoClearEventBridge bridge;
        final Method method;
        final Object event;

        PhotoUiRestoreTarget(
                Object owner,
                PhotoClearEventBridge bridge,
                Method method,
                Object event
        ) {
            this.owner = new WeakReference<>(owner);
            this.bridge = bridge;
            this.method = method;
            this.event = event;
        }
    }

    private static final class PhotoStateRestoreTarget {
        final WeakReference<Object> owner;
        final Method method;
        final String source;

        PhotoStateRestoreTarget(Object owner, Method method, String source) {
            this.owner = new WeakReference<>(owner);
            this.method = method;
            this.source = source;
        }
    }

    /**
     * Reflects a TikTok ClearMode event (for example X.0RG4 in the photo pager or X.0RMM in the
     * skylight overlay) so the module can replay an original exit event while the latch is on.
     */
    private static final class PhotoClearEventBridge {
        final Constructor<?> constructor;
        final Field clean;
        final Field kind;
        final Field source;
        final Field page;
        final Field extra;
        final Field anchor;
        final int constructorArity;

        private PhotoClearEventBridge(
                Constructor<?> constructor,
                int constructorArity,
                Field clean,
                Field kind,
                Field source,
                Field page,
                Field extra,
                Field anchor
        ) {
            this.constructor = constructor;
            this.constructorArity = constructorArity;
            this.clean = clean;
            this.kind = kind;
            this.source = source;
            this.page = page;
            this.extra = extra;
            this.anchor = anchor;
        }

        static PhotoClearEventBridge create(Class<?> eventType)
                throws ReflectiveOperationException {
            Constructor<?> constructor = null;
            int arity = 0;
            for (int candidateArity : new int[]{5, 4, 2}) {
                try {
                    if (candidateArity == 5) {
                        constructor = eventType.getDeclaredConstructor(
                                boolean.class,
                                int.class,
                                String.class,
                                String.class,
                                String.class);
                    } else if (candidateArity == 4) {
                        constructor = eventType.getDeclaredConstructor(
                                boolean.class,
                                int.class,
                                String.class,
                                String.class);
                    } else {
                        constructor = eventType.getDeclaredConstructor(
                                int.class,
                                String.class);
                    }
                    arity = candidateArity;
                    break;
                } catch (NoSuchMethodException ignored) {
                    // Try the next known ClearMode constructor shape.
                }
            }
            if (constructor == null) {
                throw new NoSuchMethodException(
                        "ClearMode event constructor on " + eventType.getName());
            }
            Field clean = eventType.getDeclaredField("LIZ");
            Field kind = eventType.getDeclaredField("LIZIZ");
            Field source = eventType.getDeclaredField("LIZJ");
            Field page = eventType.getDeclaredField("LIZLLL");
            Field extra = eventType.getDeclaredField("LJ");
            Field anchor = eventType.getDeclaredField("LJFF");
            constructor.setAccessible(true);
            clean.setAccessible(true);
            kind.setAccessible(true);
            source.setAccessible(true);
            page.setAccessible(true);
            extra.setAccessible(true);
            anchor.setAccessible(true);
            return new PhotoClearEventBridge(
                    constructor,
                    arity,
                    clean,
                    kind,
                    source,
                    page,
                    extra,
                    anchor);
        }

        boolean isClean(Object event) {
            try {
                return clean.getBoolean(event);
            } catch (IllegalAccessException | RuntimeException ignored) {
                return false;
            }
        }

        int kind(Object event) throws IllegalAccessException {
            return kind.getInt(event);
        }

        String source(Object event) throws IllegalAccessException {
            return (String) source.get(event);
        }

        Object copyWithClean(Object event, boolean cleanValue)
                throws ReflectiveOperationException {
            String sourceValue = source.get(event) instanceof String
                    ? (String) source.get(event) : "";
            String pageValue = page.get(event) instanceof String
                    ? (String) page.get(event) : "";
            String extraValue = extra.get(event) instanceof String
                    ? (String) extra.get(event) : "";
            Object replacement;
            if (constructorArity == 5) {
                replacement = constructor.newInstance(
                        cleanValue,
                        kind.getInt(event),
                        sourceValue,
                        pageValue,
                        extraValue);
            } else if (constructorArity == 4) {
                replacement = constructor.newInstance(
                        cleanValue,
                        kind.getInt(event),
                        sourceValue,
                        pageValue);
            } else {
                replacement = constructor.newInstance(
                        kind.getInt(event),
                        sourceValue);
            }
            Object anchorValue = anchor.get(event);
            if (anchorValue != null) {
                anchor.set(replacement, anchorValue);
            }
            return replacement;
        }
    }

    private static final class PhotoTitleVisibilityBridge {
        final Class<?> serviceType;
        final Method getServiceManager;
        final Method getService;
        final Method setVisible;

        PhotoTitleVisibilityBridge(
                Class<?> serviceType,
                Method getServiceManager,
                Method getService,
                Method setVisible
        ) {
            this.serviceType = serviceType;
            this.getServiceManager = getServiceManager;
            this.getService = getService;
            this.setVisible = setVisible;
        }

        boolean setVisible(boolean visible) throws ReflectiveOperationException {
            Object serviceManager = getServiceManager.invoke(null);
            Object service = getService.invoke(serviceManager, serviceType);
            if (service == null) {
                return false;
            }
            setVisible.invoke(service, visible);
            return true;
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
                BatchTranslationBridge batch) {
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

        BatchTranslationBridge(
                Class<?> requestType,
                Method getFragment,
                Method getScope,
                Method getListAbility,
                Method getLoadedComments,
                Method translateBatch,
                Method resetBatch,
                Field requestMetadata) {
            this.requestType = requestType;
            this.getFragment = getFragment;
            this.getScope = getScope;
            this.getListAbility = getListAbility;
            this.getLoadedComments = getLoadedComments;
            this.translateBatch = translateBatch;
            this.resetBatch = resetBatch;
            this.requestMetadata = requestMetadata;
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

    /**
     * Official TikTok 46.3.3 writes a saved item through ContentResolver with
     * RELATIVE_PATH set to DCIM/Camera. Intercept this stable framework boundary to apply the
     * configured media directory.
     */
    private void installOfficialDownloadLocationHook(ModuleConfig config) {
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
        logInfo("Official download location hook installed (" + installed + " insert variants)");
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

    /**
     * Latches TikTok's own temporary two-finger clean state without consuming MotionEvents.
     */
    private void installAntiBurnIn(ClassLoader classLoader, Context context) {
        antiBurnInTraceBudget.set(8);
        float density = context.getResources().getDisplayMetrics().density;
        if (Float.isFinite(density) && density > 0f) {
            antiBurnInCenterTolerancePx = 12f * density;
            antiBurnInSpanTolerancePx = 4f * density;
        }
        logInfo("Anti-burn-in stage: toast");
        installAntiBurnInToastBridge(classLoader);
        logInfo("Anti-burn-in stage: photo-gesture");
        boolean photoGestureAvailable = installAntiBurnInPhotoGesture(classLoader);
        logInfo("Anti-burn-in stage: pinch");
        boolean pinchBridgeAvailable = installAntiBurnInPinchBridge(classLoader);
        logInfo("Anti-burn-in stage: gesture");
        boolean gestureAvailable = pinchBridgeAvailable && installAntiBurnInGesture();
        logInfo("Anti-burn-in stage: clear-state");
        boolean clearStateAvailable = installAntiBurnInClearStateGate(classLoader);
        logInfo("Anti-burn-in stage: cell");
        installAntiBurnInCellCleanBridge(classLoader);
        logInfo("Anti-burn-in stage: panel");
        installAntiBurnInPanelCleanBridge(classLoader);
        logInfo("Core anti-burn-in bridges installed; scheduling remaining gates");
        antiBurnInHandler.postDelayed(
                () -> installDeferredAntiBurnInGates(
                        classLoader,
                        photoGestureAvailable,
                        pinchBridgeAvailable,
                        gestureAvailable,
                        clearStateAvailable),
                15_000L);
    }

    /**
     * The remaining gates load feed/photo UI classes that TikTok touches heavily during startup.
     * Installing them on the main looper shortly after launch keeps the module init thread short
     * and avoids TikTok startup contention.
     */
    private void installDeferredAntiBurnInGates(
            ClassLoader classLoader,
            boolean photoGestureAvailable,
            boolean pinchBridgeAvailable,
            boolean gestureAvailable,
            boolean clearStateAvailable
    ) {
        try {
            logInfo("Anti-burn-in stage: video");
            boolean videoVisibilityAvailable = installAntiBurnInVideoVisibilityGate(classLoader);
            logInfo("Anti-burn-in stage: pause");
            boolean pausePanelAvailable = installAntiBurnInPausePanelGate(classLoader);
            logInfo("Anti-burn-in stage: photo-ui");
            boolean photoUiGateAvailable = installAntiBurnInPhotoUiGate(classLoader);
            logInfo("Anti-burn-in stage: photo-state");
            boolean photoStateAvailable = installAntiBurnInPhotoStateGate(classLoader);
            logInfo("Anti-burn-in bridges: cell=" + (antiBurnInCellCleanMethod != null)
                    + ", panel=" + (antiBurnInPanelCleanMethod != null)
                    + ", clearState=" + clearStateAvailable
                    + ", video=" + videoVisibilityAvailable
                    + ", pause=" + pausePanelAvailable
                    + ", pinch=" + pinchBridgeAvailable
                    + ", photoGesture=" + photoGestureAvailable
                    + ", photoUi=" + photoUiGateAvailable
                    + ", photoState=" + photoStateAvailable
                    + ", mediaUi=cell-clean"
                    + ", gesture=" + gestureAvailable);
        } catch (Throwable error) {
            logAntiBurnInFailure("Unable to finish anti-burn-in gate installation", error);
        }
    }

    /** Keeps TikTok's own ClearMode state true while the gesture latch is enabled. */
    private boolean installAntiBurnInClearStateGate(ClassLoader classLoader) {
        try {
            Class<?> type = Class.forName("X.0Qjw", false, classLoader);
            Method query = findMethodInHierarchy(type, "LIZLLL");
            if (query == null || (query.getReturnType() != boolean.class
                    && query.getReturnType() != Boolean.class)) {
                throw new NoSuchMethodException("X.0Qjw#LIZLLL()");
            }
            query.setAccessible(true);
            hook(query)
                    .setId("toki-anti-burn-in-clear-state")
                    .intercept(chain -> antiBurnInDesiredState
                            ? Boolean.TRUE
                            : chain.proceed());
            return true;
        } catch (ClassNotFoundException | NoSuchMethodException error) {
            logInfo("TikTok clear-mode state gate is unavailable: " + error.getMessage());
            return false;
        } catch (Throwable error) {
            logAntiBurnInFailure("Unable to hook TikTok clear-mode state", error);
            return false;
        }
    }

    private void installAntiBurnInCellCleanBridge(ClassLoader classLoader) {
        try {
            Class<?> type = Class.forName(
                    "com.ss.android.ugc.feed.platform.cell.clean.CellCleanComponent",
                    false,
                    classLoader);
            Method clean = findCleanVisibilityMethod(type, "El", "Il", "Ye", "Xe");
            if (clean == null) {
                throw new NoSuchMethodException("CellCleanComponent clean method");
            }
            Method onViewCreated = findLifecycleViewMethod(type, "onViewCreated", "lf", "ue");
            Method onBind = null;
            for (Method method : type.getDeclaredMethods()) {
                Class<?>[] parameters = method.getParameterTypes();
                if ("onBind".equals(method.getName())
                        && method.getReturnType() == void.class
                        && parameters.length == 1
                        && !parameters[0].isPrimitive()) {
                    onBind = method;
                    break;
                }
            }
            clean.setAccessible(true);
            antiBurnInCellCleanMethod = clean;
            antiBurnInCellCleanModeArgument = findCleanBooleanArgumentIndex(clean);

            hook(clean)
                    .setId("toki-anti-burn-in-cell-clean-gate")
                    .intercept(chain -> {
                        if (antiBurnInDesiredState) {
                            Object[] arguments = new Object[clean.getParameterCount()];
                            for (int i = 0; i < arguments.length; i++) {
                                arguments[i] = chain.getArg(i);
                            }
                            Object requestedClean =
                                    arguments[antiBurnInCellCleanModeArgument];
                            arguments[antiBurnInCellCleanModeArgument] = Boolean.TRUE;
                            forceCellCleanImmediately(clean, arguments);
                            if (!Boolean.TRUE.equals(requestedClean)) {
                                traceAntiBurnInCleanGate("cell", arguments[1], requestedClean);
                            }
                            return chain.proceed(arguments);
                        }
                        return chain.proceed();
                    });
            if (onViewCreated != null) {
                onViewCreated.setAccessible(true);
                hook(onViewCreated)
                        .setId("toki-anti-burn-in-cell-clean-created")
                        .intercept(chain -> {
                            Object result = chain.proceed();
                            Object component = chain.getThisObject();
                            if (antiBurnInDesiredState) {
                                invokeAntiBurnInCellClean(component, true);
                            }
                            return result;
                        });
            }
            if (onBind != null) {
                onBind.setAccessible(true);
                hook(onBind)
                        .setId("toki-anti-burn-in-cell-clean-bind")
                        .intercept(chain -> {
                            Object result = chain.proceed();
                            if (antiBurnInDesiredState) {
                                invokeAntiBurnInCellClean(chain.getThisObject(), true);
                            }
                            return result;
                        });
            }
            logInfo("Cell clean lifecycle hooks: created=" + (onViewCreated != null)
                    + ", bind=" + (onBind != null));
        } catch (ClassNotFoundException | NoSuchMethodException error) {
            logInfo("Cell clean bridge is unavailable: " + error.getMessage());
        } catch (Throwable error) {
            logAntiBurnInFailure("Unable to hook TikTok cell clean state", error);
        }
    }

    /**
     * The panel clean method name varies across supported official builds. Guarding this owner
     * prevents the top navigation and feed chrome from being restored by pause/page callbacks.
     */
    private void installAntiBurnInPanelCleanBridge(ClassLoader classLoader) {
        try {
            Class<?> type = Class.forName(
                    "com.ss.android.ugc.feed.platform.panel.clean.FeedCleanComponent",
                    false,
                    classLoader);
            Method clean = findCleanVisibilityMethod(type, "El", "Il", "Ye", "Xe");
            if (clean == null) {
                throw new NoSuchMethodException("FeedCleanComponent clean method");
            }
            Method onViewCreated = findLifecycleViewMethod(type, "onViewCreated", "lf", "ue");
            clean.setAccessible(true);
            antiBurnInPanelCleanMethod = clean;
            antiBurnInPanelCleanModeArgument = findCleanBooleanArgumentIndex(clean);
            hook(clean)
                    .setId("toki-anti-burn-in-panel-clean-gate")
                    .intercept(chain -> {
                        if (antiBurnInDesiredState) {
                            Object[] arguments = new Object[clean.getParameterCount()];
                            for (int i = 0; i < arguments.length; i++) {
                                arguments[i] = chain.getArg(i);
                            }
                            Object requestedClean =
                                    arguments[antiBurnInPanelCleanModeArgument];
                            arguments[antiBurnInPanelCleanModeArgument] = Boolean.TRUE;
                            forceFeedCleanImmediately(clean, arguments);
                            if (!Boolean.TRUE.equals(requestedClean)) {
                                traceAntiBurnInCleanGate("panel", arguments[1], requestedClean);
                            }
                            return chain.proceed(arguments);
                        }
                        return chain.proceed();
                    });
            if (onViewCreated != null) {
                onViewCreated.setAccessible(true);
                hook(onViewCreated)
                        .setId("toki-anti-burn-in-panel-clean-created")
                        .intercept(chain -> {
                            Object result = chain.proceed();
                            Object component = chain.getThisObject();
                            if (antiBurnInDesiredState) {
                                invokeAntiBurnInPanelClean(component, true);
                            }
                            return result;
                        });
            }
        } catch (ClassNotFoundException | NoSuchMethodException error) {
            logInfo("Feed panel clean bridge is unavailable: " + error.getMessage());
        } catch (Throwable error) {
            logAntiBurnInFailure("Unable to hook TikTok feed panel clean state", error);
        }
    }

    /** Final visibility sink used by every ClearModePanel enter/exit path. */
    private boolean installAntiBurnInVideoVisibilityGate(ClassLoader classLoader) {
        try {
            Class<?> type = Class.forName(
                    "com.ss.android.ugc.aweme.feed.adapter.VideoViewCell",
                    false,
                    classLoader);
            Method visibility = findMethodInHierarchy(
                    type,
                    new String[]{"LLJJIII", "ot"},
                    boolean.class,
                    boolean.class);
            if (visibility == null || visibility.getReturnType() != void.class) {
                throw new NoSuchMethodException("VideoViewCell visibility method");
            }
            visibility.setAccessible(true);
            antiBurnInVideoVisibilityMethod = visibility;
            hook(visibility)
                    .setId("toki-anti-burn-in-video-visibility-gate")
                    .intercept(chain -> {
                        Object cell = chain.getThisObject();
                        if (!antiBurnInDesiredState) {
                            return chain.proceed();
                        }
                        rememberAntiBurnInHiddenVideoCell(cell);
                        if (Boolean.TRUE.equals(chain.getArg(0))) {
                            return chain.proceed();
                        }
                        traceAntiBurnInCleanGate("video", chain.getArg(1), chain.getArg(0));
                        return chain.proceed(new Object[]{Boolean.TRUE, chain.getArg(1)});
                    });

            ArrayList<Method> lifecycleMethods = new ArrayList<>();
            boolean reusedBindFound = false;
            for (Method method : type.getDeclaredMethods()) {
                Class<?>[] parameters = method.getParameterTypes();
                boolean holderSelected = "LJIIZILJ".equals(method.getName())
                        && parameters.length == 1
                        && parameters[0] == int.class;
                boolean holderState = "LLLILZLLLI".equals(method.getName())
                        && parameters.length == 1
                        && parameters[0] == int.class;
                boolean reusedBind = "LLLLLJIL".equals(method.getName())
                        && parameters.length == 3
                        && !parameters[0].isPrimitive()
                        && parameters[1] == int.class
                        && parameters[2] == boolean.class;
                if ((!holderSelected && !holderState && !reusedBind)
                        || method.getReturnType() != void.class
                        || method.isSynthetic()) {
                    continue;
                }
                lifecycleMethods.add(method);
                reusedBindFound |= reusedBind;
            }
            if (!reusedBindFound) {
                for (Method method : type.getDeclaredMethods()) {
                    Class<?>[] parameters = method.getParameterTypes();
                    if (method.getReturnType() == void.class
                            && parameters.length == 3
                            && !parameters[0].isPrimitive()
                            && parameters[1] == int.class
                            && parameters[2] == boolean.class
                            && !method.isSynthetic()) {
                        lifecycleMethods.add(method);
                        break;
                    }
                }
            }

            StringBuilder lifecycleNames = new StringBuilder();
            int lifecycleIndex = 0;
            for (Method method : lifecycleMethods) {
                method.setAccessible(true);
                String lifecycleName = method.getName();
                hook(method)
                        .setId("toki-anti-burn-in-video-lifecycle-" + lifecycleIndex)
                        .intercept(chain -> {
                            Object result = chain.proceed();
                            Object cell = chain.getThisObject();
                            if (antiBurnInDesiredState && cell != null) {
                                rememberAntiBurnInHiddenVideoCell(cell);
                                reapplyAntiBurnInVideoCell(
                                        cell,
                                        visibility,
                                        lifecycleName);
                                scheduleAntiBurnInVideoCellReapply(
                                        cell,
                                        visibility);
                            } else if (cell != null
                                    && isAntiBurnInHiddenVideoCell(cell)
                                    && restoreAntiBurnInVideoCell(
                                    cell,
                                    visibility,
                                    lifecycleName)) {
                                forgetAntiBurnInHiddenVideoCell(cell);
                            }
                            return result;
                        });
                if (lifecycleNames.length() > 0) {
                    lifecycleNames.append(',');
                }
                lifecycleNames.append(lifecycleName);
                lifecycleIndex++;
            }
            logInfo("Video cell clean lifecycle hooks installed: "
                    + lifecycleMethods.size() + " (" + lifecycleNames + ")");
            return true;
        } catch (ClassNotFoundException | NoSuchMethodException error) {
            logInfo("Video visibility gate is unavailable: " + error.getMessage());
            return false;
        } catch (Throwable error) {
            logAntiBurnInFailure("Unable to hook TikTok video visibility", error);
            return false;
        }
    }

    /** Stops the pause panel's delayed 300 ms restore after comments and other overlays close. */
    private boolean installAntiBurnInPausePanelGate(ClassLoader classLoader) {
        try {
            Class<?> type = Class.forName(
                    "com.ss.android.ugc.feed.platform.panel.pause.PausePanelComponent",
                    false,
                    classLoader);
            Method resume = findMethodInHierarchy(
                    type,
                    new String[]{"ap", "Io"},
                    boolean.class);
            if (resume == null || resume.getReturnType() != void.class) {
                throw new NoSuchMethodException("PausePanelComponent restore method");
            }
            resume.setAccessible(true);
            antiBurnInPauseResumeMethod = resume;
            hook(resume)
                    .setId("toki-anti-burn-in-pause-panel-gate")
                    .intercept(chain -> {
                        if (!antiBurnInDesiredState) {
                            return chain.proceed();
                        }
                        Object panel = chain.getThisObject();
                        if (panel != null) {
                            synchronized (antiBurnInLock) {
                                antiBurnInPausePanels.put(panel, Boolean.TRUE);
                            }
                        }
                        traceAntiBurnInCleanGate("pause", chain.getArg(0), Boolean.FALSE);
                        return null;
                    });
            return true;
        } catch (ClassNotFoundException | NoSuchMethodException error) {
            logInfo("Pause panel gate is unavailable: " + error.getMessage());
            return false;
        } catch (Throwable error) {
            logAntiBurnInFailure("Unable to hook TikTok pause panel restore", error);
            return false;
        }
    }

    private void installAntiBurnInToastBridge(ClassLoader classLoader) {
        try {
            Class<?> builderType = Class.forName(
                    "com.ss.android.ugc.aweme.services.uikit.CreativeToastBuilder",
                    false,
                    classLoader);
            Class<?> toastType = findClass(classLoader, "X.0wq4", "X.C1795960wq4");
            antiBurnInToastBuilderConstructor = builderType.getDeclaredConstructor();
            antiBurnInToastMessageMethod = builderType.getMethod("message", String.class);
            try {
                antiBurnInToastLegacyMethod = builderType.getMethod(
                        "isTuxToastLegacy", boolean.class);
            } catch (NoSuchMethodException ignored) {
                antiBurnInToastLegacyMethod = null;
            }
            antiBurnInToastShowMethod = toastType.getDeclaredMethod(
                    "LIZLLL", View.class, int.class, builderType);
            antiBurnInToastBuilderConstructor.setAccessible(true);
            antiBurnInToastMessageMethod.setAccessible(true);
            if (antiBurnInToastLegacyMethod != null) {
                antiBurnInToastLegacyMethod.setAccessible(true);
            }
            antiBurnInToastShowMethod.setAccessible(true);
        } catch (ClassNotFoundException | NoSuchMethodException error) {
            logInfo("TikTok native toast bridge is unavailable: " + error.getMessage());
        } catch (Throwable error) {
            logAntiBurnInFailure("Unable to prepare TikTok native toast bridge", error);
        }
    }

    private boolean installAntiBurnInPinchBridge(ClassLoader classLoader) {
        try {
            Class<?> type = Class.forName(
                    "com.ss.android.ugc.feed.platform.cell.pinch.PinchComponent",
                    false,
                    classLoader);
            Method clean = findMethodInHierarchy(
                    type,
                    new String[]{"Sp", "Ap", "Mf", "zf"},
                    long.class,
                    boolean.class,
                    boolean.class);
            Field detector = findDetectorField(type);
            if (clean == null || clean.getReturnType() != void.class || detector == null) {
                throw new NoSuchMethodException("PinchComponent clean/detector fields");
            }
            clean.setAccessible(true);
            detector.setAccessible(true);
            antiBurnInDetectorField = detector;

            hook(clean)
                    .setId("toki-anti-burn-in-clean-latch")
                    .intercept(chain -> {
                        if (!antiBurnInDesiredState) {
                            return chain.proceed();
                        }
                        Object requestedClean = chain.getArg(1);
                        Object requestedImmediately = chain.getArg(2);
                        if (Boolean.TRUE.equals(requestedClean)
                                && Boolean.TRUE.equals(requestedImmediately)) {
                            return chain.proceed();
                        }
                        Object[] arguments = new Object[clean.getParameterCount()];
                        for (int i = 0; i < arguments.length; i++) {
                            arguments[i] = chain.getArg(i);
                        }
                        arguments[1] = Boolean.TRUE;
                        arguments[2] = Boolean.TRUE;
                        traceAntiBurnInPinchGate(requestedClean, requestedImmediately);
                        return chain.proceed(arguments);
                    });
            return true;
        } catch (ClassNotFoundException | NoSuchMethodException error) {
            logInfo("Official pinch clean bridge is unavailable: " + error.getMessage());
            return false;
        } catch (Throwable error) {
            logAntiBurnInFailure("Unable to hook official pinch clean bridge", error);
            return false;
        }
    }

    /**
     * Keeps photo chrome (pager overlays, page dots, skylight bubbles and top title tabs) hidden
     * while the latch is enabled. TikTok's own ClearMode events are replayed on exit so the
     * photo UI returns exactly to its previous state.
     */
    private boolean installAntiBurnInPhotoUiGate(ClassLoader classLoader) {
        try {
            Class<?> stateType = findClass(classLoader, "X.0SHn", "X.0Rsh");
            Class<?> serviceManagerType = Class.forName(
                    "com.ss.android.ugc.aweme.framework.services.ServiceManager",
                    false,
                    classLoader);
            Class<?> homePageUiFrameServiceType = Class.forName(
                    "com.ss.android.ugc.aweme.homepage.api.ui.HomePageUIFrameService",
                    false,
                    classLoader);
            Method getServiceManager = serviceManagerType.getMethod("get");
            Method getService = serviceManagerType.getMethod("getService", Class.class);
            Method setTitleTabVisibility = homePageUiFrameServiceType.getMethod(
                    "setTitleTabVisibility",
                    boolean.class);
            antiBurnInPhotoTitleVisibilityBridge = new PhotoTitleVisibilityBridge(
                    homePageUiFrameServiceType,
                    getServiceManager,
                    getService,
                    setTitleTabVisibility);

            Class<?> holderType = Class.forName(
                    "com.ss.android.ugc.aweme.ui.feed.subphoto.holders.PhotosViewHolderV3",
                    false,
                    classLoader);
            Method update = findMethodInHierarchy(
                    holderType,
                    new String[]{"LJIJJLI", "LIZJ"},
                    float.class,
                    boolean.class,
                    stateType,
                    float.class,
                    String.class);
            if (update == null) {
                throw new NoSuchMethodException(
                        "PhotosViewHolderV3 clear-mode update method");
            }
            update.setAccessible(true);
            hook(update)
                    .setId("toki-anti-burn-in-photo-title-gate")
                    .intercept(chain -> {
                        Object result = chain.proceed();
                        if (antiBurnInDesiredState) {
                            setAntiBurnInPhotoTitleVisibility(false);
                        }
                        return result;
                    });

            Class<?> photoEventType = findClass(classLoader, "X.0RMM", "X.0RG4");
            PhotoClearEventBridge photoBridge = PhotoClearEventBridge.create(photoEventType);
            Class<?> pagerType = Class.forName(
                    "com.ss.android.ugc.aweme.ui.feed.photos.assem.PhotoSlideViewPagerComponent",
                    false,
                    classLoader);
            Method clearModeUpdate = pagerType.getDeclaredMethod(
                    "onClearModeEvent",
                    photoEventType);
            Field pendingPageEventField = pagerType.getDeclaredField("LLLIIL");
            clearModeUpdate.setAccessible(true);
            pendingPageEventField.setAccessible(true);
            hook(clearModeUpdate)
                    .setId("toki-anti-burn-in-photo-pager-ui-gate")
                    .intercept(chain -> {
                        Object event = chain.getArg(0);
                        if (!antiBurnInDesiredState
                                || event == null
                                || photoBridge.isClean(event)) {
                            return chain.proceed();
                        }
                        try {
                            Object pager = chain.getThisObject();
                            rememberAntiBurnInPhotoUiRestoreTarget(
                                    photoBridge,
                                    pager,
                                    clearModeUpdate,
                                    event);
                            Object replacement = photoBridge.copyWithClean(event, true);
                            if (pendingPageEventField.get(pager) == event) {
                                pendingPageEventField.set(pager, replacement);
                            }
                            traceAntiBurnInPhotoEventGate(
                                    photoBridge.kind(event),
                                    photoBridge.source(event));
                            return chain.proceed(new Object[]{replacement});
                        } catch (Throwable error) {
                            logAntiBurnInFailure(
                                    "Unable to preserve photo pager UI state",
                                    error);
                            return chain.proceed();
                        }
                    });

            int subscriberCount = 1;
            subscriberCount += installAntiBurnInPhotoUiSubscriber(
                    classLoader,
                    photoEventType,
                    photoBridge,
                    "com.ss.android.ugc.aweme.ui.feed.photos.assem.AbsPhotosDotIndicatorAssem",
                    "dots") ? 1 : 0;
            subscriberCount += installAntiBurnInPhotoUiSubscriber(
                    classLoader,
                    photoEventType,
                    photoBridge,
                    "com.ss.android.ugc.aweme.base.ui.assem.FeedSkylightBubbleAssem",
                    "skylight-bubble") ? 1 : 0;
            subscriberCount += installAntiBurnInPhotoUiSubscriber(
                    classLoader,
                    photoEventType,
                    photoBridge,
                    "com.ss.android.ugc.aweme.base.ui.assem.FYPSkylightDrawerAssem",
                    "skylight-drawer") ? 1 : 0;
            logInfo("Photo UI clear subscribers installed: " + subscriberCount + "/4");
            return true;
        } catch (ClassNotFoundException | NoSuchFieldException | NoSuchMethodException error) {
            logInfo("Photo UI clean gate is unavailable: " + error.getMessage());
            return false;
        } catch (Throwable error) {
            logAntiBurnInFailure("Unable to hook photo UI clean state", error);
            return false;
        }
    }

    private boolean installAntiBurnInPhotoUiSubscriber(
            ClassLoader classLoader,
            Class<?> eventType,
            PhotoClearEventBridge eventBridge,
            String className,
            String id
    ) {
        try {
            Class<?> type = Class.forName(className, false, classLoader);
            Method update = type.getDeclaredMethod("onClearModeEvent", eventType);
            update.setAccessible(true);
            hook(update)
                    .setId("toki-anti-burn-in-photo-ui-" + id)
                    .intercept(chain -> {
                        Object event = chain.getArg(0);
                        if (!antiBurnInDesiredState
                                || event == null
                                || eventBridge.isClean(event)) {
                            return chain.proceed();
                        }
                        try {
                            rememberAntiBurnInPhotoUiRestoreTarget(
                                    eventBridge,
                                    chain.getThisObject(),
                                    update,
                                    event);
                            return chain.proceed(new Object[]{
                                    eventBridge.copyWithClean(event, true)
                            });
                        } catch (Throwable error) {
                            logAntiBurnInFailure(
                                    "Unable to preserve photo UI subscriber " + id,
                                    error);
                            return chain.proceed();
                        }
                    });
            return true;
        } catch (ClassNotFoundException | NoSuchMethodException error) {
            logInfo("Photo UI subscriber is unavailable (" + id + "): " + error.getMessage());
            return false;
        } catch (Throwable error) {
            logAntiBurnInFailure("Unable to hook photo UI subscriber " + id, error);
            return false;
        }
    }

    /**
     * Drives TikTok's own photo clear-mode owner. The photo slideshow does not enter or exit
     * clear mode through PinchComponent; it uses PhotoGestureInterceptComponent#pp(String,
     * boolean), which posts the photo ClearMode events consumed by the pager and dots.
     */
    private boolean installAntiBurnInPhotoStateGate(ClassLoader classLoader) {
        try {
            Class<?> type = Class.forName(
                    "com.ss.android.ugc.aweme.ui.feed.photos.assem.PhotoGestureInterceptComponent",
                    false,
                    classLoader);
            Method onViewCreated = findLifecycleViewMethod(type, "onViewCreated", "lf", "ue");
            Method onTouchEvent = findMethodInHierarchy(
                    type,
                    "onTouchEvent",
                    MotionEvent.class);
            Method pp = findMethodInHierarchy(
                    type,
                    new String[]{"Zo", "pp"},
                    String.class,
                    boolean.class);
            if (pp == null || pp.getReturnType() != void.class) {
                throw new NoSuchMethodException(
                        "PhotoGestureInterceptComponent#Zo/String, boolean)");
            }
            pp.setAccessible(true);
            antiBurnInPhotoStateMethod = pp;
            if (onTouchEvent != null) {
                onTouchEvent.setAccessible(true);
                hook(onTouchEvent)
                        .setId("toki-anti-burn-in-photo-state-touch")
                        .intercept(chain -> {
                            Object result = chain.proceed();
                            rememberAntiBurnInPhotoIntercept(chain.getThisObject());
                            return result;
                        });
            }
            if (onViewCreated != null) {
                onViewCreated.setAccessible(true);
                hook(onViewCreated)
                        .setId("toki-anti-burn-in-photo-state-created")
                        .intercept(chain -> {
                            Object result = chain.proceed();
                            rememberAntiBurnInPhotoIntercept(chain.getThisObject());
                            return result;
                        });
            }
            hook(pp)
                    .setId("toki-anti-burn-in-photo-state-gate")
                    .intercept(chain -> {
                        Object component = chain.getThisObject();
                        rememberAntiBurnInPhotoIntercept(component);
                        if (!antiBurnInDesiredState
                                || Boolean.TRUE.equals(chain.getArg(1))) {
                            return chain.proceed();
                        }
                        String source = chain.getArg(0) instanceof String
                                ? (String) chain.getArg(0) : "pinch";
                        rememberAntiBurnInPhotoStateRestore(component, pp, source);
                        traceAntiBurnInPhotoStateGate(source);
                        return chain.proceed(new Object[]{
                                chain.getArg(0),
                                Boolean.TRUE
                        });
                    });
            logInfo("Photo state gate hooks: created=" + (onViewCreated != null)
                    + ", touch=" + (onTouchEvent != null));
            return true;
        } catch (ClassNotFoundException | NoSuchMethodException error) {
            logInfo("Photo state gate is unavailable: " + error.getMessage());
            return false;
        } catch (Throwable error) {
            logAntiBurnInFailure("Unable to hook TikTok photo state owner", error);
            return false;
        }
    }

    /** Observes the photo surface's own touch listener without replacing or consuming its events. */
    private boolean installAntiBurnInPhotoGesture(ClassLoader classLoader) {
        try {
            Class<?> touchType = findClass(
                    classLoader,
                    "X.0v32",
                    "X.0v2u",
                    "X.ViewOnTouchListenerC1727040v2u");
            Method touchDispatch = findPhotoTouchDispatch(touchType);
            if (touchDispatch == null) {
                throw new NoSuchMethodException(
                        "No MotionEvent dispatch on " + touchType.getName());
            }
            int motionEventArg = antiBurnInMotionEventArgumentIndex(touchDispatch);
            Field imageField = findViewField(
                    touchType,
                    "LLJILJIL",
                    "LLJ",
                    "LLIZLLLIL",
                    "LL");
            touchDispatch.setAccessible(true);
            if (imageField != null) {
                imageField.setAccessible(true);
            }

            hook(touchDispatch)
                    .setId("toki-anti-burn-in-photo-gesture")
                    .intercept(chain -> {
                        Object touch = chain.getThisObject();
                        MotionEvent event = chain.getArg(motionEventArg) instanceof MotionEvent
                                ? (MotionEvent) chain.getArg(motionEventArg)
                                : null;
                        int eventAction = event == null ? -1 : event.getActionMasked();
                        View toastAnchor = antiBurnInToastAnchor(touch, imageField);
                        Boolean targetState = observeAntiBurnInGesture(touch, event);
                        if (targetState != null) {
                            requestAntiBurnInState(targetState, toastAnchor);
                        }
                        Object result = chain.proceed();
                        if (eventAction == MotionEvent.ACTION_POINTER_DOWN) {
                            scheduleAntiBurnInLongPress(touch, toastAnchor);
                        }
                        return result;
                    });
            return true;
        } catch (ClassNotFoundException | NoSuchMethodException error) {
            logInfo("Photo-mode pinch bridge is unavailable: " + error.getMessage());
            return false;
        } catch (Throwable error) {
            logAntiBurnInFailure("Unable to hook photo-mode pinch gesture", error);
            return false;
        }
    }

    private static Method findPhotoTouchDispatch(Class<?> type) {
        Class<?> current = type;
        while (current != null) {
            try {
                Method onTouch = current.getDeclaredMethod(
                        "onTouch",
                        View.class,
                        MotionEvent.class);
                if (!onTouch.isSynthetic()) {
                    return onTouch;
                }
            } catch (NoSuchMethodException ignored) {
                // Continue with the legacy dispatch name or a superclass.
            }
            current = current.getSuperclass();
        }
        return findMotionEventMethodByName(type, "LJIILJJIL");
    }

    private static int antiBurnInMotionEventArgumentIndex(Method method) {
        Class<?>[] parameters = method.getParameterTypes();
        for (int i = 0; i < parameters.length; i++) {
            if (MotionEvent.class.isAssignableFrom(parameters[i])) {
                return i;
            }
        }
        return 0;
    }

    private boolean installAntiBurnInGesture() {
        try {
            Field detectorField = antiBurnInDetectorField;
            if (detectorField == null) {
                throw new NoSuchMethodException("Pinch detector field is unavailable");
            }
            Class<?> type = detectorField.getType();
            Method touch = findMotionEventMethod(type);
            if (touch == null) {
                throw new NoSuchMethodException("No MotionEvent method on " + type.getName());
            }
            Field toastAnchorField = findViewField(type, "LJIIZILJ", "LJIIL");
            touch.setAccessible(true);
            if (toastAnchorField != null) {
                toastAnchorField.setAccessible(true);
            }
            hook(touch)
                    .setId("toki-anti-burn-in-gesture-" + type.getName())
                    .intercept(chain -> {
                        Object detector = chain.getThisObject();
                        MotionEvent event = chain.getArg(0) instanceof MotionEvent
                                ? (MotionEvent) chain.getArg(0)
                                : null;
                        int eventAction = event == null ? -1 : event.getActionMasked();
                        View toastAnchor = antiBurnInToastAnchor(detector, toastAnchorField);
                        Boolean targetState = observeAntiBurnInGesture(detector, event);
                        if (targetState != null) {
                            requestAntiBurnInState(targetState, toastAnchor);
                        }
                        Object result = chain.proceed();
                        boolean officialAccepted = !(result instanceof Boolean)
                                || (Boolean) result;
                        if (!officialAccepted) {
                            cancelAntiBurnInGesture(detector);
                            return result;
                        }
                        if (eventAction == MotionEvent.ACTION_POINTER_DOWN) {
                            scheduleAntiBurnInLongPress(detector, toastAnchor);
                        }
                        return result;
                    });
            return true;
        } catch (NoSuchMethodException error) {
            logInfo("Official two-finger detector is unavailable: " + error.getMessage());
            return false;
        } catch (Throwable error) {
            logAntiBurnInFailure("Unable to hook official two-finger detector", error);
            return false;
        }
    }

    private Boolean observeAntiBurnInGesture(Object detector, MotionEvent event) {
        if (detector == null || event == null) {
            return null;
        }
        AntiBurnInGestureTracker tracker;
        synchronized (antiBurnInLock) {
            tracker = antiBurnInGestures.get(detector);
            if (tracker == null) {
                tracker = new AntiBurnInGestureTracker(
                        antiBurnInCenterTolerancePx,
                        antiBurnInSpanTolerancePx);
                antiBurnInGestures.put(detector, tracker);
            }

            try {
                int action = event.getActionMasked();
                int pointerCount = event.getPointerCount();
                switch (action) {
                    case MotionEvent.ACTION_DOWN:
                        cancelAntiBurnInGestureLocked(detector, tracker);
                        break;
                    case MotionEvent.ACTION_POINTER_DOWN:
                        if (pointerCount == 2) {
                            tracker.down(
                                    pointerCount,
                                    event.getEventTime(),
                                    event.getX(0),
                                    event.getY(0),
                                    event.getX(1),
                                    event.getY(1),
                                    antiBurnInDesiredState);
                        } else {
                            cancelAntiBurnInGestureLocked(detector, tracker);
                        }
                        break;
                    case MotionEvent.ACTION_MOVE:
                        if (pointerCount == 2) {
                            tracker.move(
                                    pointerCount,
                                    event.getEventTime(),
                                    event.getX(0),
                                    event.getY(0),
                                    event.getX(1),
                                    event.getY(1));
                            if (!tracker.isTracking()) {
                                removeAntiBurnInLongPressTaskLocked(detector);
                            }
                        } else {
                            cancelAntiBurnInGestureLocked(detector, tracker);
                        }
                        break;
                    case MotionEvent.ACTION_POINTER_UP:
                        if (pointerCount == 2) {
                            tracker.move(
                                    pointerCount,
                                    event.getEventTime(),
                                    event.getX(0),
                                    event.getY(0),
                                    event.getX(1),
                                    event.getY(1));
                            Boolean targetState = tracker.pointerUp(event.getEventTime());
                            removeAntiBurnInLongPressTaskLocked(detector);
                            return targetState;
                        }
                        cancelAntiBurnInGestureLocked(detector, tracker);
                        break;
                    case MotionEvent.ACTION_UP:
                    case MotionEvent.ACTION_CANCEL:
                        cancelAntiBurnInGestureLocked(detector, tracker);
                        break;
                    default:
                        break;
                }
            } catch (RuntimeException error) {
                cancelAntiBurnInGestureLocked(detector, tracker);
                logAntiBurnInFailure("Unable to inspect official touch event", error);
            }
        }
        return null;
    }

    private void cancelAntiBurnInGesture(Object detector) {
        synchronized (antiBurnInLock) {
            AntiBurnInGestureTracker tracker = antiBurnInGestures.get(detector);
            if (tracker != null) {
                cancelAntiBurnInGestureLocked(detector, tracker);
            }
        }
    }

    private void cancelAntiBurnInGestureLocked(
            Object detector,
            AntiBurnInGestureTracker tracker
    ) {
        tracker.cancel();
        removeAntiBurnInLongPressTaskLocked(detector);
    }

    private void removeAntiBurnInLongPressTaskLocked(Object detector) {
        Runnable task = antiBurnInLongPressTasks.remove(detector);
        if (task != null) {
            antiBurnInHandler.removeCallbacks(task);
        }
    }

    private void scheduleAntiBurnInLongPress(Object detector, View anchor) {
        if (detector == null) {
            return;
        }
        WeakReference<Object> detectorReference = new WeakReference<>(detector);
        WeakReference<View> anchorReference = new WeakReference<>(anchor);
        Runnable task = new Runnable() {
            @Override
            public void run() {
                Object activeDetector = detectorReference.get();
                if (activeDetector != null) {
                    completeScheduledAntiBurnInLongPress(
                            activeDetector,
                            anchorReference.get(),
                            this);
                }
            }
        };
        synchronized (antiBurnInLock) {
            AntiBurnInGestureTracker tracker = antiBurnInGestures.get(detector);
            if (tracker == null || !tracker.isTracking()) {
                return;
            }
            // Replacing the task invalidates a stale callback from an earlier pointer sequence.
            removeAntiBurnInLongPressTaskLocked(detector);
            antiBurnInLongPressTasks.put(detector, task);
        }
        if (!antiBurnInHandler.postDelayed(
                task,
                AntiBurnInGestureTracker.LONG_PRESS_MILLIS)) {
            synchronized (antiBurnInLock) {
                if (antiBurnInLongPressTasks.get(detector) == task) {
                    antiBurnInLongPressTasks.remove(detector);
                }
            }
        }
    }

    private void completeScheduledAntiBurnInLongPress(
            Object detector,
            View anchor,
            Runnable task
    ) {
        Boolean targetState;
        synchronized (antiBurnInLock) {
            if (antiBurnInLongPressTasks.get(detector) != task) {
                return;
            }
            antiBurnInLongPressTasks.remove(detector);
            AntiBurnInGestureTracker tracker = antiBurnInGestures.get(detector);
            targetState = tracker == null
                    ? null
                    : tracker.longPress(SystemClock.uptimeMillis());
        }
        if (targetState != null) {
            requestAntiBurnInState(targetState, anchor);
        }
    }

    private static View antiBurnInToastAnchor(Object detector, Field field) {
        if (detector == null || field == null) {
            return null;
        }
        try {
            Object value = field.get(detector);
            return value instanceof View ? (View) value : null;
        } catch (IllegalAccessException | RuntimeException ignored) {
            return null;
        }
    }

    private void requestAntiBurnInState(boolean enabled, View toastAnchor) {
        setAntiBurnInDesiredState(enabled);
        showAntiBurnInToast(toastAnchor, enabled);
    }

    /** Applies the latched state through TikTok's shared media clean-mode owners. */
    private void setAntiBurnInDesiredState(boolean enabled) {
        ArrayList<Object> pausePanels = new ArrayList<>();
        ArrayList<PhotoUiRestoreTarget> photoTargets = new ArrayList<>();
        ArrayList<PhotoStateRestoreTarget> photoStateTargets = new ArrayList<>();
        synchronized (antiBurnInLock) {
            antiBurnInDesiredState = enabled;
            antiBurnInTraceBudget.set(enabled ? 10 : 4);
            if (!enabled) {
                for (Object panel : antiBurnInPausePanels.keySet()) {
                    if (panel != null) {
                        pausePanels.add(panel);
                    }
                }
                antiBurnInPausePanels.clear();
                photoTargets.addAll(antiBurnInPhotoUiRestoreTargets.values());
                antiBurnInPhotoUiRestoreTargets.clear();
                photoStateTargets.addAll(antiBurnInPhotoStateRestoreTargets.values());
                antiBurnInPhotoStateRestoreTargets.clear();
            }
        }

        Runnable apply = () -> applyAntiBurnInDesiredState(
                enabled,
                pausePanels,
                photoTargets,
                photoStateTargets);
        if (Looper.myLooper() == Looper.getMainLooper()) {
            apply.run();
        } else {
            antiBurnInHandler.post(apply);
        }
    }

    private void applyAntiBurnInDesiredState(
            boolean enabled,
            List<Object> pausePanels,
            List<PhotoUiRestoreTarget> photoTargets,
            List<PhotoStateRestoreTarget> photoStateTargets
    ) {
        if (antiBurnInDesiredState != enabled) {
            return;
        }

        int restoredPausePanels = 0;
        int restoredPhotoOwners = 0;
        if (enabled) {
            setAntiBurnInPhotoTitleVisibility(false);
            driveAntiBurnInPhotoState(true);
        } else {
            if (antiBurnInPauseResumeMethod != null) {
                for (Object panel : pausePanels) {
                    try {
                        antiBurnInPauseResumeMethod.invoke(panel, false);
                        restoredPausePanels++;
                    } catch (Throwable error) {
                        logAntiBurnInFailure("Unable to restore TikTok pause panel", error);
                    }
                }
            }
            for (PhotoUiRestoreTarget target : photoTargets) {
                Object owner = target.owner.get();
                if (owner == null) {
                    continue;
                }
                try {
                    Object restoreEvent = target.bridge.copyWithClean(target.event, false);
                    target.method.invoke(owner, restoreEvent);
                    restoredPhotoOwners++;
                } catch (Throwable error) {
                    logAntiBurnInFailure("Unable to restore TikTok photo UI", error);
                }
            }
            for (PhotoStateRestoreTarget target : photoStateTargets) {
                Object owner = target.owner.get();
                if (owner == null) {
                    continue;
                }
                try {
                    target.method.invoke(owner, target.source, false);
                } catch (Throwable error) {
                    logAntiBurnInFailure("Unable to restore TikTok photo state", error);
                }
            }
            driveAntiBurnInPhotoState(false);
            setAntiBurnInPhotoTitleVisibility(true);
        }

        logInfo("Anti-burn-in mode changed: " + enabled
                + " pauseRestores=" + restoredPausePanels
                + " photoRestores=" + restoredPhotoOwners);
    }

    private void rememberAntiBurnInPhotoIntercept(Object component) {
        if (component == null) {
            return;
        }
        synchronized (antiBurnInLock) {
            antiBurnInPhotoIntercepts.put(component, Boolean.TRUE);
        }
    }

    private void rememberAntiBurnInPhotoStateRestore(
            Object component,
            Method pp,
            String source
    ) {
        if (component == null || pp == null) {
            return;
        }
        synchronized (antiBurnInLock) {
            antiBurnInPhotoStateRestoreTargets.put(
                    component,
                    new PhotoStateRestoreTarget(component, pp, source));
        }
    }

    /** Replays pp("toki", state) on every photo intercept owner seen so far. */
    private void driveAntiBurnInPhotoState(boolean enabled) {
        Method pp = antiBurnInPhotoStateMethod;
        if (pp == null) {
            return;
        }
        synchronized (antiBurnInLock) {
            for (Object component : antiBurnInPhotoIntercepts.keySet()) {
                if (component == null) {
                    continue;
                }
                try {
                    pp.invoke(component, "toki", enabled);
                } catch (Throwable error) {
                    logAntiBurnInFailure("Unable to drive TikTok photo clear state", error);
                }
            }
        }
    }

    private void rememberAntiBurnInPhotoUiRestoreTarget(
            PhotoClearEventBridge bridge,
            Object owner,
            Method method,
            Object event
    ) {
        if (bridge == null || owner == null || method == null || event == null) {
            return;
        }
        synchronized (antiBurnInLock) {
            antiBurnInPhotoUiRestoreTargets.put(
                    owner,
                    new PhotoUiRestoreTarget(owner, bridge, method, event));
        }
    }

    private void setAntiBurnInPhotoTitleVisibility(boolean visible) {
        PhotoTitleVisibilityBridge bridge = antiBurnInPhotoTitleVisibilityBridge;
        if (bridge == null || (visible && !antiBurnInPhotoTitleForcedHidden)) {
            return;
        }
        try {
            if (bridge.setVisible(visible)) {
                antiBurnInPhotoTitleForcedHidden = !visible;
            }
        } catch (Throwable error) {
            if (antiBurnInPhotoTitleFailureLogged.compareAndSet(false, true)) {
                logAntiBurnInFailure("Unable to update the photo title tab", error);
            }
        }
    }

    private void rememberAntiBurnInHiddenVideoCell(Object cell) {
        if (cell == null) {
            return;
        }
        synchronized (antiBurnInLock) {
            antiBurnInHiddenVideoCells.put(cell, Boolean.TRUE);
        }
    }

    private boolean isAntiBurnInHiddenVideoCell(Object cell) {
        synchronized (antiBurnInLock) {
            return antiBurnInHiddenVideoCells.containsKey(cell);
        }
    }

    private void forgetAntiBurnInHiddenVideoCell(Object cell) {
        synchronized (antiBurnInLock) {
            antiBurnInHiddenVideoCells.remove(cell);
        }
    }

    private boolean restoreAntiBurnInVideoCell(
            Object cell,
            Method visibility,
            String source
    ) {
        if (antiBurnInDesiredState || cell == null) {
            return false;
        }
        try {
            visibility.invoke(cell, false, false);
            traceAntiBurnInVideoRestore(source);
            return true;
        } catch (Throwable error) {
            logAntiBurnInFailure("Unable to restore TikTok video cell state", error);
            return false;
        }
    }

    private void reapplyAntiBurnInVideoCell(
            Object cell,
            Method visibility,
            String source
    ) {
        if (!antiBurnInDesiredState || cell == null) {
            return;
        }
        try {
            visibility.invoke(cell, true, false);
            traceAntiBurnInVideoLifecycle(source);
        } catch (Throwable error) {
            logAntiBurnInFailure("Unable to reapply TikTok video cell clean state", error);
        }
    }

    private void scheduleAntiBurnInVideoCellReapply(
            Object cell,
            Method visibility
    ) {
        WeakReference<Object> cellReference = new WeakReference<>(cell);
        antiBurnInHandler.post(() -> {
            Object activeCell = cellReference.get();
            if (activeCell != null) {
                reapplyAntiBurnInVideoCell(
                        activeCell,
                        visibility,
                        "next-loop");
            }
        });
    }

    private boolean invokeAntiBurnInCellClean(Object component, boolean clean) {
        Method method = antiBurnInCellCleanMethod;
        if (component == null || method == null) {
            return false;
        }
        try {
            method.invoke(component, antiBurnInCellCleanArguments(method, clean));
            return true;
        } catch (Throwable error) {
            logAntiBurnInFailure("Unable to apply TikTok cell clean state", error);
            return false;
        }
    }

    private boolean invokeAntiBurnInPanelClean(Object component, boolean clean) {
        Method method = antiBurnInPanelCleanMethod;
        if (component == null || method == null) {
            return false;
        }
        try {
            method.invoke(component, antiBurnInFeedCleanArguments(method, clean));
            return true;
        } catch (Throwable error) {
            logAntiBurnInFailure("Unable to apply TikTok feed panel clean state", error);
            return false;
        }
    }

    private void showAntiBurnInToast(View anchor, boolean enabled) {
        Constructor<?> constructor = antiBurnInToastBuilderConstructor;
        Method message = antiBurnInToastMessageMethod;
        Method legacy = antiBurnInToastLegacyMethod;
        Method show = antiBurnInToastShowMethod;
        if (anchor == null || constructor == null || message == null || show == null) {
            return;
        }
        try {
            Object builder = constructor.newInstance();
            message.invoke(builder, enabled ? "防烧屏已开启" : "防烧屏已关闭");
            if (legacy != null) {
                legacy.invoke(builder, true);
            }
            show.invoke(null, anchor, 3047, builder);
        } catch (Throwable error) {
            logAntiBurnInFailure("Unable to show anti-burn-in status toast", error);
        }
    }

    private static Class<?> findClass(ClassLoader classLoader, String... names)
            throws ClassNotFoundException {
        ClassNotFoundException failure = null;
        for (String name : names) {
            try {
                return Class.forName(name, false, classLoader);
            } catch (ClassNotFoundException error) {
                failure = error;
            }
        }
        throw failure == null ? new ClassNotFoundException() : failure;
    }

    /** Finds a private/generated field on a TikTok class or one of its Assem superclasses. */
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

    private static Method findMethodInHierarchy(
            Class<?> type,
            String name,
            Class<?>... parameters
    ) {
        return findMethodInHierarchy(type, new String[]{name}, parameters);
    }

    private static Method findMethodInHierarchy(
            Class<?> type,
            String[] names,
            Class<?>... parameters
    ) {
        Class<?> current = type;
        while (current != null) {
            for (String name : names) {
                try {
                    return current.getDeclaredMethod(name, parameters);
                } catch (NoSuchMethodException ignored) {
                    // Continue with the next known name or superclass.
                }
            }
            current = current.getSuperclass();
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

    private static Method findMethodByNameAndParameter(
            Class<?> type,
            Class<?> parameter,
            String... names
    ) {
        if (type == null || parameter == null) {
            return null;
        }
        Class<?> current = type;
        while (current != null) {
            Method fallback = null;
            for (String name : names) {
                for (Method method : current.getDeclaredMethods()) {
                    Class<?>[] parameters = method.getParameterTypes();
                    if (!method.getName().equals(name)
                            || parameters.length != 1
                            || !parameter.isAssignableFrom(parameters[0])
                            && !parameters[0].isAssignableFrom(parameter)) {
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

    private static Method findLifecycleViewMethod(
            Class<?> type,
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
                    Class<?>[] parameters = method.getParameterTypes();
                    if (!method.getName().equals(name)
                            || parameters.length != 1
                            || !View.class.isAssignableFrom(parameters[0])) {
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

    private static Method findMotionEventMethod(Class<?> type) {
        Method named = findMotionEventMethodByName(type, "LIZJ");
        if (named == null) {
            named = findMotionEventMethodByName(type, "LIZIZ");
        }
        if (named != null) {
            return named;
        }
        Class<?> current = type;
        while (current != null) {
            for (Method method : current.getDeclaredMethods()) {
                Class<?>[] parameters = method.getParameterTypes();
                if (parameters.length == 1
                        && MotionEvent.class.isAssignableFrom(parameters[0])
                        && (method.getReturnType() == boolean.class
                        || method.getReturnType() == void.class)) {
                    return method;
                }
            }
            current = current.getSuperclass();
        }
        return null;
    }

    private static Method findMotionEventMethodByName(Class<?> type, String name) {
        Class<?> current = type;
        while (current != null) {
            for (Method method : current.getDeclaredMethods()) {
                Class<?>[] parameters = method.getParameterTypes();
                if (name.equals(method.getName())
                        && parameters.length == 1
                        && MotionEvent.class.isAssignableFrom(parameters[0])
                        && (method.getReturnType() == boolean.class
                        || method.getReturnType() == void.class)) {
                    return method;
                }
            }
            current = current.getSuperclass();
        }
        return null;
    }

    private static Field findDetectorField(Class<?> type) {
        Field known = findField(type, "LLJLIL");
        if (known != null && findMotionEventMethod(known.getType()) != null) {
            return known;
        }
        Class<?> current = type;
        while (current != null) {
            for (Field field : current.getDeclaredFields()) {
                if (Modifier.isStatic(field.getModifiers()) || field.getType().isPrimitive()) {
                    continue;
                }
                if (findMotionEventMethod(field.getType()) != null) {
                    field.setAccessible(true);
                    return field;
                }
            }
            current = current.getSuperclass();
        }
        return null;
    }

    private static Field findViewField(Class<?> type, String... names) {
        for (String name : names) {
            Field field = findField(type, name);
            if (field != null && View.class.isAssignableFrom(field.getType())) {
                return field;
            }
        }
        return null;
    }

    private static Method findCleanVisibilityMethod(
            Class<?> type,
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
                    if (!method.getName().equals(name) || !isCleanVisibilitySignature(method)) {
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

        Method candidate = null;
        current = type;
        while (current != null) {
            for (Method method : current.getDeclaredMethods()) {
                if (method.isSynthetic() || !isCleanVisibilitySignature(method)) {
                    continue;
                }
                if (candidate != null) {
                    return null;
                }
                candidate = method;
            }
            current = current.getSuperclass();
        }
        return candidate;
    }

    private static boolean isCleanVisibilitySignature(Method method) {
        Class<?>[] parameters = method.getParameterTypes();
        if (method.getReturnType() != void.class
                || parameters.length < 5 || parameters.length > 6
                || !Animator.class.isAssignableFrom(parameters[0])) {
            return false;
        }
        int booleanCount = 0;
        for (int i = 1; i < parameters.length; i++) {
            if (parameters[i] == boolean.class || parameters[i] == Boolean.class) {
                booleanCount++;
            } else if (i != 1 || parameters[i] != String.class) {
                return false;
            }
        }
        return booleanCount == 4;
    }

    private static int findCleanBooleanArgumentIndex(Method method) {
        Class<?>[] parameters = method.getParameterTypes();
        for (int i = 1; i < parameters.length; i++) {
            if (parameters[i] == boolean.class || parameters[i] == Boolean.class) {
                return i;
            }
        }
        return 1;
    }

    private static Object[] antiBurnInCellCleanArguments(Method method, boolean clean) {
        Class<?>[] parameterTypes = method.getParameterTypes();
        Object[] arguments = new Object[parameterTypes.length];
        int cleanIndex = findCleanBooleanArgumentIndex(method);
        for (int i = 0; i < parameterTypes.length; i++) {
            Class<?> parameter = parameterTypes[i];
            if (parameter == boolean.class || parameter == Boolean.class) {
                arguments[i] = i == cleanIndex ? clean : Boolean.TRUE;
            } else if (parameter == String.class) {
                arguments[i] = "tokiAntiBurnIn";
            } else {
                arguments[i] = null;
            }
        }
        forceCellCleanImmediately(method, arguments);
        return arguments;
    }

    private static Object[] antiBurnInFeedCleanArguments(Method method, boolean clean) {
        Class<?>[] parameterTypes = method.getParameterTypes();
        Object[] arguments = new Object[parameterTypes.length];
        int cleanIndex = findCleanBooleanArgumentIndex(method);
        int booleanOffset = 0;
        for (int i = 0; i < parameterTypes.length; i++) {
            Class<?> parameter = parameterTypes[i];
            if (parameter == boolean.class || parameter == Boolean.class) {
                if (i == cleanIndex) {
                    arguments[i] = clean;
                } else {
                    booleanOffset++;
                    arguments[i] = booleanOffset <= 2;
                }
            } else if (parameter == String.class) {
                arguments[i] = "tokiAntiBurnIn";
            } else {
                arguments[i] = null;
            }
        }
        forceFeedCleanImmediately(method, arguments);
        return arguments;
    }

    private static void forceCellCleanImmediately(Method method, Object[] arguments) {
        Class<?>[] parameterTypes = method.getParameterTypes();
        for (int i = parameterTypes.length - 1; i >= 0; i--) {
            if (parameterTypes[i] == boolean.class || parameterTypes[i] == Boolean.class) {
                arguments[i] = Boolean.TRUE;
                return;
            }
        }
    }

    private static void forceFeedCleanImmediately(Method method, Object[] arguments) {
        Class<?>[] parameterTypes = method.getParameterTypes();
        int cleanIndex = findCleanBooleanArgumentIndex(method);
        for (int i = cleanIndex + 1; i < parameterTypes.length; i++) {
            if (parameterTypes[i] == boolean.class || parameterTypes[i] == Boolean.class) {
                arguments[i] = Boolean.TRUE;
                return;
            }
        }
    }

    private void traceAntiBurnInCleanGate(Object owner, Object source, Object requestedClean) {
        if (!consumeAntiBurnInTraceBudget()) {
            return;
        }
        StringBuilder caller = new StringBuilder();
        for (StackTraceElement frame : Thread.currentThread().getStackTrace()) {
            String className = frame.getClassName();
            if (!(className.startsWith("com.ss.android.")
                    || className.startsWith("X.")
                    || className.startsWith("Y."))) {
                continue;
            }
            if (caller.length() > 0) {
                caller.append(" <- ");
            }
            caller.append(className).append('.').append(frame.getMethodName());
            if (caller.length() >= 200) {
                break;
            }
        }
        logInfo("Anti-burn-in clean gate owner=" + owner
                + " source=" + source
                + " requested=" + requestedClean
                + " caller=" + caller);
    }

    private void traceAntiBurnInPinchGate(
            Object requestedClean,
            Object requestedImmediately
    ) {
        if (!consumeAntiBurnInTraceBudget()) {
            return;
        }
        logInfo("Anti-burn-in pinch gate requestedClean=" + requestedClean
                + " requestedImmediately=" + requestedImmediately
                + " forwardedClean=true forwardedImmediately=true");
    }

    private void traceAntiBurnInPhotoEventGate(int kind, String source) {
        if (!consumeAntiBurnInTraceBudget()) {
            return;
        }
        logInfo("Anti-burn-in photo clear event requested=false forwarded=true type="
                + kind + " source=" + source);
    }

    private void traceAntiBurnInPhotoStateGate(String source) {
        if (!consumeAntiBurnInTraceBudget()) {
            return;
        }
        logInfo("Anti-burn-in photo state requested=false forwarded=true source=" + source);
    }

    private void traceAntiBurnInVideoLifecycle(String source) {
        if (!consumeAntiBurnInTraceBudget()) {
            return;
        }
        logInfo("Anti-burn-in video cell reapplied after " + source);
    }

    private void traceAntiBurnInVideoRestore(String source) {
        if (!consumeAntiBurnInTraceBudget()) {
            return;
        }
        logInfo("Anti-burn-in video cell restored after " + source);
    }

    private boolean consumeAntiBurnInTraceBudget() {
        return antiBurnInTraceBudget.getAndUpdate(value -> value > 0 ? value - 1 : 0) > 0;
    }

    private void logAntiBurnInFailure(String message, Throwable error) {
        logError(message, error);
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
            Method currentAwemeMethod = type.getDeclaredMethod("LLJI");
            Method currentHolderMethod = type.getDeclaredMethod("LLJZIJLIL");
            Method holderForSourceMethod = type.getDeclaredMethod("LJJIJL", String.class);
            Method manualPauseMethod = type.getDeclaredMethod(
                    "qk",
                    awemeType,
                    boolean.class,
                    boolean.class,
                    boolean.class);
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
