package dev.coloros.wetype.direct;

import android.view.View;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;
import java.lang.reflect.Method;
import java.util.IdentityHashMap;
import java.util.Map;

/** Observe pressed state only. Drawing is dispatched by the real IME containers. */
public final class DrawGuard implements ViewTreeObserver.OnPreDrawListener, View.OnAttachStateChangeListener {
    private static final String VIEW = "Landroid/view/View;->";
    private static final String DRAW = "Landroid/view/ViewGroup;->drawChild(Landroid/graphics/Canvas;Landroid/view/View;J)Z";
    private static final Map<View, java.lang.ref.WeakReference<DrawGuard>> guards = new java.util.WeakHashMap<>();
    private final java.lang.ref.WeakReference<View> root;
    private final Map<View, Integer> states = new IdentityHashMap<>();
    private ViewTreeObserver observer;
    private boolean failed;
    private DrawGuard(View view) { root = new java.lang.ref.WeakReference<>(view); }
    public static boolean supports(Method method) {
        String key = DirectRuntime.key(method);
        return key.equals(DRAW) || key.equals(VIEW + "setPressed(Z)V")
                || key.equals(VIEW + "setVisibility(I)V")
                || key.equals("Landroid/inputmethodservice/InputMethodService;->setInputView(Landroid/view/View;)V");
    }
    public static void watch(View view) {
        if (view == null) return;
        java.lang.ref.WeakReference<DrawGuard> existing = guards.get(view);
        if (existing != null && existing.get() != null) return;
        DrawGuard guard = new DrawGuard(view);
        guards.put(view, new java.lang.ref.WeakReference<>(guard));
        view.addOnAttachStateChangeListener(guard);
        if (view.isAttachedToWindow()) guard.onViewAttachedToWindow(view);
    }
    public void onViewAttachedToWindow(View view) {
        observer = view.getViewTreeObserver();
        observer.addOnPreDrawListener(this);
    }
    public void onViewDetachedFromWindow(View view) {
        if (observer != null && observer.isAlive()) observer.removeOnPreDrawListener(this);
        observer = null;
        states.clear();
    }
    public boolean onPreDraw() {
        View view = root.get();
        if (view == null || failed) return true;
        IdentityHashMap<View, Boolean> seen = new IdentityHashMap<>();
        try { visit(view, seen); }
        catch (Throwable error) {
            states.clear(); failed = true;
            android.util.Log.e("WeTypeDirectMaterial", "View state observer failed", error);
            return true;
        }
        states.keySet().retainAll(seen.keySet());
        return true;
    }
    private void visit(View view, Map<View, Boolean> seen) throws Throwable {
        seen.put(view, true);
        int state = (view.isPressed() ? 1 : 0) | (view.getVisibility() << 1);
        Integer previous = states.put(view, state);
        if (previous != null && (previous & 1) != (state & 1))
            DirectRuntime.run(VIEW + "setPressed(Z)V", view, new Object[]{view.isPressed()}, () -> null);
        if (!(view instanceof ViewGroup)) return;
        ViewGroup parent = (ViewGroup) view;
        for (int i = 0; i < parent.getChildCount(); i++) {
            View child = parent.getChildAt(i);
            visit(child, seen);
        }
    }
}
