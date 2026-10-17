# LSPosed loads this entry by its name in META-INF/xposed/java_init.list.
# Keep the implicit no-arg constructor and framework lifecycle callbacks.
-keep class dev.coloros.wetype.material.WeTypeMaterialModule {
    public <init>();
    public void onPackageReady(io.github.libxposed.api.XposedModuleInterface$PackageReadyParam);
}
