plugins {
    alias(libs.plugins.yabai.android.library)
    alias(libs.plugins.yabai.android.compose)
}

android {
    namespace = "com.example.unifiedreader.feature.library"
}

dependencies {
    implementation(project(":core:ui-theme"))
    implementation(project(":core:ui-components"))
    implementation(project(":core:database"))
    implementation(project(":core:model"))
    implementation(project(":feature:reader-preferences"))
    implementation(libs.androidx.datastore.preferences)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.paging.compose)
    implementation(libs.coil.compose)
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.javax.inject)

    testImplementation(libs.junit4)
    testImplementation(libs.androidx.test.core)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.activity.compose)

    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation("androidx.compose.ui:ui-test-junit4")
    androidTestImplementation(libs.androidx.test.ext.junit)
    androidTestImplementation(libs.androidx.test.espresso.core)
    androidTestImplementation(libs.androidx.activity.compose)
    debugImplementation("androidx.compose.ui:ui-test-manifest")
}
