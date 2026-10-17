package dev.coloros.wetype.material;

import android.content.Context;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.util.TypedValue;
import android.view.ContextThemeWrapper;
import java.lang.reflect.Method;

/** Apply the system appearance through WeType's actual skin engine. */
final class WeTypeTheme {
    private final Method systemResources, skinManager, currentSkin, selectSkin;
    private Context context;
    private boolean dark;
    private boolean syncing;
    private Integer containerColor;

    WeTypeTheme(ClassLoader loader) throws ReflectiveOperationException {
        systemResources = Class.forName("e7.u", false, loader).getDeclaredMethod("j");
        skinManager = Class.forName("com.tencent.wetype.plugin.hld.skin.e", false, loader)
                .getDeclaredMethod("c");
        Class<?> manager = skinManager.getReturnType();
        currentSkin = manager.getDeclaredMethod("s");
        selectSkin = manager.getDeclaredMethod("j", int.class);
        for (Method method : new Method[]{systemResources, skinManager, currentSkin, selectSkin})
            method.setAccessible(true);
    }

    void refresh() throws ReflectiveOperationException {
        Resources resources = (Resources) systemResources.invoke(null);
        int night = resources.getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK;
        boolean value = night == Configuration.UI_MODE_NIGHT_YES;
        if (value != dark) { containerColor = null; dark = value; }
    }

    void bind(Context value) { context = value; containerColor = null; }

    void syncSkin() throws ReflectiveOperationException {
        refresh();
        // A COUI palette can change without a day/night transition.
        containerColor = null;
        if (syncing) return;
        Object manager = skinManager.invoke(null);
        int desired = dark ? 2 : 1; // ImeSkinDefine.SKIN_DARK / SKIN_WHITE
        if ((int) currentSkin.invoke(manager) == desired) return;
        syncing = true;
        try { selectSkin.invoke(manager, desired); }
        finally { syncing = false; }
    }

    int accent(int fallback) {
        if (containerColor == null && context != null) {
            try {
                Context reference = context.createPackageContext("com.oplus.keyboard", 0);
                Configuration config = new Configuration(reference.getResources().getConfiguration());
                config.uiMode = (config.uiMode & ~Configuration.UI_MODE_NIGHT_MASK)
                        | (dark ? Configuration.UI_MODE_NIGHT_YES : Configuration.UI_MODE_NIGHT_NO);
                reference = reference.createConfigurationContext(config);
                int style = reference.getResources().getIdentifier(
                        dark ? "Theme.COUI.Main.Dark" : "Theme.COUI.Main.Light", "style", "com.oplus.keyboard");
                if (style == 0) return fallback;
                ContextThemeWrapper themed = new ContextThemeWrapper(reference, style);
                XiaobuThemeOverlay.apply(themed);
                int attr = reference.getResources().getIdentifier("couiColorContainerTheme", "attr", "com.oplus.keyboard");
                TypedValue resolved = new TypedValue();
                if (attr != 0 && themed.getTheme().resolveAttribute(attr, resolved, true)) {
                    if (resolved.type >= TypedValue.TYPE_FIRST_COLOR_INT && resolved.type <= TypedValue.TYPE_LAST_COLOR_INT)
                        containerColor = resolved.data;
                    else if (resolved.resourceId != 0)
                        containerColor = themed.getResources().getColor(resolved.resourceId, themed.getTheme());
                }
            } catch (Exception ignored) { /* No installed Xiaobu/theme resource: retain WeType's accent. */ }
            if (containerColor == null) containerColor = fallback;
        }
        return containerColor == null ? fallback : containerColor;
    }

    boolean isDark() { return dark; }
}
