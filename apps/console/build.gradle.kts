plugins { id("com.android.application") }
android {
    namespace = "io.github.baitian6641.cloudy.console"
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
    defaultConfig {
        applicationId = "io.github.baitian6641.cloudy.console"
        minSdk = 35
        targetSdk = 37
        versionCode = 2
        versionName = "0.1.1"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }
}
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
