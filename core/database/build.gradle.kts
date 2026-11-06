plugins {
    alias(libs.plugins.yabai.android.library)
    alias(libs.plugins.ksp)
}

android {
    namespace = "com.example.unifiedreader.core.database"

    testOptions {
        unitTests.isIncludeAndroidResources = true
    }

    // Migration tests read the exported schemas as assets. Through the new DSL type, as in
    // feature:settings: with android.newDsl=false the typed accessor fails the configuration.
    val testSourceSet = (sourceSets as NamedDomainObjectContainer<*>).getByName("test")
    (testSourceSet as com.android.build.api.dsl.AndroidSourceSet).assets.srcDir("$projectDir/schemas")
}

// Room writes each version's schema here (exportSchema = true). Commit every new file: a migration
// test needs the old version's schema. 36.json was rebuilt from the commit before version 37.
ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
}

dependencies {
    implementation(project(":core:model"))
    api(libs.androidx.room.runtime)
    api(libs.androidx.room.ktx)
    api(libs.androidx.room.paging)
    ksp(libs.androidx.room.compiler)

    testImplementation(libs.junit4)
    testImplementation(libs.androidx.test.core)
    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.room.testing)
}
