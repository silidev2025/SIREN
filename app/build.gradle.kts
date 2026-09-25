import org.gradle.api.file.FileSystemOperations
import java.util.Properties
import javax.inject.Inject

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.google.services)
}

// Release signing values (storeFile, storePassword, keyAlias, keyPassword) live in
// env.local at the repo root — gitignored, template in env.local.example. The older
// keystore.properties is still read as a fallback so a machine that has not moved over
// keeps signing. Neither file is ever committed.
val signingPropsFile = listOf("env.local", "keystore.properties")
    .map { rootProject.file(it) }
    .firstOrNull { it.exists() }
val signingProps = Properties().apply {
    signingPropsFile?.inputStream()?.use { load(it) }
}
val releaseStoreFile = signingProps.getProperty("storeFile")
    ?.takeIf { it.isNotBlank() }
    ?.let { rootProject.file(it) }
val canSignRelease = releaseStoreFile?.exists() == true

// A release build without a signing config comes out unsigned without complaining, so say
// so up front rather than leaving it to be discovered when the APK refuses to install.
if (signingPropsFile != null && !canSignRelease) {
    logger.warn(
        "SIREN: ${signingPropsFile.name} is present but its storeFile " +
            "(${signingProps.getProperty("storeFile")}) was not found — release builds will be unsigned."
    )
}

android {
    namespace = "com.siren.mobile"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.research.siren"
        minSdk = 24
        targetSdk = 35
        versionCode = 13
        versionName = "3.1.2"
        vectorDrawables { useSupportLibrary = true }
    }

    signingConfigs {
        if (canSignRelease) {
            create("release") {
                storeFile = releaseStoreFile
                storePassword = signingProps.getProperty("storePassword")
                keyAlias = signingProps.getProperty("keyAlias")
                keyPassword = signingProps.getProperty("keyPassword")
            }
        }
    }

    buildTypes {
        debug {
            applicationIdSuffix = ""
            isMinifyEnabled = false
        }
        release {
            isMinifyEnabled = true

            isShrinkResources = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            if (canSignRelease) {
                signingConfig = signingConfigs.getByName("release")
            }
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

abstract class CopyComposeResourcesTask : DefaultTask() {
    @get:InputDirectory
    abstract val sourceDir: DirectoryProperty

    @get:Input
    abstract val resourceId: Property<String>

    @get:OutputDirectory
    abstract val outputDir: DirectoryProperty

    @get:Inject
    abstract val fs: FileSystemOperations

    @TaskAction
    fun copyFiles() {

        fs.delete { delete(outputDir) }
        fs.copy {
            from(sourceDir)
            into(outputDir.get().dir("composeResources/${resourceId.get()}"))
        }
    }
}

val copySharedComposeResources =
    tasks.register<CopyComposeResourcesTask>("copySharedComposeResources") {
        sourceDir.set(rootProject.file("shared/src/commonMain/composeResources"))
        resourceId.set("com.siren.mobile.resources")
    }

androidComponents {
    onVariants { variant ->
        variant.sources.assets?.addGeneratedSourceDirectory(
            copySharedComposeResources,
            CopyComposeResourcesTask::outputDir,
        )
    }
}

dependencies {

    implementation(project(":shared"))

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.splashscreen)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.activity.compose)

    implementation(libs.kotlinx.coroutines.core)

    implementation(libs.androidx.fragment)

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    debugImplementation(libs.androidx.ui.tooling)

    implementation(platform(libs.firebase.bom))
    implementation(libs.firebase.messaging)
}
