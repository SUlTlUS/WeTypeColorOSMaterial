package dev.coloros.wetype.material;

import android.graphics.Canvas;
import android.graphics.BlendMode;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.RuntimeShader;

/** Xiaobu's original NEW_G2 shader with AbstractC0876s's style-2 key parameters. */
final class XiaobuKeyShader {
    private final RuntimeShader shader;
    private final Paint effectPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint fillPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final float[] common = new float[12], shadow = new float[16], edge = new float[16];

    XiaobuKeyShader(String source) {
        shader = new RuntimeShader(source);
        shader.setFloatUniform("u_shadow", 1f);
        shader.setFloatUniform("u_edge", 1f);
        effectPaint.setShader(shader);
    }

    void draw(Canvas canvas, RectF bounds, float radius, int fill, boolean dark, int alpha) {
        draw(canvas, bounds, radius, fill, dark, alpha, false);
    }

    void drawToolbar(Canvas canvas, RectF bounds, int fill, boolean dark, int alpha) {
        draw(canvas, bounds, Math.min(bounds.width(), bounds.height()) / 2f, fill, dark, alpha, true);
    }

    /** C0879v/C0880w: additive foreground edge, no shadow or fill, docked bottom clipped. */
    void drawPanel(Canvas canvas, RectF bounds, float topRadius, float lineWidth,
                   boolean dark, int alpha) {
        drawPanel(canvas, bounds, topRadius, lineWidth, dark, alpha, false);
    }

    void drawPanel(Canvas canvas, RectF bounds, float topRadius, float lineWidth,
                   boolean dark, int alpha, boolean floating) {
        float width = bounds.width(), height = bounds.height();
        float scale = 3f * .34f;
        common[0] = common[4] = width;
        common[1] = common[5] = height;
        // COUI setCornerRadius(leftTop, rightTop, leftBottom, rightBottom).
        // Shader order is bottom-left, bottom-right, top-right, top-left.
        common[6] = common[7] = floating ? topRadius : 0f;
        common[8] = common[9] = topRadius;
        common[10] = 3f;
        common[11] = 1f;
        float ratio = lineWidth / 10f; // EDGE_STYLE_2 near=(2,8), far=(2,2).
        edge[0] = edge[1] = edge[2] = 1f;
        edge[3] = edge[5] = Math.max(2f, 2f * ratio);
        edge[4] = edge[3] + Math.max(2f, 8f * ratio);
        edge[6] = edge[5] + Math.max(2f, 2f * ratio);
        edge[7] = .7f * (dark ? .4f : 1f);
        edge[8] = .6f * (dark ? .4f : 1f);
        fade(.18f, .35f, width / scale, height / scale, 600f, 200f, 9);
        fade(.25f, .25f, width / scale, height / scale, 600f, 200f, 11);
        edge[10] += edge[9];
        edge[12] += edge[11];
        edge[13] = 0f;
        edge[14] = 4f;
        edge[15] = .8f;
        shader.setFloatUniform("u_shadow", 0f);
        shader.setFloatUniform("u_commonArray", common);
        shader.setFloatUniform("u_edgeArray", edge);
        effectPaint.setBlendMode(BlendMode.PLUS);
        effectPaint.setAlpha(alpha);
        int save = canvas.save();
        try {
            canvas.translate(bounds.left, bounds.top);
            canvas.clipRect(0f, 0f, width, floating ? height : Math.max(1f, height - lineWidth));
            canvas.drawRect(0f, 0f, width, height, effectPaint);
        } finally {
            canvas.restoreToCount(save);
            effectPaint.setBlendMode(null);
            shader.setFloatUniform("u_shadow", 1f);
        }
    }

    private void draw(Canvas canvas, RectF bounds, float radius, int fill, boolean dark, int alpha, boolean toolbar) {
        float width = bounds.width(), height = bounds.height();
        // ShadowEdgeShader's Canvas drawable uses the default density of 3.
        float scale = 3f * .34f;
        float normalizedWidth = Math.max(1f, width) / scale;
        float normalizedHeight = Math.max(1f, height) / scale;
        common[0] = common[4] = width;
        common[1] = common[5] = height;
        common[6] = common[7] = common[8] = common[9] = radius;
        common[10] = toolbar ? 2.3f : 2f;
        common[11] = 1f;
        shadow[0] = shadow[1] = shadow[2] = 1f;
        shadow[3] = (toolbar ? .4f : .6f) * (dark ? .2f : .4f);
        shadow[6] = (toolbar ? 140f : 86f) * .5f * scale;
        shadow[8] = (toolbar ? 20f : 10f) * scale;
        shadow[9] = 1f + (toolbar ? .4f * 160f : .1f * 600f) / normalizedWidth;
        shadow[10] = 1f + (toolbar ? .35f * 160f : .07f * 600f) / normalizedHeight;
        shadow[11] = 4f;
        shadow[12] = toolbar ? .9f : .7f;
        shadow[13] = (toolbar ? 20f : 1f) * .5f * scale;
        shadow[14] = toolbar ? 4f : 1f;
        shadow[15] = toolbar ? .7f : 0f;
        edge[0] = edge[1] = edge[2] = 1f;
        // StyleMapper clamps both near/far distances to 2px for lineWidth=1.
        edge[3] = edge[5] = 2f;
        edge[4] = edge[6] = 4f;
        edge[7] = .7f * (dark && !toolbar ? .2f : 1f);
        edge[8] = (toolbar ? 1f : .6f) * (dark && !toolbar ? .2f : 1f);
        fade(toolbar ? .06f : .18f, toolbar ? .4f : .35f, normalizedWidth, normalizedHeight,
                toolbar ? 160f : 600f, toolbar ? 160f : 200f, 9);
        fade(toolbar ? .01f : .25f, toolbar ? .28f : .25f, normalizedWidth, normalizedHeight,
                toolbar ? 160f : 600f, toolbar ? 160f : 200f, 11);
        edge[10] += edge[9];
        edge[12] += edge[11];
        edge[14] = 4f;
        edge[15] = .8f;
        shader.setFloatUniform("u_commonArray", common);
        shader.setFloatUniform("u_shadowArray", shadow);
        shader.setFloatUniform("u_edgeArray", edge);
        int save = canvas.save();
        try {
            canvas.translate(bounds.left, bounds.top);
            canvas.clipRect(0f, 0f, width, height);
            fillPaint.setColor(fill);
            fillPaint.setAlpha(((fill >>> 24) * alpha) / 255);
            effectPaint.setAlpha(alpha);
            if (toolbar) canvas.drawOval(0f, 0f, width, height, fillPaint);
            else canvas.drawRoundRect(0f, 0f, width, height, radius, radius, fillPaint);
            canvas.drawRect(0f, 0f, width, height, effectPaint);
        } finally { canvas.restoreToCount(save); }
    }

    private void fade(float start, float length, float width, float height,
                      float referenceWidth, float referenceHeight, int index) {
        // Verbatim arithmetic of GradientStrokeStyleKt.rectLineFadeForSize (600 x 200 preset).
        float referencePerimeter = (referenceHeight + referenceWidth) * 2f;
        float perimeter = (height + width) * 2f;
        float sum = start + length;
        float end = sum * referencePerimeter;
        if (end > referenceWidth) {
            float referenceLength = length * referencePerimeter;
            edge[index] = ((end - referenceWidth + width) - referenceLength) / perimeter;
            edge[index + 1] = referenceLength / perimeter;
        } else {
            float remaining = referenceWidth - end;
            float factor = (width - remaining) / perimeter;
            edge[index] = sum <= 0f ? start : start * factor / sum;
            edge[index + 1] = sum <= 0f ? length : factor * length / sum;
        }
    }
}
