plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("com.google.devtools.ksp")
}

android {
    namespace = "com.auditcamera.app"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.auditcamera.app"
        // Android 10以降にすると「自分が作った写真」をギャラリーに保存・削除するのに
        // ストレージ権限が要らなくなる(権限ダイアログはカメラの初回1回だけで済む)
        minSdk = 29
        targetSdk = 34
        versionCode = 1
        versionName = "1.0"

        // 送信先。現行のWeb版(index.html)と同じ値をそのまま使う。
        // ※合言葉トークンはフロントに載る性質のもので完全な秘密ではない(現行と同じ扱い)
        buildConfigField(
            "String",
            "GAS_URL",
            "\"https://script.google.com/macros/s/AKfycbzzm1rsYowVX0rFUb5JItn4UIhfv9rl2I_8b-aN1LnSGGREmDVREF1VXboZ77WqXCou/exec\""
        )
        buildConfigField("String", "AUTH_TOKEN", "\"audit-cam-Xk9f2Qd7mL4pRt8w-2026\"")
    }

    buildTypes {
        release {
            isMinifyEnabled = false
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2024.09.03")
    implementation(composeBom)

    implementation("androidx.core:core-ktx:1.13.1")
    // JPEGの向き情報(EXIF)の読み書き
    implementation("androidx.exifinterface:exifinterface:1.3.7")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.6")
    implementation("androidx.activity:activity-compose:1.9.2")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.6")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")

    // カメラ(アプリ内にライブ映像を埋め込む。外部カメラアプリは起動しない)
    val cameraxVersion = "1.3.4"
    implementation("androidx.camera:camera-core:$cameraxVersion")
    implementation("androidx.camera:camera-camera2:$cameraxVersion")
    implementation("androidx.camera:camera-lifecycle:$cameraxVersion")
    implementation("androidx.camera:camera-view:$cameraxVersion")

    // 未送信キュー(現行のIndexedDBに相当)
    val roomVersion = "2.6.1"
    implementation("androidx.room:room-runtime:$roomVersion")
    implementation("androidx.room:room-ktx:$roomVersion")
    ksp("androidx.room:room-compiler:$roomVersion")

    // アプリを閉じていても電波復帰時に送信を再開させる仕組み
    implementation("androidx.work:work-runtime-ktx:2.9.1")

    // GASへのPOST
    implementation("com.squareup.okhttp3:okhttp:4.12.0")

    // 一覧画面のサムネイル表示
    implementation("io.coil-kt:coil-compose:2.7.0")
}
