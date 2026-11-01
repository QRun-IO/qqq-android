pluginManagement {
   repositories {
      google {
         content {
            includeGroupByRegex("com\\.android.*")
            includeGroupByRegex("com\\.google.*")
            includeGroupByRegex("androidx.*")
         }
      }
      mavenCentral()
      gradlePluginPortal()
   }
}
dependencyResolutionManagement {
   repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
   repositories {
      google()
      mavenCentral()
      exclusiveContent {
         forRepository { maven { url = uri("https://zebratech.jfrog.io/artifactory/EMDK-Android/") } }
         filter { includeModule("com.symbol", "emdk") }
      }
   }
}

rootProject.name = "QQQ Android"
include(":qqq:android:core")
include(":qqq:android:mobileapp")
include(":qqqsampleandroidmobileapp")
include(":scanner-host")
include(":scanner-security-probe")
