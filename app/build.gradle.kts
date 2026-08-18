plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.compose.compiler)
}

val releaseStoreFile = providers.environmentVariable("TOKI_KEYSTORE_FILE").orNull
val releaseStorePassword = providers.environmentVariable("TOKI_STORE_PASSWORD").orNull
val releaseKeyAlias = providers.environmentVariable("TOKI_KEY_ALIAS").orNull
val releaseKeyPassword = providers.environmentVariable("TOKI_KEY_PASSWORD").orNull
val hasReleaseSigning = listOf(
    releaseStoreFile,
    releaseStorePassword,
    releaseKeyAlias,
    releaseKeyPassword,
).all { !it.isNullOrBlank() }

android {
    namespace = "com.seepd.toki"
    compileSdk = 37
    buildToolsVersion = "37.0.0"

    defaultConfig {
        applicationId = "com.seepd.toki"
        minSdk = 26
        targetSdk = 36
        versionCode = 419
        versionName = "0.4.19"

    }

    signingConfigs {
        if (hasReleaseSigning) {
            create("release") {
                storeFile = rootProject.file(releaseStoreFile!!)
                storePassword = releaseStorePassword
                keyAlias = releaseKeyAlias
                keyPassword = releaseKeyPassword
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            signingConfig = signingConfigs.findByName("release")
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }

    buildFeatures {
        compose = true
    }

    // TikTok's private implementation classes are intentionally accessed by
    // reflection from the LSPosed hook; these detectors cannot analyze them.
    lint {
        disable += setOf(
            "AndroidGradlePluginVersion",
            "BlockedPrivateApi",
            "DataExtractionRules",
            "DiscouragedApi",
            "DiscouragedPrivateApi",
            "GradleDependency",
            "MonochromeLauncherIcon",
            "OldTargetApi",
            "PrivateApi",
            "SdCardPath",
            "SoonBlockedPrivateApi",
            "UseKtx",
            "ObsoleteSdkInt"
        )
    }

    packaging {
        resources {
            merges += "META-INF/xposed/*"
            excludes += setOf(
                "META-INF/AL2.0",
                "META-INF/LGPL2.1",
                "META-INF/LICENSE",
                "META-INF/LICENSE.md",
                "META-INF/LICENSE-notice.md",
                "META-INF/NOTICE",
                "META-INF/NOTICE.md",
            )
        }
    }
}

dependencies {
    implementation(platform(libs.compose.bom))
    implementation(libs.activity.compose)
    implementation(libs.compose.ui)
    implementation(libs.compose.material3)
    implementation(libs.compose.material.icons.core)
    implementation(libs.lifecycle.runtime.compose)
    implementation(libs.lifecycle.viewmodel.compose)
    implementation(libs.libxposed.service)
    testImplementation(libs.junit)
    compileOnly(libs.libxposed.api)
}
