plugins {
    alias(libs.plugins.android.application)
    // Используем централизованный плагин для Kotlin и Compose
    alias(libs.plugins.kotlin.compose)
    id("com.google.gms.google-services")
}

android {
    namespace = "com.restify.rest"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.restify.rest"
        minSdk = 26
        targetSdk = 34
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables {
            useSupportLibrary = true
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8
    }

    // Современная настройка JVM для Kotlin 2.0+ и Gradle 9+
    kotlin {
        compilerOptions {
            jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_1_8)
        }
    }

    buildFeatures {
        compose = true
    }
    // Блок composeOptions удален, так как Kotlin 2.0+ управляет этим автоматически
}

dependencies {
    // Firebase платформа
    implementation(platform("com.google.firebase:firebase-bom:32.7.2"))

    // Библиотеки из каталога версий (libs.versions.toml)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.activity.compose)

    // Платформа Compose и основные компоненты
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.material3)

    // НАВИГАЦИЯ (добавлено для исправления ошибок в MainActivity)
    implementation("androidx.navigation:navigation-compose:2.7.7")

    // Сетевые библиотеки
    implementation("com.squareup.retrofit2:retrofit:2.9.0")
    implementation("com.squareup.retrofit2:converter-gson:2.9.0")

    // Firebase Messaging
    implementation("com.google.firebase:firebase-messaging-ktx")

    // Тестирование
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
}