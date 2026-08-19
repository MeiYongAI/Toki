package com.seepd.toki;

import android.content.res.Configuration;
import android.location.Location;
import android.os.LocaleList;
import android.telephony.TelephonyManager;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.time.ZoneId;
import java.util.Locale;
import java.util.TimeZone;

import org.json.JSONException;
import org.json.JSONObject;

import io.github.libxposed.api.XposedModule;

/** Installs region, locale, timezone, telephony, and GPS environment hooks. */
final class EnvironmentHooks extends HookFeature {
    EnvironmentHooks(XposedModule module) {
        super(module);
    }

    void installRegion(ClassLoader classLoader, RegionPreset preset) {
        hookTelephony("getSimCountryIso", preset.code.toLowerCase(Locale.ROOT));
        hookTelephony("getNetworkCountryIso", preset.code.toLowerCase(Locale.ROOT));
        hookTelephony("getSimOperator", preset.operator);
        hookTelephony("getNetworkOperator", preset.operator);
        hookTelephony("getSimOperatorName", preset.operatorName);
        hookTelephony("getNetworkOperatorName", preset.operatorName);
        installRegionPayloadPatches(classLoader, preset);
    }

    void installGps(ModuleConfig config) {
        int installed = 0;
        installed += hookLocationCoordinate("getLatitude", config.gpsLatitude);
        installed += hookLocationCoordinate("getLongitude", config.gpsLongitude);
        logInfo("GPS spoof hooks installed: " + installed + " coordinate getter(s)");
    }

    void installSystemEnvironment(ModuleConfig config) {
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
                    || (!("getDefault".equals(method.getName()))
                    && !("getAdjustedDefault".equals(method.getName())))) {
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
            // This exact 46.4.3 payload builder is not present in this TikTok build.
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
            // This exact 46.4.3 upload-parameter builder is not present in this TikTok build.
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
}
