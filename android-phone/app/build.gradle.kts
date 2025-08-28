plugins {
    id("com.android.application")             version "8.9.0"
    id("org.jetbrains.kotlin.android")        version "2.0.21"
    id("org.jetbrains.kotlin.plugin.compose") version "2.0.21"
}

android {
    namespace  = "com.example.forklift_phone" // ✅ 폰용 네임스페이스
    compileSdk = 35

    defaultConfig {
        applicationId = "com.example.forklift_phone" // ✅ 폰용 appId
        minSdk        = 26
        targetSdk     = 35
        versionCode   = 1
        versionName   = "1.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildFeatures { compose = true }

    // ✅ AGP 8.x + Kotlin 2.0.x → JDK 17 권장
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }

    // composeOptions는 BOM과 compose plugin이 정렬하므로 생략 OK
    // composeOptions { kotlinCompilerExtensionVersion = "1.5.15" }
}

dependencies {
    // ✅ BOM으로 버전 정렬
    implementation(platform("org.jetbrains.kotlin:kotlin-bom:2.0.21"))
    implementation(platform("androidx.compose:compose-bom:2024.10.01"))

    // 👇 Kotlin stdlib 2.2.0 끼어드는 걸 차단 (강제 정렬)
    configurations.configureEach {
        resolutionStrategy.force(
            "org.jetbrains.kotlin:kotlin-stdlib:2.0.21",
            "org.jetbrains.kotlin:kotlin-stdlib-jdk7:2.0.21",
            "org.jetbrains.kotlin:kotlin-stdlib-jdk8:2.0.21"
        )
    }
    // Core
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.appcompat:appcompat:1.7.1")
    implementation("com.google.android.material:material:1.12.0")

    // Compose (버전은 BOM이 관리)
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.foundation:foundation")
    debugImplementation("androidx.compose.ui:ui-tooling")

    // ✅ Navigation-Compose (네비게이션 unresolved 에러 해결)
    implementation("androidx.navigation:navigation-compose:2.7.7")
    implementation("androidx.navigation:navigation-runtime-ktx:2.7.7")

    // CameraX core
    implementation("androidx.camera:camera-core:1.4.2")
    implementation("androidx.camera:camera-camera2:1.4.2")
    implementation("androidx.camera:camera-lifecycle:1.4.2")
    // 미리보기 View(PreviewView)와 컨트롤러
    implementation("androidx.camera:camera-view:1.4.2")

    // 영상 촬영용 임의 !!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!
    implementation ("androidx.lifecycle:lifecycle-viewmodel-compose:2.7.0")

    // ✅ Kotlin Coroutines (Flow/collectLatest 사용)
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")

    // Coil (필요 시)
    implementation("io.coil-kt:coil-compose:2.4.0")
    implementation("io.coil-kt:coil-svg:2.4.0")

    // ✅ MQTT: hannesa2 포크(= info.mqtt.android)
    implementation("com.github.hannesa2:paho.mqtt.android:4.4.1")

    // ⛔ 충돌 제거(절대 넣지 말 것)
    // implementation("org.eclipse.paho:org.eclipse.paho.android.service:1.1.1")
    // implementation("org.eclipse.paho:org.eclipse.paho.client.mqttv3:1.2.5")
    // implementation("androidx.localbroadcastmanager:localbroadcastmanager:1.1.0")

    testImplementation("junit:junit:4.13.2")
    androidTestImplementation("androidx.test.ext:junit:1.2.1")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.6.1")
}
