plugins {
    id("org.jetbrains.kotlin.multiplatform")
    id("com.android.kotlin.multiplatform.library")
    id("org.jetbrains.compose")
    id("org.jetbrains.kotlin.plugin.compose")
    id("org.jetbrains.kotlin.plugin.serialization")
}

compose.resources {
    publicResClass = true
    packageOfResClass = "com.siren.mobile.resources"
}

kotlin {
    androidLibrary {
        namespace = "com.siren.mobile.shared"
        compileSdk = 37
        minSdk = 24
    }

    listOf(iosArm64(), iosSimulatorArm64()).forEach { target ->
        target.binaries.framework {
            baseName = "ComposeApp"
            isStatic = true
        }
    }

    sourceSets {
        commonMain.dependencies {
            implementation(compose.runtime)
            implementation(compose.foundation)
            implementation(compose.material3)
            implementation(compose.ui)
            implementation(compose.components.resources)

            implementation(libs.compose.icons.extended)

            implementation(libs.kotlinx.coroutines.core)
            implementation(libs.kotlinx.serialization.json)

            implementation(libs.gitlive.firebase.auth)
            implementation(libs.gitlive.firebase.firestore)
        }

        androidMain.dependencies {

            implementation(libs.androidx.activity.compose)
            implementation(libs.androidx.core.ktx)

            implementation(project.dependencies.platform(libs.firebase.bom))
            implementation(libs.firebase.messaging)
            implementation(libs.firebase.auth)
        }
    }
}
