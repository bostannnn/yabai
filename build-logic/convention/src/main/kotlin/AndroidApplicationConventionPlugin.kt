import org.gradle.api.Plugin
import org.gradle.api.Project

/** The app module: the same SDK levels, Java version and Kotlin target as the libraries. */
class AndroidApplicationConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) = with(target) {
        pluginManager.apply("com.android.application")
        pluginManager.apply("org.jetbrains.kotlin.android")
        configureAndroidCommon()
        configureDetekt()
    }
}
