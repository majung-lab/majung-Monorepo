plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "com.majung.vehicle"
    compileSdk = 35
    useLibrary("android.car") // CarUxRestrictionsManager

    defaultConfig {
        applicationId = "com.majung.vehicle"
        minSdk = 29
        targetSdk = 35
        versionCode = 1
        versionName = "0.1.0"
        // 에뮬레이터에서 호스트 PC의 서버 = 10.0.2.2
        buildConfigField("String", "SERVER_URL", "\"http://10.0.2.2:4000\"")
        // QR에 담을 동승자 웹 주소. 휴대폰으로 찍으려면 PC의 LAN IP로: ./gradlew assembleDebug -PwebUrl=http://192.168.0.12:5173
        val webUrl = (project.findProperty("webUrl") as String?) ?: "http://10.0.2.2:5173"
        buildConfigField("String", "WEB_URL", "\"$webUrl\"")
    }

    buildTypes {
        release {
            isMinifyEnabled = false
        }
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
    // 화면 스냅샷(Roborazzi, Figma 대조용 렌더): ./gradlew testDebugUnitTest -Psnapshots
    //   → app/build/outputs/roborazzi/*.png. 기준 이미지를 커밋하지 않으니 회귀 검사는 아니다.
    testOptions.unitTests {
        isIncludeAndroidResources = true
        all { it.systemProperty("roborazzi.test.record", project.hasProperty("snapshots").toString()) }
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.material3)
    implementation(libs.compose.ui.tooling.preview)
    debugImplementation(libs.compose.ui.tooling)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.okhttp)
    implementation(libs.zxing.core)
    implementation(libs.osmdroid)
    testImplementation(libs.junit)
    testImplementation(platform(libs.compose.bom))
    testImplementation(libs.compose.ui.test.junit4)
    testImplementation(libs.robolectric)
    testImplementation(libs.roborazzi)
    testImplementation(libs.roborazzi.compose)
    debugImplementation(libs.compose.ui.test.manifest)
}

// Compose 테스트용 ComponentActivity는 debug 매니페스트(ui-test-manifest)에만 있다
tasks.matching { it.name == "testReleaseUnitTest" }.configureEach { enabled = false }
