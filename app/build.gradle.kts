plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}
android {
    namespace = "app.derminal"
    compileSdk = 36
    defaultConfig {
        applicationId = "app.derminal"
        minSdk = 26
        // Android app-data execution policy requires API 28 for a Linux userspace.
        targetSdk = 28
        versionCode = System.getenv("DERMINAL_BUILD_NUMBER")?.toIntOrNull() ?: 1
        versionName = "1.0.0"
        ndk { abiFilters += "arm64-v8a" }
    }
    signingConfigs {
        create("fixed") {
            storeFile = file(System.getenv("DERMINAL_KEYSTORE_PATH") ?: "missing.p12")
            storePassword = System.getenv("DERMINAL_SIGNING_PASSWORD")
            keyAlias = "derminal"
            keyPassword = System.getenv("DERMINAL_SIGNING_PASSWORD")
            storeType = "PKCS12"
        }
    }
    buildTypes {
        release {
            signingConfig = signingConfigs.getByName("fixed")
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }
    buildFeatures { compose = true; buildConfig = true }
    testOptions { unitTests.isIncludeAndroidResources = true }
    compileOptions { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 }
    packaging {
        jniLibs { useLegacyPackaging = true; keepDebugSymbols += "**/*.so" }
        resources.excludes += "/META-INF/{AL2.0,LGPL2.1}"
    }
    androidResources { noCompress += "zip" }
    lint { disable += setOf("ExpiredTargetSdkVersion", "OldTargetApi"); checkReleaseBuilds = true }
}
kotlin { compilerOptions { jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17) } }
dependencies {
    implementation(project(":terminal-view"))
    implementation(platform("androidx.compose:compose-bom:2025.09.00"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.animation:animation")
    implementation("androidx.activity:activity-compose:1.11.0")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.9.4")
    implementation("androidx.core:core-ktx:1.17.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.10.2")
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.robolectric:robolectric:4.16.1")
}
