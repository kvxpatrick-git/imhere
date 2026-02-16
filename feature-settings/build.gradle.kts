plugins {
    kotlin("jvm") version "1.9.24"
}

kotlin {
    jvmToolchain(17)
}

dependencies {
    implementation(project(":core-model"))
    implementation(project(":core-domain"))
}
