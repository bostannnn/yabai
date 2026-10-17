plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.compose.compiler)
}

android {
    namespace = "com.example.unifiedreader.core.ui.components"
    compileSdk = 35

    defaultConfig {
        minSdk = 28
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
    implementation(project(":core:ui-theme"))
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.ui)

    testImplementation(libs.junit4)
}

// FeatureMaterialImportRuleTest scans feature and app sources, so they are test inputs:
// without this Gradle would keep a stale pass after a feature file changes.
tasks.withType<Test>().configureEach {
    inputs.files(
        fileTree(rootDir.resolve("app/src/main")) { include("**/*.kt") },
        fileTree(rootDir.resolve("feature")) { include("*/src/main/**/*.kt") },
    ).withPathSensitivity(PathSensitivity.RELATIVE)
}
