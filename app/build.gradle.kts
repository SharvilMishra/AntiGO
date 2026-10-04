import java.util.Properties

plugins {
  alias(libs.plugins.android.application)
  alias(libs.plugins.kotlin.compose)
  alias(libs.plugins.google.devtools.ksp)
}

if (file("google-services.json").isFile) {
  apply(plugin = "com.google.gms.google-services")
}

val local = Properties().apply { rootProject.file("local.properties").takeIf { it.exists() }?.inputStream()?.use(::load) }
fun credential(env: String, key: String) = System.getenv(env) ?: local.getProperty(key)
val apiBaseUrl = "https://api.example.invalid/"

android {
  namespace = "com.sharvil.antigo"
  compileSdk { version = release(36) { minorApiLevel = 1 } }
  defaultConfig {
    applicationId = "com.sharvil.antigo"
    minSdk = 24
    targetSdk = 36
    versionCode = 1
    versionName = "1.0"
    testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    buildConfigField("String", "API_BASE_URL", "\"$apiBaseUrl\"")
  }
  signingConfigs {
    create("release") {
      credential("KEYSTORE_PATH", "KEYSTORE_PATH")?.let { storeFile = file(it) }
      storePassword = credential("STORE_PASSWORD", "STORE_PASSWORD")
      keyAlias = credential("KEY_ALIAS", "KEY_ALIAS")
      keyPassword = credential("KEY_PASSWORD", "KEY_PASSWORD")
    }
  }
  buildTypes {
    debug { buildConfigField("String", "API_BASE_URL", "\"$apiBaseUrl\"") }
    release {
      isMinifyEnabled = true
      isShrinkResources = true
      buildConfigField("String", "API_BASE_URL", "\"$apiBaseUrl\"")
      signingConfig = signingConfigs.getByName("release")
      proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
    }
  }
  compileOptions { sourceCompatibility = JavaVersion.VERSION_11; targetCompatibility = JavaVersion.VERSION_11 }
  buildFeatures { compose = true; buildConfig = true }
  testOptions { unitTests { isIncludeAndroidResources = true } }
}

dependencies {
  implementation(platform(libs.androidx.compose.bom))
  implementation(platform(libs.firebase.bom))
  implementation(libs.androidx.activity.compose)
  implementation(libs.androidx.compose.material3)
  implementation(libs.androidx.compose.ui)
  implementation(libs.androidx.compose.ui.graphics)
  implementation(libs.androidx.compose.ui.tooling.preview)
  implementation(libs.androidx.compose.material.icons.core)
  implementation(libs.androidx.core.ktx)
  implementation(libs.androidx.lifecycle.runtime.compose)
  implementation(libs.androidx.lifecycle.viewmodel.compose)
  implementation(libs.androidx.room.ktx)
  implementation(libs.androidx.room.runtime)
  implementation(libs.firebase.auth)
  implementation(libs.androidx.credentials)
  implementation(libs.androidx.credentials.play.services)
  implementation(libs.googleid)
  implementation(libs.kotlinx.coroutines.android)
  implementation(libs.kotlinx.coroutines.core)
  implementation(libs.retrofit)
  implementation(libs.converter.moshi)
  implementation(libs.moshi.kotlin)
  implementation(libs.okhttp)
  debugImplementation(libs.logging.interceptor)
  ksp(libs.androidx.room.compiler)
  ksp(libs.moshi.kotlin.codegen)
  testImplementation(libs.junit)
  testImplementation(libs.kotlinx.coroutines.test)
}

tasks.register<Copy>("publishReleaseApkToWebsite") {
  group = "distribution"
  description = "Copies the signed release APK into the static website download folder."
  dependsOn("assembleRelease")
  from(layout.buildDirectory.dir("outputs/apk/release")) {
    include("app-release.apk")
    rename { "antigo.apk" }
  }
  into(rootProject.layout.projectDirectory.dir("website/downloads"))
}
