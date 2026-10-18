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
    androidTestImplementation(project(":qualification-ime"))
    constraints {
        // WebKit/Core resolve through lifecycle 2.6.2 offline; the app graph selects 1.4.0.
        implementation(libs.profileinstaller)
    }
}

val generatedAssets = layout.buildDirectory.dir("generated/terminalAssets")
// The product core is rebuilt from pinned source plus the reviewed IME repair.
// The build verifies its exact output pin and never fetches archives or executes package scripts.
val prepareTerminalAssets = tasks.register<Exec>("prepareTerminalAssets") {
    val repository = rootProject.layout.projectDirectory
    workingDir(repository)
    commandLine("python3", repository.file("tools/prepare_terminal_assets.py").asFile.absolutePath,
        "--product", "--offline")
    inputs.files(repository.file("tools/prepare_terminal_assets.py"),
        repository.file("tools/build_terminal_renderer.py"), repository.file("tools/prepare_rdp_native.py"),
        repository.file("qualification/probes/device-baseline/terminal-assets.json"),
        repository.file("shared/android/terminal/renderer-build.json"),
        repository.file("shared/android/terminal/interaction-assets.json"),
        repository.file("tools/patches/xterm-6.1.0-beta.315-flush-committed-input.patch"),
        repository.file(".cache/terminal-assets/xterm-6.1.0-beta.315.tgz"),
        repository.file(".cache/terminal-assets/addon-fit-0.12.0-beta.311.tgz"),
        repository.file(".cache/terminal-assets/addon-search-0.17.0-beta.310.tgz"),
        repository.file(".cache/terminal-assets/source-build/linux-x64-0.28.0.tgz"))
    outputs.dir(generatedAssets)
}
androidComponents.onVariants { variant ->
    variant.sources.assets?.addStaticSourceDirectory(generatedAssets.get().asFile.absolutePath)
}
tasks.named("preBuild").configure { dependsOn(prepareTerminalAssets) }
