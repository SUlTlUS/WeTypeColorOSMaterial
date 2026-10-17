package dev.coloros.wetype.direct;

import android.content.Context;
import android.graphics.Canvas;
import android.util.AttributeSet;
import android.view.View;
import android.widget.FrameLayout;

/** The stock IME stack/settings container with the module's actual drawChild dispatch. */
public final class MaterialFrameLayout extends FrameLayout {
    private static final String DRAW = "Landroid/view/ViewGroup;->drawChild(Landroid/graphics/Canvas;Landroid/view/View;J)Z";
    public MaterialFrameLayout(Context context) { super(context); }
    public MaterialFrameLayout(Context context, AttributeSet attrs) { super(context, attrs); }
    public MaterialFrameLayout(Context context, AttributeSet attrs, int style) { super(context, attrs, style); }
    public MaterialFrameLayout(Context context, AttributeSet attrs, int style, int resource) {
        super(context, attrs, style, resource);
    }
    @Override protected boolean drawChild(Canvas canvas, View child, long time) {
        try {
            return (boolean) DirectRuntime.run(DRAW, this, new Object[]{canvas, child, time},
                    () -> super.drawChild(canvas, child, time));
        } catch (RuntimeException | Error error) { throw error; }
        catch (Throwable error) { throw new IllegalStateException("IME child drawing callback failed", error); }
    }
}
