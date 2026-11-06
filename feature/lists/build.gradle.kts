plugins {
    alias(libs.plugins.yabai.android.library)
    alias(libs.plugins.yabai.android.compose)
}

android {
    namespace = "com.example.unifiedreader.feature.lists"
}

dependencies {
    implementation(project(":core:ui-theme"))
    implementation(project(":core:ui-components"))
    implementation(project(":core:database"))
    implementation(project(":core:model"))
    implementation(project(":feature:library"))
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.material3)
    implementation(libs.kotlinx.coroutines.core)

    testImplementation(libs.junit4)
    testImplementation(libs.kotlinx.coroutines.test)
}
