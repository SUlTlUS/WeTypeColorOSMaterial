import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.plugin.compose")
}
android {
    namespace = "dev.coloros.wetype.material"
    compileSdk = 37
    defaultConfig {
        applicationId = "dev.coloros.wetype.material"
        minSdk = 33
        targetSdk = 35
        versionCode = 29
        versionName = "1.0.0"
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }
    buildFeatures { compose = true; buildConfig = true }
    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
            signingConfig = signingConfigs.getByName("debug")
        }
    }
}
tasks.withType<KotlinCompile>().configureEach {
    compilerOptions { jvmTarget.set(JvmTarget.JVM_21) }
}
dependencies {
    compileOnly(project(":api-102"))
    implementation(project(":liquidglass"))
    implementation("androidx.activity:activity-compose:1.13.0")
    implementation("io.github.libxposed:service:102.0.0")
}

// Stage a versioned signed debug APK without overwriting earlier tested builds.
tasks.register<Copy>("stageDebugApk") {
    dependsOn("assembleDebug")
    from(layout.buildDirectory.file("outputs/apk/debug/app-debug.apk"))
    into(rootProject.layout.projectDirectory.dir("dist"))
    rename("app-debug.apk", "WeTypeColorOSMaterial-${android.defaultConfig.versionName}-debug.apk")
}

tasks.register<Copy>("stageReleaseApk") {
    dependsOn("assembleRelease")
    from(layout.buildDirectory.file("outputs/apk/release/app-release.apk"))
    into(rootProject.layout.projectDirectory.dir("dist"))
    rename("app-release.apk", "WeTypeColorOSMaterial-${android.defaultConfig.versionName}-release.apk")
}
