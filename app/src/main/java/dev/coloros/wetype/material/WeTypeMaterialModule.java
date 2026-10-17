package dev.coloros.wetype.material;

import android.content.res.Configuration;
import android.inputmethodservice.InputMethodService;
import android.util.Log;
import android.view.View;
import android.view.Window;
import android.view.ViewTreeObserver;
import java.lang.ref.WeakReference;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import io.github.libxposed.api.XposedModule;
import io.github.libxposed.api.error.XposedFrameworkError;

/** API 102 module entry; the framework constructs this with a public no-arg constructor. */
public final class WeTypeMaterialModule extends XposedModule {
    private static final String TAG = "WeTypeColorOSMaterial";
    private static final String PREFIX = "com.tencent.wetype.plugin.hld.";
    private final ThreadLocal<Boolean> nativeUpdate = new ThreadLocal<>();
    private final ThreadLocal<Boolean> restoringContent = new ThreadLocal<>();
    private final List<HookHandle> hooks = new ArrayList<>();
    private WeakReference<InputMethodService> service = new WeakReference<>(null);
    private WeakReference<View> inputView = new WeakReference<>(null);
    private ColorOsMaterial material;
    private WeTypeTheme theme;
    private ToolbarMaterial toolbar;
    private ClipboardMaterial clipboard;
    private AuxiliaryPagesMaterial auxiliary;
    private FloatingMaterial floating;
    private Field controllerField;
    private Method refreshController, updateController, enabledController;
    private boolean installed;
    private boolean refreshPending;
    private int transparentAttr;
    private ViewTreeObserver drawObserver;
    private ViewTreeObserver.OnDrawListener afterDraw;
    private ViewTreeObserver preDrawObserver;
    private ViewTreeObserver.OnPreDrawListener beforeDraw;

    @Override public void onPackageReady(PackageReadyParam param) {
        if (!"com.tencent.wetype".equals(param.getPackageName()) || installed) return;
        try {
            ClassLoader loader = param.getClassLoader();
            WeTypeCompatibility compatibility = new WeTypeCompatibility(loader);
            theme = new WeTypeTheme(loader);
            material = new ColorOsMaterial(loader, this, theme);
            Class<?> ime = Class.forName(PREFIX + "WxHldService", false, loader);
            Class<?> controller = Class.forName(PREFIX + "frosted.f", false, loader);
            // Resolve by type because JADX restores Kotlin field names from metadata.
            controllerField = fieldOfType(ime, controller);
            refreshController = method(controller, "k", boolean.class);
            updateController = method(controller, "n", View.class, boolean.class);
            enabledController = method(controller, "h");
            Method applyWindow = method(controller, "c", Window.class);
            Method changeMode = method(controller, "d", Window.class, boolean.class, boolean.class);
            Method feature = method(Class.forName(PREFIX + "feature.a", false, loader), compatibility.frostedFeature);

            add(feature, chain -> Boolean.TRUE.equals(restoringContent.get()) ? false
                    : Boolean.TRUE.equals(nativeUpdate.get()) ? true : chain.proceed());
            add(applyWindow, chain -> {
                if (Boolean.TRUE.equals(nativeUpdate.get()) || material.isActive()) {
                    if (material.activate((Window) chain.getArg(0))) return null;
                }
                return chain.proceed();
            });
            add(changeMode, chain -> {
                Object result = chain.proceed();
                if (!((Boolean) chain.getArg(1))) release();
                return result;
            });
            add(updateController, chain -> {
                boolean ready = material.prepare(inputView.get());
                if (!ready) release();
                Boolean previous = nativeUpdate.get();
                nativeUpdate.set(ready);
                Object result;
                try {
                    result = chain.proceed();
                    try {
                        // The controller may keep an enabled window without calling c().
                        if (ready && (boolean) enabledController.invoke(chain.getThisObject())) {
                            InputMethodService host = service.get();
                            Window window = host != null && host.getWindow() != null
                                    ? host.getWindow().getWindow() : null;
                            if (window != null && !material.activate(window)) {
                                restoreContent(chain.getThisObject(), chain.getArgs().toArray());
                            }
                        } else if (!ready || !(boolean) enabledController.invoke(chain.getThisObject())) release();
                    } catch (Exception | LinkageError error) {
                        restoreContent(chain.getThisObject(), chain.getArgs().toArray());
                        error("reconcile material", error);
                    }
                } finally {
                    if (previous == null) nativeUpdate.remove(); else nativeUpdate.set(previous);
                }
                return result;
            });
            // u2() and c() are small methods that ART may have inlined in these callers.
            deoptimize(updateController);
            deoptimize(changeMode);
            deoptimize(method(controller, "i", Window.class));

            // Avoid applying WeType's second tint on top of Xiaobu's native material.
            Class<?> skins = Class.forName(PREFIX + "frosted.i", false, loader);
            for (String name : new String[]{"e", "f"}) {
                add(method(skins, name, boolean.class), chain ->
                        material.isActive() && (boolean) chain.getArg(0) && transparentAttr != 0
                                ? transparentAttr : chain.proceed());
            }
            new ButtonMaterialHooks(loader, this, this::isMaterialActive, theme, compatibility);
            toolbar = new ToolbarMaterial(this, material, theme);
            clipboard = new ClipboardMaterial(loader, this, this::isMaterialActive, theme, this::requestRefresh);
            try {
                auxiliary = new AuxiliaryPagesMaterial(loader, this, material::isActive,
                        theme, this::requestRefresh);
            } catch (Exception | LinkageError error) {
                error("auxiliary pages not available on this WeType build", error);
            }
            floating = new FloatingMaterial(loader, this, theme, clipboard, () -> inputView.get(),
                    this::isMaterialActive);
            Class<?> keyboardTypes = Class.forName(PREFIX + "keyboard.u", false, loader);
            add(method(keyboardTypes, "b", int.class), chain ->
                    (Boolean.TRUE.equals(nativeUpdate.get()) || material.isActive())
                            && (clipboard.supports((int) chain.getArg(0))
                                || (auxiliary != null && auxiliary.supports((int) chain.getArg(0))))
                            ? true : chain.proceed());
            deoptimize(updateController);
            Class<?> themeManager = compatibility.themeManager;
            add(method(themeManager, "E2", boolean.class, String.class), chain -> {
                Object result = chain.proceed();
                try {
                    theme.refresh();
                    info("WeType appearance changed: systemDark=" + theme.isDark());
                } catch (Exception | LinkageError error) { error("read system appearance", error); }
                requestRefresh();
                return result;
            });
            deoptimize(method(themeManager, "F2", themeManager, boolean.class,
                    String.class, int.class, Object.class));
            // Skin changes call E2 from this listener; prevent ART from bypassing the theme hook.
            Class<?> skinListener = Class.forName(PREFIX + "skin.e$c", false, loader);
            for (Method callback : skinListener.getDeclaredMethods()) deoptimize(callback);
            add(method(ime, "onCreateInputView"), chain -> {
                Object result = chain.proceed();
                try {
                    bind((InputMethodService) chain.getThisObject(), (View) result);
                } catch (Exception | LinkageError error) { error("bind input view", error); }
                return result;
            });
            add(method(InputMethodService.class, "setInputView", View.class), chain -> {
                Object result = chain.proceed();
                if (ime.isInstance(chain.getThisObject())) {
                    try {
                        bind((InputMethodService) chain.getThisObject(), (View) chain.getArg(0));
                    } catch (Exception | LinkageError error) { error("bind supplied input view", error); }
                }
                return result;
            });
            for (String name : new String[]{"onWindowShown", "onStartInputView"}) {
                Method callback = name.equals("onStartInputView")
                        ? method(ime, name, android.view.inputmethod.EditorInfo.class, boolean.class)
                        : method(ime, name);
                add(callback, chain -> {
                    Object result = chain.proceed();
                    requestRefresh();
                    if (name.equals("onWindowShown")) { floating.resume(); refreshAfterFirstDraw(); }
                    return result;
                });
            }
            add(method(ime, "onConfigurationChanged", Configuration.class), chain -> {
                Object result = chain.proceed();
                requestRefresh();
                return result;
            });
            for (String name : new String[]{"onWindowHidden", "onDestroy"}) {
                add(method(ime, name), chain -> {
                    floating.suspend();
                    cancelAfterDraw();
                    release();
                    Object result = chain.proceed();
                    if (name.equals("onDestroy")) {
                        floating.detach();
                        detachBeforeDraw();
                        toolbar.detach(); clipboard.detach();
                        if (auxiliary != null) auxiliary.detach();
                        service.clear(); inputView.clear(); theme.bind(null);
                    }
                    return result;
                });
            }
            installed = true;
            info("API 102 hooks installed for WeType " + compatibility.label
                    + "; module=1.0.0; process=" + android.app.Application.getProcessName());
        } catch (Exception | LinkageError | XposedFrameworkError error) {
            for (HookHandle handle : hooks) handle.unhook();
            hooks.clear();
            error("module not installed: unsupported ROM or WeType signatures", error);
        }
    }

    private void bind(InputMethodService host, View view) {
        if (view == null || inputView.get() == view) return;
        cancelAfterDraw();
        detachBeforeDraw();
        release();
        refreshPending = false;
        theme.bind(host);
        service = new WeakReference<>(host);
        inputView = new WeakReference<>(view);
        toolbar.attach(view);
        clipboard.attach(view);
        if (auxiliary != null) auxiliary.attach(view);
        info("input view bound: " + view.getClass().getName());
        transparentAttr = view.getResources().getIdentifier("transparent", "attr", "com.tencent.wetype");
        view.addOnAttachStateChangeListener(new View.OnAttachStateChangeListener() {
            @Override public void onViewAttachedToWindow(View v) {
                if (inputView.get() == v) {
                    watchBeforeDraw(v);
                    toolbar.attach(v); clipboard.attach(v);
                    if (auxiliary != null) auxiliary.attach(v);
                    requestRefresh(); refreshAfterFirstDraw();
                }
            }
            @Override public void onViewDetachedFromWindow(View v) {
                if (inputView.get() == v) {
                    refreshPending = false; cancelAfterDraw(); detachBeforeDraw();
                    release(); toolbar.detach(); clipboard.detach();
                    if (auxiliary != null) auxiliary.detach();
                }
            }
        });
        view.addOnLayoutChangeListener((v, l, t, r, b, ol, ot, or, ob) -> {
            if (inputView.get() != v) return;
            boolean wasActive = material.isActive();
            material.refresh();
            toolbar.refresh();
            clipboard.refresh();
            refreshAuxiliaryMaterial();
            if (wasActive && !material.isActive()) recoverContent(v);
        });
        watchBeforeDraw(view);
        requestRefresh();
        refreshAfterFirstDraw();
    }

    private void requestRefresh() {
        View view = inputView.get();
        if (view == null || refreshPending) return;
        refreshPending = true;
        view.post(() -> {
            refreshPending = false;
            InputMethodService host = service.get();
            if (host == null || !view.isAttachedToWindow() || inputView.get() != view) return;
            try {
                theme.syncSkin();
                Object controller = controllerField.get(host);
                refreshController.invoke(controller, true);
                boolean wasActive = material.isActive();
                material.refresh();
                if (wasActive && !material.isActive())
                    restoreContent(controller, new Object[]{view, true});
                toolbar.refresh();
                clipboard.refresh();
                refreshAuxiliaryMaterial();
                floating.refresh(true);
            } catch (Exception | LinkageError error) { error("refresh material", error); }
        });
    }

    private void refreshAuxiliaryMaterial() {
        if (auxiliary == null) return;
        auxiliary.refresh();
        material.setChooserSheet(auxiliary.chooserVisible());
        material.refresh();
    }

    private void release() {
        material.release();
        if (toolbar != null) toolbar.clear();
        if (clipboard != null) clipboard.clear();
        if (auxiliary != null) auxiliary.clear();
    }

    private boolean isMaterialActive() {
        return material.isActive() || (floating != null && floating.isActive());
    }

    private void recoverContent(View view) {
        InputMethodService host = service.get();
        if (host != null) try {
            restoreContent(controllerField.get(host), new Object[]{view, true});
        } catch (Exception | LinkageError error) { error("recover material content", error); }
        requestRefresh();
    }

    private void watchBeforeDraw(View view) {
        if (!view.isAttachedToWindow()) return;
        ViewTreeObserver observer = view.getViewTreeObserver();
        if (preDrawObserver == observer) return;
        detachBeforeDraw();
        beforeDraw = () -> {
            if (inputView.get() == view && material.needsRebind()) {
                material.refresh();
                if (!material.isActive()) recoverContent(view);
                toolbar.refresh(); clipboard.refresh();
                refreshAuxiliaryMaterial();
            }
            return true;
        };
        preDrawObserver = observer;
        observer.addOnPreDrawListener(beforeDraw);
    }

    private void detachBeforeDraw() {
        if (preDrawObserver != null && preDrawObserver.isAlive() && beforeDraw != null)
            preDrawObserver.removeOnPreDrawListener(beforeDraw);
        preDrawObserver = null; beforeDraw = null;
    }

    private void restoreContent(Object controller, Object[] args) {
        release();
        Boolean previous = restoringContent.get();
        restoringContent.set(true);
        try {
            getInvoker(updateController).setType(Invoker.Type.ORIGIN).invoke(controller, args);
            info("native material unavailable; original opaque content restored");
        } catch (Exception | LinkageError error) { error("restore original content", error); }
        finally {
            if (previous == null) restoringContent.remove(); else restoringContent.set(previous);
        }
    }

    private void refreshAfterFirstDraw() {
        View view = inputView.get();
        if (view == null || !view.isAttachedToWindow()) return;
        cancelAfterDraw();
        ViewTreeObserver observer = view.getViewTreeObserver();
        ViewTreeObserver.OnDrawListener listener = new ViewTreeObserver.OnDrawListener() {
            private boolean posted;
            @Override public void onDraw() {
                if (posted) return;
                posted = true;
                view.post(() -> {
                    if (afterDraw != this) return;
                    cancelAfterDraw();
                    if (inputView.get() == view && view.getWindowVisibility() == View.VISIBLE) requestRefresh();
                });
            }
        };
        drawObserver = observer; afterDraw = listener;
        observer.addOnDrawListener(listener);
        view.invalidate();
    }

    private void cancelAfterDraw() {
        if (drawObserver != null && drawObserver.isAlive() && afterDraw != null)
            drawObserver.removeOnDrawListener(afterDraw);
        drawObserver = null; afterDraw = null;
    }

    void add(Method method, Hooker callback) {
        hooks.add(hook(method).intercept(callback));
    }

    private static Method method(Class<?> type, String name, Class<?>... args) throws NoSuchMethodException {
        Method method = type.getDeclaredMethod(name, args);
        method.setAccessible(true);
        return method;
    }

    private static Field fieldOfType(Class<?> type, Class<?> fieldType) throws NoSuchFieldException {
        for (Field field : type.getDeclaredFields()) {
            if (field.getType() == fieldType) { field.setAccessible(true); return field; }
        }
        throw new NoSuchFieldException(type.getName() + ": " + fieldType.getName());
    }

    void info(String message) { log(Log.INFO, TAG, message); }
    void error(String message, Throwable error) { log(Log.ERROR, TAG, message, error); }
}
