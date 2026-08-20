package com.seepd.toki;

import android.app.DownloadManager;
import android.content.Context;
import android.net.Uri;
import android.os.Environment;
import android.view.View;
import android.widget.Toast;

import java.io.File;
import java.lang.reflect.Method;
import java.util.List;

import io.github.libxposed.api.XposedModule;

/** Enables downloading / viewing user avatars in maximum resolution on long-click. */
final class AvatarHooks extends HookFeature {
    AvatarHooks(XposedModule module) {
        super(module);
    }

    int install(ClassLoader classLoader, ModuleConfig config) {
        int count = 0;
        count += hookFeedAvatarAssem(classLoader, "com.ss.android.ugc.aweme.feed.assem.avatar.FeedAvatarDefaultAssem", config);
        count += hookFeedAvatarAssem(classLoader, "com.ss.android.ugc.aweme.feed.assem.avatar.FeedAvatarAssemWrap", config);
        return count;
    }

    private int hookFeedAvatarAssem(ClassLoader classLoader, String className, ModuleConfig config) {
        try {
            Class<?> assemClass = Class.forName(className, false, classLoader);
            for (Method method : assemClass.getDeclaredMethods()) {
                if ("onViewCreated".equals(method.getName()) || "LJJIJLIJ".equals(method.getName())) {
                    try {
                        hook(method)
                                .setId("toki-avatar-hd-" + className + "-" + method.getName())
                                .intercept(chain -> {
                                    Object result = chain.proceed();
                                    Object assem = chain.getThisObject();
                                    setupAvatarLongClick(assem, config);
                                    return result;
                                });
                        return 1;
                    } catch (Throwable ignored) {
                    }
                }
            }
        } catch (ClassNotFoundException ignored) {
        } catch (Throwable error) {
            logError("Unable to hook avatar HD for " + className, error);
        }
        return 0;
    }

    private void setupAvatarLongClick(Object assem, ModuleConfig config) {
        if (assem == null) return;
        try {
            Method getContainerView = assem.getClass().getMethod("getContainerView");
            Object viewObj = getContainerView.invoke(assem);
            if (viewObj instanceof View) {
                View view = (View) viewObj;
                view.setOnLongClickListener(v -> {
                    try {
                        Object aweme = resolveAwemeFromAssem(assem);
                        if (aweme != null) {
                            Method getAuthor = aweme.getClass().getMethod("getAuthor");
                            Object author = getAuthor.invoke(aweme);
                            if (author != null) {
                                String avatarUrl = resolveAvatarHdUrl(author);
                                if (avatarUrl != null && !avatarUrl.isEmpty()) {
                                    downloadAvatar(v.getContext(), avatarUrl, author, config);
                                    return true;
                                }
                            }
                        }
                    } catch (Throwable ignored) {
                    }
                    return false;
                });
            }
        } catch (Throwable ignored) {
        }
    }

    private static Object resolveAwemeFromAssem(Object assem) {
        for (String getter : new String[]{"getAweme", "q0", "LJJIZ"}) {
            try {
                Method method = assem.getClass().getMethod(getter);
                Object value = method.invoke(assem);
                if (value != null) return value;
            } catch (Throwable ignored) {
            }
        }
        return null;
    }

    private static String resolveAvatarHdUrl(Object author) {
        for (String getter : new String[]{"getAvatarLarger", "getAvatarHd", "getAvatarMedium", "getAvatarThumb"}) {
            try {
                Method method = author.getClass().getMethod(getter);
                Object urlModel = method.invoke(author);
                if (urlModel != null) {
                    Method getUrlList = urlModel.getClass().getMethod("getUrlList");
                    Object listObj = getUrlList.invoke(urlModel);
                    if (listObj instanceof List<?> && !((List<?>) listObj).isEmpty()) {
                        Object first = ((List<?>) listObj).get(0);
                        if (first instanceof String) {
                            return (String) first;
                        }
                    }
                }
            } catch (Throwable ignored) {
            }
        }
        return null;
    }

    private static void downloadAvatar(Context context, String url, Object author, ModuleConfig config) {
        try {
            String nickname = "user";
            try {
                Method getNickname = author.getClass().getMethod("getNickname");
                Object name = getNickname.invoke(author);
                if (name instanceof String && !((String) name).isEmpty()) {
                    nickname = ((String) name).replaceAll("[^a-zA-Z0-9_.-]", "_");
                }
            } catch (Throwable ignored) {
            }

            String fileName = "avatar_" + nickname + "_" + System.currentTimeMillis() + ".jpg";
            DownloadManager.Request request = new DownloadManager.Request(Uri.parse(url));
            request.setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED);
            request.setTitle("Avatar: " + nickname);
            request.setDestinationInExternalPublicDir(
                    Environment.DIRECTORY_PICTURES,
                    "TikTok" + File.separator + fileName);

            DownloadManager manager = (DownloadManager) context.getSystemService(Context.DOWNLOAD_SERVICE);
            if (manager != null) {
                manager.enqueue(request);
                Toast.makeText(context, "Descargando avatar HD: " + nickname, Toast.LENGTH_SHORT).show();
            }
        } catch (Throwable error) {
            Toast.makeText(context, "Error al descargar avatar", Toast.LENGTH_SHORT).show();
        }
    }
}
