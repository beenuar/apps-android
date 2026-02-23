plugins {
    id("com.android.library")
    id("org.jetbrains.kotlin.android")
    id("kotlin-kapt")
    id("com.google.dagger.hilt.android")
}

android {
    namespace = "com.deepfakeshield.ml"
    compileSdk = 34

    defaultConfig {
        minSdk = 26
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    buildFeatures {
        mlModelBinding = true
    }
}

dependencies {
    implementation(project(":core"))
    implementation(project(":data"))

    // TensorFlow Lite (for custom model inference)
    api("org.tensorflow:tensorflow-lite:2.14.0")
    api("org.tensorflow:tensorflow-lite-support:0.4.4")
    api("org.tensorflow:tensorflow-lite-gpu:2.14.0")
    api("org.tensorflow:tensorflow-lite-gpu-delegate-plugin:0.4.4")
    api("org.tensorflow:tensorflow-lite-metadata:0.4.4")

    // Google ML Kit — real ML models for face detection & analysis
    // Includes neural network face detector, 133 landmark points, face contours,
    // classification (eyes open, smiling), head pose (Euler angles)
    api("com.google.mlkit:face-detection:16.1.6")
    api("com.google.mlkit:face-mesh-detection:16.0.0-beta3")

    // Google MediaPipe — state-of-the-art on-device ML models
    // Face Landmarker provides 478-point face mesh with blendshapes
    // Face Detector provides ultra-fast face bounding boxes
    api("com.google.mediapipe:tasks-vision:0.10.14")

    // CameraX (for video frame extraction)
    val cameraxVersion = "1.3.1"
    api("androidx.camera:camera-core:$cameraxVersion")
    api("androidx.camera:camera-camera2:$cameraxVersion")
    api("androidx.camera:camera-lifecycle:$cameraxVersion")
    api("androidx.camera:camera-video:$cameraxVersion")

    // Hilt
    implementation("com.google.dagger:hilt-android:2.50")
    kapt("com.google.dagger:hilt-android-compiler:2.50")

    // Coroutines
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.7.3")

    // Testing
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.7.3")
}

kapt {
    correctErrorTypes = true
}
