package dev.coloros.wetype.material;

import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Rect;
import android.graphics.RectF;
import android.view.View;
import java.io.InputStream;
import java.lang.reflect.Method;
import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.util.function.BooleanSupplier;
import io.github.libxposed.api.XposedInterface;

/** Replace just the key background during a WeType Canvas draw; preserve its text and input. */
final class ButtonMaterialHooks {
    private static final String PREFIX = "com.tencent.wetype.plugin.hld.keyboard.selfdraw.";
    private final WeTypeMaterialModule module;
    private final BooleanSupplier materialActive;
    private final WeTypeTheme theme;
    private final ThreadLocal<DrawState> drawing = new ThreadLocal<>();
    private final Method keyboard, isT9, rect, visible, bgColor, shadowHeight, status;
    private final Class<?> alphaDrawer;
    private final Field drawerAlpha;
    private final String source;
    private XiaobuKeyShader shader;
    private boolean shaderFailed, logged;

    private static final class DrawState {
        final Object key;
        final boolean accent;
        final float radius;
        DrawState(Object key, boolean accent, float radius) {
            this.key = key; this.accent = accent; this.radius = radius;
        }
    }

    ButtonMaterialHooks(ClassLoader loader, WeTypeMaterialModule module,
                        BooleanSupplier materialActive, WeTypeTheme theme, WeTypeCompatibility compatibility) throws Exception {
        this.module = module; this.materialActive = materialActive; this.theme = theme;
        Class<?> key = Class.forName(PREFIX + "j", false, loader);
        Class<?> base = Class.forName(PREFIX + "drawmethod.a", false, loader);
        keyboard = method(base, "c");
        isT9 = method(compatibility.keyboard, compatibility.t9Method);
        rect = method(key, "t");
        visible = method(key, "q0");
        bgColor = method(key, "i");
        shadowHeight = method(key, "Z");
        status = method(key, "c0");
        alphaDrawer = Class.forName(PREFIX + "drawmethod.h", false, loader);
        Field foundAlpha = null;
        for (Field field : alphaDrawer.getDeclaredFields()) {
            if (field.getType() == int.class) { foundAlpha = field; break; }
        }
        if (foundAlpha == null) throw new NoSuchFieldException("NormalAlphaDrawer.changeAlpha");
        drawerAlpha = foundAlpha;
        drawerAlpha.setAccessible(true);
        try (InputStream input = ButtonMaterialHooks.class.getResourceAsStream("/material/xiaobu_shadowedge.agsl")) {
            if (input == null) throw new IllegalStateException("Missing Xiaobu shader resource");
            source = new String(input.readAllBytes(), StandardCharsets.UTF_8);
        }

        // Validate all getter names before registering any hooks.
        Method[] backgroundGetters = new Method[4];
        String[] names = {"i", "Y", "k", "X"};
        for (int i = 0; i < names.length; i++) backgroundGetters[i] = method(key, names[i]);
        Method corner = method(key, "j");
        Method[] foregroundGetters = new Method[4];
        names = new String[]{"q", "G", "I", "w"};
        for (int i = 0; i < names.length; i++) foregroundGetters[i] = method(key, names[i]);
        Class<?> drawer = Class.forName(PREFIX + "drawmethod.c", false, loader);
        Method draw = method(drawer, "a", Canvas.class, key);

        for (Method getter : backgroundGetters) module.add(getter, chain -> {
            DrawState state = drawing.get();
            return state != null && state.key == chain.getThisObject() ? 0 : chain.proceed();
        });
        module.add(corner, chain -> {
            DrawState state = drawing.get();
            return state != null && state.key == chain.getThisObject() ? Math.round(state.radius) : chain.proceed();
        });
        for (Method getter : foregroundGetters) module.add(getter, chain -> {
            int color = (int) chain.proceed();
            DrawState state = drawing.get();
            if (state == null || state.key != chain.getThisObject() || state.accent || Color.alpha(color) == 0)
                return color;
            // Keep semantic accent colors. Fix neutral labels/icons if an old theme color is cached.
            int max = Math.max(Color.red(color), Math.max(Color.green(color), Color.blue(color)));
            int min = Math.min(Color.red(color), Math.min(Color.green(color), Color.blue(color)));
            if (max - min > 32) return color;
            int rgb = theme.isDark() ? 0x00f2f2f2 : 0x001d1d1f;
            return (color & 0xff000000) | rgb;
        });
        module.add(draw, chain -> {
            Canvas canvas = (Canvas) chain.getArg(0);
            Object button = chain.getArg(1);
            DrawState state = drawBackground(chain.getThisObject(), canvas, button);
            if (state == null) return chain.proceed();
            DrawState previous = drawing.get();
            drawing.set(state);
            try { return chain.proceed(); }
            finally { if (previous == null) drawing.remove(); else drawing.set(previous); }
        });
        // Key getters are routinely inlined into all these drawers, including the alpha bitmap path.
        for (String name : new String[]{"c", "d", "e", "f", "g", "h", "i", "k", "l", "b"}) {
            Class<?> type = Class.forName(PREFIX + "drawmethod." + name, false, loader);
            for (Method method : type.getDeclaredMethods()) module.deoptimize(method);
        }
        module.info("Xiaobu key material hooks installed; NEW_G2 style 2, light/dark/pressed");
    }

    private DrawState drawBackground(Object drawer, Canvas canvas, Object key) {
        if (!materialActive.getAsBoolean() || !canvas.isHardwareAccelerated() || shaderFailed) return null;
        try {
            if (!(boolean) visible.invoke(key)) return null;
            int original = (int) module.getInvoker(bgColor).setType(XposedInterface.Invoker.Type.ORIGIN)
                    .invoke(key);
            if (Color.alpha(original) == 0) return null;
            Rect bounds = (Rect) rect.invoke(key);
            if (bounds.isEmpty()) return null;
            View view = (View) keyboard.invoke(drawer);
            RectF shape = new RectF(bounds);
            if ((boolean) isT9.invoke(view)) shape.bottom -= (float) shadowHeight.invoke(key);
            if (shape.isEmpty()) return null;
            float radius = Math.min(8f * view.getResources().getDisplayMetrics().density,
                    Math.min(shape.width(), shape.height()) / 2f);
            boolean pressed = "press".equals(status.invoke(key));
            int max = Math.max(Color.red(original), Math.max(Color.green(original), Color.blue(original)));
            int min = Math.min(Color.red(original), Math.min(Color.green(original), Color.blue(original)));
            boolean accent = max - min > 32;
            int color = accent ? (theme.accent(original) & 0x00ffffff) | (pressed ? 0xc0000000 : 0x99000000)
                    : theme.isDark() ? (pressed ? 0x3dffffff : 0x26ffffff)
                    : (pressed ? 0x85bbbec2 : 0x8affffff);
            if (shader == null) shader = new XiaobuKeyShader(source);
            int alpha = alphaDrawer.isInstance(drawer) ? drawerAlpha.getInt(drawer) : 255;
            shader.draw(canvas, shape, radius, color, theme.isDark(), Math.max(0, Math.min(255, alpha)));
            if (!logged) { module.info("key material drawn on hardware canvas"); logged = true; }
            return new DrawState(key, accent, radius);
        } catch (Exception | LinkageError error) {
            if (!shaderFailed) module.error("key shader failed; keep WeType key backgrounds", error);
            shaderFailed = true;
            return null;
        }
    }

    private static Method method(Class<?> type, String name, Class<?>... arguments)
            throws NoSuchMethodException {
        Method result = type.getDeclaredMethod(name, arguments);
        result.setAccessible(true);
        return result;
    }
}
