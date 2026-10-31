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
   filterVariants = arrayOf("debug", "release", "e2e")
   strictMode = com.mikepenz.aboutlibraries.plugin.StrictMode.FAIL
   allowedLicenses = arrayOf("Apache-2.0", "MIT", "MPL-2.0", "BSD-2-Clause", "Apache-2.0 WITH LLVM-exception")
   prettyPrint = true
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
   }
   testBuildType = "e2e"
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
