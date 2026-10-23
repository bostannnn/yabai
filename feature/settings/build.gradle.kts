plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.compose.compiler)
}

val thirdPartyNoticesDir = layout.buildDirectory.dir("generated/thirdPartyNotices/assets").get().asFile

android {
    namespace = "com.example.unifiedreader.feature.settings"
    compileSdk = 37

    defaultConfig {
        minSdk = 28
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    buildFeatures {
        compose = true
    }

    // Through the new DSL type: with android.newDsl=false, AGP 9.2's typed accessor casts the
    // main source set to a class it does not implement and fails the configuration.
    val mainSourceSet = (sourceSets as NamedDomainObjectContainer<*>).getByName("main")
    (mainSourceSet as com.android.build.api.dsl.AndroidSourceSet).assets.srcDir(thirdPartyNoticesDir)
}

// The licenses page shows the repository's THIRD_PARTY_NOTICES.md; copy it into a generated
// asset folder so there is one source of truth.
val copyThirdPartyNotices = tasks.register<Copy>("copyThirdPartyNotices") {
    from(rootProject.file("THIRD_PARTY_NOTICES.md"))
    into(thirdPartyNoticesDir)
}

tasks.named("preBuild") {
    dependsOn(copyThirdPartyNotices)
}

dependencies {
    implementation(project(":core:ui-theme"))
    implementation(project(":core:ui-components"))
    implementation(project(":core:model"))
    implementation(project(":feature:reader-preferences"))
    implementation(project(":feature:navigation"))
    implementation(project(":feature:updates"))
    implementation(libs.androidx.datastore.preferences)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.material3)
    implementation(libs.kotlinx.coroutines.core)

    testImplementation(libs.junit4)
    testImplementation(libs.androidx.test.core)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.robolectric)
}
