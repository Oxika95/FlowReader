plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("com.google.devtools.ksp")
}

android {
    namespace = "com.personal.flowreader"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.personal.flowreader"
        minSdk = 26
        targetSdk = 36
        versionCode = 106
        versionName = "1.06-alpha"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
    buildFeatures {
        compose = true
    }
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2025.01.01")
    implementation(composeBom)
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    debugImplementation("androidx.compose.ui:ui-tooling")

    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.activity:activity-compose:1.10.1")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.7")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7")
    implementation("androidx.navigation:navigation-compose:2.8.9")
    implementation("androidx.datastore:datastore-preferences:1.1.2")
    implementation("androidx.media:media:1.7.0")
    implementation("androidx.work:work-runtime-ktx:2.10.0")

    implementation("androidx.room:room-runtime:2.6.1")
    implementation("androidx.room:room-ktx:2.6.1")
    ksp("androidx.room:room-compiler:2.6.1")

    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0")
    implementation("org.jsoup:jsoup:1.18.3")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("androidx.security:security-crypto:1.0.0")
    // Pinned: later releases are built with Kotlin 2.3+ metadata.
    implementation("io.github.dokar3:quickjs-kt:1.0.0-alpha13")

    testImplementation("junit:junit:4.13.2")
    testImplementation("org.json:json:20240303")
}

/**
 * Debug builds seed plugins from a local flow-reader-plugins checkout (default: sibling of
 * this repo; override with -PflowPluginsDir=...). Release builds ship no plugins.
 */
abstract class BundleDevPluginsTask : DefaultTask() {
    @get:Internal
    abstract val pluginsDir: DirectoryProperty

    @get:InputFiles
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val pluginFiles: ConfigurableFileCollection

    @get:OutputDirectory
    abstract val outputDir: DirectoryProperty

    @TaskAction
    fun bundle() {
        val out = outputDir.get().asFile.resolve("plugins")
        out.deleteRecursively()
        out.mkdirs()
        val root = pluginsDir.get().asFile
        pluginFiles.files.forEach { f -> f.copyTo(out.resolve(f.relativeTo(root).path), overwrite = true) }
    }
}

val devPluginsDir: File = file(
    providers.gradleProperty("flowPluginsDir").getOrElse(rootDir.resolve("../flow-reader-plugins").path),
)
val bundleDevPlugins = tasks.register<BundleDevPluginsTask>("bundleDevPlugins") {
    pluginsDir.set(devPluginsDir)
    pluginFiles.from(fileTree(devPluginsDir) { include("*/plugin.json", "*/index.js") })
}

androidComponents {
    onVariants(selector().withBuildType("debug")) { variant ->
        variant.sources.assets?.addGeneratedSourceDirectory(bundleDevPlugins, BundleDevPluginsTask::outputDir)
    }
}

tasks.withType<Test> {
    useJUnit()
    // Forward opt-in for live Edge length bench (EdgeSynthLengthBench).
    systemProperty("flow.bench.edge", System.getProperty("flow.bench.edge") ?: "")
    environment("FLOW_BENCH_EDGE", System.getenv("FLOW_BENCH_EDGE") ?: "")
}
