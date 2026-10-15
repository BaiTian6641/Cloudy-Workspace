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
        versionCode = 2
        versionName = "0.2.0"
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
    testImplementation("junit:junit:4.13.2")
}

dependencyLocking {
    lockAllConfigurations()
}
