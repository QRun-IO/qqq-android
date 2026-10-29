plugins {
   alias(libs.plugins.android.application)
   alias(libs.plugins.kotlin.android)
}

android {
   namespace = "io.qrun.mobile.scanner"
   compileSdk = 35
   defaultConfig {
      applicationId = "io.qrun.mobile.scanner"
      minSdk = 26
      targetSdk = 35
      versionCode = 1
      versionName = "0.1.0"
      manifestPlaceholders["auth0Domain"] = "unused.invalid"
      manifestPlaceholders["auth0Scheme"] = "io.qrun.mobile.scanner"
   }
   buildFeatures { compose = true; buildConfig = true }
   composeOptions { kotlinCompilerExtensionVersion = "1.5.1" }
   compileOptions {
      sourceCompatibility = JavaVersion.VERSION_1_8
      targetCompatibility = JavaVersion.VERSION_1_8
   }
   kotlinOptions { jvmTarget = "1.8" }
   packaging { resources.excludes += "/META-INF/{AL2.0,LGPL2.1}" }
}

dependencies {
   implementation(project(":qqq:android:core"))
   implementation(project(":qqq:android:mobileapp"))
   implementation(libs.androidx.core.ktx)
   implementation(libs.androidx.activity.compose)
   implementation(libs.androidx.lifecycle.viewmodel.compose)
   implementation(libs.androidx.lifecycle.runtime.ktx)
   implementation(libs.androidx.navigation.compose)
   implementation(libs.androidx.datastore.preferences)
   implementation(platform(libs.androidx.compose.bom))
   implementation(libs.androidx.material3)
   implementation(libs.androidx.ui)
   implementation("com.squareup.okhttp3:okhttp:4.11.0")
   testImplementation(libs.junit)
}
