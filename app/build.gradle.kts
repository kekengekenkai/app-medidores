plugins {
  id("com.android.application")
  id("org.jetbrains.kotlin.android")
  id("org.jetbrains.kotlin.plugin.compose")
}

android {
  namespace = "com.example.app"
  compileSdk = 34
  defaultConfig {
    applicationId = "com.example.app"
    minSdk = 26
    targetSdk = 33
    versionCode = 1
    versionName = "0.1"
  }
  compileOptions {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
  }
  kotlinOptions {
    jvmTarget = "17"
  }
}

dependencies {
  implementation(project(":core-excel"))
  implementation(project(":voice-input"))
  implementation(project(":storage"))
  implementation(project(":sharing"))

  implementation("androidx.core:core-ktx:1.12.0")
  implementation("androidx.appcompat:appcompat:1.6.1")
  implementation("com.google.android.material:material:1.9.0")
  implementation("androidx.activity:activity-compose:1.9.0")
  implementation("androidx.compose.ui:ui:1.6.0")
  implementation("androidx.compose.material:material:1.6.0")
  implementation("androidx.compose.material:material-icons-extended:1.6.0")
  implementation("androidx.compose.material3:material3:1.3.0")
  implementation("androidx.compose.ui:ui-tooling-preview:1.6.0")
  implementation("org.apache.poi:poi-ooxml:5.2.3")
  implementation("org.apache.poi:poi:5.2.3")
}
