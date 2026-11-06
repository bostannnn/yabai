plugins {
    alias(libs.plugins.yabai.android.application)
    alias(libs.plugins.yabai.android.compose)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
}

android {
    namespace = "com.example.unifiedreader"

    defaultConfig {
        applicationId = "com.example.unifiedreader"
        targetSdk = 35
        versionCode = 47
        versionName = "0.43.0"
        vectorDrawables.useSupportLibrary = true
    }

    val releaseSigningStoreFile = providers.environmentVariable("ANDROID_SIGNING_STORE_FILE").orNull
    val releaseSigningStorePassword = providers.environmentVariable("ANDROID_SIGNING_STORE_PASSWORD").orNull
    val releaseSigningKeyAlias = providers.environmentVariable("ANDROID_SIGNING_KEY_ALIAS").orNull
    val releaseSigningKeyPassword = providers.environmentVariable("ANDROID_SIGNING_KEY_PASSWORD").orNull
    val releaseSigningConfig = if (
        !releaseSigningStoreFile.isNullOrBlank() &&
        !releaseSigningStorePassword.isNullOrBlank() &&
        !releaseSigningKeyAlias.isNullOrBlank() &&
        !releaseSigningKeyPassword.isNullOrBlank()
    ) {
        signingConfigs.create("release") {
            storeFile = file(releaseSigningStoreFile)
            storePassword = releaseSigningStorePassword
            keyAlias = releaseSigningKeyAlias
            keyPassword = releaseSigningKeyPassword
        }
    } else {
        null
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            releaseSigningConfig?.let { signingConfig = it }
            // Translation's native code is ~16 MB per ABI; ship only 64-bit ARM, which every
            // tablet and phone this app targets runs. Debug builds keep all ABIs for emulators.
            ndk { abiFilters += "arm64-v8a" }
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
        }
    }

    compileOptions {
        isCoreLibraryDesugaringEnabled = true
    }

    sourceSets {
        getByName("test").java.setSrcDirs(listOf("src/test/java", "src/sharedTest/java"))
        getByName("androidTest").java.setSrcDirs(listOf("src/androidTest/java", "src/sharedTest/java"))
    }
}

dependencies {
    implementation(project(":core:common"))
    implementation(project(":core:ui-theme"))
    implementation(project(":core:ui-components"))
    implementation(project(":core:model"))
    implementation(project(":core:network"))
    implementation(project(":core:database"))
    implementation(project(":data:kavita"))
    implementation(project(":data:stump"))
    implementation(project(":data:grimmory"))
    implementation(project(":data:repository"))
    implementation(project(":feature:library"))
    implementation(project(":feature:library-covers"))
    implementation(project(":feature:library-shelves"))
    implementation(project(":feature:settings"))
    implementation(project(":feature:reader-preferences"))
    implementation(project(":feature:reader-core"))
    implementation(project(":feature:reader-comics"))
    implementation(project(":feature:reader-pdf"))
    implementation(project(":feature:reader-epub"))
    implementation(project(":feature:downloads"))
    implementation(project(":feature:journal"))
    implementation(project(":feature:lists"))
    implementation(project(":feature:navigation"))
    implementation(project(":feature:updates"))
    implementation(project(":feature:translation"))

    implementation(libs.androidx.appcompat)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.core.splashscreen)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.fragment.ktx)
    implementation(libs.androidx.datastore.preferences)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.material3)
    implementation(libs.junrar)
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.ktor.client.core)
    implementation(libs.androidx.work.runtime.ktx)
    implementation(libs.hilt.android)
    implementation(libs.hilt.navigation.compose)
    coreLibraryDesugaring(libs.android.desugar.jdk.libs)

    testImplementation(libs.junit4)
    testImplementation(libs.androidx.test.core)
    testImplementation(libs.robolectric)
    testImplementation(libs.kotlinx.coroutines.test)

    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation("androidx.compose.ui:ui-test-junit4")
    androidTestImplementation(libs.androidx.test.ext.junit)
    androidTestImplementation(libs.androidx.test.espresso.core)
    androidTestImplementation(libs.androidx.activity.compose)

    debugImplementation(libs.androidx.compose.ui.tooling)
    debugImplementation("androidx.compose.ui:ui-test-manifest")
    ksp(libs.hilt.compiler)
}
