plugins {
    id("com.android.application")
}

android {
    namespace = "dev.cloudy.qualification.baseline"
    compileSdk {
        version = release(37) {
            minorApiLevel = 0
        }
    }
    buildToolsVersion = "36.0.0"

    defaultConfig {
        applicationId = "dev.cloudy.qualification.baseline"
        minSdk = 35
        targetSdk = 37
        versionCode = 10
        versionName = "0.7.1"
        // Stable source build reference; the external run manifest binds its commit and exact APK.
        buildConfigField("String", "QUALIFICATION_BUILD_REF", "\"60908d62-c573-4c4b-8b0e-910e5863197e\"")
    }

    buildFeatures { buildConfig = true }

    packaging {
        resources {
            // Preserve all dependency license resources; exact upstream copies are also in assets.
            merges += setOf("META-INF/LICENSE.md", "META-INF/LICENSE.txt")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    lint {
        lintConfig = file("../lint.xml")
        abortOnError = true
        warningsAsErrors = true
        checkReleaseBuilds = true
    }
}

// AGP 9 supplies Kotlin; adding org.jetbrains.kotlin.android would duplicate it.
kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
        allWarningsAsErrors.set(true)
    }
}

dependencies {
    // Isolated qualification candidate; the product stack is not selected here.
    implementation("androidx.window:window:1.6.0-alpha05")
    // Window's public callback uses Core Consumer but publishes Core at runtime scope.
    // Preserve the alpha's audited Core runtime; review upgrades as a separate increment.
    //noinspection GradleDependency
    implementation("androidx.core:core:1.8.0")
    implementation("androidx.webkit:webkit:1.17.1")
    // P0-S3 diagnostic adoption only; all three BC modules are deliberately aligned.
    implementation("com.hierynomus:sshj:0.41.2")
    implementation("org.bouncycastle:bcprov-jdk18on:1.86")
    implementation("org.bouncycastle:bcpkix-jdk18on:1.86")
    implementation("org.bouncycastle:bcutil-jdk18on:1.86")
    // Library transcripts must not enter Android logs; the probe records bounded observations.
    implementation("org.slf4j:slf4j-api:2.0.20")
    implementation("org.slf4j:slf4j-nop:2.0.20")
    testImplementation("junit:junit:4.13.2")
}

dependencyLocking {
    lockAllConfigurations()
}

// These files are prepared outside Gradle, then verified without mutation below.
androidComponents.onVariants { variant ->
    variant.sources.assets?.addStaticSourceDirectory(layout.buildDirectory.dir("generated/terminalAssets").get().asFile.absolutePath)
}

val verifyTerminalAssets = tasks.register<Exec>("verifyTerminalAssets") {
    val repository = rootProject.layout.projectDirectory.dir("../../..")
    workingDir(repository)
    commandLine("python3", repository.file("tools/prepare_terminal_assets.py").asFile, "--check")
    // This gate is deliberately read-only/offline and always runs, including incremental builds.
}
tasks.named("preBuild").configure { dependsOn(verifyTerminalAssets) }
