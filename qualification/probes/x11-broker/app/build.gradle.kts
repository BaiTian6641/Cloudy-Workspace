plugins { id("com.android.application") }

val repositoryRoot = projectDir.resolve("../../../..").canonicalFile
android {
    namespace = "dev.cloudy.qualification.x11broker"
    compileSdk { version = release(37) { minorApiLevel = 0 } }
    buildToolsVersion = "36.0.0"
    defaultConfig {
        applicationId = "dev.cloudy.qualification.x11broker"
        minSdk = 35
        targetSdk = 37
        versionCode = 1
        versionName = "0.0.1"
        // Bounded arm64 Googlebook experiment only.
        //noinspection ChromeOsAbiSupport
        ndk { abiFilters += "arm64-v8a" }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    packaging.resources.merges += setOf("META-INF/LICENSE.md", "META-INF/LICENSE.txt")
    lint {
        lintConfig = repositoryRoot.resolve("lint.xml")
        abortOnError = true
        warningsAsErrors = true
        checkReleaseBuilds = true
        checkDependencies = true
    }
}
androidComponents.onVariants { variant ->
    variant.sources.jniLibs?.addStaticSourceDirectory(
        repositoryRoot.resolve(".cache/x11-broker-01/jniLibs").absolutePath,
    )
    variant.sources.assets?.addStaticSourceDirectory(
        repositoryRoot.resolve(".cache/x11-broker-01/assets").absolutePath,
    )
}

dependencies {
    implementation(project(":ssh"))
    implementation(libs.sshj)
    implementation(libs.bcprov)
    implementation(libs.bcpkix)
    implementation(libs.bcutil)
    implementation(libs.slf4j.api)
    implementation(libs.slf4j.nop)
}
