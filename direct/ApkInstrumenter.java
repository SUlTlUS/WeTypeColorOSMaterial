import java.io.*;
import java.nio.file.*;
import java.util.*;
import org.jf.dexlib2.*;
import org.jf.dexlib2.iface.*;
import org.jf.dexlib2.immutable.*;
import org.jf.smali.*;

/** Replaces selected method bodies with local dispatch wrappers, preserving the original DEX body. */
public final class ApkInstrumenter {
    static final String P = "Lcom/tencent/wetype/plugin/hld/";
    static final String ORIGIN = "$wetype$original$";
    static final Map<String, Set<String>> targets = new LinkedHashMap<>();
    static {
        target("WxHldService", "onCreateInputView onStartInputView onWindowShown onWindowHidden onConfigurationChanged onDestroy");
        target("feature/a", "x2");
        target("frosted/f", "c d n");
        target("frosted/i", "e f");
        target("utils/m1", "E2");
        target("keyboard/selfdraw/j", "i Y k X j q G I w");
        target("keyboard/selfdraw/drawmethod/c", "a");
        target("keyboard/u", "b");
        for (String panel : new String[]{"keyboard/S15CustomPhraseAndClipboardKeyboard", "keyboard/S29ClipboardCleanRecordKeyboard",
                "keyboard/S30KeyboardChooseKeyboard", "keyboard/selfdraw/S11EmojiKeyboard"}) target(panel, "f0 g0");
        target("view/settingkeyboard/S10SettingCustomToolbarView", "setVisibility");
        target("view/settingkeyboard/S10SettingKeyboardTypeView", "");
        target("candidate/TabContainerView", "onDraw");
        target("float/e", "onAttachedToWindow onDetachedFromWindow");
        target("float/f$g", "g");
    }
    static void target(String cls, String names) { targets.put(P + cls + ";", names.isEmpty() ? Set.of() : new HashSet<>(Arrays.asList(names.split(" ")))); }
    static String proto(Method method) {
        return "(" + String.join("", method.getParameterTypes()) + ")" + method.getReturnType();
    }
    static int width(CharSequence type) { return type.toString().equals("J") || type.toString().equals("D") ? 2 : 1; }
    static final Map<String, String> BOX = Map.of("Z", "Boolean", "B", "Byte", "C", "Character", "S", "Short", "I", "Integer", "J", "Long", "F", "Float", "D", "Double");
    static String wrapper(Method method, String key) {
        boolean isStatic = (method.getAccessFlags() & 8) != 0;
        StringBuilder out = new StringBuilder(".method ").append(isStatic ? "public static " : "public ")
                .append(method.getName()).append(proto(method)).append("\n.locals 8\n");
        out.append("const-string v0, \"").append(key).append("\"\nconst-class v1, ").append(method.getDefiningClass()).append('\n');
        out.append(isStatic ? "const/4 v2, 0\n" : "move-object/from16 v2, p0\n");
        out.append("const/16 v4, ").append(method.getParameterTypes().size()).append("\nnew-array v3, v4, [Ljava/lang/Object;\n");
        int register = isStatic ? 0 : 1, index = 0;
        for (CharSequence parameter : method.getParameterTypes()) {
            String type = parameter.toString();
            if (BOX.containsKey(type)) {
                out.append(width(type) == 2 ? "move-wide/from16 v5, p" : "move/from16 v5, p").append(register).append('\n');
                out.append("invoke-static/range {v5 .. v").append(width(type) == 2 ? 6 : 5).append("}, Ljava/lang/").append(BOX.get(type))
                        .append(";->valueOf(").append(type).append(")Ljava/lang/").append(BOX.get(type)).append(";\nmove-result-object v5\n");
            } else out.append("move-object/from16 v5, p").append(register).append('\n');
            out.append("const/16 v4, ").append(index++).append("\naput-object v5, v3, v4\n");
            register += width(type);
        }
        out.append("invoke-static/range {v0 .. v3}, Ldev/coloros/wetype/direct/DirectRuntime;->dispatch(Ljava/lang/String;Ljava/lang/Class;Ljava/lang/Object;[Ljava/lang/Object;)Ljava/lang/Object;\n");
        String ret = method.getReturnType();
        if (ret.equals("V")) out.append("return-void\n");
        else {
            out.append("move-result-object v0\n");
            if (BOX.containsKey(ret)) {
                out.append("check-cast v0, Ljava/lang/").append(BOX.get(ret)).append(";\ninvoke-virtual {v0}, Ljava/lang/").append(BOX.get(ret))
                        .append(";->").append(Map.of("Z", "boolean", "B", "byte", "C", "char", "S", "short", "I", "int", "J", "long", "F", "float", "D", "double").get(ret))
                        .append("Value()").append(ret).append('\n').append(width(ret) == 2 ? "move-result-wide v0\nreturn-wide v0\n" : "move-result v0\nreturn v0\n");
            } else out.append("check-cast v0, ").append(ret).append("\nreturn-object v0\n");
        }
        return out.append(".end method\n").toString();
    }
    static Method renamed(Method original) {
        // Keep the original visibility/static flag so DEX direct/virtual method categorization stays valid.
        return new ImmutableMethod(original.getDefiningClass(), ORIGIN + original.getName(), original.getParameters(),
                original.getReturnType(), original.getAccessFlags() & ~0x20, original.getAnnotations(),
                original.getHiddenApiRestrictions(), original.getImplementation());
    }
    static String normalized(Object value) {
        if (value instanceof org.jf.dexlib2.iface.instruction.SwitchElement) {
            var element = (org.jf.dexlib2.iface.instruction.SwitchElement) value;
            return element.getKey() + ":" + element.getOffset();
        }
        if (value instanceof Iterable<?>) {
            StringBuilder out = new StringBuilder("[");
            for (Object item : (Iterable<?>) value) out.append(normalized(item)).append(',');
            return out.append(']').toString();
        }
        return String.valueOf(value);
    }
    static List<String> instructions(Method method) throws Exception {
        List<String> out = new ArrayList<>();
        for (var instruction : method.getImplementation().getInstructions()) {
            StringBuilder item = new StringBuilder(instruction.getOpcode().name());
            var getters = new ArrayList<java.lang.reflect.Method>();
            for (var getter : instruction.getClass().getMethods()) {
                if (getter.getName().startsWith("get") && getter.getParameterCount() == 0
                        && !getter.getName().equals("getClass")) getters.add(getter);
            }
            getters.sort(Comparator.comparing(java.lang.reflect.Method::getName));
            for (var getter : getters) item.append('|').append(getter.getName()).append('=').append(normalized(getter.invoke(instruction)));
            out.add(item.toString());
        }
        return out;
    }
    static void verify(DexFile original, Path output, Set<String> changedTypes) throws Exception {
        Map<String, ClassDef> serialized = new HashMap<>();
        for (ClassDef cls : DexFileFactory.loadDexFile(output.toFile(), original.getOpcodes()).getClasses()) serialized.put(cls.getType(), cls);
        for (ClassDef cls : original.getClasses()) {
            if (!changedTypes.contains(cls.getType())) continue;
            Map<String, Method> methods = new HashMap<>();
            for (Method method : serialized.get(cls.getType()).getMethods()) methods.put(method.getName() + proto(method), method);
            for (Method method : cls.getMethods()) {
                if (!targets.get(cls.getType()).contains(method.getName()) || method.getImplementation() == null) continue;
                Method saved = methods.get(ORIGIN + method.getName() + proto(method));
                if (saved == null || !instructions(method).equals(instructions(saved)))
                    throw new IllegalStateException("Original bytecode changed: " + cls.getType() + "->" + method.getName());
                var wrapper = methods.get(method.getName() + proto(method));
                boolean routed = false;
                for (var instruction : wrapper.getImplementation().getInstructions()) {
                    if (instruction instanceof org.jf.dexlib2.iface.instruction.ReferenceInstruction) {
                        String reference = ((org.jf.dexlib2.iface.instruction.ReferenceInstruction) instruction).getReference().toString();
                        if (reference.startsWith("Ldev/coloros/wetype/direct/DirectRuntime;->dispatch(")) routed = true;
                    }
                }
                if (!routed) throw new IllegalStateException("Missing dispatch route: " + wrapper);
            }
        }
    }
    public static void main(String[] args) throws Exception {
        Path input = Path.of(args[0]), output = Path.of(args[1]);
        Files.createDirectories(output);
        var container = DexFileFactory.loadDexContainer(input.toFile(), Opcodes.forApi(35));
        Map<String, StringBuilder> generated = new LinkedHashMap<>();
        List<String> patched = new ArrayList<>();
        Map<String, Set<String>> found = new HashMap<>();
        for (String entry : container.getDexEntryNames()) {
            for (ClassDef cls : container.getEntry(entry).getDexFile().getClasses()) {
                if (!targets.containsKey(cls.getType())) continue;
                StringBuilder source = new StringBuilder(".class public ").append(cls.getType()).append("\n.super ").append(cls.getSuperclass()).append('\n');
                for (Method method : cls.getMethods()) {
                    if (!targets.get(cls.getType()).contains(method.getName()) || method.getImplementation() == null) continue;
                    String key = cls.getType() + "->" + method.getName() + proto(method);
                    source.append(wrapper(method, key)); patched.add(key);
                    found.computeIfAbsent(cls.getType(), ignored -> new HashSet<>()).add(method.getName());
                }
                // InputMethodService's framework implementation is reached through a local override.
                if (cls.getType().equals(P + "WxHldService;")) {
                    Method supplied = new ImmutableMethod(cls.getType(), "setInputView", List.of(new ImmutableMethodParameter("Landroid/view/View;", Set.of(), null)),
                            "V", 1, Set.of(), Set.of(), null);
                    for (Method method : cls.getMethods()) if (method.getName().equals("setInputView"))
                        throw new IllegalStateException("Unexpected existing setInputView override");
                    source.append(wrapper(supplied, "Landroid/inputmethodservice/InputMethodService;->setInputView(Landroid/view/View;)V"));
                    source.append(".method public ").append(ORIGIN).append("setInputView(Landroid/view/View;)V\n.locals 0\ninvoke-super/range {p0 .. p1}, ")
                            .append(cls.getSuperclass()).append("->setInputView(Landroid/view/View;)V\nreturn-void\n.end method\n");
                    patched.add("framework:setInputView");
                }
                if (cls.getType().equals(P + "view/settingkeyboard/S10SettingKeyboardTypeView;")) {
                    for (Method method : cls.getMethods()) if (method.getName().equals("setVisibility"))
                        throw new IllegalStateException("Unexpected existing keyboard type visibility override");
                    Method visibility = new ImmutableMethod(cls.getType(), "setVisibility", List.of(new ImmutableMethodParameter("I", Set.of(), null)),
                            "V", 1, Set.of(), Set.of(), null);
                    source.append(wrapper(visibility, "Landroid/view/View;->setVisibility(I)V"));
                    source.append(".method public ").append(ORIGIN).append("setVisibility(I)V\n.locals 0\ninvoke-super/range {p0 .. p1}, ")
                            .append(cls.getSuperclass()).append("->setVisibility(I)V\nreturn-void\n.end method\n");
                    patched.add("framework:keyboardType.setVisibility");
                    found.putIfAbsent(cls.getType(), Set.of());
                }
                generated.put(cls.getType(), source);
            }
        }
        for (var target : targets.entrySet()) {
            if (!found.getOrDefault(target.getKey(), Set.of()).containsAll(target.getValue()))
                throw new IllegalStateException("Missing target methods: " + target);
        }
        Path smali = output.resolve("wrappers"); Files.createDirectories(smali);
        int index = 0;
        for (StringBuilder source : generated.values()) Files.writeString(smali.resolve("Wrapper" + index++ + ".smali"), source);
        SmaliOptions options = new SmaliOptions(); options.apiLevel = 35; options.outputDexFile = output.resolve("wrappers.dex").toString();
        if (!Smali.assemble(options, List.of(smali.toString()))) throw new IllegalStateException("Wrapper assembly failed");
        Map<String, ClassDef> wrappers = new HashMap<>();
        for (ClassDef cls : DexFileFactory.loadDexFile(options.outputDexFile, Opcodes.forApi(35)).getClasses()) wrappers.put(cls.getType(), cls);
        for (String entry : container.getDexEntryNames()) {
            DexFile original = container.getEntry(entry).getDexFile();
            List<ClassDef> classes = new ArrayList<>(); boolean changed = false;
            for (ClassDef cls : original.getClasses()) {
                ClassDef generatedClass = wrappers.get(cls.getType());
                if (generatedClass == null) { classes.add(cls); continue; }
                changed = true;
                Map<String, Method> additions = new LinkedHashMap<>();
                for (Method method : generatedClass.getMethods()) additions.put(method.getName() + proto(method), method);
                List<Method> methods = new ArrayList<>();
                for (Method method : cls.getMethods()) {
                    Method replacement = additions.remove(method.getName() + proto(method));
                    if (replacement == null) methods.add(method);
                    else {
                        methods.add(renamed(method));
                        methods.add(new ImmutableMethod(method.getDefiningClass(), method.getName(), method.getParameters(), method.getReturnType(),
                                method.getAccessFlags(), method.getAnnotations(), method.getHiddenApiRestrictions(), replacement.getImplementation()));
                    }
                }
                methods.addAll(additions.values());
                classes.add(new ImmutableClassDef(cls.getType(), cls.getAccessFlags(), cls.getSuperclass(), cls.getInterfaces(), cls.getSourceFile(), cls.getAnnotations(), cls.getFields(), methods));
            }
            if (changed) {
                String name = Path.of(entry).getFileName().toString();
                DexFileFactory.writeDexFile(output.resolve(name).toString(), new ImmutableDexFile(original.getOpcodes(), classes));
                verify(original, output.resolve(name), wrappers.keySet());
                System.out.println("Rewrote " + name);
            }
        }
        Files.write(output.resolve("patched-methods.txt"), patched);
        System.out.println("Patched " + patched.size() + " entry points");
    }
}
