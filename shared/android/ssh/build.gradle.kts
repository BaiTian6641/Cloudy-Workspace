plugins { id("org.jetbrains.kotlin.jvm") }
kotlin {
    jvmToolchain(21)
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
        allWarningsAsErrors.set(true)
    }
}
java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}
dependencies {
    implementation(project(":domain"))
    // Retain the investigated P0 closure. Construction does not grant CAP-SSH adoption.
    implementation(libs.sshj)
    implementation(libs.bcprov)
    implementation(libs.bcpkix)
    implementation(libs.bcutil)
    implementation(libs.slf4j.api)
    implementation(libs.slf4j.nop)
    testImplementation(libs.junit)
}
