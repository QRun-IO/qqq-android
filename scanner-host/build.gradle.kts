/* Copyright (c) 2026 QRunIO. SPDX-License-Identifier: Apache-2.0 */
import java.security.MessageDigest

plugins {
   alias(libs.plugins.android.application)
   alias(libs.plugins.kotlin.android)
   alias(libs.plugins.aboutlibraries)
}

aboutLibraries {
   configPath = "scanner-host/third-party"
   offlineMode = true
   fetchRemoteLicense = false
   fetchRemoteFunding = false
   includePlatform = false
   filterVariants = arrayOf("debug", "release", "e2e", "qa")
   // The plugin also scans compile classpaths. This SDK is not redistributed;
   // verifyEmdkSdk separately forbids it in every runtime classpath.
   exclusionPatterns = listOf(Regex("^com\\.symbol:emdk$").toPattern())
   strictMode = com.mikepenz.aboutlibraries.plugin.StrictMode.FAIL
   allowedLicenses = arrayOf("Apache-2.0", "MIT", "MPL-2.0", "BSD-2-Clause", "Apache-2.0 WITH LLVM-exception")
   prettyPrint = true
}

// AboutLibraries 10.9.2 marks the collected graph @Internal. Without this input,
// changing a selected dependency can reuse the previous dependency_cache.json.
tasks.withType<com.mikepenz.aboutlibraries.plugin.AboutLibrariesCollectorTask>().configureEach {
   inputs.property("resolvedDependencyCoordinates", providers.provider {
      listOf("debug", "release", "e2e", "qa").flatMap { variant ->
         listOf("CompileClasspath", "RuntimeClasspath").flatMap { suffix ->
            val configurationName = "$variant$suffix"
            configurations.getByName(configurationName).incoming.resolutionResult.allComponents.map { component ->
               "$configurationName:${component.id.displayName}"
            }
         }
      }.sorted()
   })
}

val scannerVersionCode = providers.gradleProperty("scannerVersionCode").orElse("1").get().toInt()
val scannerVersionName = providers.gradleProperty("scannerVersionName").orElse("0.1.0").get()
require(scannerVersionCode in 1..2100000000) { "scannerVersionCode must be a positive Android version code" }
require(Regex("[0-9]+\\.[0-9]+\\.[0-9]+(?:-[A-Za-z0-9.-]+)?").matches(scannerVersionName)) {
   "scannerVersionName must be a semantic version"
}
val signingVariables = listOf("SCANNER_KEYSTORE", "SCANNER_STORE_PASSWORD", "SCANNER_KEY_ALIAS", "SCANNER_KEY_PASSWORD")
val signingValues = signingVariables.associateWith { providers.environmentVariable(it).orNull }
val hasSigningValues = signingValues.values.any { !it.isNullOrBlank() }
val scannerTestBuildType = providers.gradleProperty("scannerTestBuildType").orElse("e2e").get()
require(scannerTestBuildType in setOf("e2e", "qa")) { "scannerTestBuildType must be e2e or qa" }
require(!hasSigningValues || signingValues.values.all { !it.isNullOrBlank() }) {
   "Release signing requires all four SCANNER signing environment variables"
}

android {
   namespace = "io.qrun.mobile.scanner"
   compileSdk = 35
   defaultConfig {
      applicationId = "io.qrun.mobile.scanner"
      minSdk = 26
      targetSdk = 35
      versionCode = scannerVersionCode
      versionName = scannerVersionName
      testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
      manifestPlaceholders["auth0Domain"] = "unused.invalid"
      manifestPlaceholders["auth0Scheme"] = "io.qrun.mobile.scanner"
   }
   signingConfigs {
      if(hasSigningValues) {
         create("production") {
            storeFile = file(signingValues.getValue("SCANNER_KEYSTORE")!!)
            storePassword = signingValues.getValue("SCANNER_STORE_PASSWORD")
            keyAlias = signingValues.getValue("SCANNER_KEY_ALIAS")
            keyPassword = signingValues.getValue("SCANNER_KEY_PASSWORD")
         }
      }
   }
   buildTypes {
      getByName("release") {
         if(hasSigningValues) signingConfig = signingConfigs.getByName("production")
      }
      create("e2e") {
         initWith(getByName("debug"))
         applicationIdSuffix = ".e2e"
         matchingFallbacks += "debug"
      }
      create("qa") {
         initWith(getByName("debug"))
         applicationIdSuffix = ".qa"
         matchingFallbacks += "debug"
      }
   }
   testBuildType = scannerTestBuildType
   sourceSets.getByName("debug").java.srcDir("src/production/java")
   sourceSets.getByName("release").java.srcDir("src/production/java")
   sourceSets.getByName("qa").java.srcDir("src/production/java")
   if(scannerTestBuildType == "qa") {
      sourceSets.getByName("androidTestQa").java.srcDir("src/qaAndroidTest/java")
      sourceSets.getByName("androidTestQa").manifest.srcFile("src/qaAndroidTest/AndroidManifest.xml")
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
   compileOnly(libs.zebra.emdk)
   implementation(libs.aboutlibraries.compose)
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
   implementation(libs.kotlinx.serialization.json)
   implementation(libs.coil.compose)
   implementation(libs.coil.svg)
   implementation(libs.okhttp)
   testImplementation(libs.junit)
   testImplementation(libs.okhttp.mockwebserver)
   testImplementation(libs.json.test)
   testImplementation(libs.kotlinx.coroutines.test)
   androidTestImplementation(libs.androidx.junit)
   androidTestImplementation(libs.androidx.espresso.core)
   androidTestImplementation(platform(libs.androidx.compose.bom))
   androidTestImplementation(libs.androidx.ui.test.junit4)
}

// The vendor SDK is compile-only, supplied at runtime by Zebra firmware. Verify
// its independently reviewed bytes before any variant compiler consumes them.
val verifyEmdkSdk = tasks.register("verifyEmdkSdk") {
   doLast {
      configurations.filter { it.isCanBeResolved && it.name.endsWith("RuntimeClasspath") }.forEach { runtime ->
         val vendorRuntime = runtime.incoming.artifactView {
            componentFilter {
               it is org.gradle.api.artifacts.component.ModuleComponentIdentifier && it.group == "com.symbol" && it.module == "emdk"
            }
         }.artifacts.artifacts
         check(vendorRuntime.isEmpty()) { "Zebra EMDK must remain compile-only; found in ${runtime.name}" }
      }
      listOf("debug", "release", "e2e", "qa").forEach { variant ->
         val artifacts = configurations.getByName("${variant}CompileClasspath").incoming.artifactView {
            componentFilter {
               it is org.gradle.api.artifacts.component.ModuleComponentIdentifier && it.group == "com.symbol" && it.module == "emdk"
            }
         }.artifacts.artifacts
         check(artifacts.size == 1) { "Expected one Zebra EMDK compile-only SDK for $variant" }
         val artifact = artifacts.single()
         val id = artifact.id.componentIdentifier as org.gradle.api.artifacts.component.ModuleComponentIdentifier
         check(id.version == "11.0.134") { "Unexpected Zebra EMDK SDK version for $variant" }
         val digest = MessageDigest.getInstance("SHA-256").digest(artifact.file.readBytes())
            .joinToString("") { "%02x".format(it) }
         check(digest == "e0c6b481c25bf641333bf644de940f1ac7f3fc4f8d5c92ba4ae62ea2252c4e36") {
            "Zebra EMDK SDK checksum differs from the reviewed 11.0.134 artifact"
         }
      }
   }
}
tasks.matching { it.name.startsWith("compile") || it.name == "preBuild" }.configureEach { dependsOn(verifyEmdkSdk) }

// AboutLibraries generates/parses notices. Its strict mode rejects unapproved license
// IDs, but does not reject an omitted dependency or empty license text. Validate the
// standard report against Gradle's resolved runtime before any variant is packaged.
android.applicationVariants.all {
   val variantName = name
   val capitalizedName = name.replaceFirstChar { it.uppercaseChar() }
   val definitionsName = "prepareLibraryDefinitions$capitalizedName"
   val verifyNotices = tasks.register("verify${capitalizedName}ThirdPartyNotices") {
      group = "verification"
      description = "Verifies bundled license text and resolved $variantName runtime coverage"
      dependsOn(definitionsName)
      doLast {
         val reportFile = tasks.named<com.mikepenz.aboutlibraries.plugin.AboutLibrariesTask>(definitionsName)
            .get().combinedLibrariesOutputFile
         check(reportFile.isFile) { "AboutLibraries did not generate $reportFile" }
         val report = groovy.json.JsonSlurper().parse(reportFile) as Map<*, *>
         val libraries = (report["libraries"] as List<*>).map { it as Map<*, *> }
         val licenses = report["licenses"] as Map<*, *>
         check(libraries.isNotEmpty()) { "Generated third-party library list is empty" }
         val byId = libraries.associateBy { it["uniqueId"] as String }
         val runtime = configurations.getByName("${variantName}RuntimeClasspath")
            .incoming.artifactView {
               componentFilter { it is org.gradle.api.artifacts.component.ModuleComponentIdentifier }
            }.artifacts.artifacts.mapNotNull {
               it.id.componentIdentifier as? org.gradle.api.artifacts.component.ModuleComponentIdentifier
            }.associate { "${it.group}:${it.module}" to it.version }
         check(runtime.isNotEmpty()) { "No external runtime dependencies resolved for $variantName" }
         runtime.forEach { (id, version) ->
            check(byId[id]?.get("artifactVersion") == version) {
               "Missing or stale third-party notice for runtime dependency $id:$version"
            }
         }
         libraries.forEach { library ->
            val references = library["licenses"] as? List<*>
            check(!references.isNullOrEmpty()) { "No license declared for ${library["uniqueId"]}" }
            references.forEach { reference ->
               val license = licenses[reference] as? Map<*, *>
               check(!(license?.get("content") as? String).isNullOrBlank()) {
                  "Missing bundled license text $reference for ${library["uniqueId"]}"
               }
               check(license?.get("spdxId") != "MIT" || library["uniqueId"] == "com.auth0.android:auth0") {
                  "A new MIT dependency needs its own reviewed copyright notice: ${library["uniqueId"]}"
               }
               check(license?.get("spdxId") != "MPL-2.0" || library["uniqueId"] == "org.publicsuffix:list") {
                  "MPL-2.0 is reviewed only for the embedded Public Suffix List: ${library["uniqueId"]}"
               }
               check(license?.get("spdxId") != "BSD-2-Clause" || library["uniqueId"] == "android.ndk:bionic-crt") {
                  "BSD-2-Clause is reviewed only for the pinned NDK startup objects: ${library["uniqueId"]}"
               }
               check(license?.get("spdxId") != "Apache-2.0 WITH LLVM-exception" || library["uniqueId"] == "android.ndk:llvm-runtime") {
                  "LLVM exceptions are reviewed only for the pinned NDK runtime/header notice: ${library["uniqueId"]}"
               }
            }
         }
         setOf("google:material-design-icons", "org.publicsuffix:list", "io.qrun:qqq-android",
            "android.ndk:bionic-crt", "android.ndk:llvm-runtime").forEach {
            check(byId.containsKey(it)) { "Missing supplemental bundled notice: $it" }
         }
         mapOf(
            "androidx.graphics:graphics-path" to ("1.0.1" to "AndroidX-Native-Graphics"),
            "androidx.datastore:datastore-core-android" to ("1.1.1" to "AndroidX-Native-DataStore")
         ).forEach { (id, reviewed) ->
            check(runtime[id] == reviewed.first &&
               (byId[id]?.get("licenses") as? List<*>)?.firstOrNull() == reviewed.second) {
               "Review native source/toolchain notices after changing $id; its reviewed notice must remain visible first"
            }
         }
         listOf("android.ndk:bionic-crt", "android.ndk:llvm-runtime").forEach {
            check(byId.getValue(it)["artifactVersion"] == "25.2.9519653") {
               "Native runtime notice must identify the reviewed Android NDK r25c build"
            }
         }
         check(byId.getValue("org.publicsuffix:list")["artifactVersion"] ==
            "bundled with OkHttp ${runtime["com.squareup.okhttp3:okhttp"]}") {
            "Review and update the embedded Public Suffix List notice after changing OkHttp"
         }
         val projectNotice = ((licenses["QQQ-Android"] as Map<*, *>)["content"] as String).replace("\r\n", "\n")
         check(projectNotice.contains(rootProject.file("NOTICE").readText().replace("\r\n", "\n")) &&
            projectNotice.contains(rootProject.file("LICENSE").readText().replace("\r\n", "\n"))) {
            "Bundled QQQ project notice is stale; refresh it from LICENSE and NOTICE"
         }
         // Distribution copy remains the unmodified standard AboutLibraries JSON.
         val distribution = layout.buildDirectory.dir("reports/third-party/$variantName").get().asFile
         distribution.mkdirs()
         reportFile.copyTo(distribution.resolve("aboutlibraries.json"), overwrite = true)
         rootProject.file("NOTICE").copyTo(distribution.resolve("NOTICE"), overwrite = true)
         rootProject.file("LICENSE").copyTo(distribution.resolve("LICENSE"), overwrite = true)
         logger.lifecycle("Verified ${runtime.size} runtime dependencies and ${libraries.size} bundled notice entries for $variantName")
      }
   }
   mergeResourcesProvider.configure { dependsOn(verifyNotices) }
}
