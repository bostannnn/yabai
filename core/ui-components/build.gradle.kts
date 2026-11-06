plugins {
    alias(libs.plugins.yabai.android.library)
    alias(libs.plugins.yabai.android.compose)
}

android {
    namespace = "com.example.unifiedreader.core.ui.components"
}

dependencies {
    implementation(project(":core:ui-theme"))
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.ui)
    implementation(libs.haze)

    testImplementation(libs.junit4)
}

// The rule tests (FeatureMaterialImportRuleTest, AppLayoutRuleTest, DesignSystemRuleTest) scan
// feature and app sources, so they and the design baselines are test inputs: without this Gradle
// would keep a stale pass after a feature file changes. `-PupdateDesignBaselines` rewrites the
// baselines instead of checking them.
val updateDesignBaselines = providers.gradleProperty("updateDesignBaselines").isPresent
tasks.withType<Test>().configureEach {
    inputs.files(
        fileTree(rootDir.resolve("app/src/main")) { include("**/*.kt") },
        fileTree(rootDir.resolve("feature")) { include("*/src/main/**/*.kt") },
        fileTree(projectDir.resolve("src/test/design-baselines")),
    ).withPathSensitivity(PathSensitivity.RELATIVE)
    inputs.property("updateDesignBaselines", updateDesignBaselines)
    systemProperty("updateDesignBaselines", updateDesignBaselines.toString())
}
