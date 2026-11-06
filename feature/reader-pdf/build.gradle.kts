plugins {
    alias(libs.plugins.yabai.android.library)
    alias(libs.plugins.yabai.android.compose)
}

android {
    namespace = "com.example.unifiedreader.feature.reader.pdf"
}

dependencies {
    implementation(project(":core:ui-components"))
    implementation(project(":core:model"))
    implementation(project(":feature:reader-core"))
    implementation(project(":feature:reader-preferences"))
    implementation(platform(libs.androidx.compose.bom))
    implementation("androidx.compose.foundation:foundation")
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.material3)

    testImplementation(libs.junit4)
}
