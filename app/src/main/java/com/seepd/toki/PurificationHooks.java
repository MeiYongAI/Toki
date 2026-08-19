package com.seepd.toki;

import android.app.Activity;
import android.os.Build;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewParent;
import android.view.ViewTreeObserver;
import android.view.WindowInsets;
import android.view.WindowInsetsController;
import android.widget.HorizontalScrollView;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.WeakHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

import io.github.libxposed.api.XposedModule;

/** Installs view-level component and global navigation purification hooks. */
final class PurificationHooks extends HookFeature {
    private static final String MAIN_ACTIVITY = "com.ss.android.ugc.aweme.main.MainActivity";
    private final AtomicBoolean visibilityLogged = new AtomicBoolean(false);
    private final AtomicBoolean globalVisibilityLogged = new AtomicBoolean(false);
    private final WeakHashMap<View, Boolean> observedRoots = new WeakHashMap<>();

    PurificationHooks(XposedModule module) {
        super(module);
    }

    int installComponents(ClassLoader classLoader, ModuleConfig config) {
        int installed = 0;
        if (config.hideAuthorAvatar) {
            installed += installComponentVisibilityHooks(classLoader, "author-avatar",
                    "com.ss.android.ugc.aweme.feed.assem.avatar.FeedAvatarAssemWrap",
                    "com.ss.android.ugc.aweme.feed.assem.avatar.FeedAvatarDefaultAssem");
        }
        if (config.hideAuthorInfo) {
            installed += installComponentVisibilityHooks(classLoader, "author-info",
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
            installed += installMusicTitleVisibilityHook(classLoader);
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
        if (config.hideTranslationControls) {
            installed += installComponentVisibilityHooks(classLoader, "translation-controls",
                    "com.ss.android.ugc.aweme.translation.ui.TranslationControlsAssem");
        }
        return installed;
    }

    /** Prevents TikTok 46.4.3 from restoring the top-left LIVE entry during feed transitions. */
    private int installLiveEntryVisibilityHook(ClassLoader classLoader) {
        try {
            Class<?> generatorType = Class.forName(
                    "com.bytedance.tiktok.homepage.mainfragment.toolbar.LiveIconGenerator",
                    false,
                    classLoader);
            Method visibilityMethod = generatorType.getDeclaredMethod("LJIIIZ", boolean.class);
            if (visibilityMethod.getReturnType() != void.class) {
                throw new NoSuchMethodException("LiveIconGenerator#LJIIIZ(boolean): void");
            }
            visibilityMethod.setAccessible(true);
            hook(visibilityMethod)
                    .setId("toki-purify-live-entry-generator-4643")
                    .intercept(chain -> chain.proceed(new Object[]{false}));
            return 1;
        } catch (ClassNotFoundException ignored) {
            // The verified 46.4.3 live-entry controller is absent in this process.
            return 0;
        } catch (Throwable error) {
            logError("Unable to prevent 46.4.3 LIVE entry restoration", error);
            return 0;
        }
    }

    int installGlobalNavigation(ClassLoader classLoader, ModuleConfig config) {
        int installed = config.hideLiveEntry
                ? installLiveEntryVisibilityHook(classLoader)
                : 0;
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
            return installed + 1;
        } catch (Throwable error) {
            logError("Unable to install global navigation purification", error);
            return installed;
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

    /** Forces the 46.4.3 music-title controller to keep its root hidden. */
    private int installMusicTitleVisibilityHook(ClassLoader classLoader) {
        try {
            Class<?> type = Class.forName(
                    "com.ss.android.ugc.aweme.feed.assem.music.VideoMusicTitleAssem",
                    false,
                    classLoader);
            // JADX renders this obfuscated method as m47347mr; the runtime name is mr.
            Method visibility = type.getDeclaredMethod("mr", int.class);
            if (visibility.getReturnType() != void.class
                    || Modifier.isStatic(visibility.getModifiers())) {
                throw new NoSuchMethodException(
                        "VideoMusicTitleAssem#mr(int): void");
            }
            visibility.setAccessible(true);
            hook(visibility)
                    .setId("toki-purify-music-title-visibility-4643")
                    .intercept(chain -> chain.proceed(new Object[]{8}));
            return 1;
        } catch (ClassNotFoundException ignored) {
            return 0;
        } catch (Throwable error) {
            logError("Unable to hide 46.4.3 music title", error);
            return 0;
        }
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
        applyGlobalNavigationPurification(activity, decorView, config, viewIds);
        ViewTreeObserver observer = decorView.getViewTreeObserver();
        if (observer.isAlive()) {
            observer.addOnGlobalLayoutListener(() ->
                    applyGlobalNavigationPurification(activity, decorView, config, viewIds));
        }
    }

    private void applyGlobalNavigationPurification(
            Activity activity,
            View root,
            ModuleConfig config,
            GlobalNavigationViewIds viewIds
    ) {
        if (config.hideStatusBar && MAIN_ACTIVITY.equals(activity.getClass().getName())) {
            hideStatusBar(activity);
        }
        if (config.hideLiveEntry) {
            hideViewById(root, viewIds.liveEntry);
        }
        if (config.hideTopNavigation) {
            hideTopNavigation(root, viewIds.topNavigationHost);
        }
        if (config.hideSearchEntry) {
            hideViewByIdAfterLayout(root, viewIds.searchEntry);
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

    private static void hideStatusBar(Activity activity) {
        View decorView = activity.getWindow().getDecorView();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            WindowInsetsController controller = decorView.getWindowInsetsController();
            if (controller != null) {
                controller.setSystemBarsBehavior(
                        WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE);
                controller.hide(WindowInsets.Type.statusBars());
            }
            return;
        }

        int flags = decorView.getSystemUiVisibility()
                | View.SYSTEM_UI_FLAG_FULLSCREEN
                | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                | View.SYSTEM_UI_FLAG_LAYOUT_STABLE;
        decorView.setSystemUiVisibility(flags);
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

    private static void hideViewByIdAfterLayout(View root, int resourceId) {
        hideViewById(root, resourceId);
        if (resourceId != 0) {
            root.post(() -> hideViewById(root, resourceId));
        }
    }

    private static void hideTopNavigation(View root, int hostId) {
        View host = hostId == 0 ? null : root.findViewById(hostId);
        if (!(host instanceof ViewGroup)) {
            return;
        }
        ViewGroup hostGroup = (ViewGroup) host;
        for (int index = 0; index < hostGroup.getChildCount(); index++) {
            View child = hostGroup.getChildAt(index);
            if (containsHorizontalScrollView(child)) {
                child.setVisibility(View.GONE);
                return;
            }
        }
    }

    private static boolean containsHorizontalScrollView(View view) {
        if (view instanceof HorizontalScrollView) {
            return true;
        }
        if (!(view instanceof ViewGroup)) {
            return false;
        }
        ViewGroup group = (ViewGroup) view;
        for (int index = 0; index < group.getChildCount(); index++) {
            if (containsHorizontalScrollView(group.getChildAt(index))) {
                return true;
            }
        }
        return false;
    }

    private static final class GlobalNavigationViewIds {
        final int liveEntry;
        final int topNavigationHost;
        final int searchEntry;
        final int bottomNavigation;
        final int videoProgressBar;

        private GlobalNavigationViewIds(
                int liveEntry,
                int topNavigationHost,
                int searchEntry,
                int bottomNavigation,
                int videoProgressBar
        ) {
            this.liveEntry = liveEntry;
            this.topNavigationHost = topNavigationHost;
            this.searchEntry = searchEntry;
            this.bottomNavigation = bottomNavigation;
            this.videoProgressBar = videoProgressBar;
        }

        static GlobalNavigationViewIds from(View root) {
            return new GlobalNavigationViewIds(
                    viewId(root, "jyx"),
                    viewId(root, "u3t"),
                    viewId(root, "jz0"),
                    viewId(root, "o7b"),
                    viewId(root, "video_seek_bar"));
        }

        private static int viewId(View root, String resourceName) {
            return root.getResources().getIdentifier(
                    resourceName, "id", ModuleConfig.TARGET_PACKAGE);
        }
    }


}
