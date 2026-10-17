package dev.coloros.wetype.direct;

import android.util.Log;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/** Every patched method enters here; the renamed bytecode remains the original implementation. */
public final class DirectRuntime {
    public static final String ORIGINAL = "$wetype$original$";
    private static final ConcurrentHashMap<String, CopyOnWriteArrayList<DirectModule.Hooker>> hooks = new ConcurrentHashMap<>();
    private static final ConcurrentHashMap<String, Method> originals = new ConcurrentHashMap<>();
    private static volatile boolean initialized;
    private static boolean initializing;

    private static synchronized void initialize() {
        if (initialized || initializing) return;
        initializing = true;
        try {
            new dev.coloros.wetype.material.WeTypeMaterialModule().onPackageReady(new DirectModule.PackageReadyParam() {
                public String getPackageName() { return "com.tencent.wetype"; }
                public ClassLoader getClassLoader() { return DirectRuntime.class.getClassLoader(); }
            });
        } catch (Throwable error) {
            hooks.clear();
            Log.e("WeTypeDirectMaterial", "Initialization failed; keep original keyboard", error);
        } finally {
            initializing = false;
            initialized = true;
        }
    }

    public static String descriptor(Class<?> type) {
        if (type.isArray()) return type.getName().replace('.', '/');
        if (!type.isPrimitive()) return "L" + type.getName().replace('.', '/') + ";";
        if (type == void.class) return "V";
        if (type == boolean.class) return "Z";
        if (type == byte.class) return "B";
        if (type == char.class) return "C";
        if (type == short.class) return "S";
        if (type == int.class) return "I";
        if (type == long.class) return "J";
        if (type == float.class) return "F";
        return "D";
    }
    public static String key(Method method) {
        StringBuilder key = new StringBuilder(descriptor(method.getDeclaringClass())).append("->")
                .append(method.getName()).append('(');
        for (Class<?> parameter : method.getParameterTypes()) key.append(descriptor(parameter));
        return key.append(')').append(descriptor(method.getReturnType())).toString();
    }
    static DirectModule.HookHandle add(Method method, DirectModule.Hooker callback) {
        String key = key(method);
        if (!method.getDeclaringClass().getName().startsWith("android.")) {
            try { origin(key, method.getDeclaringClass()); }
            catch (ReflectiveOperationException error) { throw new DirectModule.FrameworkError("Unpatched method: " + key); }
        } else if (!DrawGuard.supports(method)) {
            throw new DirectModule.FrameworkError("Unsupported framework callback: " + key);
        }
        CopyOnWriteArrayList<DirectModule.Hooker> callbacks = hooks.computeIfAbsent(key, ignored -> new CopyOnWriteArrayList<>());
        callbacks.add(callback);
        return () -> callbacks.remove(callback);
    }

    private static Class<?> type(String descriptor) throws ClassNotFoundException {
        switch (descriptor) {
            case "Z": return boolean.class;
            case "B": return byte.class;
            case "C": return char.class;
            case "S": return short.class;
            case "I": return int.class;
            case "J": return long.class;
            case "F": return float.class;
            case "D": return double.class;
            default: return Class.forName(descriptor.startsWith("L")
                    ? descriptor.substring(1, descriptor.length() - 1).replace('/', '.')
                    : descriptor.replace('/', '.'), false, DirectRuntime.class.getClassLoader());
        }
    }
    private static Method origin(String key, Class<?> holder) throws ReflectiveOperationException {
        String cacheKey = holder.getName() + ':' + key;
        Method cached = originals.get(cacheKey);
        if (cached != null) return cached;
        int start = key.indexOf('('), end = key.indexOf(')', start);
        String name = key.substring(key.indexOf("->") + 2, start);
        List<Class<?>> parameters = new ArrayList<>();
        for (int i = start + 1; i < end;) {
            int from = i;
            while (key.charAt(i) == '[') i++;
            i = key.charAt(i) == 'L' ? key.indexOf(';', i) + 1 : i + 1;
            parameters.add(type(key.substring(from, i)));
        }
        Method result = holder.getDeclaredMethod(ORIGINAL + name, parameters.toArray(new Class<?>[0]));
        result.setAccessible(true);
        originals.put(cacheKey, result);
        return result;
    }
    static Object invokeOrigin(Method method, Object receiver, Object[] args) throws ReflectiveOperationException {
        return origin(key(method), method.getDeclaringClass()).invoke(receiver, args);
    }
    public static Object dispatch(String key, Class<?> holder, Object receiver, Object[] args) throws Throwable {
        if (!initialized) initialize();
        Method original = origin(key, holder);
        if (!initialized) {
            try { return original.invoke(receiver, args); }
            catch (InvocationTargetException error) { throw error.getCause(); }
        }
        return run(key, receiver, args, () -> {
            try { return original.invoke(receiver, args); }
            catch (InvocationTargetException error) { throw error.getCause(); }
        });
    }
    interface Original { Object call() throws Throwable; }
    static Object run(String key, Object receiver, Object[] args, Original original) throws Throwable {
        List<DirectModule.Hooker> callbacks = hooks.get(key);
        if (callbacks == null || callbacks.isEmpty()) return original.call();
        return new LocalChain(callbacks, 0, receiver, args, original).proceed();
    }
    private static final class LocalChain implements DirectModule.Chain {
        final List<DirectModule.Hooker> callbacks;
        final int index;
        final Object receiver;
        final Object[] args;
        final Original original;
        LocalChain(List<DirectModule.Hooker> callbacks, int index, Object receiver, Object[] args, Original original) {
            this.callbacks = callbacks; this.index = index; this.receiver = receiver; this.args = args; this.original = original;
        }
        public Object proceed() throws Throwable {
            return index == callbacks.size() ? original.call() : callbacks.get(index).intercept(
                    new LocalChain(callbacks, index + 1, receiver, args, original));
        }
        public Object getThisObject() { return receiver; }
        public Object getArg(int index) { return args[index]; }
        public List<Object> getArgs() { return Arrays.asList(args); }
    }
}
