plugins {
    alias(libs.plugins.kotlin.jvm)
}

dependencies {
    implementation(project(":core:model"))
    implementation(project(":core:common"))
    implementation(project(":data:kavita"))
    implementation(project(":data:grimmory"))
    implementation(libs.kotlinx.coroutines.core)
}
