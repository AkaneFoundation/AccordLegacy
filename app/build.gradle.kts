@file:Suppress("UnstableApiUsage")

import com.android.build.gradle.tasks.PackageAndroidArtifact
import org.jetbrains.kotlin.util.removeSuffixIfPresent
import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("com.google.devtools.ksp")
    id("org.jetbrains.kotlin.plugin.parcelize")
}

android {
    val releaseType = readProperties(file("../package.properties")).getProperty("releaseType")
    if (releaseType.contains("\"")) {
        throw IllegalArgumentException("releaseType must not contain \"")
    }

    namespace = "org.akanework.gramophone"
    compileSdk = 35
    buildToolsVersion = "35.0.0"

    androidResources {
        generateLocaleConfig = true
    }

    buildFeatures {
        buildConfig = true
    }

    packaging {
        jniLibs {
            useLegacyPackaging = false
        }
        dex {
            useLegacyPackaging = false
        }
        resources {
            excludes += "META-INF/*.version"
        }
    }

    defaultConfig {
        applicationId = "uk.akane.accord"
        // Reasons to not support KK include me.zhanghai.android.fastscroll, WindowInsets for
        // bottom sheet padding, ExoPlayer requiring multidex for KK and poor SD card support
        // That said, supporting Android 5.0 barely costs any tech debt and we plan to keep support
        // for it for a while.
        // Bye bye android 12 - cuz blur
        minSdk = 31
        targetSdk = 34
        versionCode = 18
        versionName = "beta1"
        buildConfigField(
            "String",
            "MY_VERSION_NAME",
            "\"Beta 1\""
        )
        buildConfigField(
            "String",
            "RELEASE_TYPE",
            "\"$releaseType\""
        )
        setProperty("archivesBaseName", "Accord-$versionName")

        ksp {
            arg("room.schemaLocation", "$projectDir/schemas")
        }

        renderscriptTargetApi = 21
        renderscriptSupportModeEnabled = true
    }

    signingConfigs {
        create("release") {
            if (project.hasProperty("AKANE_RELEASE_KEY_ALIAS")) {
                storeFile = file(project.properties["AKANE_RELEASE_STORE_FILE"].toString())
                storePassword = project.properties["AKANE_RELEASE_STORE_PASSWORD"].toString()
                keyAlias = project.properties["AKANE_RELEASE_KEY_ALIAS"].toString()
                keyPassword = project.properties["AKANE_RELEASE_KEY_PASSWORD"].toString()
            }
        }
    }

    splits {
        abi {

            // Enables building multiple APKs per ABI.
            isEnable = true

            // By default all ABIs are included, so use reset() and include to specify that you only
            // want APKs for x86 and x86_64.

            // Resets the list of ABIs for Gradle to create APKs for to none.
            reset()

            // Specifies a list of ABIs for Gradle to create APKs for.
            include("armeabi-v7a", "arm64-v8a", "x86", "x86_64")

            // Specifies that you don't want to also generate a universal APK that includes all ABIs.
            isUniversalApk = false
        }
    }

    buildTypes {
        release {
            if (releaseType != "Profile") {
                isMinifyEnabled = true
                isShrinkResources = true
                proguardFiles(
                    getDefaultProguardFile("proguard-android-optimize.txt"),
                    "proguard-rules.pro"
                )
            } else {
                isMinifyEnabled = false
                isProfileable = true
            }
            if (project.hasProperty("AKANE_RELEASE_KEY_ALIAS")) {
                signingConfig = signingConfigs["release"]
            }
        }
        debug {
            applicationIdSuffix = ".debug"
            if (project.hasProperty("AKANE_RELEASE_KEY_ALIAS")) {
                signingConfig = signingConfigs["release"]
            }
        }
    }

    java {
        toolchain {
            languageVersion = JavaLanguageVersion.of(17)
        }
    }

    kotlin {
        jvmToolchain(17)
        compilerOptions {
            freeCompilerArgs = listOf(
                "-Xno-param-assertions",
                "-Xno-call-assertions",
                "-Xno-receiver-assertions"
            )
        }
    }

    // https://gitlab.com/IzzyOnDroid/repo/-/issues/491
    dependenciesInfo {
        includeInApk = false
        includeInBundle = false
    }

    // https://stackoverflow.com/a/77745844
    tasks.withType<PackageAndroidArtifact> {
        doFirst { appMetadata.asFile.orNull?.writeText("") }
    }
}

dependencies {
    val media3Version = "1.4.0"
    val roomVersion = "2.6.1"

    ksp("androidx.room:room-compiler:$roomVersion")
    implementation("androidx.room:room-runtime:$roomVersion")
    implementation("androidx.room:room-ktx:$roomVersion")
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.activity:activity-ktx:1.9.1")
    implementation("androidx.concurrent:concurrent-futures-ktx:1.2.0")
    implementation("androidx.transition:transition-ktx:1.5.1") // <-- for predictive back
    implementation("androidx.fragment:fragment-ktx:1.8.2")
    implementation("androidx.core:core-splashscreen:1.0.1")
    implementation("androidx.lifecycle:lifecycle-viewmodel-ktx:2.8.4")
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("androidx.constraintlayout:constraintlayout:2.2.0-alpha13")
    implementation("androidx.media3:media3-exoplayer:$media3Version")
    implementation("androidx.media3:media3-exoplayer-midi:$media3Version")
    implementation("androidx.media3:media3-session:$media3Version")
    implementation("androidx.preference:preference-ktx:1.2.1")
    implementation("com.google.android.material:material:1.13.0-alpha04")
    implementation("io.coil-kt.coil3:coil:3.0.0-alpha09")
    implementation(files("../libs/lib-decoder-ffmpeg-release.aar"))
    implementation(project(":fluidrecyclerview"))
    implementation(project(":fastscroll"))
    // --- below does not apply to release builds ---
    debugImplementation("com.squareup.leakcanary:leakcanary-android:2.14")
    // Note: JAudioTagger is not compatible with Android 5, we can't ship it in app
    debugImplementation("net.jthink:jaudiotagger:3.0.1") // <-- for "SD Exploder"
    testImplementation("junit:junit:4.13.2")
}

fun String.runCommand(
    workingDir: File = File(".")
): String = providers.exec {
    setWorkingDir(workingDir)
    commandLine(split(' '))
}.standardOutput.asText.get().removeSuffixIfPresent("\n")

fun readProperties(propertiesFile: File) = Properties().apply {
    propertiesFile.inputStream().use { fis ->
        load(fis)
    }
}