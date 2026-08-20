package com.seepd.toki;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;

import io.github.libxposed.api.XposedModule;

/** Handles Ghost Mode / Privacy features (anonymous stories, invisible DMs, hide typing). */
final class GhostModeHooks extends HookFeature {
    GhostModeHooks(XposedModule module) {
        super(module);
    }

    int install(ClassLoader classLoader, ModuleConfig config) {
        int installed = 0;
        if (config.ghostModeStories) {
            installed += installStoryPrivacyHooks(classLoader);
        }
        if (config.ghostModeDmRead) {
            installed += installDmReadPrivacyHooks(classLoader);
        }
        if (config.ghostModeTyping) {
            installed += installDmTypingPrivacyHooks(classLoader);
        }
        return installed;
    }

    private int installStoryPrivacyHooks(ClassLoader classLoader) {
        int count = 0;
        String[] storyApiClasses = {
                "com.ss.android.ugc.aweme.story.api.StoryApi",
                "com.ss.android.ugc.aweme.story.api.StoryFeedApi",
                "com.ss.android.ugc.aweme.story.feed.common.StoryFeedPanel"
        };
        for (String className : storyApiClasses) {
            try {
                Class<?> type = Class.forName(className, false, classLoader);
                for (Method method : type.getDeclaredMethods()) {
                    String name = method.getName().toLowerCase();
                    if (name.contains("view") || name.contains("read") || name.contains("reportview")) {
                        try {
                            hook(method)
                                    .setId("toki-ghost-story-" + className + "-" + method.getName())
                                    .intercept(chain -> {
                                        Class<?> returnType = method.getReturnType();
                                        if (returnType == boolean.class) return true;
                                        return null;
                                    });
                            count++;
                        } catch (Throwable ignored) {
                        }
                    }
                }
            } catch (ClassNotFoundException ignored) {
            } catch (Throwable error) {
                logError("Unable to hook story privacy " + className, error);
            }
        }
        return count;
    }

    private int installDmReadPrivacyHooks(ClassLoader classLoader) {
        int count = 0;
        String[] dmClasses = {
                "com.bytedance.im.core.internal.link.handler.ConversationMarkReadHandler",
                "com.ss.android.ugc.aweme.im.sdk.chat.feature.single.ui.viewmodel.SingleChatViewModel",
                "com.ss.android.ugc.aweme.im.sdk.chat.feature.group.viewmodel.GroupChatViewModel"
        };
        for (String className : dmClasses) {
            try {
                Class<?> type = Class.forName(className, false, classLoader);
                for (Method method : type.getDeclaredMethods()) {
                    String name = method.getName().toLowerCase();
                    if (name.contains("markread") || name.contains("sendreadreceipt") || name.contains("markasread")) {
                        try {
                            hook(method)
                                    .setId("toki-ghost-dm-read-" + className + "-" + method.getName())
                                    .intercept(chain -> null);
                            count++;
                        } catch (Throwable ignored) {
                        }
                    }
                }
            } catch (ClassNotFoundException ignored) {
            } catch (Throwable error) {
                logError("Unable to hook DM read privacy " + className, error);
            }
        }
        return count;
    }

    private int installDmTypingPrivacyHooks(ClassLoader classLoader) {
        int count = 0;
        String[] typingClasses = {
                "com.ss.android.ugc.aweme.im.sdk.chat.feature.input.base.InputView",
                "com.ss.android.ugc.aweme.im.sdk.chat.ui.base.BaseChatPanel"
        };
        for (String className : typingClasses) {
            try {
                Class<?> type = Class.forName(className, false, classLoader);
                for (Method method : type.getDeclaredMethods()) {
                    String name = method.getName().toLowerCase();
                    if (name.contains("sendtyping") || name.contains("reporttyping") || name.contains("typingstatus")) {
                        try {
                            hook(method)
                                    .setId("toki-ghost-dm-typing-" + className + "-" + method.getName())
                                    .intercept(chain -> null);
                            count++;
                        } catch (Throwable ignored) {
                        }
                    }
                }
            } catch (ClassNotFoundException ignored) {
            } catch (Throwable error) {
                logError("Unable to hook typing privacy " + className, error);
            }
        }
        return count;
    }
}
