plugins {
    alias(libs.plugins.yabai.android.library)
    alias(libs.plugins.yabai.android.compose)
}

android {
    namespace = "com.example.unifiedreader.core.ui.theme"
}

dependencies {
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.ui)
    testImplementation(libs.junit4)
}
