plugins {
   alias(libs.plugins.android.library)
   alias(libs.plugins.kotlin.android)
   kotlin("plugin.serialization")
   id("kotlinx-serialization")
   id("com.vanniktech.maven.publish") version "0.30.0"
}

publishing {
   repositories {
      maven {
         name = "githubQqqMavenRegistry"
         url = uri("https://maven.pkg.github.com/QRun-IO/qqq-maven-registry")
         // username and password (a personal Github access token) should be specified as
         // `githubPackagesUsername` and `githubPackagesPassword` Gradle properties
         credentials(PasswordCredentials::class)
      }
   }
}

android {
   namespace = "com.kingsrook.qqq.frontend.android.mobileapp"
   compileSdk = 35

   defaultConfig {
      minSdk = 26

      testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
      consumerProguardFiles("consumer-rules.pro")

      manifestPlaceholders["auth0Domain"] = "placeholder.domain"
      manifestPlaceholders["auth0Scheme"] = "placeholderscheme"
   }

   buildTypes {
      release {
         isMinifyEnabled = false
         proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
      }
   }
   compileOptions {
      sourceCompatibility = JavaVersion.VERSION_1_8
      targetCompatibility = JavaVersion.VERSION_1_8
   }
   buildFeatures {
      compose = true
   }
   composeOptions {
      kotlinCompilerExtensionVersion = "1.5.1"
   }
   kotlinOptions {
      jvmTarget = "1.8"
   }
}

dependencies {

   implementation(libs.androidx.core.ktx)
   implementation(libs.androidx.appcompat)
   implementation(libs.material)
   implementation(libs.androidx.runtime.android)
   implementation(libs.androidx.ui.android)
   implementation(libs.androidx.material3.android)
   implementation(libs.androidx.ui.tooling.preview.android)
   implementation(libs.androidx.navigation.compose)
   testImplementation(libs.junit)
   testImplementation(libs.junit.junit)
   androidTestImplementation(libs.androidx.junit)
   androidTestImplementation(libs.androidx.espresso.core)

   // Kotlin serialization
   implementation(libs.kotlinx.serialization.json)

   implementation(libs.androidx.datastore.preferences)
   implementation(libs.androidx.datastore)

   implementation(libs.coil.network.okhttp) // Only available on Android/JVM.
   implementation(libs.coil.compose)
   implementation(libs.coil.svg)

   implementation(project(":qqq:android:core"))
   implementation("com.squareup.retrofit2:retrofit:2.9.0")
   implementation(libs.auth0)
}
