package dev.coloros.wetype.material;

import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.PixelFormat;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.LayerDrawable;
import android.view.View;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

/** Xiaobu's independent foreground highlight over the native blurred keyboard panel. */
final class PanelEdgeMaterial {
    private final WeTypeMaterialModule module;
    private final String source;
    private View target;
    private Drawable original, installed;
    private Stroke stroke;
    private boolean failed;

    PanelEdgeMaterial(WeTypeMaterialModule module) throws Exception {
        this.module = module;
        try (InputStream input = PanelEdgeMaterial.class.getResourceAsStream("/material/xiaobu_shadowedge.agsl")) {
            if (input == null) throw new IllegalStateException("Missing Xiaobu panel edge shader");
            source = new String(input.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    void apply(View view, float corner, boolean dark) {
        apply(view, corner, dark, false);
    }

    void apply(View view, float corner, boolean dark, boolean floating) {
        if (target != view) {
            release();
            target = view;
            failed = false;
            stroke = new Stroke();
        }
        if (failed) return;
        float width = 4f * view.getResources().getDisplayMetrics().density;
        if (stroke.corner != corner || stroke.lineWidth != width || stroke.dark != dark || stroke.floating != floating) {
            stroke.corner = corner;
            stroke.lineWidth = width;
            stroke.dark = dark;
            stroke.floating = floating;
            stroke.invalidateSelf();
        }
        if (installed == null || view.getForeground() != installed) {
            original = view.getForeground();
            if (original == null) installed = stroke;
            else {
                LayerDrawable layers = new LayerDrawable(new Drawable[]{original, stroke});
                layers.setPaddingMode(LayerDrawable.PADDING_MODE_STACK);
                installed = layers;
            }
            view.setForeground(installed);
            module.info("panel edge attached; NEW_G2 style 2, PLUS, lineWidth=" + width);
        }
    }

    void release() {
        if (target != null && installed != null && target.getForeground() == installed)
            target.setForeground(original);
        target = null;
        original = installed = null;
        stroke = null;
    }

    private final class Stroke extends Drawable {
        private final RectF shape = new RectF();
        private XiaobuKeyShader shader;
        private float corner, lineWidth;
        private boolean dark, logged, floating;
        private int alpha = 255;

        @Override public void draw(Canvas canvas) {
            if (failed || getBounds().isEmpty() || !canvas.isHardwareAccelerated()) return;
            try {
                if (shader == null) shader = new XiaobuKeyShader(source);
                shape.set(getBounds());
                shader.drawPanel(canvas, shape, Math.min(corner, Math.min(shape.width(), shape.height()) / 2f),
                        lineWidth, dark, alpha, floating);
                if (!logged) {
                    module.info("panel edge drawn on hardware canvas; dark=" + dark);
                    logged = true;
                }
            } catch (RuntimeException | LinkageError error) {
                failed = true;
                module.error("panel edge shader failed; keep native panel background", error);
                if (target != null) target.post(() -> {
                    if (stroke == this) {
                        if (installed != null && target.getForeground() == installed)
                            target.setForeground(original);
                        original = installed = null;
                    }
                });
            }
        }

        @Override public void setAlpha(int value) { alpha = value; invalidateSelf(); }
        @Override public void setColorFilter(ColorFilter filter) { }
        @Override public int getOpacity() { return PixelFormat.TRANSLUCENT; }
    }
}
