package dev.coloros.wetype.material;

import android.graphics.ColorFilter;
import android.graphics.ColorMatrix;
import android.graphics.ColorMatrixColorFilter;
import android.graphics.Canvas;
import android.graphics.Matrix;
import android.graphics.Path;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.InsetDrawable;
import android.view.View;
import android.widget.ImageView;

/** Remove only the neutral opaque fill from WeType's green resize frame. */
final class FloatingResizeFrame {
    private final ImageView image;
    private final ColorFilter filter = new ColorMatrixColorFilter(new ColorMatrix(new float[]{
            1, 0, 0, 0, 0,
            0, 1, 0, 0, 0,
            0, 0, 1, 0, 0,
            -4, 4, 0, 0, -220
    }));
    private ColorFilter original;
    private boolean applied;
    private final View inner;
    private final float radius;
    private Drawable originalImage, installed;

    FloatingResizeFrame(ImageView image, View body, View surface) {
        this.image = image;
        // getContent() is the rounded J panel, a sibling of the I backdrop.
        // Walking toward I would reach the entire window and erase every marker.
        inner = body;
        float corner = 0;
        try { corner = ((Number) inner.getClass().getMethod("getRadius").invoke(inner)).floatValue(); }
        catch (ReflectiveOperationException ignored) { }
        radius = corner;
    }

    void apply() {
        if (image == null) return;
        if (image.getDrawable() != installed || installed == null) {
            originalImage = image.getDrawable();
            if (originalImage == null) return;
            installed = new InsetDrawable(originalImage, 0) {
                private final Path cutout = new Path();
                private final RectF rect = new RectF();
                private final Matrix inverse = new Matrix();
                private final Path arcs = new Path(), stroke = new Path();
                private final Paint pen = new Paint(Paint.ANTI_ALIAS_FLAG);
                private final int[] outerPosition = new int[2], innerPosition = new int[2];
                @Override public void draw(Canvas canvas) {
                    if (inner.getWidth() <= 0 || inner.getHeight() <= 0) return;
                    image.getLocationOnScreen(outerPosition);
                    inner.getLocationOnScreen(innerPosition);
                    float left = innerPosition[0] - outerPosition[0] - image.getPaddingLeft();
                    float top = innerPosition[1] - outerPosition[1] - image.getPaddingTop();
                    rect.set(left, top, left + inner.getWidth(), top + inner.getHeight());
                    cutout.reset();
                    cutout.addRoundRect(rect, radius, radius, Path.Direction.CW);
                    float width = 2f * image.getResources().getDisplayMetrics().density;
                    float outset = width * .6f, r = radius + outset;
                    float l = rect.left - outset, t = rect.top - outset;
                    float right = rect.right + outset, bottom = rect.bottom + outset;
                    arcs.reset();
                    arcs.addArc(l, t, l + 2*r, t + 2*r, 180, 90);
                    arcs.addArc(right - 2*r, t, right, t + 2*r, 270, 90);
                    arcs.addArc(right - 2*r, bottom - 2*r, right, bottom, 0, 90);
                    arcs.addArc(l, bottom - 2*r, l + 2*r, bottom, 90, 90);
                    pen.setStyle(Paint.Style.STROKE);
                    pen.setStrokeWidth(width); pen.setStrokeCap(Paint.Cap.ROUND);
                    stroke.reset(); pen.getFillPath(arcs, stroke);
                    // ImageView applies its image matrix before drawing this drawable.
                    if (image.getImageMatrix().invert(inverse)) {
                        cutout.transform(inverse); stroke.transform(inverse);
                    }
                    int save = canvas.save();
                    try { canvas.clipOutPath(cutout); canvas.clipPath(stroke); super.draw(canvas); }
                    finally { canvas.restoreToCount(save); }
                }
                @Override public int getOpacity() { return PixelFormat.TRANSLUCENT; }
            };
            image.setImageDrawable(installed);
        }
        if (image.getColorFilter() != filter) {
            original = image.getColorFilter();
            image.setColorFilter(filter);
        }
        applied = true;
    }

    void restore() {
        if (image != null && applied && image.getColorFilter() == filter)
            image.setColorFilter(original);
        if (image != null && image.getDrawable() == installed) image.setImageDrawable(originalImage);
        installed = originalImage = null;
        applied = false;
    }
}
