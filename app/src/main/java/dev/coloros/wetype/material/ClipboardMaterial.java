package dev.coloros.wetype.material;

import android.annotation.SuppressLint;
import android.content.res.ColorStateList;
import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.Paint;
import android.graphics.LinearGradient;
import android.graphics.Shader;
import android.graphics.PixelFormat;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.InsetDrawable;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewParent;
import android.view.ViewTreeObserver;
import java.io.InputStream;
import java.lang.reflect.Method;
import java.lang.reflect.Field;
import java.lang.ref.WeakReference;
import java.nio.charset.StandardCharsets;
import java.util.IdentityHashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.function.BooleanSupplier;

/** Scoped backgrounds on the real clipboard/phrase panel and its recycled content cards. */
final class ClipboardMaterial {
    private static final String PREFIX = "com.tencent.wetype.plugin.hld.keyboard.";
    private static final int CLEAR = 1, CARD = 2, CIRCLE = 3, ACTION = 4, DELETE = 5, TAB = 6, MORE = 7;
    private final WeTypeMaterialModule module;
    private final BooleanSupplier materialActive;
    private final WeTypeTheme theme;
    private final Class<?> panelClass, historyClass;
    private final int clipboardType, historyType;
    private final String source;
    private final Map<View, Entry> entries = new IdentityHashMap<>();
    private final Map<View, ViewGroup> underlays = new IdentityHashMap<>();
    private final ViewTreeObserver.OnGlobalLayoutListener layout = this::refresh;
    private final ViewTreeObserver.OnPreDrawListener preDraw = () -> { refresh(); return true; };
    private View root;
    private WeakReference<View> visiblePanel = new WeakReference<>(null);
    private ViewTreeObserver observer;
    private XiaobuKeyShader shader;
    private boolean shaderFailed, logged, hardwareLogged, softwareLogged, waitingLogged, underlayDrawLogged;

    ClipboardMaterial(ClassLoader loader, WeTypeMaterialModule module, BooleanSupplier materialActive,
                      WeTypeTheme theme, Runnable requestRefresh) throws Exception {
        this.module = module; this.materialActive = materialActive; this.theme = theme;
        panelClass = Class.forName(PREFIX + "S15CustomPhraseAndClipboardKeyboard", false, loader);
        historyClass = Class.forName(PREFIX + "S29ClipboardCleanRecordKeyboard", false, loader);
        Class<?> type = panelClass.getMethod("getKeyboardType").getReturnType();
        Method value = type.getMethod("c"); // Real DEX getter; JADX restores its alias to getValue().
        clipboardType = enumValue(type, value, "CustomPhraseAndClipboard");
        historyType = enumValue(type, value, "ClipboardCleanRecord");
        try (InputStream input = ClipboardMaterial.class.getResourceAsStream("/material/xiaobu_shadowedge.agsl")) {
            if (input == null) throw new IllegalStateException("Missing clipboard material shader");
            source = new String(input.readAllBytes(), StandardCharsets.UTF_8);
        }
        // These are real panel lifecycle callbacks; recycled panels do not always rebuild the IME root.
        for (Class<?> panel : new Class<?>[]{panelClass, historyClass}) {
            module.add(panel.getMethod("f0"), chain -> {
                Object result = chain.proceed();
                View view = (View) chain.getThisObject();
                visiblePanel = new WeakReference<>(view);
                observe(view);
                view.post(() -> {
                    if (visiblePanel.get() == view && view.isAttachedToWindow()) {
                        refresh(); requestRefresh.run();
                    }
                });
                module.info("clipboard panel shown: " + panel.getSimpleName());
                return result;
            });
            module.add(panel.getMethod("g0"), chain -> {
                Object result = chain.proceed();
                ((View) chain.getThisObject()).post(this::refresh);
                return result;
            });
        }
        installSelectedTab(loader);
        // The stock clipboard is an opaque overlay, not a replacement for the retained keyboard.
        // Omit covered sibling RenderNodes while transparent; leave visibility/layout/input state alone.
        Method drawChild = ViewGroup.class.getDeclaredMethod("drawChild", Canvas.class, View.class, long.class);
        drawChild.setAccessible(true);
        module.add(drawChild, chain -> {
            View child = (View) chain.getArg(1);
            View panel = visiblePanel.get();
            if (underlays.get(child) == chain.getThisObject() && materialActive.getAsBoolean()
                    && panel != null && panel.isShown()) {
                if (!underlayDrawLogged) {
                    module.info("clipboard covered keyboard drawing omitted"); underlayDrawLogged = true;
                }
                return false;
            }
            return chain.proceed();
        });
        module.deoptimize(ViewGroup.class.getDeclaredMethod("dispatchDraw", Canvas.class));
        module.add(View.class.getDeclaredMethod("setPressed", boolean.class), chain -> {
            Object result = chain.proceed();
            View view = (View) chain.getThisObject();
            for (Entry entry : entries.values()) {
                if (within(entry.view, view, 3) || within(view, entry.view, 3)) entry.drawable.invalidateSelf();
            }
            return result;
        });
    }

    boolean supports(int type) { return type == clipboardType || type == historyType; }

    boolean ownsBackground(View view) {
        Entry entry = entries.get(view);
        return entry != null && view.getBackground() == entry.drawable;
    }

    private static int enumValue(Class<?> type, Method getter, String name) throws ReflectiveOperationException {
        Object[] values = type.getEnumConstants();
        if (values != null) for (Object value : values) {
            if (((Enum<?>) value).name().equals(name)) return (int) getter.invoke(value);
        }
        throw new NoSuchFieldException("KeyboardType." + name);
    }

    void attach(View view) {
        if (root == view) { observe(view); return; }
        clear(); root = view; observe(view);
    }

    private void observe(View view) {
        ViewTreeObserver next = view.getViewTreeObserver();
        if (observer == next) return;
        if (observer != null && observer.isAlive()) {
            observer.removeOnGlobalLayoutListener(layout); observer.removeOnPreDrawListener(preDraw);
        }
        observer = next;
        observer.addOnGlobalLayoutListener(layout); observer.addOnPreDrawListener(preDraw);
    }

    void detach() {
        if (observer != null && observer.isAlive()) {
            observer.removeOnGlobalLayoutListener(layout);
            observer.removeOnPreDrawListener(preDraw);
        }
        clear(); observer = null; root = null; visiblePanel.clear();
    }

    void refresh() {
        View panel = visiblePanel.get();
        if (panel != null && (!panel.isAttachedToWindow() || !panel.isShown())) {
            visiblePanel.clear(); panel = null;
        }
        View scanRoot = panel != null ? panel : root;
        if (scanRoot == null || !scanRoot.isAttachedToWindow()) { clear(); return; }
        scan(scanRoot, false);
        panel = visiblePanel.get();
        if (panel == null || !panel.isAttachedToWindow() || !panel.isShown()) { clear(); return; }
        refreshUnderlays(panel);
        // The clipboard is hosted in a separate upper/left container with its own opaque background.
        if (panel != null && panel.isShown() && root != null && within(panel, root, 32)) {
            ViewParent parent = panel.getParent();
            while (parent instanceof View && parent != root) {
                View view = (View) parent;
                if (view.getBackground() != null && !entries.containsKey(view))
                    entries.put(view, new Entry(view, CLEAR));
                parent = view.getParent();
            }
        }
        Iterator<Entry> iterator = entries.values().iterator();
        while (iterator.hasNext()) {
            Entry entry = iterator.next();
            boolean belongs = within(entry.view, panel, 32) || within(panel, entry.view, 32);
            if (!entry.view.isShown() || !belongs) {
                entry.restore(); iterator.remove();
            } else if (entry.role == CLEAR && !materialActive.getAsBoolean()) {
                entry.restore(); iterator.remove();
            } else entry.apply();
        }
        if (!logged && !entries.isEmpty()) {
            module.info("clipboard material attached; surfaces=" + entries.size()); logged = true;
        }
        if (!entries.isEmpty() && !materialActive.getAsBoolean() && !waitingLogged) {
            module.info("clipboard cards styled; panel transparency awaits native blur"); waitingLogged = true;
        }
    }

    private void scan(View view, boolean inside) {
        if (!view.isShown()) return;
        boolean panel = panelClass.isInstance(view) || historyClass.isInstance(view);
        if (panel) visiblePanel = new WeakReference<>(view);
        inside |= panel;
        if (inside && !entries.containsKey(view)) {
            int role = panel ? CLEAR : role(view.getId());
            if (role != 0) entries.put(view, new Entry(view, role));
        }
        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int i = 0; i < group.getChildCount(); i++) scan(group.getChildAt(i), inside);
        }
    }

    private static int role(int id) {
        // IDs verified from both supplied APK R.id definitions; never match arbitrary application views.
        switch (id) {
            case 2131297624: case 2131297625: // clipboard/phrase list
            case 2131297067: case 2131297635: // header backgrounds
            case 2131296572: case 2131296574: case 2131296578: case 2131296661: // empty/enable panels
                return CLEAR;
            case 2131296580: case 2131296581: case 2131296582: // card and folded undercards
            case 2131296670: // phrase content card
            case 2131296979: // extracted key information pill
                return CARD;
            case 2131297634: return TAB; // segmented backdrop retains its original vertical insets
            case 2131296407: case 2131296636: case 2131296639: // back/settings/clear header buttons
                return CIRCLE;
            case 2131296585: // item expand/more
            case 2131296671: // phrase expand/more
                return MORE;
            case 2131296557: case 2131296560: case 2131296662: // add/split/empty add button
            case 2131296650: case 2131296652: // phrase edit/pin
                return ACTION;
            case 2131296564: case 2131296565: case 2131296645: case 2131296646: return DELETE;
            default: return 0;
        }
    }

    void clear() {
        for (Entry entry : entries.values()) entry.restore();
        entries.clear();
        clearUnderlays();
    }

    @SuppressLint("ResourceType") // IDs belong to the target APK, verified from its binary XML.
    private void refreshUnderlays(View panel) {
        View stackView = root != null ? root.findViewById(2131296987) : null; // keyboard_container_rl
        if (!materialActive.getAsBoolean() || !(stackView instanceof ViewGroup) || !within(panel, stackView, 32)) {
            clearUnderlays(); return;
        }
        ViewGroup stack = (ViewGroup) stackView;
        View branch = panel;
        while (branch.getParent() != stack && branch.getParent() instanceof View)
            branch = (View) branch.getParent();
        // Live WeType 4.0.0 hosts S15 in input_upper_view; other routes use the side slots.
        if (branch.getId() != 2131296952 && branch.getId() != 2131296948 && branch.getId() != 2131296949) {
            clearUnderlays(); return;
        }
        int panelIndex = stack.indexOfChild(branch);
        Iterator<Map.Entry<View, ViewGroup>> iterator = underlays.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<View, ViewGroup> entry = iterator.next();
            int index = stack.indexOfChild(entry.getKey());
            if (entry.getValue() != stack || index < 0 || index >= panelIndex) {
                entry.getValue().invalidate(); iterator.remove();
            }
        }
        for (int i = 0; i < panelIndex; i++) {
            View child = stack.getChildAt(i);
            if (!underlays.containsKey(child)) {
                underlays.put(child, stack);
                // Force the parent display list to be recorded without its retained keyboard nodes.
                stack.invalidate();
            }
        }
    }

    private void clearUnderlays() {
        for (ViewGroup parent : underlays.values()) parent.invalidate();
        underlays.clear();
    }


    private static boolean within(View child, View parent, int limit) {
        if (parent == null) return false;
        for (int i = 0; child != null && i <= limit; i++) {
            if (child == parent) return true;
            ViewParent next = child.getParent(); child = next instanceof View ? (View) next : null;
        }
        return false;
    }

    private void installSelectedTab(ClassLoader loader) throws ReflectiveOperationException {
        Class<?> tab = Class.forName("com.tencent.wetype.plugin.hld.candidate.TabContainerView", false, loader);
        Field rect = fieldOfType(tab, RectF.class), paint = fieldOfType(tab, Paint.class);
        Field radius = fieldOfType(tab, float.class);
        Field overlay = tab.getDeclaredField("k"); // DEX onDraw reads k:Z; i:Z is animation state.
        overlay.setAccessible(true);
        Method draw = tab.getDeclaredMethod("onDraw", Canvas.class);
        module.add(draw, chain -> {
            View view = (View) chain.getThisObject();
            Entry entry = entries.get(view);
            if (entry == null || !view.isShown() || overlay.getBoolean(view)) return chain.proceed();
            RectF selected = new RectF((RectF) rect.get(view));
            if (selected.isEmpty()) return chain.proceed();
            RectF outer = new RectF(0, 0, view.getWidth(), view.getHeight());
            entry.adjustBounds(outer);
            float gap = 2f * view.getResources().getDisplayMetrics().density;
            outer.inset(gap, gap);
            if (!selected.intersect(outer)) return chain.proceed();
            Paint original = (Paint) paint.get(view);
            int alpha = original.getAlpha();
            original.setAlpha(0);
            try {
                Object result = chain.proceed();
                drawSurface((Canvas) chain.getArg(0), selected, radius.getFloat(view),
                        theme.isDark() ? 0x3dffffff : 0x8affffff, 255, false);
                return result;
            } finally { original.setAlpha(alpha); }
        });
    }

    private static Field fieldOfType(Class<?> type, Class<?> fieldType) throws NoSuchFieldException {
        Field result = null;
        for (Field field : type.getDeclaredFields()) if (field.getType() == fieldType) {
            if (result != null) throw new NoSuchFieldException("Ambiguous " + fieldType.getName());
            result = field;
        }
        if (result == null) throw new NoSuchFieldException(type.getName() + ": " + fieldType.getName());
        result.setAccessible(true); return result;
    }

    private void drawSurface(Canvas canvas, RectF shape, float corner, int fill, int alpha, boolean circle) {
        if (canvas.isHardwareAccelerated() && !shaderFailed) try {
            if (shader == null) shader = new XiaobuKeyShader(source);
            if (circle) shader.drawToolbar(canvas, shape, fill, theme.isDark(), alpha);
            else shader.draw(canvas, shape, corner, fill, theme.isDark(), alpha);
            if (!hardwareLogged) { module.info("clipboard surface drawn: hardware NEW_G2"); hardwareLogged = true; }
            return;
        } catch (RuntimeException | LinkageError error) {
            shaderFailed = true; module.error("clipboard shader unavailable; use canvas material", error);
        }
        // A cached/software Canvas still gets the material, rather than restoring the old solid card.
        drawCanvasMaterial(canvas, shape, corner, fill, alpha, circle);
        if (!softwareLogged) { module.info("clipboard surface drawn: canvas fallback"); softwareLogged = true; }
    }

    private void drawCanvasMaterial(Canvas canvas, RectF shape, float corner, int fill, int alpha, boolean circle) {
        float density = root != null ? root.getResources().getDisplayMetrics().density : 3f;
        Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        paint.setColor(fill); paint.setAlpha((fill >>> 24) * alpha / 255);
        if (circle) canvas.drawOval(shape, paint); else canvas.drawRoundRect(shape, corner, corner, paint);
        float width = density;
        RectF edge = new RectF(shape); edge.inset(width / 2f, width / 2f);
        paint.setStyle(Paint.Style.STROKE); paint.setStrokeWidth(width); paint.setAlpha(alpha);
        paint.setShader(new LinearGradient(edge.left, edge.top, edge.right, edge.bottom,
                theme.isDark() ? new int[]{0x70ffffff, 0x08ffffff, 0x30ffffff}
                        : new int[]{0xc0ffffff, 0x20ffffff, 0x70ffffff},
                new float[]{0f, .55f, 1f}, Shader.TileMode.CLAMP));
        if (circle) canvas.drawOval(edge, paint);
        else canvas.drawRoundRect(edge, Math.max(0f, corner - width / 2f), Math.max(0f, corner - width / 2f), paint);
    }

    private static void setBackground(View view, Drawable background) {
        int l = view.getPaddingLeft(), t = view.getPaddingTop(), r = view.getPaddingRight(), b = view.getPaddingBottom();
        view.setBackground(background); view.setPadding(l, t, r, b);
    }

    private final class Entry {
        final View view;
        final int role;
        final Surface drawable = new Surface(this);
        Drawable original;
        ColorStateList tint;
        Method radius, border, setBorder, shadow, setShadow;
        float originalBorder, originalShadow;
        boolean dark, decorationChanged;

        Entry(View view, int role) {
            this.view = view; this.role = role;
            original = view.getBackground(); tint = view.getBackgroundTintList();
            updateFoldAlpha();
            try {
                radius = view.getClass().getMethod("getRadius");
            } catch (ReflectiveOperationException ignored) { radius = null; }
            try {
                border = view.getClass().getMethod("getBorderWidth");
                setBorder = view.getClass().getMethod("setBorderWidth", float.class);
                shadow = view.getClass().getMethod("getShadowAlpha");
                setShadow = view.getClass().getMethod("setShadowAlpha", float.class);
                originalBorder = (float) border.invoke(view); originalShadow = (float) shadow.invoke(view);
            } catch (ReflectiveOperationException ignored) {
                border = setBorder = shadow = setShadow = null;
            }
        }

        void apply() {
            if (view.getBackground() != drawable) {
                original = view.getBackground(); updateFoldAlpha(); setBackground(view, drawable);
            }
            if (view.getBackgroundTintList() != null) {
                tint = view.getBackgroundTintList(); view.setBackgroundTintList(null);
            }
            if (dark != theme.isDark()) { dark = theme.isDark(); drawable.invalidateSelf(); }
            if (border != null) try {
                float value = (float) border.invoke(view);
                if (value != 0f) { originalBorder = value; setBorder.invoke(view, 0f); decorationChanged = true; }
                value = (float) shadow.invoke(view);
                if (value != 0f) { originalShadow = value; setShadow.invoke(view, 0f); decorationChanged = true; }
            } catch (ReflectiveOperationException ignored) { }
        }

        float corner() {
            if (radius != null) try {
                float value = ((Number) radius.invoke(view)).floatValue();
                if (value > 0f) return value;
            }
            catch (ReflectiveOperationException ignored) { }
            Drawable background = original != null ? original.getCurrent() : null;
            while (background instanceof InsetDrawable) background = ((InsetDrawable) background).getDrawable();
            if (background instanceof GradientDrawable) {
                GradientDrawable gradient = (GradientDrawable) background;
                // WeType sets an eight-value corner array; getCornerRadius() alone returns zero for it.
                float[] corners = gradient.getCornerRadii();
                if (corners != null && corners[0] > 0f) return corners[0];
                if (gradient.getCornerRadius() > 0f) return gradient.getCornerRadius();
            }
            return (role == CARD ? 12f : 8f) * view.getResources().getDisplayMetrics().density;
        }

        void adjustBounds(RectF bounds) {
            float density = view.getResources().getDisplayMetrics().density;
            if (role == MORE) {
                float size = Math.min(28f * density, Math.min(bounds.width(), bounds.height()));
                float x = bounds.centerX(), y = bounds.centerY();
                bounds.set(x - size / 2f, y - size / 2f, x + size / 2f, y + size / 2f);
                return;
            }
            if (role != TAB) return;
            bounds.inset(6f * density, 0f);
            bounds.top += Math.max(view.getPaddingTop(), 6f * density);
            bounds.bottom -= Math.max(view.getPaddingBottom(), 6f * density);
        }

        private void updateFoldAlpha() {
            int id = view.getId();
            if (id == 2131296581 || id == 2131296582)
                drawable.alpha = original != null ? original.getAlpha() : (id == 2131296581 ? 63 : 31);
        }

        boolean pressed() {
            View current = view;
            for (int i = 0; current != null && current != root && i < 3; i++) {
                if (current.isPressed()) return true;
                ViewParent parent = current.getParent(); current = parent instanceof View ? (View) parent : null;
            }
            return false;
        }

        void restore() {
            if (view.getBackground() == drawable) {
                setBackground(view, original); view.setBackgroundTintList(tint);
            }
            if (border != null && decorationChanged) try {
                if (originalBorder != 0f && (float) border.invoke(view) == 0f) setBorder.invoke(view, originalBorder);
                if (originalShadow != 0f && (float) shadow.invoke(view) == 0f) setShadow.invoke(view, originalShadow);
                decorationChanged = false;
            } catch (ReflectiveOperationException ignored) { }
        }
    }

    private final class Surface extends Drawable {
        final Entry entry;
        final RectF shape = new RectF();
        int alpha = 255;
        Surface(Entry entry) { this.entry = entry; }

        @Override public void draw(Canvas canvas) {
            if (entry.role == CLEAR && !materialActive.getAsBoolean()) {
                if (entry.original != null) {
                    entry.original.setBounds(getBounds()); entry.original.setState(getState()); entry.original.draw(canvas);
                }
                return;
            }
            if (entry.role == CLEAR || getBounds().isEmpty()) return;
            shape.set(getBounds());
            entry.adjustBounds(shape);
            if (shape.isEmpty()) return;
            boolean pressed = entry.pressed(), dark = theme.isDark();
            int fill = entry.role == DELETE ? (pressed ? 0xc0e34a4a : 0x99e34a4a)
                    : dark ? (pressed ? 0x3dffffff : 0x26ffffff) : (pressed ? 0x85bbbec2 : 0x8affffff);
            if (entry.role == MORE) {
                if (!pressed) fill = dark ? 0x18ffffff : 0x38ffffff;
                float corner = 9f * entry.view.getResources().getDisplayMetrics().density;
                drawSurface(canvas, shape, Math.min(corner, shape.width() / 2f), fill, alpha, false);
                return;
            }
            if (entry.role == CIRCLE) {
                float size = Math.min(shape.width(), shape.height());
                float x = shape.centerX(), y = shape.centerY();
                shape.set(x - size / 2, y - size / 2, x + size / 2, y + size / 2);
                drawSurface(canvas, shape, size / 2f, fill, alpha, true);
            } else drawSurface(canvas, shape, Math.min(entry.corner(), Math.min(shape.width(), shape.height()) / 2),
                    fill, alpha, false);
        }

        @Override public boolean isStateful() { return true; }
        @Override protected boolean onStateChange(int[] state) { invalidateSelf(); return true; }
        @Override public void setAlpha(int value) { alpha = value; invalidateSelf(); }
        @Override public void setColorFilter(ColorFilter filter) { }
        @Override public int getOpacity() { return PixelFormat.TRANSLUCENT; }
    }
}
