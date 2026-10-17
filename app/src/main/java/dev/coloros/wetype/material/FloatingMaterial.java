package dev.coloros.wetype.material;

import android.annotation.SuppressLint;
import android.graphics.drawable.Drawable;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;
import android.widget.ImageView;
import java.lang.reflect.Method;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.function.Supplier;
import java.util.function.BooleanSupplier;

/** Material on the real floating PopupWindow, independent of the docked IME controller. */
final class FloatingMaterial {
    private final WeTypeMaterialModule module;
    private final WeTypeTheme theme;
    private final ColorOsMaterial material;
    private final ToolbarMaterial toolbar;
    private final ClipboardMaterial clipboard;
    private final AuxiliaryPagesMaterial auxiliary;
    private final Supplier<View> mainRoot;
    private final FloatingPreviewMaterial previews;
    private final Method getSurface, getContent;
    private final Map<View, Saved> saved = new IdentityHashMap<>();
    private final ViewTreeObserver.OnGlobalLayoutListener layout = () -> refresh(true);
    private final ViewTreeObserver.OnPreDrawListener preDraw = () -> { refresh(false); return true; };
    private ViewTreeObserver observer;
    private ViewTreeObserver.OnDrawListener firstDraw;
    private View popup, surface, body;
    private ImageView normalBackground;
    private FloatingResizeFrame editFrame;
    private float normalAlpha;
    private boolean suspended, lastDark, faultLogged;
    private int width, height;

    FloatingMaterial(ClassLoader loader, WeTypeMaterialModule module, WeTypeTheme theme,
                     ClipboardMaterial clipboard, AuxiliaryPagesMaterial auxiliary,
                     Supplier<View> mainRoot, BooleanSupplier active) throws Exception {
        this.module = module; this.theme = theme; this.clipboard = clipboard;
        this.auxiliary = auxiliary; this.mainRoot = mainRoot;
        material = new ColorOsMaterial(loader, module, theme, true);
        toolbar = new ToolbarMaterial(module, material, theme);
        Class<?> content = targetType(loader, "com.tencent.wetype.plugin.hld.float.e");
        getSurface = content.getDeclaredMethod("getRootView");
        getContent = content.getDeclaredMethod("getContent");
        FloatingPreviewMaterial previewMaterial = null;
        try {
            previewMaterial = new FloatingPreviewMaterial(loader, module, theme, active,
                    content, getSurface, getContent);
        } catch (Exception | LinkageError error) {
            module.error("floating resize preview unavailable; keep normal floating material", error);
        }
        previews = previewMaterial;
        module.add(content.getDeclaredMethod("onAttachedToWindow"), chain -> {
            Object result = chain.proceed();
            View view = (View) chain.getThisObject();
            view.post(() -> {
                if (view.isAttachedToWindow()) try { attach(view); }
                catch (Exception | LinkageError error) { module.error("attach floating material", error); detachLive(); }
            });
            return result;
        });
        module.add(content.getDeclaredMethod("onDetachedFromWindow"), chain -> {
            if (previews != null) previews.detach((View) chain.getThisObject());
            if (chain.getThisObject() == popup) detachLive();
            return chain.proceed();
        });
    }

    private static Class<?> targetType(ClassLoader loader, String name) throws ClassNotFoundException {
        // The APK package includes the Java keyword "float"; it is valid bytecode, not Java source.
        return Class.forName(name, false, loader);
    }

    @SuppressLint("ResourceType") // Target APK's keyboard_container_rl, verified in binary XML.
    private void attach(View view) throws ReflectiveOperationException {
        View content = (View) getContent.invoke(view);
        // Resize previews are independent windows with their own background and lifetime.
        if (content.findViewById(2131296987) == null) {
            try { if (previews != null) previews.attach(view); }
            catch (Exception error) { module.error("attach floating resize material", error); }
            return;
        }
        if (popup == view) { refresh(true); return; }
        detachLive();
        popup = view; body = content; surface = (View) getSurface.invoke(view);
        // getRootView() is overridden here to return the rounded panel, not the PopupWindow decor.
        material.setFloatingCorner(((Number) surface.getClass().getMethod("getRadius").invoke(surface)).floatValue());
        if (surface instanceof ViewGroup) {
            ViewGroup root = (ViewGroup) surface;
            if (root.getChildCount() > 0 && root.getChildAt(0) instanceof ImageView) {
                normalBackground = (ImageView) root.getChildAt(0);
                normalAlpha = normalBackground.getAlpha();
            }
            if (root.getChildCount() > 1 && root.getChildAt(1) instanceof ImageView)
                editFrame = new FloatingResizeFrame((ImageView) root.getChildAt(1), body, surface);
        }
        toolbar.attach(view);
        observer = view.getViewTreeObserver();
        observer.addOnGlobalLayoutListener(layout); observer.addOnPreDrawListener(preDraw);
        firstDraw = new ViewTreeObserver.OnDrawListener() {
            private boolean posted;
            @Override public void onDraw() {
                if (posted) return;
                posted = true;
                view.post(() -> {
                    if (popup != view || firstDraw != this) return;
                    if (observer != null && observer.isAlive()) observer.removeOnDrawListener(this);
                    firstDraw = null; refresh(true);
                });
            }
        };
        observer.addOnDrawListener(firstDraw);
        refresh(true);
        module.info("floating popup material bound");
    }

    boolean isActive() { return !suspended && (material.isActive() || (previews != null && previews.isActive())); }

    boolean isPopupActive() { return !suspended && material.isActive(); }

    void resume() { suspended = false; if (previews != null) previews.resume(); refresh(true); }

    void suspend() {
        suspended = true; if (previews != null) previews.suspend(); restore();
        View root = mainRoot.get();
        if (root != null) clipboard.attach(root);
    }

    void refresh(boolean force) {
        if (force && previews != null) previews.refresh();
        if (popup == null || suspended) return;
        if (!popup.isAttachedToWindow() || popup.getWindowVisibility() != View.VISIBLE) { restore(); return; }
        try {
            if (force || !material.isActive() || lastDark != theme.isDark() || width != surface.getWidth() || height != surface.getHeight()
                    || material.needsRebind()) {
                theme.refresh();
                if (!material.isActive()) {
                    if (!material.prepare(surface) || !material.activate(null)) { restore(); return; }
                } else material.refresh();
                if (!material.isActive()) { restore(); return; }
                lastDark = theme.isDark(); width = surface.getWidth(); height = surface.getHeight();
                remember(surface, false); remember(body, true); scan(body);
            }
            if (!material.isActive()) return;
            for (Saved state : saved.values()) state.apply(!clipboard.ownsBackground(state.view));
            if (normalBackground != null && normalBackground.getAlpha() != 0f) {
                normalAlpha = normalBackground.getAlpha(); normalBackground.setAlpha(0f);
            }
            if (editFrame != null) editFrame.apply();
            // Floating content is a separate View root; scan its active auxiliary
            // page against the popup blur, not the hidden docked IME panel.
            if (auxiliary != null) {
                auxiliary.attachFloating(popup);
                if (force) auxiliary.refresh();
            }
            clipboard.attach(popup); clipboard.refresh(); toolbar.refresh();
        } catch (Exception | LinkageError error) {
            restore();
            if (!faultLogged) { module.error("floating material unavailable; restore original panel", error); faultLogged = true; }
        }
    }

    private void remember(View view, boolean background) {
        if (clipboard.ownsBackground(view)) return;
        if (!saved.containsKey(view)) saved.put(view, new Saved(view, background));
    }

    private void scan(View view) {
        // Only panel layers; key faces, content cards, text, images and gesture targets keep their roles.
        switch (view.getId()) {
            case 2131297824: case 2131296425: case 2131296487: case 2131296932:
            case 2131297374: case 2131296987: case 2131296931: case 2131296951:
            case 2131296952: case 2131296948: case 2131296949:
                remember(view, true); break;
            default: break;
        }
        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int i = 0; i < group.getChildCount(); i++) scan(group.getChildAt(i));
        }
    }

    private void restore() {
        if (auxiliary != null && auxiliary.usingFloatingRoot()) {
            View docked = mainRoot.get();
            if (docked != null) auxiliary.attach(docked);
            else auxiliary.detach();
        }
        if (popup != null) clipboard.clear();
        toolbar.clear(); material.release();
        for (Saved state : saved.values()) if (belongsToPopup(state.view)) state.restore();
        saved.clear();
        if (normalBackground != null && normalBackground.getAlpha() == 0f) normalBackground.setAlpha(normalAlpha);
        if (editFrame != null) editFrame.restore();
    }

    private boolean belongsToPopup(View view) {
        // Returning to docked mode reparents the same keyboard. Do not overwrite its new owner's skin.
        for (int i = 0; view != null && i < 32; i++) {
            if (view == popup) return true;
            view = view.getParent() instanceof View ? (View) view.getParent() : null;
        }
        return false;
    }

    void detach() {
        if (previews != null) previews.clear();
        detachLive();
    }

    private void detachLive() {
        if (observer != null && observer.isAlive()) {
            observer.removeOnGlobalLayoutListener(layout); observer.removeOnPreDrawListener(preDraw);
            if (firstDraw != null) observer.removeOnDrawListener(firstDraw);
        }
        restore(); toolbar.detach();
        observer = null; firstDraw = null; popup = surface = body = null;
        normalBackground = null; editFrame = null;
        width = height = 0; faultLogged = false;
        View root = mainRoot.get();
        if (root != null) clipboard.attach(root);
    }

    private static void background(View view, Drawable value) {
        int l = view.getPaddingLeft(), t = view.getPaddingTop(), r = view.getPaddingRight(), b = view.getPaddingBottom();
        view.setBackground(value); view.setPadding(l, t, r, b);
    }

    private static final class Saved {
        final View view;
        final boolean clearBackground;
        Drawable original;
        Method border, setBorder, shadow, setShadow;
        float borderWidth, shadowAlpha;
        boolean changed;
        Saved(View view, boolean clearBackground) {
            this.view = view; this.clearBackground = clearBackground;
            original = view.getBackground();
            try {
                border = view.getClass().getMethod("getBorderWidth");
                setBorder = view.getClass().getMethod("setBorderWidth", float.class);
                shadow = view.getClass().getMethod("getShadowAlpha");
                setShadow = view.getClass().getMethod("setShadowAlpha", float.class);
            } catch (ReflectiveOperationException ignored) { border = setBorder = shadow = setShadow = null; }
        }
        void apply(boolean clearAllowed) throws ReflectiveOperationException {
            if (clearAllowed && clearBackground && view.getBackground() != null) {
                original = view.getBackground(); background(view, null);
            }
            if (border != null) {
                float value = ((Number) border.invoke(view)).floatValue();
                if (value != 0f) { borderWidth = value; setBorder.invoke(view, 0f); changed = true; }
                value = ((Number) shadow.invoke(view)).floatValue();
                if (value != 0f) { shadowAlpha = value; setShadow.invoke(view, 0f); changed = true; }
            }
        }
        void restore() {
            if (clearBackground && view.getBackground() == null) background(view, original);
            if (border != null && changed) try {
                if (((Number) border.invoke(view)).floatValue() == 0f && borderWidth != 0f) setBorder.invoke(view, borderWidth);
                if (((Number) shadow.invoke(view)).floatValue() == 0f && shadowAlpha != 0f) setShadow.invoke(view, shadowAlpha);
            } catch (ReflectiveOperationException ignored) { }
        }
    }
}
