package dev.coloros.wetype.material;

import android.graphics.Bitmap;
import android.graphics.ColorSpace;
import android.graphics.HardwareRenderer;
import android.graphics.PixelFormat;
import android.graphics.RecordingCanvas;
import android.graphics.RenderNode;
import android.hardware.HardwareBuffer;
import android.media.Image;
import android.media.ImageReader;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;
import android.widget.ImageView;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.WeakHashMap;
import java.util.function.BooleanSupplier;

/** Transparent hardware snapshots and independent material for WeType's resize PopupWindow. */
final class FloatingPreviewMaterial {
    private final ClassLoader loader;
    private final WeTypeMaterialModule module;
    private final WeTypeTheme theme;
    private final Method getSurface, getContent, captureRoot;
    private final Field modelInstance, snapshot;
    private final Map<Bitmap, Boolean> captures = new WeakHashMap<>();
    private final Map<View, Preview> previews = new IdentityHashMap<>();
    private boolean captureFaultLogged, suspended;

    FloatingPreviewMaterial(ClassLoader loader, WeTypeMaterialModule module, WeTypeTheme theme,
                            BooleanSupplier active, Class<?> popupType,
                            Method getSurface, Method getContent) throws Exception {
        this.loader = loader; this.module = module; this.theme = theme;
        this.getSurface = getSurface; this.getContent = getContent;
        Class<?> model = Class.forName("com.tencent.wetype.plugin.hld.model.N", false, loader);
        captureRoot = model.getDeclaredMethod("C0");
        Field singleton = null;
        for (Field field : model.getDeclaredFields()) {
            if (Modifier.isStatic(field.getModifiers()) && field.getType() == model) {
                singleton = field; break;
            }
        }
        if (singleton == null) throw new NoSuchFieldException("keyboard model singleton");
        modelInstance = singleton; modelInstance.setAccessible(true);
        snapshot = popupType.getDeclaredField("U");
        if (snapshot.getType() != ImageView.class) throw new NoSuchFieldException("floating snapshot ImageView");
        snapshot.setAccessible(true);
        Class<?> factory = Class.forName(popupType.getPackage().getName() + ".f$g", false, loader);
        Method capture = factory.getDeclaredMethod("g");
        module.add(capture, chain -> {
            if (active.getAsBoolean()) {
                try {
                    View source = (View) captureRoot.invoke(modelInstance.get(null));
                    Bitmap bitmap = hardwareSnapshot(source);
                    if (bitmap != null) {
                        captures.put(bitmap, Boolean.TRUE);
                        module.info("floating resize snapshot captured on hardware canvas with alpha");
                        return bitmap;
                    }
                } catch (Exception | LinkageError error) {
                    if (!captureFaultLogged) {
                        module.error("floating hardware snapshot unavailable; keep original preview", error);
                        captureFaultLogged = true;
                    }
                }
            }
            return chain.proceed();
        });
        module.deoptimize(factory.getDeclaredMethod("invoke"));
    }

    private Bitmap hardwareSnapshot(View source) {
        if (source == null || !source.isAttachedToWindow() || !source.isHardwareAccelerated()) return null;
        int width = source.getWidth(), height = source.getHeight();
        if (width <= 0 || height <= 0 || (long) width * height > 16_777_216L) return null;
        RenderNode node = new RenderNode("WeType floating material snapshot");
        node.setPosition(0, 0, width, height);
        Bitmap hardware = null;
        HardwareRenderer renderer = null;
        ImageReader reader = null;
        Image image = null;
        HardwareBuffer buffer = null;
        try {
            RecordingCanvas canvas = node.beginRecording(width, height);
            Drawable originalBackground = source.getBackground();
            try {
                // Capture keyboard content, never its window/panel fill. Restore in
                // the same UI-thread call before another frame can observe the change.
                setBackground(source, null);
                source.draw(canvas);
            } finally {
                if (source.getBackground() == null) setBackground(source, originalBackground);
                node.endRecording();
            }
            // Public HWUI APIs; createHardwareBitmap() is blocked for target SDK 35.
            reader = ImageReader.newInstance(width, height, PixelFormat.RGBA_8888, 2,
                    HardwareBuffer.USAGE_GPU_SAMPLED_IMAGE | HardwareBuffer.USAGE_GPU_COLOR_OUTPUT);
            renderer = new HardwareRenderer();
            renderer.setSurface(reader.getSurface());
            renderer.setOpaque(false);
            renderer.setContentRoot(node);
            int result = renderer.createRenderRequest().setWaitForPresent(true).syncAndDraw();
            if ((result & (HardwareRenderer.SYNC_LOST_SURFACE_REWARD_IF_FOUND
                    | HardwareRenderer.SYNC_CONTEXT_IS_STOPPED | HardwareRenderer.SYNC_FRAME_DROPPED)) != 0)
                return null;
            image = reader.acquireLatestImage();
            if (image == null) return null;
            buffer = image.getHardwareBuffer();
            if (buffer == null) return null;
            hardware = Bitmap.wrapHardwareBuffer(buffer, ColorSpace.get(ColorSpace.Named.SRGB));
            if (hardware == null) return null;
            // Stock Z()/l0() mutate the alpha flag. Use a readable ARGB copy and
            // keep ownership of this bitmap; never alter WeType's original captures.
            Bitmap bitmap = hardware.copy(Bitmap.Config.ARGB_8888, false);
            if (bitmap != null) bitmap.setHasAlpha(true);
            return bitmap;
        } finally {
            if (hardware != null) hardware.recycle();
            if (buffer != null) buffer.close();
            if (image != null) image.close();
            if (renderer != null) renderer.destroy();
            if (reader != null) reader.close();
            node.discardDisplayList();
        }
    }

    void attach(View popup) throws Exception {
        if (previews.containsKey(popup)) { previews.get(popup).refresh(true); return; }
        ImageView image = (ImageView) snapshot.get(popup);
        if (ownedBitmap(image) == null) return; // Safe fallback when hardware capture was unavailable.
        Preview preview = new Preview(popup, image);
        previews.put(popup, preview);
        preview.refresh(true);
        module.info("floating resize preview material bound");
    }

    private Bitmap ownedBitmap(ImageView image) {
        Drawable drawable = image == null ? null : image.getDrawable();
        Bitmap bitmap = drawable instanceof BitmapDrawable ? ((BitmapDrawable) drawable).getBitmap() : null;
        return bitmap != null && !bitmap.isRecycled() && captures.containsKey(bitmap) ? bitmap : null;
    }

    boolean isActive() {
        for (Preview preview : previews.values()) if (preview.material.isActive()) return true;
        return false;
    }

    void detach(View popup) {
        Preview preview = previews.remove(popup);
        if (preview != null) preview.detach();
    }

    void resume() { suspended = false; refresh(); }
    void refresh() { for (Preview preview : previews.values()) preview.refresh(true); }
    void suspend() { suspended = true; for (Preview preview : previews.values()) preview.restore(); }
    void clear() {
        for (Preview preview : previews.values()) preview.detach();
        previews.clear();
    }

    private final class Preview {
        final View popup, surface, body;
        final ImageView image, background;
        final FloatingResizeFrame frame;
        final ColorOsMaterial material;
        final ViewTreeObserver observer;
        final ViewTreeObserver.OnGlobalLayoutListener layout = () -> refresh(true);
        final ViewTreeObserver.OnPreDrawListener beforeDraw = () -> { refresh(false); return true; };
        Drawable bodyBackground;
        float backgroundAlpha;
        boolean clearedBody, hiddenBackground, failed;
        int width, height;
        boolean dark;

        Preview(View popup, ImageView image) throws Exception {
            this.popup = popup; this.image = image;
            surface = (View) getSurface.invoke(popup);
            body = (View) getContent.invoke(popup);
            material = new ColorOsMaterial(loader, module, theme, true);
            material.setFloatingCorner(((Number) surface.getClass().getMethod("getRadius")
                    .invoke(surface)).floatValue());
            ViewGroup group = surface instanceof ViewGroup ? (ViewGroup) surface : null;
            background = group != null && group.getChildCount() > 0
                    && group.getChildAt(0) instanceof ImageView ? (ImageView) group.getChildAt(0) : null;
            frame = new FloatingResizeFrame(group != null && group.getChildCount() > 1
                    && group.getChildAt(1) instanceof ImageView ? (ImageView) group.getChildAt(1) : null,
                    body, surface);
            observer = popup.getViewTreeObserver();
            observer.addOnGlobalLayoutListener(layout);
            observer.addOnPreDrawListener(beforeDraw);
        }

        void refresh(boolean force) {
            if (suspended || failed || !popup.isAttachedToWindow() || popup.getWindowVisibility() != View.VISIBLE
                    || !popup.isShown()) { restore(); return; }
            Bitmap bitmap = ownedBitmap(image);
            if (bitmap == null) { restore(); return; }
            try {
                // WeType forces screenshot alpha off both at construction and on reuse.
                if (!bitmap.hasAlpha()) { bitmap.setHasAlpha(true); image.invalidate(); }
                if (force || !material.isActive() || material.needsRebind()
                        || width != surface.getWidth() || height != surface.getHeight() || dark != theme.isDark()) {
                    if (!material.isActive()) {
                        if (!material.prepare(surface) || !material.activate(null)) { restore(); return; }
                    } else material.refresh();
                    if (!material.isActive()) { restore(); return; }
                    width = surface.getWidth(); height = surface.getHeight(); dark = theme.isDark();
                }
                if (body.getBackground() != null) {
                    bodyBackground = body.getBackground();
                    setBackground(body, null); clearedBody = true;
                }
                if (background != null && background.getAlpha() != 0f) {
                    backgroundAlpha = background.getAlpha();
                    background.setAlpha(0f); hiddenBackground = true;
                }
                frame.apply();
            } catch (Exception | LinkageError error) {
                failed = true; restore();
                module.error("floating resize material unavailable; restore original panel", error);
            }
        }

        void restore() {
            material.release();
            if (clearedBody && body.getBackground() == null) setBackground(body, bodyBackground);
            if (hiddenBackground && background != null && background.getAlpha() == 0f)
                background.setAlpha(backgroundAlpha);
            frame.restore();
            clearedBody = hiddenBackground = false;
        }

        void detach() {
            if (observer.isAlive()) {
                observer.removeOnGlobalLayoutListener(layout);
                observer.removeOnPreDrawListener(beforeDraw);
            }
            restore();
        }
    }

    private static void setBackground(View view, Drawable drawable) {
        int l = view.getPaddingLeft(), t = view.getPaddingTop(), r = view.getPaddingRight(), b = view.getPaddingBottom();
        view.setBackground(drawable); view.setPadding(l, t, r, b);
    }
}
