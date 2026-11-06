import com.android.build.api.dsl.CommonExtension
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.configure

/** Turns on Compose for an Android module. Apply after `yabai.android.library` or `yabai.android.application`. */
class AndroidComposeConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) = with(target) {
        pluginManager.apply("org.jetbrains.kotlin.plugin.compose")
        extensions.configure<CommonExtension> {
            buildFeatures.compose = true
        }
    }
}
