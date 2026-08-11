import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.compose.compiler)
    alias(libs.plugins.kotlin.serialization)
}

// 서명 정보는 저장소에 올라가지 않는 keystore.properties에서 읽는다.
// storeFile은 이 모듈(app/) 기준 상대 경로로 해석된다.
// 파일이 없으면 release는 서명 없이 빌드된다 — 디버그 키로 조용히 대체하지 않는다.
val keystorePropertiesFile = rootProject.file("keystore.properties")
val keystoreProperties = Properties().apply {
    if (keystorePropertiesFile.exists()) {
        keystorePropertiesFile.inputStream().use { load(it) }
    }
}
val hasReleaseSigning = keystorePropertiesFile.exists()

/**
 * 서울 열린데이터광장 인증키는 저장소에 올리지 않는다.
 *
 * 실시간 지하철 API는 '지하철인증키'만 받는다 — '일반인증키'를 넣으면 ERROR-338로
 * 거부한다. 그래서 둘을 다른 이름으로 받고 실시간용만 앱에 넣는다.
 */
fun envValue(name: String): String? = rootProject.file(".env")
    .takeIf { it.exists() }
    ?.readLines()
    ?.firstOrNull { it.startsWith("$name=") }
    ?.substringAfter("=")
    ?.trim()
    ?.takeIf { it.isNotEmpty() }

val seoulApiKey: String = envValue("SEOUL_SUBWAY_API_KEY") ?: "sample"

// 공공데이터포털 열차시간표. 없으면 빈 값으로 두고 시간표 맞추기 기능만 조용히 쉰다.
val seoulTimetableKey: String = envValue("SEOUL_SUBWAY2_API_KEY").orEmpty()

android {
    namespace = "com.actimedi.travle"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.actimedi.travle"
        minSdk = 26
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"

        buildConfigField("String", "SEOUL_API_KEY", "\"$seoulApiKey\"")
        buildConfigField("String", "SEOUL_TIMETABLE_KEY", "\"$seoulTimetableKey\"")
    }

    signingConfigs {
        if (hasReleaseSigning) {
            create("release") {
                keyAlias = keystoreProperties.getProperty("keyAlias")
                keyPassword = keystoreProperties.getProperty("keyPassword")
                storeFile = keystoreProperties.getProperty("storeFile")?.let { file(it) }
                storePassword = keystoreProperties.getProperty("storePassword")
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
                "proguard-rules.pro",
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    packaging {
        resources.excludes += "/META-INF/{AL2.0,LGPL2.1}"
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.activity.compose)
    implementation(libs.kotlinx.serialization.json)

    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.ui.graphics)
    implementation(libs.compose.foundation)
    implementation(libs.compose.material3)
    implementation(libs.compose.ui.tooling.preview)
    debugImplementation(libs.compose.ui.tooling)

    testImplementation(libs.junit)
}
