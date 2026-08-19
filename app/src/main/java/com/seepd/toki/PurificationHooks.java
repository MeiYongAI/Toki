package com.seepd.toki;

import android.app.Activity;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewParent;
import android.view.ViewTreeObserver;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.WeakHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

import io.github.libxposed.api.XposedModule;

/** Installs component, video overlay, and global navigation purification hooks. */
final class PurificationHooks extends HookFeature {
    private final AtomicBoolean visibilityLogged = new AtomicBoolean(false);
    private final AtomicBoolean globalVisibilityLogged = new AtomicBoolean(false);
    private final WeakHashMap<View, Boolean> observedRoots = new WeakHashMap<>();

    PurificationHooks(XposedModule module) {
        super(module);
    }

    int install(ClassLoader classLoader, ModuleConfig config) {
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

    boolean installGlobalNavigation(ModuleConfig config) {
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

    /** Removes the feed payloads that cause TikTok to render visual and similar-content search. */
    private int installContentSearchVisibilityHooks(ClassLoader classLoader) {
        return installAwemePayloadRemovalHooks(
                classLoader,
                "content-search",
                "getSmartSearchInfo",
                "getVisualSearchInfo"
        );
    }

    int installVideoOverlay(ClassLoader classLoader, ModuleConfig config) {
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
                                if (visibilityLogged.compareAndSet(false, true)) {
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

    private void observeGlobalNavigation(Activity activity, ModuleConfig config) {
        View decorView;
        try {
            decorView = activity.getWindow().getDecorView();
        } catch (RuntimeException ignored) {
            return;
        }
        if (decorView == null || observedRoots.put(decorView, Boolean.TRUE) != null) {
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
        if (globalVisibilityLogged.compareAndSet(false, true)) {
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


}
