package dev.coloros.wetype.direct;

import android.util.Log;
import java.lang.reflect.Method;
import java.util.List;

/** Local dispatch contracts used by the instrumented APK. No framework or native hooks. */
public class DirectModule {
    public interface PackageReadyParam {
        String getPackageName();
        ClassLoader getClassLoader();
    }
    public interface Hooker { Object intercept(Chain chain) throws Throwable; }
    public interface HookHandle { void unhook(); }
    public interface Chain {
        Object proceed() throws Throwable;
        Object getThisObject();
        Object getArg(int index);
        List<Object> getArgs();
    }
    public static class FrameworkError extends Error {
        public FrameworkError(String message) { super(message); }
    }
    public static final class Invoker {
        public enum Type { ORIGIN }
        private final Method method;
        Invoker(Method method) { this.method = method; }
        public Invoker setType(Type ignored) { return this; }
        public Object invoke(Object receiver, Object... args) throws ReflectiveOperationException {
            return DirectRuntime.invokeOrigin(method, receiver, args);
        }
    }
    public final class HookBuilder {
        private final Method method;
        HookBuilder(Method method) { this.method = method; }
        public HookHandle intercept(Hooker callback) { return DirectRuntime.add(method, callback); }
    }
    public void onPackageReady(PackageReadyParam param) {}
    public HookBuilder hook(Method method) { return new HookBuilder(method); }
    public Invoker getInvoker(Method method) { return new Invoker(method); }
    // The dispatch is part of the method body; compiler inlining retains it.
    public void deoptimize(Method ignored) {}
    public void log(int level, String tag, String message) { Log.println(level, tag, message); }
    public void log(int level, String tag, String message, Throwable error) {
        Log.println(level, tag, message + "\n" + Log.getStackTraceString(error));
    }
}
