plugins {
    alias(libs.plugins.yabai.android.library)
    alias(libs.plugins.yabai.android.compose)
}

android {
    namespace = "com.example.unifiedreader.feature.downloads"
}

dependencies {
    implementation(project(":core:ui-theme"))
    implementation(project(":core:ui-components"))
    implementation(project(":core:model"))
    implementation(project(":core:database"))
    implementation(project(":data:repository"))
    implementation(project(":feature:library"))

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.material3)
    implementation(libs.kotlinx.coroutines.core)

    testImplementation(libs.junit4)
    testImplementation(libs.androidx.test.core)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.robolectric)
}
