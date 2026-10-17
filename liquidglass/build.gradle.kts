import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    id("com.android.library")
    id("org.jetbrains.kotlin.plugin.compose")
}
android {
    namespace = "dev.liquidglass.ios27"
    compileSdk = 37
    defaultConfig { minSdk = 24 }
    buildFeatures { compose = true }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }
}
tasks.withType<KotlinCompile>().configureEach {
    // Host-only preferences depend on Tianyin's Application; keep its source intact.
    exclude("**/utils/LiquidGlassPrefs.kt")
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_21)
        freeCompilerArgs.add("-Xcontext-parameters")
    }
}
dependencies {
    api("androidx.compose.foundation:foundation:1.11.2")
    api("androidx.compose.material:material:1.11.2")
    api("io.github.kyant0:backdrop:2.0.1")
    api("io.github.kyant0:shapes:1.2.1")
    implementation("androidx.core:core-ktx:1.17.0")
    testImplementation("junit:junit:4.13.2")
}
