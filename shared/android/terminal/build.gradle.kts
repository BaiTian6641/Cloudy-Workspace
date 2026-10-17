plugins { id("com.android.library") }
android {
    namespace = "io.github.baitian6641.cloudy.terminal"
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
    defaultConfig {
        minSdk = 35
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }
    buildFeatures { buildConfig = false }
}
// AGP supplies Android Kotlin; never also apply kotlin.android.
kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
        allWarningsAsErrors.set(true)
    }
}
dependencies {
    // Exact reviewed renderer feature API; see the bundled terminal/androidx-provenance.json.
    implementation(libs.webkit)
    // Align WebKit's transitive Core floor with the app graph (platform/activity also select 1.18.0).
    implementation(libs.core)
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.test.runner)
    androidTestImplementation(libs.androidx.test.core)
    androidTestImplementation(libs.androidx.test.junit)
    constraints {
        // WebKit/Core resolve through lifecycle 2.6.2 offline; the app graph selects 1.4.0.
        implementation(libs.profileinstaller)
    }
}

val generatedAssets = layout.buildDirectory.dir("generated/terminalAssets")
// Vendor assets are produced from the pinned archives and verified by the preparation script.
// The ordinary build is offline: a missing pinned archive fails instead of fetching at build time.
val prepareTerminalAssets = tasks.register<Exec>("prepareTerminalAssets") {
    val repository = rootProject.layout.projectDirectory
    workingDir(repository)
    commandLine("python3", repository.file("tools/prepare_terminal_assets.py").asFile.absolutePath,
        "--product", "--offline")
    inputs.file(repository.file("tools/prepare_terminal_assets.py"))
    inputs.file(repository.file("qualification/probes/device-baseline/terminal-assets.json"))
    inputs.dir(repository.dir(".cache/terminal-assets"))
    outputs.dir(generatedAssets)
}
androidComponents.onVariants { variant ->
    variant.sources.assets?.addStaticSourceDirectory(generatedAssets.get().asFile.absolutePath)
}
tasks.named("preBuild").configure { dependsOn(prepareTerminalAssets) }
