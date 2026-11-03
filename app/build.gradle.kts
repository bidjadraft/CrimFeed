import com.google.gms.googleservices.GoogleServicesPlugin.MissingGoogleServicesStrategy
import java.io.ByteArrayOutputStream
import java.io.File
import java.security.MessageDigest
import java.util.Base64
import java.util.Locale
import java.util.zip.Deflater
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

plugins {
  alias(libs.plugins.android.application)
  alias(libs.plugins.kotlin.compose)
  alias(libs.plugins.google.devtools.ksp)
  alias(libs.plugins.secrets)
  alias(libs.plugins.google.services)
  id("org.jetbrains.kotlin.plugin.serialization") version "2.2.10"
}

android {
  namespace = "com.example"
  compileSdk { version = release(36) { minorApiLevel = 1 } }

  defaultConfig {
    applicationId = "com.aistudio.applet.khpscn"
    minSdk = 24
    targetSdk = 36
    versionCode = 48
    versionName = "4.8"

    testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
  }

  // Do not remove or modify these signingConfigs. They are necessary for building, installing,
  // and updating Android apps in AI Studio.
  signingConfigs {
    create("release") {
      val keystorePath = System.getenv("KEYSTORE_PATH") ?: "${rootDir}/my-upload-key.jks"
      storeFile = file(keystorePath)
      storePassword = System.getenv("STORE_PASSWORD") ?: "android"
      keyAlias = "upload"
      keyPassword = System.getenv("KEY_PASSWORD") ?: "android"
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
      isCrunchPngs = true
      isMinifyEnabled = true
      isShrinkResources = true
      proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
      // Do not remove or modify this signingConfig assignment. It is necessary for Android apps in
      // AI Studio.
      signingConfig = signingConfigs.getByName("release")
    }
    debug {
      // Do not remove or modify this signingConfig assignment. It is necessary for Android apps in
      // AI Studio.
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
  testOptions {
    unitTests {
      isIncludeAndroidResources = true
      all {
        it.systemProperty("robolectric.offline", "true")
        it.systemProperty("robolectric.dependency.dir", "/home/appuser/.m2/repository/org/robolectric/android-all-instrumented/16-robolectric-13921718-i7")
      }
    }
  }
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

// Unused dependencies are commented out to optimize APK size and build speed.
dependencies {
  implementation(platform(libs.androidx.compose.bom))
  // implementation(platform(libs.firebase.bom))
  // implementation(libs.accompanist.permissions)
  implementation(libs.androidx.activity.compose)
  // implementation(libs.androidx.camera.camera2)
  // implementation(libs.androidx.camera.core)
  // implementation(libs.androidx.camera.lifecycle)
  // implementation(libs.androidx.camera.view)
  implementation(libs.androidx.compose.material.icons.core)
  implementation(libs.androidx.compose.material.icons.extended)
  implementation(libs.androidx.compose.material3)
  implementation(libs.androidx.compose.ui)
  implementation(libs.androidx.compose.ui.graphics)
  implementation(libs.androidx.compose.ui.tooling.preview)
  implementation(libs.androidx.core.ktx)
  implementation(libs.androidx.datastore.preferences)
  implementation(libs.androidx.lifecycle.runtime.compose)
  implementation(libs.androidx.lifecycle.runtime.ktx)
  implementation(libs.androidx.lifecycle.viewmodel.compose)
  implementation(libs.androidx.navigation.compose)
  implementation(libs.androidx.room.ktx)
  implementation(libs.androidx.room.runtime)
  implementation(libs.coil.compose)
  // implementation(libs.converter.moshi)
  // implementation(libs.firebase.ai)
  // implementation(libs.firebase.firestore)
  // implementation(libs.firebase.auth)
  // implementation(libs.androidx.credentials)
  // implementation(libs.androidx.credentials.play.services)
  // implementation(libs.googleid)
  // implementation(libs.firebase.appcheck.recaptcha)
  // implementation(libs.firebase.appcheck.debug)
  implementation(libs.kotlinx.coroutines.android)
  implementation(libs.kotlinx.coroutines.core)
  // implementation(libs.logging.interceptor)
  // implementation(libs.moshi.kotlin)
  implementation(libs.okhttp)
  // implementation(libs.play.services.location)
  implementation(libs.retrofit)
  implementation("com.squareup.retrofit2:converter-kotlinx-serialization:2.11.0")
  implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.8.0")
  testImplementation(libs.androidx.compose.ui.test.junit4)
  testImplementation(libs.androidx.core)
  testImplementation(libs.androidx.junit)
  testImplementation(libs.junit)
  testImplementation(libs.kotlinx.coroutines.test)
  testImplementation(libs.robolectric)
  androidTestImplementation(platform(libs.androidx.compose.bom))
  androidTestImplementation(libs.androidx.compose.ui.test.junit4)
  androidTestImplementation(libs.androidx.espresso.core)
  androidTestImplementation(libs.androidx.junit)
  androidTestImplementation(libs.androidx.runner)
  debugImplementation(libs.androidx.compose.ui.test.manifest)
  debugImplementation(libs.androidx.compose.ui.tooling)
  "ksp"(libs.androidx.room.compiler)
  // "ksp"(libs.moshi.kotlin.codegen)
}

val workspaceRootPath: String = project.projectDir.parentFile.absolutePath

tasks.register("archiveReleaseApk") {
  dependsOn("assembleRelease")
  val rootPath = workspaceRootPath
  val apkOutputFile = layout.buildDirectory.file("outputs/apk/release/app-release.apk")
  doLast {
    val builtApk = apkOutputFile.get().asFile
    require(builtApk.exists()) { "Release APK not found at ${builtApk.absolutePath}" }
    val bytes = builtApk.readBytes()
    val sha256Hex = MessageDigest.getInstance("SHA-256")
      .digest(bytes)
      .joinToString("") { b -> String.format(Locale.US, "%02x", b) }

    val dotAiStudioDir = File(rootPath, ".aistudio").apply { mkdirs() }
    val safeVaultDir = File(rootPath, "safe_vault").apply { mkdirs() }
    val distributionDir = File(rootPath, "distribution").apply { mkdirs() }
    val crimsonSafeDir = File(rootPath, "crimsonapk_safe").apply { mkdirs() }
    val safeReleaseDir = File(dotAiStudioDir, "safe_release").apply { mkdirs() }

    val zipBytes = ByteArrayOutputStream().use { baos ->
      ZipOutputStream(baos).use { zos ->
        zos.setLevel(Deflater.BEST_COMPRESSION)
        val apkEntry = ZipEntry("CrimsonFeed-v4.8-release.apk")
        zos.putNextEntry(apkEntry)
        zos.write(bytes)
        zos.closeEntry()

        val readmeEntry = ZipEntry("README_v4.8.txt")
        zos.putNextEntry(readmeEntry)
        zos.write("CrimsonFeed Release v4.8\nSigned Production APK\nSHA-256: $sha256Hex\n".toByteArray(Charsets.UTF_8))
        zos.closeEntry()
      }
      baos.toByteArray()
    }

    // Save crimsonapk.zip in safe permanent directories
    File(safeVaultDir, "crimsonapk.zip").writeBytes(zipBytes)
    File(distributionDir, "crimsonapk.zip").writeBytes(zipBytes)
    File(crimsonSafeDir, "crimsonapk.zip").writeBytes(zipBytes)
    File(safeReleaseDir, "crimsonapk.zip").writeBytes(zipBytes)
    File(dotAiStudioDir, "CrimsonFeed.zip").writeBytes(zipBytes)

    println("Archived Release ZIP v4.8: (APK=${bytes.size} bytes, ZIP=${zipBytes.size} bytes, SHA-256: $sha256Hex).")
  }
}

tasks.register("stageProtectedRelease") {
  val rootPath = workspaceRootPath
  val apkOutputFile = layout.buildDirectory.file("outputs/apk/release/app-release.apk")
  val stagedDirProvider = layout.buildDirectory.dir("staged_safe")
  doLast {
    val builtApk = apkOutputFile.get().asFile
    require(builtApk.exists()) { "Release APK not found at ${builtApk.absolutePath}" }
    val apkBytes = builtApk.readBytes()
    val apkSha256 = MessageDigest.getInstance("SHA-256")
      .digest(apkBytes)
      .joinToString("") { b -> String.format(Locale.US, "%02x", b) }

    val zipBytes = ByteArrayOutputStream().use { baos ->
      ZipOutputStream(baos).use { zos ->
        zos.setLevel(Deflater.BEST_COMPRESSION)
        val apkEntry = ZipEntry("CrimsonFeed-v4.8-release.apk")
        zos.putNextEntry(apkEntry)
        zos.write(apkBytes)
        zos.closeEntry()
      }
      baos.toByteArray()
    }

    // Remove obsolete files in .aistudio so fresh copies register cleanly
    val dotAiStudioDir = File(rootPath, ".aistudio").apply { mkdirs() }
    listOf("CrimsonFeed.zip", "CrimsonFeed_v4.7.bak", "Download_CrimsonFeed_v4.7.html", "CrimsonFeed_v4.8.bak", "Download_CrimsonFeed_v4.8.html").forEach { name ->
      File(dotAiStudioDir, name).takeIf { it.exists() }?.delete()
    }

    val stagedDir = stagedDirProvider.get().asFile.apply {
      deleteRecursively()
      mkdirs()
    }
    File(stagedDir, "CrimsonFeed.zip").writeBytes(zipBytes)
    File(stagedDir, "crimsonapk.zip").writeBytes(zipBytes)
    File(File(rootPath, "safe_vault").apply { mkdirs() }, "crimsonapk.zip").writeBytes(zipBytes)
    File(File(dotAiStudioDir, "safe_release").apply { mkdirs() }, "crimsonapk.zip").writeBytes(zipBytes)

    val zipBase64 = Base64.getEncoder().encodeToString(zipBytes)
    val htmlExtractor = """<!DOCTYPE html>
<html lang="ar" dir="rtl">
<head>
<meta charset="UTF-8">
<meta name="viewport" content="width=device-width, initial-scale=1.0">
<title>CrimsonFeed v4.8 — تحميل النسخة الموقعة</title>
<style>
  body { font-family: system-ui, -apple-system, sans-serif; background: #0f1115; color: #f3f4f6; display: flex; align-items: center; justify-content: center; min-height: 100vh; margin: 0; padding: 20px; box-sizing: border-box; }
  .card { background: #1a1d24; border: 1px solid #2d3340; border-radius: 18px; max-width: 460px; width: 100%; padding: 28px; text-align: center; box-shadow: 0 16px 40px rgba(0,0,0,0.45); }
  h1 { margin: 0 0 8px; font-size: 22px; color: #ffffff; }
  p { margin: 0 0 20px; font-size: 14px; color: #9ca3af; line-height: 1.6; }
  button { background: #e11d48; color: #fff; border: none; border-radius: 12px; padding: 14px 24px; font-size: 16px; font-weight: 700; cursor: pointer; width: 100%; transition: opacity 0.15s; }
  button:hover { opacity: 0.92; }
  .meta { margin-top: 16px; font-size: 11px; color: #6b7280; word-break: break-all; direction: ltr; }
</style>
</head>
<body>
  <div class="card">
    <h1>CrimsonFeed v4.8</h1>
    <p>اضغط الزر أدناه لاستخراج وتنزيل ملف <strong>crimsonapk.zip</strong> (يحتوي على <code>CrimsonFeed-v4.8-release.apk</code> الموقع رسمياً) مباشرةً بدون إنترنت.</p>
    <button id="dlBtn" onclick="downloadZip()">تنزيل crimsonapk.zip الآن</button>
    <div class="meta">APK SHA-256: $apkSha256</div>
  </div>
<script>
const B64 = "$zipBase64";
function downloadZip() {
  const raw = atob(B64);
  const len = raw.length;
  const bytes = new Uint8Array(len);
  for (let i = 0; i < len; i++) bytes[i] = raw.charCodeAt(i);
  const blob = new Blob([bytes], { type: "application/zip" });
  const url = URL.createObjectURL(blob);
  const a = document.createElement("a");
  a.href = url;
  a.download = "crimsonapk.zip";
  document.body.appendChild(a);
  a.click();
  a.remove();
  setTimeout(() => URL.revokeObjectURL(url), 10000);
}
window.addEventListener("load", () => { setTimeout(downloadZip, 300); });
</script>
</body>
</html>"""
    File(stagedDir, "Download_CrimsonFeed_v4.8.html").writeText(htmlExtractor, Charsets.UTF_8)

    println("Staged Release v4.8: APK=${apkBytes.size} bytes, ZIP=${zipBytes.size} bytes, HTML=${htmlExtractor.toByteArray().size} bytes, SHA-256=$apkSha256")
  }
}

