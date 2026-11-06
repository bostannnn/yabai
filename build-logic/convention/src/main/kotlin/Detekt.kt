import io.gitlab.arturbosch.detekt.extensions.DetektExtension
import org.gradle.api.Project
import org.gradle.api.artifacts.VersionCatalogsExtension
import org.gradle.kotlin.dsl.configure
import org.gradle.kotlin.dsl.getByType

/**
 * detekt with the Compose rules, on every module. Rules come from `config/detekt/detekt.yml` on top of
 * detekt's defaults; each module's `detekt-baseline.xml` holds the findings that predate the check, so only
 * new code has to pass. Regenerate a baseline with `./gradlew :module:detektBaseline` only after fixing
 * findings, never to hide new ones.
 */
internal fun Project.configureDetekt() {
    val libs = extensions.getByType<VersionCatalogsExtension>().named("libs")
    pluginManager.apply("io.gitlab.arturbosch.detekt")
    extensions.configure<DetektExtension> {
        buildUponDefaultConfig = true
        parallel = true
        config.setFrom(rootProject.file("config/detekt/detekt.yml"))
        baseline = file("detekt-baseline.xml")
        source.setFrom(
            listOf("main", "test", "androidTest", "sharedTest").flatMap { set ->
                listOf("src/$set/java", "src/$set/kotlin")
            },
        )
    }
    dependencies.add("detektPlugins", libs.findLibrary("compose-rules-detekt").get())
    // The Kotlin plugin raises every org.jetbrains.kotlin dependency to its own version; detekt's
    // compiler must stay on the one it was built with.
    val detektKotlin = libs.findVersion("detektKotlin").get().requiredVersion
    configurations.matching { it.name == "detekt" }.configureEach {
        resolutionStrategy.eachDependency {
            if (requested.group == "org.jetbrains.kotlin") useVersion(detektKotlin)
        }
    }
}
