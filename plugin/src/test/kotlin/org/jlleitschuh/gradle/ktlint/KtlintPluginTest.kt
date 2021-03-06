package org.jlleitschuh.gradle.ktlint

import org.assertj.core.api.Assertions.assertThat
import org.gradle.testkit.runner.GradleRunner
import org.gradle.testkit.runner.TaskOutcome
import org.jlleitschuh.gradle.ktlint.reporter.ReporterType
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

/**
 * Runs the tests with the current version of Gradle.
 */
class GradleCurrentKtlintPluginTest : BaseKtlintPluginTest()

@Suppress("ClassName")
class Gradle4_10KtlintPluginTest : BaseKtlintPluginTest() {

    override fun gradleRunnerFor(vararg arguments: String): GradleRunner =
            super.gradleRunnerFor(*arguments).withGradleVersion("4.10")
}

abstract class BaseKtlintPluginTest : AbstractPluginTest() {

    @BeforeEach
    fun setupBuild() {
        projectRoot.apply {
            buildFile().writeText("""
                ${buildscriptBlockWithUnderTestPlugin()}

                ${pluginsBlockWithKotlinJvmPlugin()}

                apply plugin: "org.jlleitschuh.gradle.ktlint"

                repositories {
                    gradlePluginPortal()
                }

                import org.jlleitschuh.gradle.ktlint.reporter.ReporterType

                ktlint.reporters = [ReporterType.CHECKSTYLE, ReporterType.PLAIN]
            """.trimIndent())
        }
    }

    @Test
    fun `fails on versions older than 0_22_0`() {
        projectRoot.buildFile().appendText("""

            ktlint.version = "0.21.0"
        """.trimIndent())

        projectRoot.withCleanSources()

        buildAndFail("ktlintCheck").apply {
            assertThat(task(":ktlintMainSourceSetCheck")?.outcome).isEqualTo(TaskOutcome.FAILED)
            assertThat(output)
                .contains("Ktlint versions less than 0.22.0 are not supported. Detected Ktlint version: 0.21.0.")
        }
    }

    @Test
    fun `should fail check on failing sources`() {
        projectRoot.withFailingSources()

        buildAndFail("ktlintCheck").apply {
            assertThat(task(":ktlintMainSourceSetCheck")!!.outcome).isEqualTo(TaskOutcome.FAILED)
            assertThat(output).contains("Unnecessary space(s)")
            assertReportCreated(ReporterType.PLAIN)
            assertReportCreated(ReporterType.CHECKSTYLE)
            assertReportNotCreated(ReporterType.JSON)
        }
    }

    @Test
    fun `creates multiple reports`() {
        projectRoot.withFailingSources()

        projectRoot.buildFile().appendText("""

            ktlint.reporters = [ReporterType.PLAIN_GROUP_BY_FILE, ReporterType.CHECKSTYLE, ReporterType.JSON]
        """.trimIndent())

        buildAndFail("ktlintCheck").apply {
            assertThat(task(":ktlintMainSourceSetCheck")!!.outcome).isEqualTo(TaskOutcome.FAILED)
            assertThat(output).contains("Unnecessary space(s)")
            assertReportCreated(ReporterType.PLAIN_GROUP_BY_FILE)
            assertReportCreated(ReporterType.CHECKSTYLE)
            assertReportCreated(ReporterType.JSON)
        }
    }

    @Test
    fun `is out of date when different report is enabled`() {
        projectRoot.withCleanSources()

        projectRoot.buildFile().appendText("""

            ktlint.reporters = [ReporterType.JSON, ReporterType.PLAIN]
        """.trimIndent())

        build("ktlintCheck").apply {
            assertThat(task(":ktlintMainSourceSetCheck")!!.outcome).isEqualTo(TaskOutcome.SUCCESS)
            assertReportCreated(ReporterType.PLAIN)
            assertReportCreated(ReporterType.JSON)
        }

        build("ktlintCheck").apply {
            assertThat(task(":ktlintMainSourceSetCheck")!!.outcome).isEqualTo(TaskOutcome.UP_TO_DATE)
            assertReportCreated(ReporterType.PLAIN)
            assertReportCreated(ReporterType.JSON)
            assertReportNotCreated(ReporterType.CHECKSTYLE)
        }

        projectRoot.buildFile().appendText("""

            ktlint.reporters = [ReporterType.JSON, ReporterType.PLAIN_GROUP_BY_FILE]
        """.trimIndent())

        build("ktlintCheck").apply {
            assertThat(task(":ktlintMainSourceSetCheck")!!.outcome).isEqualTo(TaskOutcome.SUCCESS)
            assertReportCreated(ReporterType.PLAIN_GROUP_BY_FILE)
            assertReportCreated(ReporterType.JSON)
        }

        projectRoot.buildFile().appendText("""

            ktlint.reporters = [ReporterType.JSON, ReporterType.CHECKSTYLE]
        """.trimIndent())

        build("ktlintCheck").apply {
            assertThat(task(":ktlintMainSourceSetCheck")!!.outcome).isEqualTo(TaskOutcome.SUCCESS)
            assertReportCreated(ReporterType.JSON)
            assertReportCreated(ReporterType.CHECKSTYLE)
            // TODO: Stale reports are not cleaned up
            assertReportCreated(ReporterType.PLAIN)
        }
    }

    @Test
    fun `Check task should be up_to_date if editorconfig content not changed`() {
        projectRoot.withCleanSources()
        projectRoot.createEditorconfigFile()

        build("ktlintCheck").apply {
            assertThat(task(":ktlintMainSourceSetCheck")!!.outcome).isEqualTo(TaskOutcome.SUCCESS)
        }
        build("ktlintCheck").apply {
            assertThat(task(":ktlintMainSourceSetCheck")!!.outcome).isEqualTo(TaskOutcome.UP_TO_DATE)
        }
    }

    @Test
    fun `Check task should rerun if editorconfig content changed`() {
        projectRoot.withCleanSources()
        projectRoot.createEditorconfigFile()

        build("ktlintCheck").apply {
            assertThat(task(":ktlintMainSourceSetCheck")!!.outcome).isEqualTo(TaskOutcome.SUCCESS)
        }

        projectRoot.modifyEditorconfigFile(100)
        build("ktlintCheck").apply {
            assertThat(task(":ktlintMainSourceSetCheck")!!.outcome).isEqualTo(TaskOutcome.SUCCESS)
        }
    }

    @Test
    fun `check task is relocatable`() {
        val originalLocation = temporaryFolder.resolve("original")
        val relocatedLocation = temporaryFolder.resolve("relocated")
        val localBuildCacheDirectory = temporaryFolder.resolve("build-cache")
        listOf(originalLocation, relocatedLocation).forEach {
            it.apply {
                withCleanSources()
                buildFile().writeText("""
                    ${buildscriptBlockWithUnderTestPlugin()}

                    ${pluginsBlockWithKotlinJvmPlugin()}

                    apply plugin: "org.jlleitschuh.gradle.ktlint"

                    repositories {
                        gradlePluginPortal()
                    }

                    import org.jlleitschuh.gradle.ktlint.reporter.ReporterType

                    ktlint.reporters = [ReporterType.PLAIN, ReporterType.CHECKSTYLE]
                """.trimIndent())
                settingsFile().writeText("""
                    buildCache {
                        local {
                            directory = '${localBuildCacheDirectory.toURI()}'
                        }
                    }
                """.trimIndent())
            }
        }

        GradleRunner.create()
                .withProjectDir(originalLocation)
                .withArguments("ktlintCheck", "--build-cache")
                .forwardOutput()
                .build().apply {
                    assertThat(task(":ktlintMainSourceSetCheck")!!.outcome).isEqualTo(TaskOutcome.SUCCESS)
                }

        GradleRunner.create()
                .withProjectDir(relocatedLocation)
                .withArguments("ktlintCheck", "--build-cache")
                .forwardOutput()
                .build().apply {
                    assertThat(task(":ktlintMainSourceSetCheck")!!.outcome).isEqualTo(TaskOutcome.FROM_CACHE)
                }
    }

    private
    fun assertReportCreated(reportType: ReporterType) {
        assertThat(reportLocation(reportType).isFile).isTrue()
    }

    private
    fun assertReportNotCreated(reportType: ReporterType) {
        assertThat(reportLocation(reportType).isFile).isFalse()
    }

    private fun reportLocation(reportType: ReporterType) =
            projectRoot.resolve("build/reports/ktlint/ktlintMainSourceSetCheck.${reportType.fileExtension}")

    @Test
    fun `should succeed check on clean sources`() {

        projectRoot.withCleanSources()

        build("ktlintCheck").apply {
            assertThat(task(":ktlintMainSourceSetCheck")!!.outcome).isEqualTo(TaskOutcome.SUCCESS)
        }
    }

    @Test
    fun `should generate code style files in project`() {
        projectRoot.withCleanSources()
        val ideaRootDir = projectRoot.resolve(".idea").apply { mkdir() }

        build("ktlintApplyToIdea").apply {
            assertThat(task(":ktlintApplyToIdea")?.outcome).isEqualTo(TaskOutcome.SUCCESS)
            assertThat(ideaRootDir.listFiles().isNotEmpty()).isTrue()
        }
    }

    @Test
    fun `should generate code style file globally`() {
        val ideaRootDir = projectRoot.resolve(".idea").apply { mkdir() }

        build(":ktlintApplyToIdeaGlobally").apply {
            assertThat(task(":ktlintApplyToIdeaGlobally")?.outcome).isEqualTo(TaskOutcome.SUCCESS)
            assertThat(ideaRootDir.listFiles().isNotEmpty()).isTrue()
        }
    }

    @Test
    fun `should show only plugin meta tasks in task output`() {
        projectRoot.withCleanSources()

        build("tasks").apply {
            val ktlintTasks = output
                    .lineSequence()
                    .filter { it.startsWith("ktlint") }
                    .toList()

            assertThat(ktlintTasks).hasSize(4)
            assertThat(ktlintTasks).anyMatch { it.startsWith(CHECK_PARENT_TASK_NAME) }
            assertThat(ktlintTasks).anyMatch { it.startsWith(FORMAT_PARENT_TASK_NAME) }
            assertThat(ktlintTasks).anyMatch { it.startsWith(APPLY_TO_IDEA_TASK_NAME) }
            assertThat(ktlintTasks).anyMatch { it.startsWith(APPLY_TO_IDEA_GLOBALLY_TASK_NAME) }
        }
    }

    @Test
    fun `should show all ktlint tasks in task output`() {
        build("tasks", "--all").apply {
            val ktlintTasks = output
                    .lineSequence()
                    .filter { it.startsWith("ktlint") }
                    .toList()

            // Plus for main and test sources format and check tasks
            assertThat(ktlintTasks).hasSize(8)
            assertThat(ktlintTasks).anyMatch { it.startsWith(CHECK_PARENT_TASK_NAME) }
            assertThat(ktlintTasks).anyMatch { it.startsWith(FORMAT_PARENT_TASK_NAME) }
            assertThat(ktlintTasks).anyMatch { it.startsWith(APPLY_TO_IDEA_TASK_NAME) }
            assertThat(ktlintTasks).anyMatch { it.startsWith(APPLY_TO_IDEA_GLOBALLY_TASK_NAME) }
        }
    }

    @Test
    fun `Should ignore excluded sources`() {
        projectRoot.withCleanSources()
        projectRoot.withFailingSources()

        projectRoot.buildFile().appendText("""

            ktlint.filter { exclude("**/fail-source.kt") }
        """.trimIndent())

        build(":ktlintCheck").apply {
            assertThat(task(":ktlintMainSourceSetCheck")!!.outcome).isEqualTo(TaskOutcome.SUCCESS)
        }
    }

    @Test
    fun `Should fail on additional source set directories files style violation`() {
        projectRoot.withCleanSources()
        val alternativeDirectory = "src/main/shared"
        projectRoot.withAlternativeFailingSources(alternativeDirectory)

        projectRoot.buildFile().appendText("""

            sourceSets {
                findByName("main")?.java?.srcDirs(project.file("$alternativeDirectory"))
            }
        """.trimIndent())

        buildAndFail(":ktlintCheck").apply {
            assertThat(task(":ktlintMainSourceSetCheck")!!.outcome).isEqualTo(TaskOutcome.FAILED)
        }
    }

    @Test
    fun `Should always format again restored to pre-format state sources`() {
        projectRoot.withFailingSources()
        build(":ktlintFormat").apply {
            assertThat(task(":ktlintMainSourceSetFormat")!!.outcome).isEqualTo(TaskOutcome.SUCCESS)
        }

        projectRoot.restoreFailingSources()

        build(":ktlintFormat").apply {
            assertThat(task(":ktlintMainSourceSetFormat")!!.outcome).isEqualTo(TaskOutcome.SUCCESS)
        }
    }

    @Test
    fun `Format task should be up-to-date on 3rd run`() {
        projectRoot.withFailingSources()

        build(":ktlintFormat").apply {
            assertThat(task(":ktlintMainSourceSetFormat")!!.outcome).isEqualTo(TaskOutcome.SUCCESS)
        }
        build(":ktlintFormat").apply {
            assertThat(task(":ktlintMainSourceSetFormat")!!.outcome).isEqualTo(TaskOutcome.SUCCESS)
        }
        build(":ktlintFormat").apply {
            assertThat(task(":ktlintMainSourceSetFormat")!!.outcome).isEqualTo(TaskOutcome.UP_TO_DATE)
        }
    }

    @Test
    fun `Should apply ktlint version from extension`() {
        projectRoot.withCleanSources()

        projectRoot.buildFile().appendText("""

            ktlint.version = "0.26.0"
        """.trimIndent())

        build(":dependencies").apply {
            assertThat(output).contains("ktlint\n" +
                "\\--- com.github.shyiko:ktlint:0.26.0\n")
        }
    }
}
