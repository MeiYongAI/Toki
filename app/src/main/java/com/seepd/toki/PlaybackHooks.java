package com.seepd.toki;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Map;
import java.util.WeakHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

import io.github.libxposed.api.XposedModule;

/** Handles loop prevention and the configured default playback speed. */
final class PlaybackHooks extends HookFeature {
    private final AtomicBoolean configInvocationLogged = new AtomicBoolean(false);
    private final AtomicBoolean engineInvocationLogged = new AtomicBoolean(false);
    private final AtomicBoolean manualPauseLogged = new AtomicBoolean(false);
    private final AtomicBoolean manualPauseFailureLogged = new AtomicBoolean(false);
    private final AtomicBoolean speedAppliedLogged = new AtomicBoolean(false);
    private final AtomicBoolean speedFailureLogged = new AtomicBoolean(false);
    private final WeakHashMap<Object, String> speedSourceIds = new WeakHashMap<>();

    PlaybackHooks(XposedModule module) {
        super(module);
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
    int installLoopPrevention(ClassLoader classLoader) {
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
    void installDefaultSpeed(ClassLoader classLoader) {
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
        synchronized (speedSourceIds) {
            if (sourceId.equals(speedSourceIds.get(controller))) {
                return;
            }
            speedSourceIds.put(controller, sourceId);
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
            if (speedAppliedLogged.compareAndSet(false, true)) {
                logInfo("Default playback speed active: " + speed + "x");
            }
        } catch (Throwable error) {
            synchronized (speedSourceIds) {
                speedSourceIds.remove(controller);
            }
            if (speedFailureLogged.compareAndSet(false, true)) {
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
            if (speedFailureLogged.compareAndSet(false, true)) {
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
                            if (manualPauseLogged.compareAndSet(false, true)) {
                                logInfo("Loop-prevention paused and rewound to the replay frame");
                            }
                        } catch (Throwable error) {
                            if (manualPauseFailureLogged.compareAndSet(false, true)) {
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
                        .setId("toki-disable-loop-config-4643")
                        .intercept(chain -> {
                            Object result = chain.proceed();
                            Object mapArgument = chain.getArg(1);
                            Object bypassArgument = chain.getArg(2);
                            if (Boolean.FALSE.equals(bypassArgument)
                                    && mapArgument instanceof Map<?, ?>) {
                                Object requested = forceLoopFlagOff((Map<?, ?>) mapArgument);
                                if (configInvocationLogged.compareAndSet(false, true)) {
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
                        if (engineInvocationLogged.compareAndSet(false, true)) {
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
}
