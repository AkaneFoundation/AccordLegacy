// Top-level build file where you can add configuration options common to all sub-projects/modules.
plugins {
    val agpVersion = "8.8.0-rc01"
    id("com.android.application") version agpVersion apply false
    id("com.android.library") version agpVersion apply false
    val kotlinVersion = "2.1.0"
    kotlin("android") version kotlinVersion apply false
    kotlin("plugin.parcelize") version kotlinVersion apply false
    id("com.google.devtools.ksp") version "$kotlinVersion-1.0.29" apply false
}

tasks.withType(JavaCompile::class) {
    options.compilerArgs.add("-Xlint:all")
}
