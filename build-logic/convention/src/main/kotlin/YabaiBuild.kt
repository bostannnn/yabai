import com.android.build.api.dsl.CommonExtension
import org.gradle.api.JavaVersion
import org.gradle.api.Project
import org.gradle.kotlin.dsl.configure
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.dsl.KotlinAndroidProjectExtension

/** The numbers every module shares. One place to change them. */
internal object YabaiBuild {
    const val COMPILE_SDK = 37
    const val MIN_SDK = 28

    /** Every module, Android and plain Kotlin, builds for this Java version. */
    const val JAVA = 17
    val javaVersion: JavaVersion = JavaVersion.toVersion(JAVA)
    val jvmTarget: JvmTarget = JvmTarget.fromTarget(JAVA.toString())
}

internal fun Project.configureAndroidCommon() {
    extensions.configure<CommonExtension> {
        compileSdk = YabaiBuild.COMPILE_SDK
        defaultConfig.minSdk = YabaiBuild.MIN_SDK
        defaultConfig.testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        compileOptions.sourceCompatibility = YabaiBuild.javaVersion
        compileOptions.targetCompatibility = YabaiBuild.javaVersion
        // Each module's lint-baseline.xml holds the findings from before lint ran in CI (2026-11-06).
        // Refresh it with `./gradlew :module:updateLintBaseline` only after fixing findings.
        lint.baseline = file("lint-baseline.xml")
        lint.abortOnError = true
        lint.checkDependencies = false
        // These ask the network for newer library versions, so their findings change without a code change.
        lint.disable += setOf("NewerVersionAvailable", "GradleDependency", "AndroidGradlePluginVersion")
    }
    extensions.configure<KotlinAndroidProjectExtension> {
        compilerOptions.jvmTarget.set(YabaiBuild.jvmTarget)
    }
}
