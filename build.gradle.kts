plugins {
    id("imhere.architecture")
    id("com.android.application") version "8.7.3" apply false
    id("com.google.devtools.ksp") version "1.9.24-1.0.20" apply false
    kotlin("android") version "1.9.24" apply false
    kotlin("jvm") version "1.9.24" apply false
}

allprojects {
    group = "com.imhere"
    version = "0.1.0"

    repositories {
        google()
        mavenCentral()
    }
}
