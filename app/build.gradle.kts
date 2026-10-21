plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
}

android {
    namespace = "com.lumiform.formviewer"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.lumiform.formviewer"
        minSdk = 26
        targetSdk = 37
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
    }

    testOptions {
        unitTests.all { it.useJUnitPlatform() }
    }
}

dependencies {
    implementation(project(":core:domain"))
    implementation(project(":core:data"))

    implementation(libs.androidx.activity.compose)

    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)

    testImplementation(platform(libs.junit5.bom))
    testImplementation(libs.junit5.jupiter)
    testRuntimeOnly(libs.junit5.platform.launcher)
}
