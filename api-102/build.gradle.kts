// Compile the unmodified API 102 sources from the requested JingMatrix fork.
plugins { id("com.android.library") }
android {
    namespace = "io.github.libxposed.api"
    compileSdk = 37
    enableKotlin = false
    defaultConfig { minSdk = 26 }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}
dependencies {
    compileOnly("androidx.annotation:annotation:1.10.0") { isTransitive = false }
    compileOnly("org.jetbrains.kotlin:kotlin-stdlib:2.4.10") { isTransitive = false }
    compileOnly(files("libs/annotation-1.0.0.jar"))
}
