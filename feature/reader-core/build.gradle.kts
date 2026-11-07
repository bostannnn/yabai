plugins {
    alias(libs.plugins.yabai.android.library)
    alias(libs.plugins.yabai.android.compose)
}

android {
    namespace = "com.example.unifiedreader.feature.reader.core"
}

dependencies {
    implementation(project(":core:ui-components"))
    implementation(project(":core:ui-theme"))
    implementation(project(":core:model"))
    implementation(project(":feature:reader-preferences"))
    implementation(platform(libs.androidx.compose.bom))
    implementation("androidx.compose.animation:animation")
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.kotlinx.coroutines.core)

    testImplementation(libs.junit4)
    testImplementation(libs.kotlinx.coroutines.test)
}
