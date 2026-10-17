package dev.coloros.wetype.material;

import android.graphics.Color;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.ColorDrawable;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;

/** The window material used by Xiaobu 1.8.33.17, supplied by the ROM. */
final class ColorOsMaterial {
    private final Constructor<?> managerConstructor;
    private final Constructor<?> paramsConstructor;
    private final Method getDrawable, setParams, setRadius, setCorners;
    private final Method setType, setMaterial, setCornerType, setCornerWeight;
    private final WeTypeTheme theme;
    private final WeTypeMaterialModule module;
    private final PanelEdgeMaterial panelEdge;
    private final boolean floating;
    private float floatingCorner;
    private View target;
    private Object manager;
    private Drawable drawable, previousBackground;
    private Window window;
    private Drawable previousWindowBackground;
    private Drawable installedWindowBackground;
    private final Drawable transparentWindowBackground = new ColorDrawable(Color.TRANSPARENT);
    private Object nativeParams, rootSurface;
    private boolean active;
    private boolean chooserSheet;
    private int lastTint;
    private float lastCorner = -1;
    private boolean failed;
    private String prepareState;
    private final int[] location = new int[2];
    private int appliedX, appliedY;
    private boolean settling;
    private final Runnable settle = () -> {
        if (!active) return;
        settling = true;
        try { refresh(); }
        finally { settling = false; }
    };

    ColorOsMaterial(ClassLoader loader, WeTypeMaterialModule module, WeTypeTheme theme) throws Exception {
        this(loader, module, theme, false);
    }

    ColorOsMaterial(ClassLoader loader, WeTypeMaterialModule module, WeTypeTheme theme, boolean floating) throws Exception {
        this.module = module;
        this.theme = theme;
        this.floating = floating;
        Class<?> properties = Class.forName("android.os.SystemProperties");
        String rom = (String) properties.getMethod("get", String.class, String.class)
                .invoke(null, "ro.build.version.oplusrom", "");
        if (!rom.matches("(?i)(?:V|ColorOS\\s*)?17(?:[. ].*)?")) {
            throw new UnsupportedOperationException("ColorOS 17 required; oplusrom=" + rom);
        }
        Class<?> managerType = Class.forName("com.oplus.view.ViewRootManager", false, loader);
        Class<?> paramsType = Class.forName("com.oplus.graphics.OplusBlurParam", false, loader);
        managerConstructor = managerType.getConstructor(View.class);
        paramsConstructor = paramsType.getConstructor();
        getDrawable = managerType.getMethod("getBackgroundBlurDrawable");
        setParams = managerType.getMethod("setBlurParams", paramsType);
        setRadius = managerType.getMethod("setBlurRadius", int.class);
        setCorners = managerType.getMethod("setCornerRadius", float.class, float.class,
                float.class, float.class);
        setType = paramsType.getMethod("setBlurType", int.class);
        setMaterial = paramsType.getMethod("setMaterialParams", int.class, float[].class, float[].class);
        setCornerType = paramsType.getMethod("setSmoothCornerType", int.class);
        setCornerWeight = paramsType.getMethod("setSmoothCornerWeight", float.class);
        panelEdge = new PanelEdgeMaterial(module);
        module.info("ColorOS 17 material API resolved; source=Xiaobu 1.8.33.17");
    }

    boolean prepare(View view) {
        if (view == null || !view.isAttachedToWindow() || !view.isHardwareAccelerated()
                || view.getWindowVisibility() != View.VISIBLE) {
            release();
            reportPrepare(view == null ? "no input view" : !view.isAttachedToWindow()
                    ? "input view detached" : !view.isHardwareAccelerated()
                    ? "software input view" : "input window hidden");
            return false;
        }
        WindowManager wm = view.getContext().getSystemService(WindowManager.class);
        if (wm == null || !wm.isCrossWindowBlurEnabled()) {
            release();
            reportPrepare("system window blur disabled");
            return false;
        }
        if (target != view || rootSurface != view.getRootSurfaceControl()) {
            release();
            target = view;
            rootSurface = view.getRootSurfaceControl();
            failed = false;
        }
        if (failed) return false;
        try {
            if (manager == null) {
                manager = managerConstructor.newInstance(view);
                drawable = (Drawable) getDrawable.invoke(manager);
                if (drawable == null) {
                    manager = null;
                    return false;
                }
            }
            updateParams();
            reportPrepare("ready");
            return true;
        } catch (Exception | LinkageError error) {
            fail(error);
            return false;
        }
    }

    private void reportPrepare(String state) {
        if (!state.equals(prepareState)) {
            prepareState = state;
            module.info("material preparation: " + state);
        }
    }

    boolean activate(Window window) {
        if (target == null || manager == null || drawable == null || failed) return false;
        try {
            updateParams();
            setRadius.invoke(manager, 150);
            boolean firstActivation = !active;
            if (firstActivation || target.getBackground() != drawable) {
                previousBackground = target.getBackground();
            }
            target.setBackground(drawable);
            if (window != null) {
                if (this.window != window) restoreWindow();
                if (this.window != window || window.getDecorView().getBackground() != installedWindowBackground)
                    previousWindowBackground = window.getDecorView().getBackground();
                this.window = window;
                // The Oplus drawable owns the blur; disable WeType's ordinary window blur.
                window.setBackgroundBlurRadius(0);
                window.setBackgroundDrawable(transparentWindowBackground);
                installedWindowBackground = window.getDecorView().getBackground();
            }
            panelEdge.apply(target, lastCorner, theme.isDark(), floating);
            active = true;
            rememberPosition();
            settleAfterTraversal();
            target.invalidate();
            if (firstActivation) module.info("native material attached; backend=coloros17-system");
            return true;
        } catch (Exception | LinkageError error) {
            fail(error);
            return false;
        }
    }

    void refresh() {
        if (!active || target == null) return;
        WindowManager wm = target.getContext().getSystemService(WindowManager.class);
        if (!target.isAttachedToWindow() || !target.isHardwareAccelerated()
                || target.getWindowVisibility() != View.VISIBLE
                || rootSurface != target.getRootSurfaceControl()
                || wm == null || !wm.isCrossWindowBlurEnabled()) {
            release(); return;
        }
        if (window != null && window.getDecorView().getBackground() != installedWindowBackground) {
            activate(window); return;
        }
        try {
            updateParams();
            setRadius.invoke(manager, 150);
            if (target.getBackground() != drawable) {
                previousBackground = target.getBackground();
                target.setBackground(drawable);
            }
            panelEdge.apply(target, lastCorner, theme.isDark(), floating);
            rememberPosition();
            settleAfterTraversal();
        } catch (Exception | LinkageError error) {
            fail(error);
        }
    }

    private void updateParams() throws Exception {
        theme.refresh();
        boolean dark = theme.isDark();
        // Values extracted from Xiaobu's color_keyboard_bg_blur[_dark].
        // Keyboard chooser uses a larger grid panel; the standard 80%-opaque
        // keyboard tint makes it look like a solid white slab rather than glass.
        // Retain the Xiaobu blur backend, but lower only this page's tint alpha.
        int tint = dark ? (chooserSheet ? 0x770d0d0d : 0x990d0d0d)
                : (chooserSheet ? 0x7fe1e1e1 : 0xcce1e1e1);
        if (lastTint != tint) {
            Object params = paramsConstructor.newInstance();
            setType.invoke(params, 2);
            setMaterial.invoke(params, 1, new float[]{1f, 1f, 1f, 1f},
                    new float[]{Color.red(tint) / 255f, Color.green(tint) / 255f,
                            Color.blue(tint) / 255f, Color.alpha(tint) / 255f});
            setCornerType.invoke(params, 1);
            setCornerWeight.invoke(params, 3f);
            nativeParams = params;
            lastTint = tint;
            module.info("native material parameters: radius=150 tint=" + Integer.toHexString(tint));
        }
        float corner = floating && floatingCorner > 0f ? floatingCorner
                : 28f * target.getResources().getDisplayMetrics().density;
        // Cache configuration values, not the renderer's state. Window/surface changes can reset it.
        setParams.invoke(manager, nativeParams);
        setCorners.invoke(manager, corner, corner, floating ? corner : 0f, floating ? corner : 0f);
        lastCorner = corner;
    }

    boolean isActive() { return active; }

    void setChooserSheet(boolean chooser) { chooserSheet = chooser && !floating; }

    void setFloatingCorner(float radius) { floatingCorner = radius; }

    private boolean moved() {
        if (!floating || target == null) return false;
        target.getLocationOnScreen(location);
        return location[0] != appliedX || location[1] != appliedY;
    }

    private void rememberPosition() {
        if (!floating) return;
        target.getLocationOnScreen(location);
        appliedX = location[0]; appliedY = location[1];
    }

    private void settleAfterTraversal() {
        if (!floating || settling || target == null) return;
        // Popup movement/resize can clear OEM renderer state after pre-draw.
        // Debounce one submission after the last traversal, never poll every frame.
        target.removeCallbacks(settle);
        target.postDelayed(settle, 160L);
    }

    boolean needsRebind() {
        return active && target != null && (!target.isAttachedToWindow()
                || target.getWindowVisibility() != View.VISIBLE || !target.isHardwareAccelerated()
                || target.getBackground() != drawable || rootSurface != target.getRootSurfaceControl() || moved()
                || (window != null && window.getDecorView().getBackground() != installedWindowBackground));
    }

    void release() {
        if (target != null) target.removeCallbacks(settle);
        panelEdge.release();
        if (manager != null) {
            try { setRadius.invoke(manager, 0); }
            catch (Exception | LinkageError error) { module.error("clear native blur", error); }
        }
        if (target != null && drawable != null && target.getBackground() == drawable) {
            target.setBackground(previousBackground);
        }
        restoreWindow();
        active = false;
        target = null;
        manager = null;
        drawable = null;
        previousBackground = null;
        nativeParams = null;
        rootSurface = null;
        lastTint = 0;
        chooserSheet = false;
        lastCorner = -1;
    }

    private void restoreWindow() {
        if (window != null && window.getDecorView().getBackground() == installedWindowBackground)
            window.setBackgroundDrawable(previousWindowBackground);
        window = null;
        previousWindowBackground = null;
        installedWindowBackground = null;
    }

    private void fail(Throwable error) {
        module.error("native material failed; restore WeType background", error);
        View failedTarget = target;
        Object failedSurface = rootSurface;
        release();
        target = failedTarget;
        rootSurface = failedSurface;
        failed = true;
    }
}
