import org.gradle.api.file.FileSystemOperations
import java.util.Properties
import javax.inject.Inject

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.google.services)
}

val keystorePropsFile = rootProject.file("keystore.properties")
val keystoreProps = Properties().apply {
    if (keystorePropsFile.exists()) keystorePropsFile.inputStream().use { load(it) }
}

android {
    namespace = "com.siren.mobile"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.research.siren"
        minSdk = 24
        targetSdk = 35
        versionCode = 9
        versionName = "2.9.2"
        vectorDrawables { useSupportLibrary = true }
    }

    signingConfigs {
        if (keystorePropsFile.exists()) {
            create("release") {
                storeFile = rootProject.file(keystoreProps.getProperty("storeFile"))
                storePassword = keystoreProps.getProperty("storePassword")
                keyAlias = keystoreProps.getProperty("keyAlias")
                keyPassword = keystoreProps.getProperty("keyPassword")
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
            if (keystorePropsFile.exists()) {
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
