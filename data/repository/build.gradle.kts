plugins {
    alias(libs.plugins.yabai.android.library)
}

android {
    namespace = "com.example.unifiedreader.data.repository"
}

dependencies {
    implementation(project(":core:model"))
    implementation(project(":core:database"))
    implementation(project(":data:stump"))
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.javax.inject)

    testImplementation(libs.junit4)
    testImplementation(libs.kotlinx.coroutines.test)
}
