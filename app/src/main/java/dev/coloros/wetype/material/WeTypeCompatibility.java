package dev.coloros.wetype.material;

/** Exact mappings verified against the supplied 3.5.5 and 4.0.0 APK DEX definitions. */
final class WeTypeCompatibility {
    private static final String PREFIX = "com.tencent.wetype.plugin.hld.";
    final Class<?> keyboard, themeManager;
    final String t9Method, frostedFeature, label;

    WeTypeCompatibility(ClassLoader loader) throws ReflectiveOperationException {
        keyboard = Class.forName(PREFIX + "keyboard.selfdraw.drawmethod.a", false, loader)
                .getDeclaredMethod("c").getReturnType();
        String theme;
        switch (keyboard.getName()) {
            case PREFIX + "keyboard.selfdraw.n":
                theme = "utils.n1"; t9Method = "a2"; frostedFeature = "u2"; label = "3.5.5";
                break;
            case PREFIX + "keyboard.selfdraw.o":
                theme = "utils.m1"; t9Method = "b2"; frostedFeature = "x2"; label = "4.0.0";
                break;
            default: throw new ClassNotFoundException("Unsupported WeType keyboard: " + keyboard.getName());
        }
        themeManager = Class.forName(PREFIX + theme, false, loader);
        themeManager.getDeclaredMethod("E2", boolean.class, String.class);
        keyboard.getDeclaredMethod(t9Method);
    }
}
