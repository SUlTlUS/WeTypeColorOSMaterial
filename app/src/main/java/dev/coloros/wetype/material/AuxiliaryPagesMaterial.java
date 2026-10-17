package dev.coloros.wetype.material;

import android.annotation.SuppressLint;
import android.content.res.ColorStateList;
import android.graphics.Canvas;
import android.graphics.Rect;
import android.graphics.ColorFilter;
import android.graphics.PixelFormat;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.InsetDrawable;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewParent;
import android.view.ViewTreeObserver;
import android.widget.TextView;
import java.io.InputStream;
import java.lang.ref.WeakReference;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.util.IdentityHashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.function.BooleanSupplier;

/**
 * IME-only pages verified in WeType's 4.0.0 layouts:
 * S30/eo+dz and the standalone S10 type sheet (keyboard chooser), S11/e4
 * (emoji), and the S10 toolbar editor. Resource names refer to WeType 4.0.0.
 * Never touches the separate Settings Activity or emoji bitmap/text rendering.
 */
final class AuxiliaryPagesMaterial {
    private static final int CLEAR = 1, CARD = 2, ICON = 3, BACKPLATE = 5, DIALOG_CARD = 6;
    private final WeTypeMaterialModule module;
    private final BooleanSupplier active;
    private final WeTypeTheme theme;
    private final Class<?> chooser, emoji, custom, keyboardType, chooserItem, customItem, plusModeOpen;
    private final Method configureToolbarScroll;
    private final int chooserType, emojiType;
    private final String shaderSource;
    private final Map<View, Entry> entries = new IdentityHashMap<>();
    private final Map<View, ToolbarItemSizing> toolbarSizing = new IdentityHashMap<>();
    // Drawing-only suppression; never change retained views' visibility or click behavior.
    private final Map<View, ViewGroup> coveredSiblings = new IdentityHashMap<>();
    private WeakReference<View> foreground = new WeakReference<>(null);
    private View scanningForeground;
    private final ViewTreeObserver.OnGlobalLayoutListener layout = this::refresh;
    private final ViewTreeObserver.OnPreDrawListener preDraw = () -> {
        reconcile();
        // A lazy S10 editor may attach before its rows have final dimensions.
        // Recheck after OEM layout, not only at its first GlobalLayout event.
        refreshToolbarBeforeDraw();
        return true;
    };
    private View root;
    private boolean floatingRoot;
    private ViewTreeObserver observer;
    private XiaobuKeyShader shader;
    private boolean shaderFailed, logged, coveredDrawLogged;

    AuxiliaryPagesMaterial(ClassLoader loader, WeTypeMaterialModule module,
                           BooleanSupplier active, WeTypeTheme theme, Runnable requestRefresh)
            throws Exception {
        this.module = module;
        this.active = active;
        this.theme = theme;
        String base = "com.tencent.wetype.plugin.hld.";
        chooser = Class.forName(base + "keyboard.S30KeyboardChooseKeyboard", false, loader);
        emoji = Class.forName(base + "keyboard.selfdraw.S11EmojiKeyboard", false, loader);
        custom = Class.forName(base + "view.settingkeyboard.S10SettingCustomToolbarView", false, loader);
        keyboardType = Class.forName(base + "view.settingkeyboard.S10SettingKeyboardTypeView", false, loader);
        chooserItem = Class.forName(base + "view.settingkeyboard.S10SettingItemView", false, loader);
        customItem = Class.forName(base + "view.settingkeyboard.c", false, loader);
        Class<?> plus = null;
        try {
            plus = Class.forName(base + "view.settingkeyboard.S10SettingPlusModeOpenView", false, loader);
        } catch (ClassNotFoundException ignored) { // Older WeType releases omit this optional page.
        }
        plusModeOpen = plus;
        configureToolbarScroll = Class.forName(base
                + "view.toolbar.S10CustomToolbarHorizontalScrollView", false, loader)
                .getMethod("a", int.class, int.class, int.class, int.class);
        Class<?> enumType = chooser.getMethod("getKeyboardType").getReturnType();
        Method getter = enumType.getMethod("c"); // DEX getter, not JADX's getValue().
        chooserType = valueOf(enumType, getter, "KeyboardChoose");
        emojiType = valueOf(enumType, getter, "Emoji");
        try (InputStream input = AuxiliaryPagesMaterial.class.getResourceAsStream(
                "/material/xiaobu_shadowedge.agsl")) {
            if (input == null) throw new IllegalStateException("Missing NEW_G2 shader");
            shaderSource = new String(input.readAllBytes(), StandardCharsets.UTF_8);
        }
        // These callbacks belong to the actual IME panels, not settings Activities.
        for (Class<?> panel : new Class<?>[]{chooser, emoji}) {
            for (String method : new String[]{"f0", "g0"}) {
                module.add(panel.getMethod(method), chain -> {
                    Object result = chain.proceed();
                    View view = (View) chain.getThisObject();
                    view.post(() -> {
                        refresh();
                        if (view.isShown() && method.equals("f0")) requestRefresh.run();
                    });
                    return result;
                });
            }
        }
        module.add(custom.getMethod("setVisibility", int.class), chain -> {
            Object result = chain.proceed();
            View view = (View) chain.getThisObject();
            view.post(this::refresh);
            return result;
        });
        // The first-use Plus confirmation (including text poster, from=114) is
        // an S10 input panel, not an Android dialog. It needs its own refresh
        // when the content is opened/closed without changing keyboard type.
        if (plusModeOpen != null) {
            for (Method callback : new Method[]{
                    plusModeOpen.getMethod("d", View.class, android.os.Bundle.class, boolean.class),
                    plusModeOpen.getMethod("e")}) {
                module.add(callback, chain -> {
                    Object result = chain.proceed();
                    View view = (View) chain.getThisObject();
                    view.post(() -> { refresh(); requestRefresh.run(); });
                    return result;
                });
            }
        }
        // S10 lazily inflates the same keyboard-type sheet outside S30. It uses
        // View.setVisibility(), including at the end of its close animation.
        module.add(View.class.getDeclaredMethod("setVisibility", int.class), chain -> {
            Object result = chain.proceed();
            View view = (View) chain.getThisObject();
            if (keyboardType.isInstance(view) && root != null && within(view, root)) {
                view.post(() -> {
                    refresh();
                    requestRefresh.run(); // Also restore the normal tint after closing.
                });
            }
            return result;
        });
        // Settings/chooser/emoji are retained in overlapping view branches. Their original
        // opaque backgrounds hid the other branches; making them clear must not reveal them.
        Method drawChild = ViewGroup.class.getDeclaredMethod("drawChild",
                Canvas.class, View.class, long.class);
        drawChild.setAccessible(true);
        module.add(drawChild, chain -> {
            View child = (View) chain.getArg(1);
            View panel = foreground.get();
            if (coveredSiblings.get(child) == chain.getThisObject()
                    && active.getAsBoolean() && panel != null && panel.isShown()) {
                if (!coveredDrawLogged) {
                    module.info("auxiliary covered old-page drawing omitted");
                    coveredDrawLogged = true;
                }
                return false;
            }
            return chain.proceed();
        });
        module.deoptimize(ViewGroup.class.getDeclaredMethod("dispatchDraw", Canvas.class));
    }

    private static int valueOf(Class<?> type, Method getter, String name) throws Exception {
        for (Object constant : type.getEnumConstants()) {
            if (((Enum<?>) constant).name().equals(name))
                return ((Number) getter.invoke(constant)).intValue();
        }
        throw new NoSuchFieldException("KeyboardType." + name);
    }

    boolean supports(int keyboard) { return keyboard == chooserType || keyboard == emojiType; }

    boolean chooserVisible() {
        View panel = foreground.get();
        return active.getAsBoolean() && panel != null && panel.isShown()
                && (chooser.isInstance(panel) || keyboardType.isInstance(panel));
    }

    void attach(View view) { attach(view, false); }

    void attachFloating(View view) { attach(view, true); }

    boolean usingFloatingRoot() { return floatingRoot; }

    private void attach(View view, boolean floating) {
        if (root == view) { floatingRoot = floating; observe(view); return; }
        detach();
        root = view;
        floatingRoot = floating;
        observe(view);
    }

    private void observe(View view) {
        ViewTreeObserver next = view.getViewTreeObserver();
        if (observer == next) return;
        if (observer != null && observer.isAlive()) {
            observer.removeOnGlobalLayoutListener(layout);
            observer.removeOnPreDrawListener(preDraw);
        }
        observer = next;
        observer.addOnGlobalLayoutListener(layout);
        observer.addOnPreDrawListener(preDraw);
    }

    void detach() {
        if (observer != null && observer.isAlive()) {
            observer.removeOnGlobalLayoutListener(layout);
            observer.removeOnPreDrawListener(preDraw);
        }
        clear();
        observer = null;
        root = null;
        floatingRoot = false;
        foreground.clear();
    }

    void refresh() {
        if (root == null || !root.isAttachedToWindow() || !active.getAsBoolean()) {
            clear();
            return;
        }
        scanningForeground = null;
        scan(root, false);
        foreground = new WeakReference<>(scanningForeground);
        scanningForeground = null;
        reconcile();
        if (custom.isInstance(foreground.get())) adjustToolbarPaging(foreground.get());
        if (!logged && !entries.isEmpty()) {
            module.info("auxiliary page material attached: surfaces=" + entries.size());
            logged = true;
        }
    }

    private void refreshToolbarBeforeDraw() {
        View panel = foreground.get();
        if (panel != null && custom.isInstance(panel)) adjustToolbarPaging(panel);
    }

    private boolean isPlusPage(View view) {
        return plusModeOpen != null && plusModeOpen.isInstance(view);
    }

    private void scan(View view, boolean inside) {
        if (!view.isShown()) return;
        boolean page = chooser.isInstance(view) || keyboardType.isInstance(view)
                || emoji.isInstance(view) || custom.isInstance(view) || isPlusPage(view);
        if (page) {
            inside = true;
            // A nested toolbar editor covers the settings grid; chooser covers the normal
            // settings page, while emoji is the active content keyboard.
            if (scanningForeground == null || precedence(view) > precedence(scanningForeground))
                scanningForeground = view;
        }
        if (inside) {
            int role = role(view);
            if (role != 0 && !entries.containsKey(view)) entries.put(view, new Entry(view, role));
        }
        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int i = 0; i < group.getChildCount(); i++) scan(group.getChildAt(i), inside);
        }
    }

    private int precedence(View view) {
        return isPlusPage(view) ? 4 : custom.isInstance(view) ? 3
                : chooser.isInstance(view) || keyboardType.isInstance(view) ? 2 : 1;
    }

    private int role(View view) {
        if (chooser.isInstance(view) || emoji.isInstance(view) || custom.isInstance(view)
                || keyboardType.isInstance(view) || isPlusPage(view)) return CLEAR;
        if (chooserItem.isInstance(view)) return CARD;
        // Custom toolbar editor: the module-added NEW_G2 item CARD is the
        // unwanted little rounded rectangle. Keep its inner ICON surface so
        // selection remains visible, and preserve the 4 x 2 paging/layout.
        if (customItem.isInstance(view)) return CLEAR;
        // Exact R.id values checked against WeType 4.0.0's decompiled s class.
        switch (view.getId()) {
            case 2131296683: // custom_tool_setting_base_grid_view_bg
            case 2131297515: // setting_base_grid_view_bg (keyboard chooser)
                return BACKPLATE;
            case 2131297427: // s10_setting_item_icon_bg
            case 2131297423: // s10_setting_custom_toolbar_item_icon_bg
                return ICON;
            case 2131297242: // plus_mode_bg, rounded confirmation card in WeType 4.0.0 e3.xml
                return DIALOG_CARD;
            case 2131296784: // emoji_type_root, actual category item (not fullscreen RecyclerView)
                // Leave the inner emoji_item_container_rl's circular selection
                // drawable intact; only remove our outer rectangular surface.
                return CLEAR;
            case 2131296424: // below_bg_fl: chooser's full-screen stock skin background
            case 2131296615: // content_container_fl
            case 2131296679: // custom_tool_below_bg_fl
            case 2131296680: // custom_tool_content_container_fl
                return CLEAR;
            default:
                return 0;
        }
    }

    private void reconcile() {
        if (root == null || !root.isAttachedToWindow() || !active.getAsBoolean()) {
            clear(); return;
        }
        refreshCoveredSiblings(foreground.get());
        Iterator<Map.Entry<View, ToolbarItemSizing>> sizingIterator = toolbarSizing.entrySet().iterator();
        while (sizingIterator.hasNext()) {
            Map.Entry<View, ToolbarItemSizing> sizing = sizingIterator.next();
            if (!sizing.getKey().isShown() || !within(sizing.getKey(), root)) {
                sizing.getValue().restore(); sizingIterator.remove();
            }
        }
        Iterator<Entry> iterator = entries.values().iterator();
        while (iterator.hasNext()) {
            Entry entry = iterator.next();
            if (!entry.view.isShown() || !within(entry.view, root)) {
                entry.restore();
                iterator.remove();
            } else entry.apply();
        }
    }

    void clear() {
        for (ToolbarItemSizing sizing : toolbarSizing.values()) sizing.restore();
        toolbarSizing.clear();
        for (Entry entry : entries.values()) entry.restore();
        entries.clear();
        clearCoveredSiblings();
    }

    @SuppressLint("ResourceType") // IDs belong to WeType 4.0.0, not this module.
    private void refreshCoveredSiblings(View panel) {
        if (panel == null || !panel.isShown() || root == null || !active.getAsBoolean()) {
            clearCoveredSiblings(); return;
        }
        // Only manipulate drawChild of ancestors between the foreground panel and the
        // IME's keyboard stack. For S10 editor this also excludes the earlier settings
        // grid directly beneath the editor inside s10_setting_container.
        View stackView = root.findViewById(2131296987); // keyboard_container_rl
        ViewGroup stack = stackView instanceof ViewGroup ? (ViewGroup) stackView : null;
        Map<View, ViewGroup> next = new IdentityHashMap<>();
        View branch = panel;
        for (int depth = 0; depth < 36 && branch != root; depth++) {
            ViewParent parent = branch.getParent();
            if (!(parent instanceof ViewGroup)) break;
            ViewGroup group = (ViewGroup) parent;
            int index = group.indexOfChild(branch);
            if (index < 0) break;
            // Only skip OLDER siblings. Newer siblings include the IME footer,
            // emoji category rail and other system-owned overlays; suppressing
            // all slots previously made the bottom of the emoji page go black.
            for (int i = 0; i < index; i++) {
                View child = group.getChildAt(i);
                // Skipping a partly overlapped RenderNode discards its entire output,
                // including the keyboard's bottom rail. Only omit a sibling when
                // the foreground sheet completely covers its visible bounds.
                if (child != branch && child.isShown() && fullyCovered(child, panel))
                    next.put(child, group);
            }
            if (group == stack) break;
            branch = group;
        }
        for (Map.Entry<View, ViewGroup> entry : coveredSiblings.entrySet()) {
            if (next.get(entry.getKey()) != entry.getValue()) entry.getValue().invalidate();
        }
        for (Map.Entry<View, ViewGroup> entry : next.entrySet()) {
            if (coveredSiblings.get(entry.getKey()) != entry.getValue()) entry.getValue().invalidate();
        }
        if (!next.keySet().equals(coveredSiblings.keySet())) {
            coveredDrawLogged = false;
            module.info("auxiliary covered views updated: " + next.size()
                    + "; panel=" + panel.getClass().getSimpleName());
        }
        coveredSiblings.clear();
        coveredSiblings.putAll(next);
    }

    @SuppressLint("ResourceType") // Resource IDs belong to WeType 4.0.0.
    private void adjustToolbarPaging(View panel) {
        // Stock WeType divides the available width by 4.38 for multiple pages,
        // deliberately exposing part of column 5. The requested editor layout
        // uses four complete columns and two rows on EACH swipeable page.
        if (panel == null || !panel.isShown()) return;
        View scroll = panel.findViewById(2131296684); // custom_tool_setting_base_grid_view_container
        View inner = panel.findViewById(2131296685); // inner container, owns left/right padding
        View top = panel.findViewById(2131296681); // custom_tool_line1
        View bottom = panel.findViewById(2131296682); // custom_tool_line2
        if (!(top instanceof ViewGroup) || !(bottom instanceof ViewGroup) || scroll == null
                || inner == null) return;
        ViewGroup first = (ViewGroup) top, second = (ViewGroup) bottom;
        int width = scroll.getWidth() - scroll.getPaddingLeft() - scroll.getPaddingRight()
                - inner.getPaddingLeft() - inner.getPaddingRight();
        if (width <= 0 || first.getChildCount() == 0 || first.getChildCount() != second.getChildCount()
                || first.getChildCount() % 4 != 0) return;
        int itemWidth = width / 4;
        if (itemWidth < 1) return;
        boolean resized = false;
        for (ViewGroup row : new ViewGroup[]{first, second}) {
            for (int i = 0; i < row.getChildCount(); i++) {
                View cell = row.getChildAt(i);
                ViewGroup.LayoutParams params = cell.getLayoutParams();
                if (params != null && params.width != itemWidth) {
                    params.width = itemWidth;
                    cell.setLayoutParams(params);
                    resized = true;
                }
                View face = cell.findViewById(2131297423); // true circular icon_bg
                View glyph = cell.findViewById(2131297422); // ImageView inside icon_bg
                View text = cell.findViewById(2131297424);
                if (face != null && text instanceof TextView) {
                    ToolbarItemSizing sizing = toolbarSizing.get(cell);
                    if (sizing != null && !sizing.owns(face, glyph, (TextView) text)) {
                        // WeType may rebuild a cell in place after a skin/page change.
                        sizing.restore();
                        sizing = null;
                    }
                    if (sizing == null) {
                        sizing = new ToolbarItemSizing(face, glyph, (TextView) text);
                        toolbarSizing.put(cell, sizing);
                    }
                    int available = Math.min(row.getHeight(), cell.getHeight())
                            - cell.getPaddingTop() - cell.getPaddingBottom();
                    sizing.apply(available, cell.getResources().getDisplayMetrics().density);
                }
            }
        }
        if (resized) {
            // Recompute WeType's swipe snap points from the SAME new item width;
            // never alter its item list, selection state, paging callbacks or scroll position.
            try {
                configureToolbarScroll.invoke(scroll, first.getChildCount() / 4,
                        itemWidth, 4, inner.getPaddingLeft());
                module.info("custom toolbar paging fixed: 4 columns x 2 rows; width=" + itemWidth);
            } catch (ReflectiveOperationException | IllegalArgumentException error) {
                module.error("adjust toolbar page snapping", error);
            }
        }
    }

    private static boolean fullyCovered(View candidate, View panel) {
        Rect a = new Rect(), b = new Rect();
        if (!candidate.getGlobalVisibleRect(a) || !panel.getGlobalVisibleRect(b)) return false;
        return b.contains(a);
    }

    private void clearCoveredSiblings() {
        for (ViewGroup group : coveredSiblings.values()) group.invalidate();
        coveredSiblings.clear();
        coveredDrawLogged = false;
    }

    private static boolean within(View view, View root) {
        for (int i = 0; view != null && i < 40; i++) {
            if (view == root) return true;
            ViewParent parent = view.getParent();
            view = parent instanceof View ? (View) parent : null;
        }
        return false;
    }

    private static void background(View view, Drawable drawable) {
        int left = view.getPaddingLeft(), top = view.getPaddingTop();
        int right = view.getPaddingRight(), bottom = view.getPaddingBottom();
        view.setBackground(drawable);
        view.setPadding(left, top, right, bottom);
    }

    private final class Entry {
        final View view;
        final int role;
        final Surface surface = new Surface(this);
        Drawable original;
        ColorStateList tint;
        boolean dark;

        Entry(View view, int role) {
            this.view = view;
            this.role = role;
            original = view.getBackground();
            tint = view.getBackgroundTintList();
            dark = theme.isDark();
        }

        void apply() {
            if (view.getBackground() != surface) {
                original = view.getBackground();
                tint = view.getBackgroundTintList();
                background(view, surface);
            }
            if (view.getBackgroundTintList() != null) {
                tint = view.getBackgroundTintList();
                view.setBackgroundTintList(null);
            }
            if (dark != theme.isDark()) {
                dark = theme.isDark();
                surface.invalidateSelf();
            }
        }

        float corner() {
            try {
                Method getter = view.getClass().getMethod("getRadius");
                float radius = ((Number) getter.invoke(view)).floatValue();
                if (radius > 0) return radius;
            } catch (ReflectiveOperationException | ClassCastException ignored) { }
            Drawable drawable = original;
            while (drawable instanceof InsetDrawable)
                drawable = ((InsetDrawable) drawable).getDrawable();
            if (drawable instanceof GradientDrawable) {
                GradientDrawable gradient = (GradientDrawable) drawable;
                float[] corners = gradient.getCornerRadii();
                if (corners != null && corners.length > 0 && corners[0] > 0f)
                    return corners[0];
                if (gradient.getCornerRadius() > 0f) return gradient.getCornerRadius();
            }
            return 12f * view.getResources().getDisplayMetrics().density;
        }

        void restore() {
            if (view.getBackground() == surface) {
                background(view, original);
                view.setBackgroundTintList(tint);
            }
        }
    }

    private final class Surface extends Drawable {
        final Entry entry;
        final RectF shape = new RectF();
        private int alpha = 255;
        Surface(Entry entry) { this.entry = entry; }

        @Override public void draw(Canvas canvas) {
            if (!active.getAsBoolean()) { original(canvas); return; }
            if (entry.role == CLEAR || getBounds().isEmpty()) return;
            if (!canvas.isHardwareAccelerated() || shaderFailed) { original(canvas); return; }
            try {
                shape.set(getBounds());
                if (entry.role == CARD) {
                    // Chooser cells have contiguous weighted layout bounds. Inset only
                    // the material, leaving the full cell available for taps and icons.
                    float inset = Math.min(4f * entry.view.getResources().getDisplayMetrics().density,
                            shape.width() / 4f);
                    shape.inset(inset, 0f);
                }
                boolean dark = theme.isDark();
                boolean selected = entry.view.isSelected(), pressed = entry.view.isPressed();
                int fill = dark ? (pressed ? 0x3dffffff : selected ? 0x38ffffff : 0x26ffffff)
                        : pressed ? 0x85bbbec2 : selected ? 0xb3ffffff : 0x8affffff;
                if (!entry.view.isEnabled()) fill = dark ? 0x18ffffff : 0x55ffffff;
                if (entry.role == BACKPLATE)
                    fill = dark ? 0x1effffff : 0x40ffffff; // broad grid backplate, not an opaque card
                // DIALOG_CARD deliberately keeps its original bounds and radius;
                // unlike chooser cells it must not gain an extra 4dp inset.
                float radius = entry.role == ICON ? Math.min(shape.width(), shape.height()) / 2f
                        : Math.min(entry.corner(), Math.min(shape.width(), shape.height()) / 2f);
                if (shader == null) shader = new XiaobuKeyShader(shaderSource);
                if (entry.role == ICON) {
                    float size = Math.min(shape.width(), shape.height());
                    shape.set(shape.centerX() - size / 2f, shape.centerY() - size / 2f,
                            shape.centerX() + size / 2f, shape.centerY() + size / 2f);
                    shader.drawToolbar(canvas, shape, fill, dark, alpha);
                } else {
                    shader.draw(canvas, shape, radius, fill, dark, alpha);
                }
            } catch (RuntimeException | LinkageError error) {
                shaderFailed = true;
                module.error("auxiliary material shader failed; restore original backgrounds", error);
                original(canvas);
                if (root != null) root.post(AuxiliaryPagesMaterial.this::clear);
            }
        }

        private void original(Canvas canvas) {
            Drawable drawable = entry.original;
            if (drawable == null) return;
            drawable.setBounds(getBounds());
            drawable.setState(getState());
            drawable.draw(canvas);
        }
        @Override public boolean isStateful() { return true; }
        @Override protected boolean onStateChange(int[] state) {
            invalidateSelf(); return true;
        }
        @Override public void setAlpha(int value) { alpha = value; invalidateSelf(); }
        @Override public void setColorFilter(ColorFilter filter) { }
        @Override public int getOpacity() { return PixelFormat.TRANSLUCENT; }
    }
}
