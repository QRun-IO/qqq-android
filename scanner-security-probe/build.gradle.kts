plugins {
   alias(libs.plugins.android.application)
}

android {
   namespace = "io.qrun.mobile.scanner.probe"
   compileSdk = 35
   defaultConfig {
      applicationId = "io.qrun.mobile.scanner.probe"
      minSdk = 26
      targetSdk = 35
      versionCode = 1
      versionName = "1.0"
   }
   compileOptions {
      sourceCompatibility = JavaVersion.VERSION_17
      targetCompatibility = JavaVersion.VERSION_17
   }
}

// Deliberately no runtime dependencies, network permission, shared UID, or target-app permissions.
// The physical harness requires a separately signed copy; it rejects the ordinary debug signer.
