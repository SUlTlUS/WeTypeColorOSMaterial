package dev.coloros.wetype.material;

import android.util.TypedValue;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

/** Fit the stock icon/label stack into the measured row without shrinking its touch target. */
final class ToolbarItemSizing {
    private final View icon;
    private final TextView label;
    private int originalWidth, originalHeight, originalGap;
    private float originalTextSize;
    private int appliedWidth = Integer.MIN_VALUE, appliedHeight, appliedGap;
    private float appliedTextSize;
    private final int[] originalPadding = new int[4], appliedPadding = new int[4];

    ToolbarItemSizing(View icon, TextView label) {
        this.icon = icon; this.label = label;
    }

    void apply(int rowHeight, float density) {
        ViewGroup.LayoutParams size = icon.getLayoutParams();
        ViewGroup.LayoutParams textParams = label.getLayoutParams();
        if (size == null || !(textParams instanceof ViewGroup.MarginLayoutParams)
                || rowHeight <= 0 || size.width <= 0 || size.height <= 0) return;
        ViewGroup.MarginLayoutParams margins = (ViewGroup.MarginLayoutParams) textParams;
        // Skin/configuration changes may replace the stock metrics while this sheet stays open.
        if (size.width != appliedWidth) originalWidth = size.width;
        if (appliedWidth == Integer.MIN_VALUE || size.height != appliedHeight) originalHeight = size.height;
        if (appliedWidth == Integer.MIN_VALUE || margins.topMargin != appliedGap) originalGap = margins.topMargin;
        if (appliedWidth == Integer.MIN_VALUE || label.getTextSize() != appliedTextSize)
            originalTextSize = label.getTextSize();
        int[] padding = {icon.getPaddingLeft(), icon.getPaddingTop(), icon.getPaddingRight(), icon.getPaddingBottom()};
        for (int i = 0; i < 4; i++) {
            if (appliedWidth == Integer.MIN_VALUE || padding[i] != appliedPadding[i]) originalPadding[i] = padding[i];
        }
        int room = Math.max(1, rowHeight - Math.round(8f * density));
        float textSize = Math.min(originalTextSize, room * .28f);
        // Reserve font ascent/descent, not a clipped TextView's measured height.
        float currentSize = Math.max(1f, label.getTextSize());
        float originalFontHeight = label.getPaint().getFontMetricsInt(null) * originalTextSize / currentSize;
        float fontHeight = originalFontHeight * textSize / originalTextSize;
        int gap = Math.min(originalGap, Math.round(4f * density));
        int maxIcon = Math.max(1, (int) (room - fontHeight - gap));
        boolean fits = originalHeight + originalGap + originalFontHeight <= room;
        if (fits) { textSize = originalTextSize; gap = originalGap; maxIcon = originalHeight; }
        float ratio = Math.min(1f, maxIcon / (float) originalHeight);
        int width = Math.max(1, Math.round(originalWidth * ratio));
        int height = Math.max(1, Math.round(originalHeight * ratio));
        if (size.width != width || size.height != height) {
            size.width = width; size.height = height; icon.setLayoutParams(size);
        }
        if (margins.topMargin != gap) { margins.topMargin = gap; label.setLayoutParams(margins); }
        if (label.getTextSize() != textSize) label.setTextSize(TypedValue.COMPLEX_UNIT_PX, textSize);
        for (int i = 0; i < 4; i++) appliedPadding[i] = Math.round(originalPadding[i] * ratio);
        if (padding[0] != appliedPadding[0] || padding[1] != appliedPadding[1]
                || padding[2] != appliedPadding[2] || padding[3] != appliedPadding[3])
            icon.setPadding(appliedPadding[0], appliedPadding[1], appliedPadding[2], appliedPadding[3]);
        appliedWidth = width; appliedHeight = height; appliedGap = gap; appliedTextSize = textSize;
    }

    void restore() {
        if (appliedWidth == Integer.MIN_VALUE) return;
        ViewGroup.LayoutParams size = icon.getLayoutParams();
        if (size != null && size.width == appliedWidth && size.height == appliedHeight) {
            size.width = originalWidth; size.height = originalHeight; icon.setLayoutParams(size);
        }
        ViewGroup.LayoutParams params = label.getLayoutParams();
        if (params instanceof ViewGroup.MarginLayoutParams) {
            ViewGroup.MarginLayoutParams margins = (ViewGroup.MarginLayoutParams) params;
            if (margins.topMargin == appliedGap) { margins.topMargin = originalGap; label.setLayoutParams(margins); }
        }
        if (label.getTextSize() == appliedTextSize) label.setTextSize(TypedValue.COMPLEX_UNIT_PX, originalTextSize);
        if (icon.getPaddingLeft() == appliedPadding[0] && icon.getPaddingTop() == appliedPadding[1]
                && icon.getPaddingRight() == appliedPadding[2] && icon.getPaddingBottom() == appliedPadding[3])
            icon.setPadding(originalPadding[0], originalPadding[1], originalPadding[2], originalPadding[3]);
    }
}
