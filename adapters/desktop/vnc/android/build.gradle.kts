plugins { id("com.android.library") }

val repositoryRoot = projectDir.resolve("../../../..").canonicalFile
// Fresh local preparation can coexist with immutable historical receipts. The
// helper still restricts this path and verifies its complete compiled closure.
val nativeRoot = repositoryRoot.resolve(
    providers.gradleProperty("cloudy.vnc.preparedDirectory")
        .orElse(".cache/vnc-product-engine-01/prepared").get(),
).canonicalFile
val verifyNative = tasks.register<Exec>("verifyNative") {
    workingDir(repositoryRoot)
    val receipt = providers.gradleProperty("cloudy.vnc.reuseReceiptSha").orNull
    val declaration = providers.gradleProperty("cloudy.vnc.reuseDeclaration").orNull
    val declarationSha = providers.gradleProperty("cloudy.vnc.reuseDeclarationSha").orNull
    require(listOf(receipt, declaration, declarationSha).all { it == null } ||
        listOf(receipt, declaration, declarationSha).all { it != null }) { "Incomplete explicit native reuse binding" }
    if (receipt == null) {
        commandLine("python3", "tools/prepare_desktop_vnc.py", "--check", "--output", nativeRoot.absolutePath)
    } else {
        commandLine("python3", "tools/prepare_desktop_vnc.py", "--check-native-reuse", "--output", nativeRoot.absolutePath,
            "--receipt-sha256", receipt, "--reuse-declaration", declaration!!, "--reuse-sha256", declarationSha!!)
    }
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
