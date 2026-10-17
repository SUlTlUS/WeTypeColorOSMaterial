package dev.coloros.wetype.material;

import android.content.res.ColorStateList;
import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.PixelFormat;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewParent;
import android.view.ViewTreeObserver;
import android.widget.ImageView;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.IdentityHashMap;
import java.util.Iterator;
import java.util.Map;

/** Xiaobu C0882y's circular style-1 material on WeType's actual custom toolbar buttons. */
final class ToolbarMaterial {
    // Verified from WeType 3.5.5 R.id and ToolbarAdapter; resource names are obfuscated in the APK.
    private static final int ITEM = 2131296696, RECENT_ITEM = 2131296695;
    private static final int IMAGE = 2131296698, RECENT_IMAGE = 2131296699;
    private static final int COMPLETE = 2131296687, COMPLETE_IMAGE = 2131296688;
    private final WeTypeMaterialModule module;
    private final ColorOsMaterial material;
    private final WeTypeTheme theme;
    private final String source;
    private final Map<View, Entry> entries = new IdentityHashMap<>();
    private final ViewTreeObserver.OnGlobalLayoutListener layout = this::refresh;
    private final ViewTreeObserver.OnPreDrawListener preDraw = () -> { reconcile(); return true; };
    private View root;
    private ViewTreeObserver observer;
    private XiaobuKeyShader shader;
    private boolean failed, logged;

    ToolbarMaterial(WeTypeMaterialModule module, ColorOsMaterial material, WeTypeTheme theme) throws Exception {
        this.module = module; this.material = material; this.theme = theme;
        try (InputStream input = ToolbarMaterial.class.getResourceAsStream("/material/xiaobu_shadowedge.agsl")) {
            if (input == null) throw new IllegalStateException("Missing Xiaobu toolbar shader");
            source = new String(input.readAllBytes(), StandardCharsets.UTF_8);
        }
        module.add(View.class.getDeclaredMethod("setPressed", boolean.class), chain -> {
            Object result = chain.proceed();
            if (material.isActive() && !entries.isEmpty()) {
                View pressed = (View) chain.getThisObject();
                for (Entry entry : entries.values()) {
                    if (within(entry.owner, pressed, 4) || within(pressed, entry.owner, 4))
                        entry.background.invalidateSelf();
                }
            }
            return result;
        });
    }

    void attach(View view) {
        detach();
        root = view;
        observer = view.getViewTreeObserver();
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
    }

    void refresh() {
        if (root == null || !root.isAttachedToWindow() || !material.isActive() || failed) {
            clear();
            return;
        }
        scan(root);
        reconcile();
        if (!logged && !entries.isEmpty()) {
            module.info("toolbar material attached: buttons=" + entries.size() + "; NEW_G2 style 1");
            logged = true;
        }
    }

    private void scan(View view) {
        int id = view.getId();
        if (id == ITEM || id == RECENT_ITEM || id == COMPLETE) {
            int imageId = id == RECENT_ITEM ? RECENT_IMAGE : id == COMPLETE ? COMPLETE_IMAGE : IMAGE;
            View image = view.findViewById(imageId);
            if (image instanceof ImageView && !entries.containsKey(view))
                entries.put(view, new Entry(view, (ImageView) image));
        }
        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int i = 0; i < group.getChildCount(); i++) scan(group.getChildAt(i));
        }
    }

    private void reconcile() {
        if (root == null || !root.isAttachedToWindow() || !material.isActive() || failed) {
            clear();
            return;
        }
        Iterator<Entry> iterator = entries.values().iterator();
        while (iterator.hasNext()) {
            Entry entry = iterator.next();
            if (!within(entry.owner, root, 32) || !within(entry.icon, entry.owner, 8)) {
                entry.restore(); iterator.remove();
            } else entry.apply();
        }
    }

    void clear() {
        for (Entry entry : entries.values()) entry.restore();
        entries.clear();
    }

    private static boolean within(View child, View parent, int limit) {
        for (int i = 0; child != null && i <= limit; i++) {
            if (child == parent) return true;
            ViewParent next = child.getParent();
            child = next instanceof View ? (View) next : null;
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
        final View owner;
        final ImageView icon;
        final Circle background = new Circle(this);
        Drawable originalBackground;
        ColorStateList originalTint;
        ColorFilter originalFilter, appliedFilter;
        boolean dark;

        Entry(View owner, ImageView icon) {
            this.owner = owner; this.icon = icon;
            originalBackground = owner.getBackground();
            originalTint = icon.getImageTintList();
            originalFilter = icon.getColorFilter();
        }

        void apply() {
            if (owner.getBackground() != background) {
                originalBackground = owner.getBackground();
                background(owner, background);
            }
            boolean changed = appliedFilter == null || dark != theme.isDark();
            if (appliedFilter == null || icon.getColorFilter() != appliedFilter || icon.getImageTintList() != null) {
                if (appliedFilter == null || icon.getColorFilter() != appliedFilter)
                    originalFilter = icon.getColorFilter();
                originalTint = icon.getImageTintList();
                changed = true;
            }
            if (changed) {
                dark = theme.isDark();
                appliedFilter = new PorterDuffColorFilter(dark ? 0xfff2f2f2 : 0xff757575, PorterDuff.Mode.SRC_IN);
                icon.setImageTintList(null);
                icon.setColorFilter(appliedFilter);
                background.invalidateSelf();
            }
        }

        boolean pressed() {
            View current = owner;
            for (int i = 0; current != null && current != root && i < 4; i++) {
                if (current.isPressed()) return true;
                ViewParent parent = current.getParent();
                current = parent instanceof View ? (View) parent : null;
            }
            return icon.isPressed();
        }

        void restore() {
            if (owner.getBackground() == background) background(owner, originalBackground);
            if (icon.getColorFilter() == appliedFilter && appliedFilter != null) {
                icon.setColorFilter(originalFilter);
                icon.setImageTintList(originalTint);
            }
        }
    }

    private final class Circle extends Drawable {
        private final Entry entry;
        private final Rect iconRect = new Rect();
        private final RectF shape = new RectF();
        private int alpha = 255;

        Circle(Entry entry) { this.entry = entry; }

        @Override public void draw(Canvas canvas) {
            if (!material.isActive() || failed || !canvas.isHardwareAccelerated()) {
                drawOriginal(canvas);
                return;
            }
            Rect bounds = getBounds();
            if (bounds.isEmpty()) return;
            try {
                entry.icon.getDrawingRect(iconRect);
                if (entry.owner instanceof ViewGroup)
                    ((ViewGroup) entry.owner).offsetDescendantRectToMyCoords(entry.icon, iconRect);
                float diameter = Math.min(bounds.width(), bounds.height());
                float cx = Math.max(bounds.left + diameter / 2f,
                        Math.min(bounds.right - diameter / 2f, bounds.left + iconRect.exactCenterX()));
                float cy = bounds.exactCenterY();
                shape.set(cx - diameter / 2f, cy - diameter / 2f, cx + diameter / 2f, cy + diameter / 2f);
                boolean dark = theme.isDark(), pressed = entry.pressed();
                int color = dark ? (pressed ? 0x3dffffff : 0x26ffffff)
                        : (pressed ? 0x85bbbec2 : 0x8affffff);
                if (shader == null) shader = new XiaobuKeyShader(source);
                shader.drawToolbar(canvas, shape, color, dark, alpha);
            } catch (RuntimeException | LinkageError error) {
                failed = true;
                module.error("toolbar shader failed; restore original toolbar", error);
                drawOriginal(canvas);
                if (root != null) root.post(ToolbarMaterial.this::clear);
            }
        }

        private void drawOriginal(Canvas canvas) {
            Drawable original = entry.originalBackground;
            if (original == null) return;
            original.setBounds(getBounds());
            original.setState(getState());
            original.draw(canvas);
        }

        @Override public boolean isStateful() { return true; }
        @Override protected boolean onStateChange(int[] state) { invalidateSelf(); return true; }
        @Override public void setAlpha(int value) { alpha = value; invalidateSelf(); }
        @Override public void setColorFilter(ColorFilter filter) { }
        @Override public int getOpacity() { return PixelFormat.TRANSLUCENT; }
    }
}
