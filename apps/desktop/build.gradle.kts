plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.plugin.compose")
}
android {
    namespace = "io.github.baitian6641.cloudy.desktop"
    compileSdk { version = release(37) { minorApiLevel = 0 } }
    buildToolsVersion = "36.0.0"
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    lint {
        lintConfig = rootProject.file("lint.xml")
        abortOnError = true
        warningsAsErrors = true
        checkReleaseBuilds = true
        checkDependencies = true
    }
    sourceSets.getByName("androidTest").kotlin.directories.add(
        rootProject.file("testing/foundation/src/androidTest/kotlin").absolutePath,
    )
    sourceSets.getByName("debug").apply {
        kotlin.directories.add(rootProject.file("testing/foundation/src/debug/kotlin").absolutePath)
        manifest.srcFile(rootProject.file("testing/foundation/src/debug/AndroidManifest.xml"))
    }
    buildFeatures { compose = true }
    packaging.resources.merges += setOf("META-INF/LICENSE.md", "META-INF/LICENSE.txt")
    defaultConfig {
        applicationId = "io.github.baitian6641.cloudy.desktop"
        minSdk = 35
        targetSdk = 37
        versionCode = 20
        versionName = "0.1.19"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }
}
// Requires parent-owned :vnc root registration and reviewed clean-host native preparation.
// Local retained-native properties are explicit invocation inputs, never workstation pins here.
// AGP supplies Android Kotlin; never also apply kotlin.android.
kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
        allWarningsAsErrors.set(true)
    }
}
dependencies {
    implementation(project(":domain"))
    implementation(project(":platform"))
    implementation(project(":vnc"))
    implementation(project(":design-system"))
    implementation(platform(libs.compose.bom))
    implementation(libs.activity.compose)
    implementation(libs.compose.runtime)
    implementation(libs.compose.ui)
    implementation(libs.compose.foundation)
    implementation(libs.compose.material3)
    debugImplementation(project(":identity"))
    testImplementation(libs.junit)
}

// Tests exercise real Android boundaries; these artifacts are not app runtime dependencies.
dependencies {
    androidTestImplementation(libs.androidx.test.runner)
    androidTestImplementation(libs.androidx.test.core)
    androidTestImplementation(libs.androidx.test.junit)
    androidTestImplementation(platform(libs.compose.bom))
    androidTestImplementation(libs.compose.ui.test)
    // Compose's older floor selects Espresso 3.5, whose hidden input lookup fails
    // on Android 17. Stable 3.7 uses the public system service on our SDK range.
    androidTestImplementation(libs.espresso.core)
    androidTestImplementation(libs.espresso.idling)
    androidTestImplementation(project(":design-system"))
}
