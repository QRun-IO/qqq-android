/*
 * SPDX-License-Identifier: Apache-2.0
 * QQQ - Low-code Application Framework for Engineers.
 * Copyright (C) 2004-2026.  Kingsrook, LLC
 * 651 N Broad St Ste 205 # 6917 | Middletown DE 19709 | United States
 * contact@kingsrook.com
 * https://github.com/Kingsrook/
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

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
   namespace = "com.kingsrook.qqq.frontend.android.core"
   compileSdk = 35

   defaultConfig {
      minSdk = 26

      testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
      consumerProguardFiles("consumer-rules.pro")
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
   kotlinOptions {
      jvmTarget = "1.8"
   }
}

dependencies {

   implementation(libs.androidx.core.ktx)
   implementation(libs.androidx.appcompat)
   implementation(libs.material)

   testImplementation(libs.junit)
   testImplementation(libs.okhttp.mockwebserver)
   testImplementation("org.json:json:20240303")
   testImplementation(libs.junit.jupiter)
   androidTestImplementation(libs.androidx.junit)
   androidTestImplementation(libs.androidx.espresso.core)

   // Kotlin serialization
   implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.5.1")

   // Retrofit
   implementation("com.squareup.retrofit2:retrofit:2.9.0")

   // Retrofit with Kotlin serialization Converter
   implementation("com.jakewharton.retrofit:retrofit2-kotlinx-serialization-converter:1.0.0")
   implementation(libs.okhttp)

   implementation("org.jetbrains.kotlin:kotlin-reflect:2.0.20")
}
