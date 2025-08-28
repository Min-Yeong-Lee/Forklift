plugins {
    id("com.android.application")             version "8.9.0"
    id("org.jetbrains.kotlin.android")        version "2.0.21"
    id("org.jetbrains.kotlin.plugin.compose") version "2.0.21"
}

android {
    namespace  = "com.example.forklift_k"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.example.forklift_k"
        minSdk = 24
        targetSdk = 35
        versionCode = 1
        versionName = "1.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables.useSupportLibrary = true
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
        isCoreLibraryDesugaringEnabled = true
    }
    kotlinOptions { jvmTarget = "17" }

    flavorDimensions += "device"
    productFlavors {
        create("phone") {
            dimension = "device"
            buildConfigField("String", "DEVICE_ROLE", "\"ph\"")
            buildConfigField("String", "DEVICE_INSTANCE", "\"01\"")
        }
        create("tabletLegacy") {
            dimension = "device"
            buildConfigField("String", "DEVICE_ROLE", "\"tb\"")
            buildConfigField("String", "DEVICE_INSTANCE", "\"01\"")
        }
    }

    packaging {
        resources {
            excludes += setOf(
                "META-INF/DEPENDENCIES",
                "META-INF/NOTICE", "META-INF/NOTICE.txt",
                "META-INF/LICENSE", "META-INF/LICENSE.txt",
                "META-INF/LICENSE.md", "META-INF/NOTICE.md",
                // ✅ 충돌 리소스 제외
                "META-INF/versions/9/OSGI-INF/MANIFEST.MF"
            )
            // 또는 pickFirsts += "META-INF/versions/9/OSGI-INF/MANIFEST.MF"
        }
    }
}

dependencies {
    implementation(platform("org.jetbrains.kotlin:kotlin-bom:2.0.21"))
    implementation(platform("androidx.compose:compose-bom:2024.10.01"))

    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.appcompat:appcompat:1.7.1")
    implementation("com.google.android.material:material:1.12.0")

    implementation("androidx.activity:activity-compose")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.foundation:foundation")
    debugImplementation("androidx.compose.ui:ui-tooling")

    implementation("io.coil-kt:coil-compose:2.4.0")
    implementation("io.coil-kt:coil-svg:2.4.0")

    // ✅ MQTT (Paho Core + Android Service)
    implementation("com.github.hannesa2:paho.mqtt.android:4.4.1")
    implementation("org.eclipse.paho:org.eclipse.paho.client.mqttv3:1.2.5")
    //implementation("info.mqtt.android:service:1.1.1")

    // ✅ Crypto (BouncyCastle PEM Parser)
    implementation("org.bouncycastle:bcprov-jdk15to18:1.78.1")
    implementation("org.bouncycastle:bcpkix-jdk15to18:1.78.1")

    implementation("com.google.code.gson:gson:2.11.0")
    coreLibraryDesugaring("com.android.tools:desugar_jdk_libs:2.0.4")

    testImplementation("junit:junit:4.13.2")
    androidTestImplementation("androidx.test.ext:junit:1.2.1")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.6.1")
}

configurations.all {
    resolutionStrategy {
        force(
            "org.jetbrains.kotlin:kotlin-stdlib:2.0.21",
            "org.jetbrains.kotlin:kotlin-stdlib-jdk7:2.0.21",
            "org.jetbrains.kotlin:kotlin-stdlib-jdk8:2.0.21",
            "org.jetbrains.kotlin:kotlin-stdlib-common:2.0.21",
            "org.jetbrains.kotlin:kotlin-reflect:2.0.21"
        )
        eachDependency {
            if (requested.group == "org.jetbrains.kotlin" && requested.version?.startsWith("2.2") == true) {
                useVersion("2.0.21")
                because("K2 메타데이터 버전 충돌 방지")
            }
        }
    }
    exclude(group = "org.eclipse.paho", module = "org.eclipse.paho.android.service")
    // ✅ 원인 차단하고 싶으면 주석 해제:
    // exclude(group = "org.jspecify", module = "jspecify")
}
