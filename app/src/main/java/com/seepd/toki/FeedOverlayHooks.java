package com.seepd.toki;

import android.view.View;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.List;

import io.github.libxposed.api.XposedModule;

/** Installs TikTok 46.4.3 feed payload, label, anchor, and warning gates. */
final class FeedOverlayHooks extends HookFeature {
    FeedOverlayHooks(XposedModule module) {
        super(module);
    }

    int install(ClassLoader classLoader, ModuleConfig config) {
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
        if (config.hideContentSearch) {
            installed += installAwemePayloadRemovalHooks(
                    classLoader,
                    "content-search",
                    "getSmartSearchInfo",
                    "getVisualSearchInfo"
            );
        }
        if (config.hideContentClassification) {
            installed += installContentEvaluationSurveyGate(classLoader);
        }
        if (config.hideCommercialLabels || config.hideIncentiveShare) {
            installed += installPriorityProtocolRegistrationGate(classLoader, config);
        }
        if (config.hideCommercialLabels) {
            installed += installCommercialStandardTagGate(classLoader);
            installed += installPromotedTagStateGate(classLoader);
        }
        if (config.hideIncentiveShare) {
            installed += installIncentiveStandardButtonGate(classLoader);
        }
        if (config.hideCreativeToolAnchors || config.hideMovieAnimeAnchors) {
            installed += installAnchorSelectionGate(classLoader, config);
        }
        if (config.hideSafetyWarning) {
            installed += installRiskWarningLabelGate(classLoader);
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

    /** Disables the 46.4.3 feed feedback survey through its native enable-state gate. */
    private int installContentEvaluationSurveyGate(ClassLoader classLoader) {
        try {
            Class<?> surveyType = Class.forName(
                    "com.ss.android.ugc.feed.platform.cell.component.survey."
                            + "CellSurveyComponent",
                    false,
                    classLoader);
            Class<?> awemeType = Class.forName(
                    "com.ss.android.ugc.aweme.feed.model.Aweme",
                    false,
                    classLoader);
            Field enabledField = surveyType.getDeclaredField("LLLJ");
            Method displaySurvey = null;
            for (Method method : surveyType.getDeclaredMethods()) {
                Class<?>[] parameters = method.getParameterTypes();
                if (method.getReturnType() == void.class
                        && parameters.length == 2
                        && parameters[0] == int.class
                        && parameters[1] == awemeType) {
                    displaySurvey = method;
                    if ("T1".equals(method.getName())) {
                        break;
                    }
                }
            }
            if (displaySurvey == null) {
                throw new NoSuchMethodException(
                        "CellSurveyComponent#T1(int, Aweme): void");
            }
            if (enabledField.getType() != Boolean.class) {
                throw new NoSuchFieldException("CellSurveyComponent#LLLJ: Boolean");
            }
            enabledField.setAccessible(true);
            displaySurvey.setAccessible(true);
            hook(displaySurvey)
                    .setId("toki-purify-video-feedback-survey-4643")
                    .intercept(chain -> {
                        enabledField.set(chain.getThisObject(), Boolean.FALSE);
                        return chain.proceed();
                    });
            return 1;
        } catch (ClassNotFoundException ignored) {
            return 0;
        } catch (Throwable error) {
            logError("Unable to hide 46.4.3 video feedback survey", error);
            return 0;
        }
    }

    /** Drops selected priority protocols before their containers can register them. */
    private int installPriorityProtocolRegistrationGate(
            ClassLoader classLoader,
            ModuleConfig config
    ) {
        try {
            Class<?> registryType = Class.forName("X.0oTT", false, classLoader);
            Method register = null;
            for (Method method : registryType.getDeclaredMethods()) {
                Class<?>[] parameters = method.getParameterTypes();
                if ("LIZJ".equals(method.getName())
                        && Modifier.isStatic(method.getModifiers())
                        && method.getReturnType() == void.class
                        && parameters.length == 3
                        && List.class.isAssignableFrom(parameters[2])) {
                    register = method;
                    break;
                }
            }
            if (register == null) {
                throw new NoSuchMethodException("X.0oTT#LIZJ(..., List): void");
            }
            register.setAccessible(true);
            hook(register)
                    .setId("toki-purify-priority-protocol-gate-4643")
                    .intercept(chain -> {
                        Object value = chain.getArg(2);
                        if (!(value instanceof List<?>)) {
                            return chain.proceed();
                        }
                        List<?> source = (List<?>) value;
                        ArrayList<Object> filtered = new ArrayList<>(source.size());
                        for (Object protocol : source) {
                            if (!shouldBlockRegisteredProtocol(protocol, config)) {
                                filtered.add(protocol);
                            }
                        }
                        if (filtered.size() == source.size()) {
                            return chain.proceed();
                        }
                        return chain.proceed(new Object[]{
                                chain.getArg(0), chain.getArg(1), filtered
                        });
                    });
            return 1;
        } catch (ClassNotFoundException ignored) {
            return 0;
        } catch (Throwable error) {
            logError("Unable to install 46.4.3 priority-protocol gate", error);
            return 0;
        }
    }

    private static boolean shouldBlockRegisteredProtocol(Object protocol, ModuleConfig config) {
        if (protocol == null) {
            return false;
        }
        String name = protocol.getClass().getSimpleName();
        if (config.hideCommercialLabels
                && ("PaidPartnershipBottomLabelTriggerAssem".equals(name)
                || "AdBottomLabelTriggerAssem".equals(name)
                || "PoiCreatorBottomLabelTriggerAssem".equals(name))) {
            return true;
        }
        return config.hideIncentiveShare
                && ("IncentiveShareButtonTrigger".equals(name)
                || "IncentiveBottomButtonTrigger".equals(name));
    }

    /** Filters server-provided commercial labels before standardized tag state is built. */
    private int installCommercialStandardTagGate(ClassLoader classLoader) {
        try {
            Class<?> converterType = Class.forName("X.02vm", false, classLoader);
            Class<?> containerType = Class.forName(
                    "com.ss.android.ugc.aweme.feed.model.banner.StandardTagContainerStruct",
                    false,
                    classLoader);
            Class<?> tagType = Class.forName(
                    "com.ss.android.ugc.aweme.feed.model.banner.StandardTagStruct",
                    false,
                    classLoader);
            Class<?> keyType = Class.forName(
                    "com.ss.android.ugc.aweme.feed.model.banner.StandardTagKey",
                    false,
                    classLoader);
            Method converter = converterType.getDeclaredMethod("LIZ", containerType);
            Field tagsField = containerType.getField("tags");
            Field keyField = tagType.getField("key");
            Field componentKeyField = keyType.getField("componentKey");
            Constructor<?> containerConstructor = containerType.getConstructor(List.class);
            converter.setAccessible(true);
            hook(converter)
                    .setId("toki-purify-commercial-standard-tags-4643")
                    .intercept(chain -> {
                        Object container = chain.getArg(0);
                        Object value = container == null ? null : tagsField.get(container);
                        if (!(value instanceof List<?>)) {
                            return chain.proceed();
                        }
                        List<?> source = (List<?>) value;
                        ArrayList<Object> filtered = new ArrayList<>(source.size());
                        for (Object tag : source) {
                            Object key = keyField.get(tag);
                            Object componentKey = key == null ? null : componentKeyField.get(key);
                            if (!(componentKey instanceof String)
                                    || !isCommercialStandardTagKey((String) componentKey)) {
                                filtered.add(tag);
                            }
                        }
                        if (filtered.size() == source.size()) {
                            return chain.proceed();
                        }
                        return chain.proceed(new Object[]{
                                containerConstructor.newInstance(filtered)
                        });
                    });
            return 1;
        } catch (ClassNotFoundException ignored) {
            return 0;
        } catch (Throwable error) {
            logError("Unable to filter 46.4.3 standardized commercial tags", error);
            return 0;
        }
    }

    private static boolean isCommercialStandardTagKey(String key) {
        return "bottom_tag_container_ad_label".equals(key)
                || "bottom_tag_container_paid_partnership_label".equals(key)
                || "bottom_tag_container_bottom_label_poi_alliance".equals(key);
    }

    /** Filters server-provided incentive buttons before FCP state is built. */
    private int installIncentiveStandardButtonGate(ClassLoader classLoader) {
        try {
            Class<?> converterType = Class.forName("X.02vn", false, classLoader);
            Class<?> componentType = Class.forName(
                    "com.ss.android.ugc.aweme.feed.model.banner.StandardComponentStruct",
                    false,
                    classLoader);
            Class<?> keyType = Class.forName(
                    "com.ss.android.ugc.aweme.feed.model.banner.StandardComponentKey",
                    false,
                    classLoader);
            Method converter = converterType.getDeclaredMethod("LIZ", List.class);
            Field keyField = componentType.getField("key");
            Field componentKeyField = keyType.getField("componentKey");
            converter.setAccessible(true);
            hook(converter)
                    .setId("toki-purify-standard-buttons-4643")
                    .intercept(chain -> {
                        Object value = chain.getArg(0);
                        if (!(value instanceof List<?>)) {
                            return chain.proceed();
                        }
                        List<?> source = (List<?>) value;
                        ArrayList<Object> filtered = new ArrayList<>(source.size());
                        for (Object component : source) {
                            Object key = keyField.get(component);
                            Object componentKey = key == null ? null : componentKeyField.get(key);
                            if (!(componentKey instanceof String)
                                    || !isIncentiveStandardButtonKey((String) componentKey)) {
                                filtered.add(component);
                            }
                        }
                        if (filtered.size() == source.size()) {
                            return chain.proceed();
                        }
                        return chain.proceed(new Object[]{filtered});
                    });
            return 1;
        } catch (ClassNotFoundException ignored) {
            return 0;
        } catch (Throwable error) {
            logError("Unable to filter 46.4.3 standardized feed buttons", error);
            return 0;
        }
    }

    private static boolean isIncentiveStandardButtonKey(String key) {
        return "bottom_button_incentive_share".equals(key)
                || "bottom_button_ug_incentive_jump_page".equals(key);
    }

    /** Keeps VideoDescTagAssem intact while forcing only its promoted-tag state hidden. */
    private int installPromotedTagStateGate(ClassLoader classLoader) {
        try {
            Class<?> type = Class.forName(
                    "com.ss.android.ugc.aweme.feed.assem.desc.commerce.PromotedTagVM",
                    false,
                    classLoader);
            Method stateMethod = null;
            for (Method method : type.getDeclaredMethods()) {
                if ("paramSync2StateAccept".equals(method.getName())
                        && method.getParameterCount() == 2) {
                    stateMethod = method;
                    break;
                }
            }
            if (stateMethod == null) {
                throw new NoSuchMethodException(
                        "PromotedTagVM#paramSync2StateAccept(...)");
            }
            Method defaultState = type.getDeclaredMethod("defaultState");
            stateMethod.setAccessible(true);
            defaultState.setAccessible(true);
            hook(stateMethod)
                    .setId("toki-purify-promoted-tag-state-4643")
                    .intercept(chain -> defaultState.invoke(chain.getThisObject()));
            return 1;
        } catch (ClassNotFoundException ignored) {
            return 0;
        } catch (Throwable error) {
            logError("Unable to hide 46.4.3 promoted-tag state", error);
            return 0;
        }
    }

    /** Uses the native empty-selection path for selected anchor types. */
    private int installAnchorSelectionGate(ClassLoader classLoader, ModuleConfig config) {
        try {
            Class<?> managerType = Class.forName("X.0tuF", false, classLoader);
            Class<?> requestType = Class.forName("X.0RQn", false, classLoader);
            Class<?> priorityConfigType = Class.forName("X.0RHB", false, classLoader);
            Class<?> providerType = Class.forName(
                    "com.ss.android.ugc.aweme.feed.model.banner.IStandardModelProvider",
                    false,
                    classLoader);
            Class<?> anchorType = Class.forName(
                    "com.ss.android.ugc.aweme.feed.model.AnchorCommonStruct",
                    false,
                    classLoader);
            Method select = managerType.getDeclaredMethod(
                    "LIZJ", requestType, priorityConfigType);
            Method selectedProvider = managerType.getDeclaredMethod("LIZLLL");
            Method clearSelection = managerType.getDeclaredMethod("LIZIZ");
            Field selectedCount = managerType.getDeclaredField("LLJ");
            Method providerDisplayAnchor = providerType.getMethod("providerDisplayAnchor");
            Method getComponentKey = anchorType.getMethod("getComponentKey");
            if (Modifier.isStatic(select.getModifiers())
                    || select.getReturnType() != int.class) {
                throw new NoSuchMethodException("X.0tuF#LIZJ(0RQn, 0RHB): int");
            }
            select.setAccessible(true);
            selectedProvider.setAccessible(true);
            clearSelection.setAccessible(true);
            selectedCount.setAccessible(true);
            providerDisplayAnchor.setAccessible(true);
            hook(select)
                    .setId("toki-purify-anchor-selection-4643")
                    .intercept(chain -> {
                        Object result = chain.proceed();
                        Object manager = chain.getThisObject();
                        Object provider = selectedProvider.invoke(manager);
                        if (!providerType.isInstance(provider)) {
                            return result;
                        }
                        Object anchor = providerDisplayAnchor.invoke(provider);
                        if (!anchorType.isInstance(anchor)) {
                            return result;
                        }
                        Object componentKey = getComponentKey.invoke(anchor);
                        if (!(componentKey instanceof String)
                                || !shouldHideAnchorKey((String) componentKey, config)) {
                            return result;
                        }
                        clearSelection.invoke(manager);
                        selectedCount.setInt(manager, 0);
                        return 0;
                    });
            return 1;
        } catch (ClassNotFoundException ignored) {
            return 0;
        } catch (Throwable error) {
            logError("Unable to filter 46.4.3 selected anchors", error);
            return 0;
        }
    }

    private static boolean shouldHideAnchorKey(String key, ModuleConfig config) {
        return (config.hideCreativeToolAnchors && isCreativeToolAnchorKey(key))
                || (config.hideMovieAnimeAnchors && "anchor_movie_tok".equals(key));
    }

    private static boolean isCreativeToolAnchorKey(String key) {
        switch (key) {
            case "anchor_effect":
            case "anchor_edit_effect":
            case "anchor_ai_style":
            case "anchor_capcut":
            case "anchor_template":
            case "anchor_pugc_template":
            case "anchor_ucg_template":
            case "anchor_ugc_photo_template":
            case "anchor_tt_capcut_template":
            case "anchor_aigt_template":
            case "anchor_sgt_template":
            case "anchor_aigc_avatar":
            case "anchor_auto_cut":
            case "anchor_editor_pro":
            case "anchor_sound_sync":
            case "anchor_tts_voice":
            case "anchor_hypic":
            case "anchor_photo_app_upsell":
            case "anchor_voice_filter_anchor":
            case "anchor_tiktok_studio_feed_anchor":
            case "anchor_ai_group_shot":
            case "anchor_text_to_image":
            case "anchor_social_avatar":
                return true;
            default:
                return false;
        }
    }

    /** Hides the left-container risk warning after its native bind path resolves it. */
    private int installRiskWarningLabelGate(ClassLoader classLoader) {
        try {
            Class<?> warningType = Class.forName(
                    "com.ss.android.ugc.aweme.feed.assem.tns.label.TnSWarningLabelAssem",
                    false,
                    classLoader);
            Class<?> paramsType = Class.forName(
                    "com.ss.android.ugc.aweme.feed.model.VideoItemParams",
                    false,
                    classLoader);
            Field warningTypeField = warningType.getDeclaredField("LLLF");
            Method bind = null;
            for (Method method : warningType.getDeclaredMethods()) {
                Class<?>[] parameters = method.getParameterTypes();
                if (!Modifier.isStatic(method.getModifiers())
                        && method.getReturnType() == void.class
                        && parameters.length == 1
                        && parameters[0] == paramsType
                        && !method.isBridge()) {
                    bind = method;
                    if ("rr".equals(method.getName())) {
                        break;
                    }
                }
            }
            if (bind == null) {
                throw new NoSuchMethodException(
                        "TnSWarningLabelAssem#bind(VideoItemParams): void");
            }
            Method contentViewMethod = warningType.getMethod("getContentView");
            Method slotViewMethod = warningType.getMethod("LJJIJLIJ");
            if (!View.class.isAssignableFrom(contentViewMethod.getReturnType())
                    || !View.class.isAssignableFrom(slotViewMethod.getReturnType())) {
                throw new NoSuchMethodException(
                        "TnSWarningLabelAssem content/slot view method");
            }
            warningTypeField.setAccessible(true);
            bind.setAccessible(true);
            hook(bind)
                    .setId("toki-purify-risk-warning-label-4643")
                    .intercept(chain -> {
                        Object result = chain.proceed();
                        Object component = chain.getThisObject();
                        Object type = warningTypeField.get(component);
                        if (type instanceof Enum<?>
                                && "RISK".equals(((Enum<?>) type).name())) {
                            hideView(contentViewMethod.invoke(component));
                            hideView(slotViewMethod.invoke(component));
                        }
                        return result;
                    });
            return 1;
        } catch (ClassNotFoundException ignored) {
            return 0;
        } catch (Throwable error) {
            logError("Unable to hide 46.4.3 risk-warning label", error);
            return 0;
        }
    }

    private static void hideView(Object value) {
        if (value instanceof View) {
            ((View) value).setVisibility(View.GONE);
        }
    }
}
