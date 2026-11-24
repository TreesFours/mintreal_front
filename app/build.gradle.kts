plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.hilt)
    alias(libs.plugins.ksp)
    alias(libs.plugins.compose.compiler)
    alias(libs.plugins.google.services)
}

android {
    namespace = "com.example.mistreal_mini"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.example.mistreal_mini"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables {
            useSupportLibrary = true
        }

        buildConfigField("String", "BACKEND_HOST", "\"mistreal-backend.onrender.com\"")
        buildConfigField("String", "BASE_URL", "\"https://\" + BACKEND_HOST + \"/\"")
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
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
    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

hilt {
    enableAggregatingTask = false
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.runtime)
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)
    implementation("androidx.compose.material:material-icons-extended")
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.paging.runtime)
    implementation(libs.androidx.paging.compose)

    // CameraX — was headless-only (ImageCapture, no Preview/PreviewView — see
    // util/SosCameraCapture.kt). camera-view added for Live AI's self-view
    // camera preview (ImageAnalysis feeds frames to Gemini; PreviewView is
    // just what the user sees of themselves, same idea as a video call).
    implementation("androidx.camera:camera-core:1.3.4")
    implementation("androidx.camera:camera-camera2:1.3.4")
    implementation("androidx.camera:camera-lifecycle:1.3.4")
    implementation("androidx.camera:camera-view:1.3.4")
    implementation("androidx.camera:camera-video:1.3.4")

    // Hilt
    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)
    implementation(libs.hilt.navigation.compose)
    implementation(libs.hilt.work)
    ksp(libs.hilt.work.compiler)

    // Retrofit & OkHttp
    implementation(libs.retrofit)
    implementation(libs.retrofit.converter.gson)
    implementation(libs.okhttp)
    implementation(libs.okhttp.logging.interceptor)

    // Room
    implementation(libs.room.runtime)
    implementation(libs.room.ktx)
    implementation(libs.room.paging)
    ksp(libs.room.compiler)

    // Media3 (Video/Audio)
    implementation(libs.media3.exoplayer)
    implementation(libs.media3.ui)
    implementation(libs.media3.transformer)
    implementation(libs.media3.effect)
    implementation(libs.media3.exoplayer.hls)

    // Coil (Image Loading)
    implementation(libs.coil.compose)

    // WorkManager (Background Tasks)
    implementation(libs.androidx.work.runtime.ktx)

    // Stripe
    implementation(libs.stripe.android)
    
    // Play Services Location & Coroutines
    implementation(libs.play.services.location)
    implementation(libs.kotlinx.coroutines.play.services)
    
    // Biometric & ML Kit Face Detection
    implementation(libs.androidx.biometric)
    implementation(libs.play.services.mlkit.face.detection)

    // DataStore
    implementation(libs.androidx.datastore.preferences)

    // Billing
    implementation(libs.billing.ktx)

    // Watch Hub (live screencast/share) — WebRTC for the actual peer media,
    // Socket.IO client for signaling against watchSignalingService.ts's
    // '/watch' namespace. org.json is excluded because Android already
    // ships its own implementation of those classes; without excluding the
    // transitive dependency the two collide at build time.
    implementation("io.getstream:stream-webrtc-android:1.1.1")
    implementation("io.socket:socket.io-client:2.1.1") {
        exclude(group = "org.json", module = "json")
    }

    // Watch Hub Phase 3 — Nearby Connections handles the Bluetooth/Wi-Fi-
    // Direct/local-hotspot discovery and link negotiation itself (don't
    // hand-roll raw BluetoothSocket/WifiP2pManager); used here purely as an
    // alternate SIGNALING transport alongside Socket.IO, not as the media
    // path itself (see NearbyTransport.kt for why).
    implementation("com.google.android.gms:play-services-nearby:19.3.0")

    // Logging
    implementation(libs.timber)

    // Firebase
    implementation(platform(libs.firebase.bom))
    implementation(libs.firebase.auth)
    implementation(libs.firebase.messaging)
    implementation(libs.play.services.auth)

    // SQLCipher (Database Encryption)
    implementation(libs.sqlcipher.android)
    implementation(libs.sqlite.ktx)
    implementation(libs.androidx.security.crypto)

    testImplementation(libs.junit)
    testImplementation("org.mockito:mockito-core:5.11.0")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.9.0")
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.ui.test.junit4)
    debugImplementation(libs.androidx.ui.tooling)
    debugImplementation(libs.androidx.ui.test.manifest)
}
