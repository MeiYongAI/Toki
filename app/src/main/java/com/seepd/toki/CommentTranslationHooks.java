package com.seepd.toki;

import android.content.Context;
import android.content.res.Configuration;
import android.graphics.drawable.Drawable;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewParent;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.RelativeLayout;

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
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

import io.github.libxposed.api.XposedModule;

/** Restores and maintains the TikTok 46.4.3 comment translation control. */
final class CommentTranslationHooks extends HookFeature {
    private static final String BUTTON_TAG = "toki-official-comment-translation-button";
    private static final int MAX_TRACKED_COMMENTS = 512;

    private final AtomicBoolean failureLogged = new AtomicBoolean(false);
    private final Object translationLock = new Object();
    private final LinkedHashMap<String, BoundComment> boundComments = new LinkedHashMap<>();
    private final HashSet<String> translationRequests = new HashSet<>();
    private final Context stateContext;
    private volatile boolean translationEnabled;
    private volatile String commentPageAwemeId;
    private volatile String translatedAwemeId;

    CommentTranslationHooks(XposedModule module, Context stateContext, boolean translationEnabled) {
        super(module);
        this.stateContext = stateContext;
        this.translationEnabled = translationEnabled;
    }

    /** Restores the comment-page translation control in the TikTok 46.4.3 target. */
    void install(ClassLoader classLoader) {
        if (!installTikTok464CommentTranslationButton(classLoader)) {
            logInfo("TikTok 46.4.3 comment translation bridge unavailable");
        }
    }

    /**
     * Uses public translation interfaces and structural discovery for obfuscated comment-cell
     * members. The same path therefore survives ordinary TikTok minor-version renaming.
     */
    private boolean installTikTok464CommentTranslationButton(ClassLoader classLoader) {
        try {
            Class<?> commentType = Class.forName(
                    "com.ss.android.ugc.aweme.comment.model.Comment", false, classLoader);
            Class<?> baseCommentCell = Class.forName(
                    "com.ss.android.ugc.aweme.commentv2.commentlist.powercell.BaseCommentCell",
                    false,
                    classLoader);
            TranslationBindingMembers bindingMembers = findCommentTranslationMembers(
                    baseCommentCell, commentType);

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
            Class<?> translationServiceInterface = Class.forName(
                    "com.ss.android.ugc.aweme.translation.service.ITranslationService",
                    false,
                    classLoader);
            Class<?> serviceManagerType = Class.forName(
                    "com.ss.android.ugc.aweme.framework.services.ServiceManager",
                    false,
                    classLoader);

            Method getAwemeId = commentType.getMethod("getAwemeId");
            Method getCommentId = commentType.getMethod("getCid");
            Method isTranslated = commentType.getMethod("isTranslated");
            Field translationManager = bindingMembers.manager;
            Field boundComment = bindingMembers.comment;
            Field translationAction = bindingMembers.action;
            Method translate = bindingMembers.translate;
            Method resetTranslate = bindingMembers.reset;
            Method onActionBarCreated = actionBarType.getDeclaredMethod("onViewCreated", View.class);
            Method getCommentContext = findNoArgMethodReturningType(actionBarType, contextSourceType);
            Method getAweme = contextSourceKt.getMethod("aweme", contextSourceType);
            Method getAid = awemeType.getMethod("getAid");
            // A missing close-icon member should never prevent injection into the provided root.
            Field closeButton = findField(actionBarType, "LLJJLIIIJLLLLLLLZ");
            Method isTranslatable = findCommentTranslatabilityMethod(
                    translationServiceInterface, commentType);
            Method setTranslation = findPreferredMethodBySignature(
                    translationServiceInterface,
                    void.class,
                    new Class<?>[]{commentType, boolean.class},
                    "LJJJJZI",
                    "setTranslation",
                    "setCommentTranslation");
            Method getServiceManager = serviceManagerType.getMethod("get");
            Method getService = serviceManagerType.getMethod("getService", Class.class);
            Constructor<?> newTuxIconView = tuxIconViewType.getConstructor(Context.class);
            Method setIconRes = tuxIconViewType.getMethod("setIconRes", int.class);
            Method setIconWidth = tuxIconViewType.getMethod("setIconWidth", int.class);
            Method setIconHeight = tuxIconViewType.getMethod("setIconHeight", int.class);
            Method setTintColor = tuxIconViewType.getMethod("setTintColor", int.class);
            Method setTintColorRes = tuxIconViewType.getMethod("setTintColorRes", int.class);

            translationManager.setAccessible(true);
            boundComment.setAccessible(true);
            translationAction.setAccessible(true);
            if (getCommentContext != null) {
                getCommentContext.setAccessible(true);
            }
            setTranslation.setAccessible(true);

            OfficialTranslationBridge bridge = new OfficialTranslationBridge(
                    translationManager,
                    boundComment,
                    translationAction,
                    getAwemeId,
                    getCommentId,
                    isTranslated,
                    translate,
                    resetTranslate,
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
                    new DirectTranslationBridge(
                            setTranslation,
                            isTranslatable,
                            getServiceManager,
                            getService,
                            translationServiceInterface));

            int bindHooks = hookTikTok464CommentBindingMethods(baseCommentCell, bridge);
            if (bindHooks == 0) {
                throw new NoSuchMethodException("BaseCommentCell#onBindItemView/Q6/K6(*)");
            }
            hook(onActionBarCreated)
                    .setId("toki-464-comment-translation-action-bar")
                    .intercept(chain -> {
                        Object result = chain.proceed();
                        Object root = chain.getArg(0);
                        injectOfficialCommentTranslationButton(
                                chain.getThisObject(), root instanceof View ? (View) root : null, bridge);
                        return result;
                    });
            logInfo("Enabled official TikTok 46.4.3 comment translation button hooks");
            return true;
        } catch (ClassNotFoundException | NoSuchMethodException | NoSuchFieldException error) {
            logInfo("Stable comment translation symbols unavailable: "
                    + error.getClass().getSimpleName() + ": " + error.getMessage());
            return false;
        } catch (Throwable error) {
            logError("Unable to enable TikTok 46.4.3 comment translation button", error);
            return false;
        }
    }

    private int hookTikTok464CommentBindingMethods(
            Class<?> baseCommentCell,
            OfficialTranslationBridge bridge) {
        int bindHooks = 0;
        for (Method method : baseCommentCell.getDeclaredMethods()) {
            String name = method.getName();
            boolean supportedName = "onBindItemView".equals(name)
                    || "Q6".equals(name)
                    || "K6".equals(name);
            if (!supportedName
                    || method.getParameterCount() != 1
                    || Modifier.isStatic(method.getModifiers())
                    || (("Q6".equals(name) || "K6".equals(name))
                    && method.getReturnType() != void.class)) {
                continue;
            }
            method.setAccessible(true);
            final int hookIndex = bindHooks++;
            hook(method)
                    .setId("toki-464-comment-translation-bind-" + hookIndex)
                    .intercept(chain -> {
                        Object result = chain.proceed();
                        captureTikTok464CommentBinding(chain.getThisObject(), bridge);
                        return result;
                    });
        }
        return bindHooks;
    }

    /** Finds the current cell's Comment and native translation action by their stable shape. */
    private static TranslationBindingMembers findCommentTranslationMembers(
            Class<?> baseCommentCell,
            Class<?> commentType) throws NoSuchFieldException {
        // Prefer the verified 46.4.3 holder when present, then fall back to structural discovery.
        for (Class<?> current = baseCommentCell; current != null; current = current.getSuperclass()) {
            for (Field managerField : current.getDeclaredFields()) {
                if (!"LLJJIJI".equals(managerField.getName())) {
                    continue;
                }
                TranslationBindingMembers preferred = createTranslationBindingMembers(
                        managerField, commentType);
                if (preferred != null) {
                    return preferred;
                }
            }
        }
        for (Class<?> current = baseCommentCell; current != null; current = current.getSuperclass()) {
            for (Field managerField : current.getDeclaredFields()) {
                if (Modifier.isStatic(managerField.getModifiers()) || managerField.getType().isPrimitive()) {
                    continue;
                }
                TranslationBindingMembers discovered = createTranslationBindingMembers(
                        managerField, commentType);
                if (discovered != null) {
                    return discovered;
                }
            }
        }
        throw new NoSuchFieldException("Comment translation manager/action on "
                + baseCommentCell.getName());
    }

    private static TranslationBindingMembers createTranslationBindingMembers(
            Field managerField,
            Class<?> commentType) {
        if (Modifier.isStatic(managerField.getModifiers()) || managerField.getType().isPrimitive()) {
            return null;
        }
        Field commentField = findFieldByType(managerField.getType(), commentType);
        if (commentField == null) {
            return null;
        }
        for (Class<?> managerType = managerField.getType(); managerType != null;
                managerType = managerType.getSuperclass()) {
            for (Field actionField : managerType.getDeclaredFields()) {
                if (Modifier.isStatic(actionField.getModifiers())
                        || actionField.getType().isPrimitive()) {
                    continue;
                }
                Method translate = findNoArgVoidMethod(actionField.getType(), "translate");
                Method reset = findNoArgVoidMethod(actionField.getType(), "resetTranslate", "LIZJ");
                if (translate == null || reset == null) {
                    continue;
                }
                managerField.setAccessible(true);
                commentField.setAccessible(true);
                actionField.setAccessible(true);
                return new TranslationBindingMembers(
                        managerField, commentField, actionField, translate, reset);
            }
        }
        return null;
    }

    private static Field findFieldByType(Class<?> owner, Class<?> expectedType) {
        for (Class<?> current = owner; current != null; current = current.getSuperclass()) {
            for (Field field : current.getDeclaredFields()) {
                if (!Modifier.isStatic(field.getModifiers())
                        && expectedType.isAssignableFrom(field.getType())) {
                    field.setAccessible(true);
                    return field;
                }
            }
        }
        return null;
    }

    private static Method findNoArgVoidMethod(Class<?> type, String... names) {
        if (type == null) {
            return null;
        }
        for (String name : names) {
            for (Method method : type.getMethods()) {
                if (name.equals(method.getName())
                        && method.getParameterCount() == 0
                        && method.getReturnType() == void.class) {
                    method.setAccessible(true);
                    return method;
                }
            }
            for (Class<?> current = type; current != null; current = current.getSuperclass()) {
                for (Method method : current.getDeclaredMethods()) {
                    if (name.equals(method.getName())
                            && method.getParameterCount() == 0
                            && method.getReturnType() == void.class) {
                        method.setAccessible(true);
                        return method;
                    }
                }
            }
        }
        return null;
    }

    private static Method findNoArgMethodReturningType(Class<?> owner, Class<?> returnType) {
        Method candidate = null;
        for (Class<?> current = owner; current != null; current = current.getSuperclass()) {
            for (Method method : current.getDeclaredMethods()) {
                if (Modifier.isStatic(method.getModifiers())
                        || method.getParameterCount() != 0
                        || method.getReturnType() != returnType) {
                    continue;
                }
                if (candidate != null) {
                    return null;
                }
                method.setAccessible(true);
                candidate = method;
            }
        }
        return candidate;
    }

    private static Method findUniqueMethodBySignature(
            Class<?> owner,
            Class<?> returnType,
            Class<?>... parameterTypes) throws NoSuchMethodException {
        Method candidate = null;
        for (Method method : owner.getMethods()) {
            if (method.getReturnType() != returnType
                    || method.getParameterCount() != parameterTypes.length) {
                continue;
            }
            Class<?>[] actualParameters = method.getParameterTypes();
            boolean matches = true;
            for (int index = 0; index < parameterTypes.length; index++) {
                if (actualParameters[index] != parameterTypes[index]) {
                    matches = false;
                    break;
                }
            }
            if (!matches) {
                continue;
            }
            if (candidate != null) {
                throw new NoSuchMethodException("Ambiguous translation method on "
                        + owner.getName());
            }
            method.setAccessible(true);
            candidate = method;
        }
        if (candidate == null) {
            throw new NoSuchMethodException("Translation method on " + owner.getName());
        }
        return candidate;
    }

    private static Method findCommentTranslatabilityMethod(
            Class<?> owner,
            Class<?> commentType) throws NoSuchMethodException {
        return findPreferredMethodBySignature(
                owner,
                boolean.class,
                new Class<?>[]{commentType},
                "LJIILJJIL",
                "isTranslatable",
                "canTranslate",
                "isCommentTranslatable");
    }

    private static Method findPreferredMethodBySignature(
            Class<?> owner,
            Class<?> returnType,
            Class<?>[] parameterTypes,
            String... preferredNames) throws NoSuchMethodException {
        for (String preferredName : preferredNames) {
            for (Method method : owner.getMethods()) {
                if (preferredName.equals(method.getName())
                        && method.getReturnType() == returnType
                        && hasExactParameters(method, parameterTypes)) {
                    method.setAccessible(true);
                    return method;
                }
            }
        }
        return findUniqueMethodBySignature(owner, returnType, parameterTypes);
    }

    private static boolean hasExactParameters(Method method, Class<?>[] parameterTypes) {
        Class<?>[] actualParameters = method.getParameterTypes();
        if (actualParameters.length != parameterTypes.length) {
            return false;
        }
        for (int index = 0; index < parameterTypes.length; index++) {
            if (actualParameters[index] != parameterTypes[index]) {
                return false;
            }
        }
        return true;
    }

    private void captureTikTok464CommentBinding(
            Object cell,
            OfficialTranslationBridge bridge) {
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
            boolean translateNow;
            synchronized (translationLock) {
                boundComments.put(
                        key, new BoundComment(key, awemeId, comment, action));
                pruneOfficialCommentBindingsLocked();
                translateNow = isOfficialTranslationActive(awemeId)
                        && !Boolean.TRUE.equals(bridge.isTranslated.invoke(comment))
                        && translationRequests.add(key);
            }
            if (translateNow && !translateOfficialComment(comment, action, bridge)) {
                synchronized (translationLock) {
                    translationRequests.remove(key);
                }
            }
        } catch (ReflectiveOperationException | RuntimeException error) {
            logOfficialTranslationFailure("Unable to capture a TikTok 46.4.3 bound comment", error);
        }
    }

    private void observeOfficialCommentPage(String awemeId) {
        if (awemeId == null || awemeId.isEmpty()) {
            return;
        }
        synchronized (translationLock) {
            if (awemeId.equals(commentPageAwemeId)) {
                return;
            }
            commentPageAwemeId = awemeId;
            translatedAwemeId = translationEnabled ? awemeId : null;
            translationRequests.clear();
            boundComments.entrySet().removeIf(
                    entry -> !awemeId.equals(entry.getValue().awemeId));
        }
        logInfo("Observed comment page for aweme " + awemeId);
    }

    private void resetOfficialCommentPageState() {
        synchronized (translationLock) {
            commentPageAwemeId = null;
            translatedAwemeId = null;
            translationRequests.clear();
            boundComments.clear();
        }
    }

    private boolean isOfficialTranslationActive(String awemeId) {
        return translationEnabled && awemeId != null && !awemeId.isEmpty()
                && awemeId.equals(translatedAwemeId);
    }

    private void setOfficialTranslationEnabled(boolean enabled) {
        translationEnabled = enabled;
        Context context = stateContext;
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
        synchronized (translationLock) {
            return commentPageAwemeId == null ? "" : commentPageAwemeId;
        }
    }

    private void injectOfficialCommentTranslationButton(
            Object actionBar,
            View actionBarRoot,
            OfficialTranslationBridge bridge) {
        try {
            View closeButton = bridge.closeButton == null
                    ? null
                    : (View) bridge.closeButton.get(actionBar);
            ViewGroup host = findOfficialTranslationButtonHost(closeButton, actionBarRoot);
            if (host == null) {
                return;
            }
            // TikTok may reuse the action-bar view for the next video's comment sheet.
            // Recreate our control so no icon state or listener closure leaks across videos.
            View previousButton = findTaggedChild(host, BUTTON_TAG);
            if (previousButton != null) {
                host.removeView(previousButton);
            }

            Context context = host.getContext();
            int normalIcon = context.getResources().getIdentifier(
                    "icon_languages", "raw", context.getPackageName());
            int translatedIcon = context.getResources().getIdentifier(
                    "icon_languages_tick", "raw", context.getPackageName());
            if (normalIcon == 0 || translatedIcon == 0) {
                // These are stable in the verified official 46.4.3 resource table.
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
            button.setTag(BUTTON_TAG);
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
            boolean translated = translationEnabled;
            updateOfficialTranslationButton(
                    iconObject, button, translated, normalIcon, translatedIcon, bridge);
            final int finalNormalIcon = normalIcon;
            final int finalTranslatedIcon = translatedIcon;
            button.setOnClickListener(view -> {
                String activeAwemeId = resolveOfficialCommentPageAwemeId(actionBar, bridge);
                boolean nextState = !translationEnabled;
                setOfficialTranslationEnabled(nextState);
                if (!activeAwemeId.isEmpty()) {
                    observeOfficialCommentPage(activeAwemeId);
                    int affected = setOfficialCommentTranslationState(
                            activeAwemeId, nextState, bridge);
                    if (nextState && affected == 0) {
                        logInfo("Official comment translation armed for comments loaded later");
                    }
                }
                try {
                    updateOfficialTranslationButton(
                            iconObject,
                            button,
                            translationEnabled,
                            finalNormalIcon,
                            finalTranslatedIcon,
                            bridge);
                } catch (ReflectiveOperationException | RuntimeException error) {
                    logOfficialTranslationFailure("Unable to update the translation button", error);
                }
            });
            if (translationEnabled && !pageAwemeId.isEmpty()) {
                final String initialAwemeId = pageAwemeId;
                button.post(() -> {
                    String activeAwemeId = resolveOfficialCommentPageAwemeId(actionBar, bridge);
                    if (translationEnabled && initialAwemeId.equals(activeAwemeId)) {
                        setOfficialCommentTranslationState(initialAwemeId, true, bridge);
                    }
                });
            }
            logInfo("Injected official comment translation button for aweme " + pageAwemeId);
        } catch (ReflectiveOperationException | RuntimeException error) {
            logOfficialTranslationFailure("Unable to inject the official translation button", error);
        }
    }

    private int setOfficialCommentTranslationState(
            String requestedAwemeId,
            boolean translated,
            OfficialTranslationBridge bridge) {
        String awemeId = requestedAwemeId;
        List<BoundComment> bindings = new ArrayList<>();
        synchronized (translationLock) {
            pruneOfficialCommentBindingsLocked();
            if (awemeId.isEmpty()) {
                awemeId = commentPageAwemeId == null ? "" : commentPageAwemeId;
            }
            for (BoundComment binding : boundComments.values()) {
                if (awemeId.equals(binding.awemeId)) {
                    bindings.add(binding);
                }
            }
        }
        if (awemeId.isEmpty()) {
            return 0;
        }

        final String targetAwemeId = awemeId;
        synchronized (translationLock) {
            if (translated) {
                translatedAwemeId = targetAwemeId;
            } else {
                if (targetAwemeId.equals(translatedAwemeId)) {
                    translatedAwemeId = null;
                }
                String keyPrefix = targetAwemeId + '\n';
                translationRequests.removeIf(key -> key.startsWith(keyPrefix));
            }
        }

        int affected = 0;
        for (BoundComment binding : bindings) {
            Object comment = binding.comment.get();
            Object action = binding.action.get();
            if (comment == null || action == null) {
                continue;
            }
            if (translated) {
                boolean shouldInvoke;
                synchronized (translationLock) {
                    shouldInvoke = translationRequests.add(binding.key);
                }
                if (!shouldInvoke) {
                    affected++;
                    continue;
                }
                if (translateOfficialComment(comment, action, bridge)) {
                    affected++;
                } else {
                    synchronized (translationLock) {
                        translationRequests.remove(binding.key);
                    }
                }
            } else if (resetOfficialComment(comment, action, bridge)) {
                affected++;
            }
        }

        logInfo((translated ? "Requested translation for " : "Restored ")
                + affected + " loaded comments for aweme " + targetAwemeId);
        return affected;
    }

    private boolean translateOfficialComment(
            Object comment,
            Object action,
            OfficialTranslationBridge bridge) {
        try {
            Object service = resolveDirectTranslationService(bridge.direct);
            if (service == null
                    || !Boolean.TRUE.equals(bridge.direct.isTranslatable.invoke(service, comment))) {
                return false;
            }
            bridge.direct.setTranslation.invoke(service, comment, Boolean.TRUE);
            return invokeOfficialCommentAction(bridge.translate, action);
        } catch (ReflectiveOperationException | RuntimeException error) {
            logOfficialTranslationFailure("Unable to check comment translation eligibility", error);
            return false;
        }
    }

    private boolean resetOfficialComment(
            Object comment,
            Object action,
            OfficialTranslationBridge bridge) {
        try {
            Object service = resolveDirectTranslationService(bridge.direct);
            if (service == null) {
                return false;
            }
            bridge.direct.setTranslation.invoke(service, comment, Boolean.FALSE);
            return invokeOfficialCommentAction(bridge.resetTranslate, action);
        } catch (ReflectiveOperationException | RuntimeException error) {
            logOfficialTranslationFailure("Unable to restore native comment translation", error);
            return false;
        }
    }

    private static Object resolveDirectTranslationService(DirectTranslationBridge bridge)
            throws ReflectiveOperationException {
        Object serviceManager = bridge.serviceManagerGetter.invoke(null);
        return serviceManager == null
                ? null
                : bridge.serviceGetter.invoke(serviceManager, bridge.serviceInterface);
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
            if (bridge.getCommentContext == null) {
                return "";
            }
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
        Iterator<Map.Entry<String, BoundComment>> iterator = boundComments.entrySet().iterator();
        while (iterator.hasNext()) {
            BoundComment binding = iterator.next().getValue();
            if (binding.comment.get() == null) {
                translationRequests.remove(binding.key);
                iterator.remove();
            }
        }
        iterator = boundComments.entrySet().iterator();
        while (boundComments.size() > MAX_TRACKED_COMMENTS && iterator.hasNext()) {
            BoundComment binding = iterator.next().getValue();
            translationRequests.remove(binding.key);
            iterator.remove();
        }
    }

    private void logOfficialTranslationFailure(String message, Throwable error) {
        if (failureLogged.compareAndSet(false, true)) {
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

    private static final class TranslationBindingMembers {
        final Field manager;
        final Field comment;
        final Field action;
        final Method translate;
        final Method reset;

        TranslationBindingMembers(
                Field manager,
                Field comment,
                Field action,
                Method translate,
                Method reset) {
            this.manager = manager;
            this.comment = comment;
            this.action = action;
            this.translate = translate;
            this.reset = reset;
        }
    }

    private static final class DirectTranslationBridge {
        final Method setTranslation;
        final Method isTranslatable;
        final Method serviceManagerGetter;
        final Method serviceGetter;
        final Class<?> serviceInterface;

        DirectTranslationBridge(
                Method setTranslation,
                Method isTranslatable,
                Method serviceManagerGetter,
                Method serviceGetter,
                Class<?> serviceInterface) {
            this.setTranslation = setTranslation;
            this.isTranslatable = isTranslatable;
            this.serviceManagerGetter = serviceManagerGetter;
            this.serviceGetter = serviceGetter;
            this.serviceInterface = serviceInterface;
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
        final DirectTranslationBridge direct;

        OfficialTranslationBridge(
                Field translationManager,
                Field boundComment,
                Field translationAction,
                Method getAwemeId,
                Method getCommentId,
                Method isTranslated,
                Method translate,
                Method resetTranslate,
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
                DirectTranslationBridge direct) {
            this.translationManager = translationManager;
            this.boundComment = boundComment;
            this.translationAction = translationAction;
            this.getAwemeId = getAwemeId;
            this.getCommentId = getCommentId;
            this.isTranslated = isTranslated;
            this.translate = translate;
            this.resetTranslate = resetTranslate;
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
            this.direct = direct;
        }
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
}
