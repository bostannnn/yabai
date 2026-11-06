plugins {
    alias(libs.plugins.yabai.android.library)
    alias(libs.plugins.yabai.android.compose)
}

android {
    namespace = "com.example.unifiedreader.feature.reader.epub"

    compileOptions {
        isCoreLibraryDesugaringEnabled = true
    }
}

dependencies {
    implementation(project(":core:ui-components"))
    implementation(project(":core:ui-theme"))
    implementation(project(":core:model"))
    implementation(project(":feature:reader-core"))
    implementation(project(":feature:reader-preferences"))
    implementation(project(":feature:translation"))
    implementation(platform(libs.androidx.compose.bom))
    implementation("androidx.compose.animation:animation")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.runtime:runtime-saveable")
    implementation(libs.androidx.fragment.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.readium.shared)
    implementation(libs.readium.streamer)
    implementation(libs.readium.navigator)
    coreLibraryDesugaring(libs.android.desugar.jdk.libs)

    testImplementation(libs.junit4)
    testImplementation(libs.robolectric)

    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation("androidx.compose.ui:ui-test-junit4")
    androidTestImplementation(libs.androidx.test.ext.junit)
    androidTestImplementation(libs.androidx.test.espresso.core)
    androidTestImplementation(libs.androidx.activity.compose)
    debugImplementation("androidx.compose.ui:ui-test-manifest")
}
