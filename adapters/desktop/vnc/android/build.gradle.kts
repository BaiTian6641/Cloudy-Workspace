plugins { id("com.android.library") }

val repositoryRoot = projectDir.resolve("../../../..").canonicalFile
val nativeRoot = repositoryRoot.resolve(".cache/vnc-product-engine-01/prepared")
val verifyNative = tasks.register<Exec>("verifyNative") {
    workingDir(repositoryRoot)
    commandLine("python3", "tools/prepare_desktop_vnc.py", "--check", "--output", nativeRoot.absolutePath)
}
android {
    namespace = "io.github.baitian6641.cloudy.desktop.vnc"
    compileSdk { version = release(37) { minorApiLevel = 0 } }
    buildToolsVersion = "36.0.0"
    defaultConfig {
        minSdk = 35
        consumerProguardFiles("consumer-rules.pro")
        // Bounded sampled arm64 Googlebook construction; no ChromeOS or x86 qualification claim.
        //noinspection ChromeOsAbiSupport
        ndk { abiFilters += "arm64-v8a" }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    lint {
        lintConfig = repositoryRoot.resolve("lint.xml")
        abortOnError = true
        warningsAsErrors = true
        checkReleaseBuilds = true
        checkDependencies = true
    }
}
androidComponents.onVariants { variant ->
    variant.sources.jniLibs?.addStaticSourceDirectory(nativeRoot.resolve("jniLibs").absolutePath)
    variant.sources.assets?.addStaticSourceDirectory(nativeRoot.resolve("assets").absolutePath)
}
tasks.named("preBuild").configure { dependsOn(verifyNative) }
dependencies { testImplementation(libs.junit) }
