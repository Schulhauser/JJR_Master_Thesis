plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.example.ble_gpstelegram"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.example.ble_gpstelegram"
        minSdk = 21
        targetSdk = 34
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
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

    testOptions {
        unitTests {
            isIncludeAndroidResources = true
        }
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.12.0")
    implementation("androidx.appcompat:appcompat:1.6.1")

    // Dependencias para BLE y GPS
    implementation("com.google.android.gms:play-services-location:21.2.0")  // Para LocationRequest
    implementation("androidx.activity:activity-ktx:1.9.0")  // Para manejo de permisos
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.7.0")  // Corrutinas
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.7.3")  // Corrutinas

    // Testing
    testImplementation("junit:junit:4.13.2")
    androidTestImplementation("androidx.test.ext:junit:1.1.5")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.5.1")

    // Para mejor manejo de permisos
    implementation("com.google.accompanist:accompanist-permissions:0.34.0")

    implementation("com.squareup.okhttp3:okhttp:4.9.3")

    implementation("org.json:json:20210307")
}
