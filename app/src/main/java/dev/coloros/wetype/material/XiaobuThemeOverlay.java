package dev.coloros.wetype.material;

import android.content.Context;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.content.res.TypedArray;
import android.os.Build;
import android.provider.Settings;
import android.util.TypedValue;
import java.io.File;
import java.lang.reflect.Method;
import java.util.Locale;

/** Resource-only COUIThemeOverlay.resolveThemeStyle path from the supplied Xiaobu APK. */
final class XiaobuThemeOverlay {
    private XiaobuThemeOverlay() { }

    static void apply(Context context) throws ReflectiveOperationException {
        Configuration config = context.getResources().getConfiguration();
        Object extra = config.getClass().getField("mOplusExtraConfiguration").get(config);
        if (extra == null) return;
        long material = ((Number) extra.getClass().getField("mMaterialColor").get(extra)).longValue();
        long flags = ((Number) extra.getClass().getField("mThemeChangedFlags").get(extra)).longValue();
        int user = ((Number) extra.getClass().getField("mUserId").get(extra)).intValue();
        if (rejectTheme(context, config, flags, user) || material == 0) return;
        int select = (int) material & 0xffff;
        int type = (int) material & 0xff0000;
        if (select == 0 && type == 0) return;
        if (type == 0x20000) {
            applyStyle(context, id(context, "COUIOverlay.Theme.Single.First", "style"));
            return;
        }
        TypedValue identifier = new TypedValue();
        int attr = id(context, "couiThemeIdentifier", "attr");
        int themeIndex = attr != 0 && context.getTheme().resolveAttribute(attr, identifier, true)
                ? identifier.data : 0;
        int version = themeVersion();
        String manufacturer = Build.MANUFACTURER.toLowerCase(Locale.ROOT);
        boolean oneplus = manufacturer.equals("oneplus") || manufacturer.equals("galilei") || manufacturer.equals("faraday");
        int array;
        if (type == 0x10000) {
            array = id(context, oneplus ? (version < 12000 ? "coui_theme_arrays_single_repatch_p"
                    : "coui_theme_arrays_single_patch_p") : "coui_theme_arrays_single", "array");
        } else if (type == 0x40000) {
            array = id(context, "coui_theme_arrays_default_patch", "array");
            select = themeIndex - 1;
        } else if (type == 0 || type == 0x100000) {
            String name = "coui_theme_arrays_ids";
            boolean realme = manufacturer.equals("realme");
            if (version == 12000 && !(manufacturer.equals("oppo") && type == 0x100000))
                name = realme ? "coui_theme_arrays_ids_patch_r" : "coui_theme_arrays_ids_patch_o";
            else if (version < 12000)
                name = realme ? "coui_theme_arrays_ids_repatch_r" : "coui_theme_arrays_ids_repatch_o";
            array = resourceAt(context.getResources(), id(context, name, "array"), select - 1);
            select = themeIndex - 1;
        } else return;
        applyStyle(context, resourceAt(context.getResources(), array, select));
    }

    private static int themeVersion() throws ReflectiveOperationException {
        Method get = Class.forName("android.os.SystemProperties").getMethod("get", String.class, String.class);
        String value = (String) get.invoke(null, "ro.oplus.theme.version", "0");
        if (value.isEmpty() || value.equals("0"))
            value = (String) get.invoke(null, "ro.oppo.theme.version", "0");
        try { return Integer.parseInt(value.trim()); }
        catch (NumberFormatException ignored) { return 0; }
    }

    private static boolean rejectTheme(Context context, Configuration config, long flags, int user) {
        if ((config.uiMode & Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES || (flags & 1) == 0)
            return false;
        String pkg = context.getPackageName();
        if ((flags & 256) == 0) {
            File base = new File(user > 0 ? "/data/theme/" + user : "/data/theme");
            return new File(base, pkg).exists();
        }
        File base = new File("/my_company/media/theme");
        if (!base.exists()) return false;
        if (new File(base, pkg).exists()) return true;
        String custom = Settings.System.getString(context.getContentResolver(), "custom_theme_path_setting");
        return custom != null && !custom.isEmpty() && new File(custom, pkg).exists();
    }

    private static int id(Context context, String name, String type) {
        return context.getResources().getIdentifier(name, type, "com.oplus.keyboard");
    }

    private static int resourceAt(Resources resources, int array, int index) {
        if (array == 0 || index < 0) return 0;
        TypedArray values = resources.obtainTypedArray(array);
        try { return index < values.length() ? values.getResourceId(index, 0) : 0; }
        finally { values.recycle(); }
    }

    private static void applyStyle(Context context, int style) {
        if (style != 0) context.getTheme().applyStyle(style, true);
    }
}
