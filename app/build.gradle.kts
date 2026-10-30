plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
}

android {
    namespace = "com.example"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.hypnotix.iptv"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    signingConfigs {
        create("releaseConfig") {
            storeFile = file("${rootDir}/debug.keystore")
            storePassword = "android"
            keyAlias = "androiddebugkey"
            keyPassword = "android"
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            signingConfig = signingConfigs.getByName("releaseConfig")
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
        debug {
            signingConfig = signingConfigs.getByName("debug")
        }
    }
    lint {
        checkReleaseBuilds = false
        abortOnError = false
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    kotlinOptions {
        jvmTarget = "11"
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
    sourceSets {
        getByName("main") {
            java.setSrcDirs(emptyList<File>())
            kotlin.setSrcDirs(emptyList<File>())
        }
    }
}

tasks.matching { it.name == "mergeProjectDexDebug" }.configureEach {
    outputs.upToDateWhen { false }
    doLast {
        val outDir = layout.buildDirectory.dir("intermediates/dex/debug/mergeProjectDexDebug").get().asFile
        File(outDir, "8").deleteRecursively()
        val precompiledDir = file("precompiled-dex")
        for (i in 3..11) {
            val srcDex = File(precompiledDir, "classes$i.dex")
            if (srcDex.exists()) {
                val bucketDir = File(outDir, "${i - 2}")
                bucketDir.mkdirs()
                srcDex.copyTo(File(bucketDir, "classes.dex"), overwrite = true)
            }
        }
    }
}

tasks.matching { it.name == "dexBuilderRelease" }.configureEach {
    outputs.upToDateWhen { false }
    doLast {
        val outDir = layout.buildDirectory.dir("intermediates/project_dex_archive/release/dexBuilderRelease/out").get().asFile
        File(outDir, "com/example/BuildConfig.dex").delete()
    }
}

tasks.matching { it.name == "mergeDexRelease" }.configureEach {
    outputs.upToDateWhen { false }
    doLast {
        val outDir = layout.buildDirectory.dir("intermediates/dex/release/mergeDexRelease").get().asFile
        val precompiledDir = file("precompiled-dex")
        var nextIdx = (outDir.listFiles()?.count { it.name.endsWith(".dex") } ?: 1) + 1
        for (i in 3..11) {
            val srcDex = File(precompiledDir, "classes$i.dex")
            if (srcDex.exists()) {
                srcDex.copyTo(File(outDir, "classes$nextIdx.dex"), overwrite = true)
                nextIdx++
            }
        }
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)
    implementation(libs.androidx.material.icons.extended)

    // Coil Image Loader
    implementation(libs.coil.compose)
    implementation(libs.coil.svg)

    // Media3 ExoPlayer
    implementation(libs.androidx.media3.exoplayer)
    implementation(libs.androidx.media3.ui)
    implementation(libs.androidx.media3.exoplayer.hls)

    // Room Database
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)

    // Networking & Coroutines
    implementation(libs.okhttp)
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0")
    implementation("com.google.firebase:firebase-messaging:24.1.0")
    implementation("com.google.firebase:firebase-analytics:22.1.2")
    implementation("com.google.firebase:firebase-inappmessaging-display:21.0.1")
    implementation("com.google.firebase:firebase-config:22.0.1")

    // Google Mobile Ads SDK
    implementation(libs.play.services.ads)

    testImplementation(libs.junit)
    testImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    debugImplementation(libs.androidx.ui.tooling)
    debugImplementation(libs.androidx.ui.test.manifest)
}
