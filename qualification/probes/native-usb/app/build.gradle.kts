plugins { id("com.android.application") }
android {
    namespace = "dev.cloudy.qualification.usb"
    compileSdk { version = release(37) { minorApiLevel = 0 } }
    ndkVersion = "29.0.14206865"
    buildToolsVersion = "36.0.0"
    defaultConfig {
        applicationId = "dev.cloudy.qualification.usb"
        minSdk = 35
        targetSdk = 37
        versionCode = 1
        versionName = "0.1.0"
        // Finite arm64 Googlebook diagnostic; other ABIs remain unqualified.
        //noinspection ChromeOsAbiSupport
        ndk { abiFilters += "arm64-v8a" }
        buildConfigField("String", "QUALIFICATION_BUILD_REF", "\"d207f066-517d-4a2f-8897-f347998696da\"")
    }
    buildFeatures { buildConfig = true }
    compileOptions { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 }
    buildTypes { getByName("release") { isMinifyEnabled = false } }
    lint { abortOnError = true; warningsAsErrors = true; checkReleaseBuilds = true }
}
kotlin { compilerOptions { jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17); allWarningsAsErrors.set(true) } }
dependencies { testImplementation("junit:junit:4.13.2") }
dependencyLocking { lockAllConfigurations() }
androidComponents.onVariants { variant ->
    variant.sources.jniLibs?.addStaticSourceDirectory(layout.buildDirectory.dir("generated/nativeLibraries").get().asFile.absolutePath)
    variant.sources.assets?.addStaticSourceDirectory(layout.buildDirectory.dir("generated/nativeNotices").get().asFile.absolutePath)
}
val verifyNativeInputs = tasks.register<Exec>("verifyNativeInputs") {
    val repository = rootProject.layout.projectDirectory.dir("../../..")
    workingDir(repository)
    commandLine("python3", repository.file("tools/prepare_usb_native.py").asFile, "--check")
}
tasks.named("preBuild").configure { dependsOn(verifyNativeInputs) }
