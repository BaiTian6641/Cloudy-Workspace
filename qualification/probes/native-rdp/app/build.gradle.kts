plugins { id("com.android.application") }
android {
    namespace = "dev.cloudy.qualification.rdp"
    compileSdk { version = release(37) { minorApiLevel = 0 } }
    ndkVersion = "29.0.14206865"
    buildToolsVersion = "36.0.0"
    defaultConfig {
        applicationId = "dev.cloudy.qualification.rdp"
        minSdk = 35
        targetSdk = 37
        versionCode = 7
        versionName = "0.1.6"
        // This finite diagnostic targets the connected arm64 Googlebook only.
        //noinspection ChromeOsAbiSupport
        ndk { abiFilters += "arm64-v8a" }
        buildConfigField("String", "QUALIFICATION_BUILD_REF", "\"27cb59d8-a829-4424-a888-db41259a006c\"")
    }
    buildFeatures { buildConfig = true }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    // This ABI bridge is fixed to symbol-name JNI; never rename it or callbacks.
    buildTypes { getByName("release") { isMinifyEnabled = false } }
    lint { abortOnError = true; warningsAsErrors = true; checkReleaseBuilds = true }
}
kotlin { compilerOptions { jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17); allWarningsAsErrors.set(true) } }
dependencies { testImplementation("junit:junit:4.13.2") }
dependencyLocking { lockAllConfigurations() }
// Native files are prepared outside Gradle. The offline preBuild gate verifies them.
androidComponents.onVariants { variant ->
    variant.sources.jniLibs?.addStaticSourceDirectory(layout.buildDirectory.dir("generated/nativeLibraries").get().asFile.absolutePath)
    variant.sources.assets?.addStaticSourceDirectory(layout.buildDirectory.dir("generated/nativeNotices").get().asFile.absolutePath)
}
val verifyNativeInputs = tasks.register<Exec>("verifyNativeInputs") {
    val repository = rootProject.layout.projectDirectory.dir("../../..")
    workingDir(repository)
    commandLine("python3", repository.file("tools/prepare_rdp_native.py").asFile, "--check")
}
tasks.named("preBuild").configure { dependsOn(verifyNativeInputs) }
