import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.plugins.JavaPluginExtension
import org.gradle.api.tasks.compile.JavaCompile
import org.gradle.kotlin.dsl.configure
import org.gradle.kotlin.dsl.withType
import org.jetbrains.kotlin.gradle.dsl.KotlinJvmProjectExtension

/**
 * A plain Kotlin module. It builds for the same Java version as the Android modules, so the Android
 * modules can inline its functions, and against that version's API, whatever JDK runs Gradle.
 */
class JvmLibraryConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) = with(target) {
        pluginManager.apply("org.jetbrains.kotlin.jvm")
        extensions.configure<JavaPluginExtension> {
            sourceCompatibility = YabaiBuild.javaVersion
            targetCompatibility = YabaiBuild.javaVersion
        }
        tasks.withType<JavaCompile>().configureEach {
            options.release.set(YabaiBuild.JAVA)
        }
        extensions.configure<KotlinJvmProjectExtension> {
            compilerOptions {
                jvmTarget.set(YabaiBuild.jvmTarget)
                freeCompilerArgs.add("-Xjdk-release=${YabaiBuild.JAVA}")
            }
        }
        configureDetekt()
    }
}
