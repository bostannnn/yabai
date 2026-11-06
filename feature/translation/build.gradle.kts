plugins {
    alias(libs.plugins.yabai.android.library)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.yabai.android.compose)
}

android {
    namespace = "com.example.unifiedreader.feature.translation"
}

dependencies {
    implementation(project(":core:model"))
    implementation(project(":core:ui-components"))

    implementation(platform(libs.androidx.compose.bom))
    implementation("androidx.compose.foundation:foundation")
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.kotlinx.coroutines.play.services)
    implementation(libs.mlkit.translate)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.ktor.client.core)

    testImplementation(libs.junit4)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.ktor.client.mock)
}
