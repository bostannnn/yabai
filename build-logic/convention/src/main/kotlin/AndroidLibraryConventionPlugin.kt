import org.gradle.api.Plugin
import org.gradle.api.Project

/** An Android library module: SDK levels, Java version and Kotlin target from [YabaiBuild]. */
class AndroidLibraryConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) = with(target) {
        pluginManager.apply("com.android.library")
        pluginManager.apply("org.jetbrains.kotlin.android")
        configureAndroidCommon()
        configureDetekt()
    }
}
