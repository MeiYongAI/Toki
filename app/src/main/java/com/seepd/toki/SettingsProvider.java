package com.seepd.toki;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.content.Context;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.net.Uri;
import android.os.Binder;
import android.os.Bundle;
import android.os.Process;

public final class SettingsProvider extends ContentProvider {
    @Override
    public boolean onCreate() {
        return true;
    }

    @Override
    public Bundle call(String method, String arg, Bundle extras) {
        Context context = getContext();
        if (context == null) {
            return Bundle.EMPTY;
        }
        if (!isAuthorizedCaller(context)) {
            return Bundle.EMPTY;
        }
        SharedPreferences prefs = context.getSharedPreferences(
                ModuleConfig.PREFS, Context.MODE_PRIVATE);
        if ("getCommentTranslationState".equals(method)) {
            Bundle result = new Bundle();
            result.putBoolean(
                    ModuleConfig.KEY_COMMENT_TRANSLATION_ACTIVE,
                    prefs.getBoolean(ModuleConfig.KEY_COMMENT_TRANSLATION_ACTIVE, false));
            return result;
        }
        if ("setCommentTranslationState".equals(method)) {
            boolean active = extras != null && extras.getBoolean(
                    ModuleConfig.KEY_COMMENT_TRANSLATION_ACTIVE, false);
            boolean saved = prefs.edit()
                    .putBoolean(ModuleConfig.KEY_COMMENT_TRANSLATION_ACTIVE, active)
                    .commit();
            Bundle result = new Bundle();
            result.putBoolean("saved", saved);
            result.putBoolean(ModuleConfig.KEY_COMMENT_TRANSLATION_ACTIVE, active);
            return result;
        }
        return Bundle.EMPTY;
    }

    private static boolean isAuthorizedCaller(Context context) {
        int callingUid = Binder.getCallingUid();
        if (callingUid == Process.myUid()) {
            return true;
        }
        String[] packages = context.getPackageManager().getPackagesForUid(callingUid);
        if (packages == null) {
            return false;
        }
        for (String packageName : packages) {
            if (ModuleConfig.TARGET_PACKAGE.equals(packageName)) {
                return true;
            }
        }
        return false;
    }

    @Override public Cursor query(Uri uri, String[] projection, String selection, String[] selectionArgs, String sortOrder) { return null; }
    @Override public String getType(Uri uri) { return null; }
    @Override public Uri insert(Uri uri, ContentValues values) { return null; }
    @Override public int delete(Uri uri, String selection, String[] selectionArgs) { return 0; }
    @Override public int update(Uri uri, ContentValues values, String selection, String[] selectionArgs) { return 0; }
}
