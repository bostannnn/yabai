plugins {
    alias(libs.plugins.yabai.android.library)
}

android {
    namespace = "com.example.unifiedreader.feature.reader.preferences"
}

dependencies {
    implementation(project(":core:model"))
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.kotlinx.coroutines.core)

    testImplementation(libs.junit4)
    testImplementation(libs.androidx.test.core)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.robolectric)
}
