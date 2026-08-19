package com.seepd.toki;

import android.os.Message;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.List;

import io.github.libxposed.api.XposedModule;

/** Filters 46.4.3 FYP network responses and cache-backed feed insertions. */
final class FeedHooks extends HookFeature {
    private static final String FEED_FETCH_MODEL = "X.12gy";
    private static final String MESSAGE_HOOK =
            "com.bytedance.bpea.transmit.hook.HandlerHook";
    private static final String BASE_FEED_PANEL =
            "com.ss.android.ugc.aweme.feed.panel.BaseListFragmentPanel";
    private static final String FYP_FEED_PANEL =
            "com.ss.android.ugc.aweme.feed.panel.RecommendFeedFragmentPanel";
    private static final String INSERT_REQUEST = "X.0SYB";

    FeedHooks(XposedModule module) {
        super(module);
    }

    void install(ClassLoader classLoader, ModuleConfig config) {
        FeedFilter filter = new FeedFilter(config);
        hookFeedResults(classLoader, "com.ss.android.ugc.aweme.feed.FeedApiService", filter);
        hookFeedResults(classLoader, "com.ss.android.ugc.aweme.feed.api.FeedApi", filter);
        hookFeedDispatch(classLoader, filter);
        hookFypInsertion(classLoader, filter);
    }

    private void hookFeedDispatch(ClassLoader classLoader, FeedFilter filter) {
        try {
            Class<?> modelClass = Class.forName(FEED_FETCH_MODEL, false, classLoader);
            Method handleMessage = modelClass.getDeclaredMethod("handleMsg", Message.class);
            if (Modifier.isStatic(handleMessage.getModifiers())
                    || handleMessage.getReturnType() != void.class) {
                throw new NoSuchMethodException(FEED_FETCH_MODEL + "#handleMsg(Message): void");
            }

            Class<?> messageHookClass = Class.forName(MESSAGE_HOOK, false, classLoader);
            Method unwrapMessage = messageHookClass.getDeclaredMethod("getMessageObj", Object.class);
            hook(handleMessage)
                    .setId("toki-fyp-feed-dispatch-4643")
                    .intercept(chain -> {
                        Object argument = chain.getArg(0);
                        if (argument instanceof Message) {
                            filter.apply(messagePayload((Message) argument, unwrapMessage));
                        }
                        return chain.proceed();
                    });
        } catch (Throwable error) {
            logError("Unable to hook 46.4.3 FYP feed dispatch", error);
        }
    }

    private void hookFypInsertion(ClassLoader classLoader, FeedFilter filter) {
        try {
            Class<?> basePanelClass = Class.forName(BASE_FEED_PANEL, false, classLoader);
            Class<?> fypPanelClass = Class.forName(FYP_FEED_PANEL, false, classLoader);
            Class<?> requestClass = Class.forName(INSERT_REQUEST, false, classLoader);
            Method insert = basePanelClass.getDeclaredMethod("LLII", requestClass);
            if (Modifier.isStatic(insert.getModifiers()) || insert.getReturnType() != void.class) {
                throw new NoSuchMethodException(BASE_FEED_PANEL + "#LLII(X.0SYB): void");
            }

            Field itemsField = requestClass.getDeclaredField("LIZ");
            Field indexField = requestClass.getDeclaredField("LIZIZ");
            Field reasonField = requestClass.getDeclaredField("LIZJ");
            Constructor<?> requestConstructor = requestClass.getDeclaredConstructor(
                    int.class, String.class, List.class);
            hook(insert)
                    .setId("toki-fyp-list-insertion-4643")
                    .intercept(chain -> {
                        if (!fypPanelClass.isInstance(chain.getThisObject())) {
                            return chain.proceed();
                        }
                        Object request = chain.getArg(0);
                        Object originalItems;
                        try {
                            originalItems = itemsField.get(request);
                        } catch (ReflectiveOperationException | RuntimeException ignored) {
                            return chain.proceed();
                        }
                        Object filteredItems = filter.filterListResult(originalItems);
                        if (filteredItems == originalItems || !(filteredItems instanceof List<?>)) {
                            return chain.proceed();
                        }
                        if (((List<?>) filteredItems).isEmpty()) {
                            return null;
                        }
                        try {
                            Object filteredRequest = requestConstructor.newInstance(
                                    indexField.getInt(request),
                                    reasonField.get(request),
                                    filteredItems);
                            return chain.proceed(new Object[]{filteredRequest});
                        } catch (ReflectiveOperationException | RuntimeException ignored) {
                            return chain.proceed();
                        }
                    });
        } catch (Throwable error) {
            logError("Unable to hook 46.4.3 FYP list insertion", error);
        }
    }

    private static Object messagePayload(Message message, Method unwrapMessage) {
        if (unwrapMessage == null) {
            return message.obj;
        }
        try {
            return unwrapMessage.invoke(null, message.obj);
        } catch (ReflectiveOperationException | RuntimeException ignored) {
            return message.obj;
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
}
