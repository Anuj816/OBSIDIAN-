import com.google.gms.googleservices.GoogleServicesPlugin.MissingGoogleServicesStrategy

plugins {
  alias(libs.plugins.android.application)
  alias(libs.plugins.kotlin.compose)
  alias(libs.plugins.google.devtools.ksp)
  alias(libs.plugins.roborazzi)
  alias(libs.plugins.secrets)
  alias(libs.plugins.google.services)
}

android {
  namespace = "com.anuj.obsidian"
  compileSdk { version = release(36) { minorApiLevel = 1 } }

  defaultConfig {
    applicationId = "com.anuj.obsidian"
    minSdk = 23
    targetSdk = 36
    versionCode = 1
    versionName = "1.0"
    multiDexEnabled = true
    testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    
    ndk {
      abiFilters += listOf("arm64-v8a", "x86_64")
    }
    
    externalNativeBuild {
      cmake {
        cppFlags += "-std=c++17"
        arguments += listOf(
          "-DANDROID_STL=c++_shared",
          "-DBUILD_TESTING=OFF",
          "-DENABLE_SDL_UI=OFF",
          "-DENABLE_INSTALL=OFF",
          "-DENABLE_PLAYLIST=OFF",
          "-DBUILD_SHARED_LIBS=OFF"
        )
      }
    }
  }

  externalNativeBuild {
    cmake {
      path = file("src/main/cpp/CMakeLists.txt")
      version = "3.22.1"
    }
  }

  signingConfigs {
    create("release") {
      val keystorePath = System.getenv("KEYSTORE_PATH") ?: "${rootDir}/my-upload-key.jks"
      val file = file(keystorePath)
      if (file.exists()) {
          storeFile = file
          storePassword = System.getenv("STORE_PASSWORD")
          keyAlias = "upload"
          keyPassword = System.getenv("KEY_PASSWORD")
      } else {
          println("WARNING: Release keystore not found at $keystorePath. Release builds will fail.")
      }
    }
  }

  buildTypes {
    release {
      isCrunchPngs = false
      isMinifyEnabled = true
      isShrinkResources = true
      proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
      signingConfig = signingConfigs.getByName("release")
    }
    debug { }
  }

  compileOptions {
    sourceCompatibility = JavaVersion.VERSION_11
    targetCompatibility = JavaVersion.VERSION_11
  }

  buildFeatures {
    compose = true
    buildConfig = true
  }

  testOptions { unitTests { isIncludeAndroidResources = true } }

  dependenciesInfo {
    includeInApk = false
    includeInBundle = true
  }
}

// Configure the Secrets Gradle Plugin to use .env and .env.example files
// to match the convention used in Web projects.
secrets {
  propertiesFileName = ".env"
  defaultPropertiesFileName = ".env.example"
  ignoreList.add("FIREBASE_APPCHECK_DEBUG_TOKEN")
}

googleServices { missingGoogleServicesStrategy = MissingGoogleServicesStrategy.WARN }

// Some unused dependencies are removed to keep the build clean.
dependencies {
  implementation("sh.calvin.reorderable:reorderable:3.1.0")
  implementation("androidx.documentfile:documentfile:1.0.1")
  implementation("androidx.media:media:1.7.0")
  implementation(libs.androidx.media3.exoplayer)
  implementation(libs.androidx.media3.common)
  implementation(libs.androidx.media3.session)
  implementation(platform(libs.androidx.compose.bom))
  implementation(platform(libs.firebase.bom))
  
  implementation(libs.androidx.activity.compose)
  
  implementation(libs.androidx.compose.material.icons.core)
  implementation(libs.androidx.compose.material.icons.extended)
  implementation(libs.androidx.compose.material3)
  implementation(libs.androidx.compose.ui)
  implementation(libs.androidx.compose.ui.graphics)
  implementation("androidx.compose.animation:animation")
  implementation(libs.androidx.compose.ui.tooling.preview)
  
  implementation(libs.androidx.core.ktx)
  implementation("androidx.palette:palette-ktx:1.0.0")
  implementation("androidx.constraintlayout:constraintlayout-compose:1.1.1")
  
  implementation(libs.androidx.lifecycle.runtime.compose)
  implementation(libs.androidx.lifecycle.runtime.ktx)
  implementation(libs.androidx.lifecycle.viewmodel.compose)
  
  implementation(libs.androidx.room.ktx)
  implementation(libs.androidx.room.runtime)
  implementation(libs.coil.compose)
  
  implementation(libs.firebase.crashlytics)
  implementation(libs.kotlinx.coroutines.android)
  implementation(libs.kotlinx.coroutines.core)
  
  implementation(libs.okhttp)
  implementation("net.jthink:jaudiotagger:3.0.1")
  
  // MilkDrop support dependencies
  implementation("commons-io:commons-io:2.11.0")
  implementation("org.apache.commons:commons-compress:1.21")
  
  testImplementation(libs.androidx.compose.ui.test.junit4)
  testImplementation(libs.androidx.core)
  testImplementation(libs.androidx.junit)
  testImplementation(libs.junit)
  testImplementation(libs.kotlinx.coroutines.test)
  testImplementation(libs.robolectric)
  testImplementation(libs.roborazzi)
  testImplementation(libs.roborazzi.compose)
  testImplementation(libs.roborazzi.junit.rule)

  androidTestImplementation(platform(libs.androidx.compose.bom))
  androidTestImplementation(libs.androidx.compose.ui.test.junit4)
  androidTestImplementation(libs.androidx.espresso.core)
  androidTestImplementation(libs.androidx.junit)
  androidTestImplementation(libs.androidx.runner)

  debugImplementation(libs.androidx.compose.ui.test.manifest)
  debugImplementation(libs.androidx.compose.ui.tooling)

  "ksp"(libs.androidx.room.compiler)
  "ksp"(libs.moshi.kotlin.codegen)
}
