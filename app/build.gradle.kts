import com.google.gms.googleservices.GoogleServicesPlugin.MissingGoogleServicesStrategy
import java.io.ByteArrayOutputStream
import javax.inject.Inject

plugins {
  alias(libs.plugins.android.application)
  alias(libs.plugins.kotlin.compose)
  alias(libs.plugins.google.devtools.ksp)
  alias(libs.plugins.roborazzi)
  alias(libs.plugins.secrets)
  alias(libs.plugins.google.services)
}

android {
  namespace = "com.example"
  compileSdk { version = release(36) { minorApiLevel = 1 } }

  defaultConfig {
    applicationId = "com.aistudio.unzix.archiver"
    minSdk = 24
    targetSdk = 36
    versionCode = 1
    versionName = "1.0"

    testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
  }

  signingConfigs {
    create("release") {
      val envKeystorePath = System.getenv("KEYSTORE_PATH")
      val envStorePassword = System.getenv("STORE_PASSWORD")
      val envKeyPassword = System.getenv("KEY_PASSWORD")
      val hasReleaseCreds = envKeystorePath != null &&
        envStorePassword != null &&
        envKeyPassword != null &&
        file(envKeystorePath).exists()

      if (hasReleaseCreds) {
        storeFile = file(envKeystorePath!!)
        storePassword = envStorePassword
        keyAlias = "upload"
        keyPassword = envKeyPassword
      } else {
        logger.warn(
          "Release signing credentials not found (KEYSTORE_PATH/STORE_PASSWORD/KEY_PASSWORD). " +
            "Falling back to the debug keystore for the release build type. " +
            "Set those env vars before publishing a real release build."
        )
        storeFile = file("${rootDir}/debug.keystore")
        storePassword = "android"
        keyAlias = "androiddebugkey"
        keyPassword = "android"
      }
    }
    create("debugConfig") {
      storeFile = file("${rootDir}/debug.keystore")
      storePassword = "android"
      keyAlias = "androiddebugkey"
      keyPassword = "android"
    }
  }

  buildTypes {
    release {
      isCrunchPngs = false
      isMinifyEnabled = false
      proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
      signingConfig = signingConfigs.getByName("release")
    }
    debug {
      signingConfig = signingConfigs.getByName("debugConfig")
    }
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
}

// Configure the Secrets Gradle Plugin to use .env and .env.example files
// to match the convention used in Web projects.
secrets {
  propertiesFileName = ".env"
  defaultPropertiesFileName = ".env.example"
}

googleServices {
  missingGoogleServicesStrategy = MissingGoogleServicesStrategy.WARN
}

// Task type that generates a debug.keystore if one doesn't already exist.
// Uses an injected ExecOperations instead of the deprecated/removed Project.exec
// extension so this works under Gradle's configuration cache (Gradle 9.x+).
abstract class GenerateDebugKeystoreTask : DefaultTask() {

  @get:Inject
  abstract val execOperations: ExecOperations

  @get:OutputFile
  abstract val keystoreFile: RegularFileProperty

  @get:Internal
  abstract val javaHome: Property<String>

  @TaskAction
  fun generate() {
    val ksFile = keystoreFile.get().asFile
    if (ksFile.exists()) {
      logger.lifecycle("debug.keystore already exists at ${ksFile.absolutePath}, skipping generation.")
      return
    }

    logger.lifecycle("debug.keystore not found at ${ksFile.absolutePath} — generating one now.")
    val keytoolCandidate = File(javaHome.get(), "bin/keytool")
    val keytoolPath = if (keytoolCandidate.exists()) keytoolCandidate.absolutePath else "keytool"

    val stdout = ByteArrayOutputStream()
    val stderr = ByteArrayOutputStream()
    val result = execOperations.exec {
      commandLine(
        keytoolPath,
        "-genkey", "-v",
        "-keystore", ksFile.absolutePath,
        "-storetype", "PKCS12",
        "-alias", "androiddebugkey",
        "-storepass", "android",
        "-keypass", "android",
        "-keyalg", "RSA",
        "-keysize", "2048",
        "-validity", "10000",
        "-dname", "CN=Android Debug,O=Android,C=US"
      )
      standardOutput = stdout
      errorOutput = stderr
      isIgnoreExitValue = true
    }

    if (result.exitValue != 0 || !ksFile.exists()) {
      throw GradleException(
        "Failed to auto-generate debug.keystore via keytool.\n" +
          "stdout:\n$stdout\n" +
          "stderr:\n$stderr\n" +
          "You can generate it manually with:\n" +
          "keytool -genkey -v -keystore debug.keystore -storetype PKCS12 " +
          "-alias androiddebugkey -storepass android -keypass android " +
          "-keyalg RSA -keysize 2048 -validity 10000 -dname \"CN=Android Debug,O=Android,C=US\""
      )
    }
    logger.lifecycle("debug.keystore generated successfully at ${ksFile.absolutePath}.")
  }
}

// Auto-generate a debug.keystore at the PROJECT ROOT (matches the path used by
// signingConfigs above: "${rootDir}/debug.keystore") if one doesn't already exist.
// Prevents ":app:validateSigningDebug" from failing on fresh checkouts / CI machines
// where debug.keystore was never committed (it's normally gitignored).
val generateDebugKeystore = tasks.register<GenerateDebugKeystoreTask>("generateDebugKeystore") {
  keystoreFile.set(File(rootDir, "debug.keystore"))
  javaHome.set(System.getProperty("java.home"))
}

// Make sure the keystore exists before Gradle tries to validate any signing config
// that depends on it (debug build type, and release when it falls back to debug signing).
tasks.matching { it.name.startsWith("validateSigning") }.configureEach {
  dependsOn(generateDebugKeystore)
}

// Some unused dependencies are commented out below instead of being removed.
// This makes it easy to add them back in the future if needed.
dependencies {
  implementation(platform(libs.androidx.compose.bom))
  implementation(platform(libs.firebase.bom))
  // implementation(libs.accompanist.permissions)
  implementation(libs.androidx.activity.compose)
  // implementation(libs.androidx.camera.camera2)
  // implementation(libs.androidx.camera.core)
  // implementation(libs.androidx.camera.lifecycle)
  // implementation(libs.androidx.camera.view)
  implementation(libs.androidx.compose.material.icons.core)
  // implementation(libs.androidx.compose.material.icons.extended)
  implementation(libs.androidx.compose.material3)
  implementation(libs.androidx.compose.ui)
  implementation(libs.androidx.compose.ui.graphics)
  implementation(libs.androidx.compose.ui.tooling.preview)
  implementation(libs.androidx.core.ktx)
  // implementation(libs.androidx.datastore.preferences)
  implementation(libs.androidx.lifecycle.runtime.compose)
  implementation(libs.androidx.lifecycle.runtime.ktx)
  implementation(libs.androidx.lifecycle.viewmodel.compose)
  // implementation(libs.androidx.navigation.compose)
  implementation(libs.androidx.room.ktx)
  implementation(libs.androidx.room.runtime)
  // implementation(libs.coil.compose)
  implementation(libs.converter.moshi)
  implementation(libs.firebase.ai)
  implementation(libs.firebase.appcheck.recaptcha)
  implementation(libs.kotlinx.coroutines.android)
  implementation(libs.kotlinx.coroutines.core)
  implementation(libs.logging.interceptor)
  implementation(libs.moshi.kotlin)
  implementation(libs.okhttp)
  // implementation(libs.play.services.location)
  implementation(libs.retrofit)
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
