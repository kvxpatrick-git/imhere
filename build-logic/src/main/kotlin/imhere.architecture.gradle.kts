import org.gradle.api.DefaultTask
import org.gradle.api.tasks.TaskAction

abstract class CheckArchitectureTask : DefaultTask() {
    @TaskAction
    fun run() {
        val repoRoot = project.rootProject.projectDir
        val coreDomain = repoRoot.resolve("core-domain")
        val featureDirs = repoRoot.listFiles()?.filter { it.isDirectory && it.name.startsWith("feature-") } ?: emptyList()

        if (!coreDomain.exists()) {
            logger.lifecycle("No core-domain module found; architecture check skipped")
            return
        }

        val kotlinFiles = coreDomain.walkTopDown().filter { it.isFile && it.extension == "kt" }
        val forbidden = listOf("import android.", "import androidx.", "room", "datastore", "compose")
        val outerDeps = listOf("core.data", "platform.", "feature.", "app.")

        val violations = mutableListOf<String>()
        kotlinFiles.forEach { file ->
            val lines = file.readLines()
            lines.forEachIndexed { idx, line ->
                if (line.trimStart().startsWith("import ")) {
                    if (forbidden.any { token -> line.contains(token, ignoreCase = true) }) {
                        violations += "${file.path}:${idx + 1} forbidden framework/storage/ui import"
                    }
                    if (outerDeps.any { token -> line.contains(token, ignoreCase = true) }) {
                        violations += "${file.path}:${idx + 1} core-domain depends on outer layer"
                    }
                }
            }
        }

        featureDirs.forEach { dir ->
            dir.walkTopDown().filter { it.isFile && it.extension == "kt" }.forEach { file ->
                file.readLines().forEachIndexed { idx, line ->
                    if (line.trimStart().startsWith("import ") && line.contains("platform.", ignoreCase = true)) {
                        violations += "${file.path}:${idx + 1} feature module directly depends on platform"
                    }
                }
            }
        }

        if (violations.isNotEmpty()) {
            violations.forEach { logger.error(it) }
            error("Architecture rule violations: ${violations.size}")
        }

        logger.lifecycle("Architecture checks passed")
    }
}

abstract class CheckForbiddenImportsTask : DefaultTask() {
    @TaskAction
    fun run() {
        val repoRoot = project.rootProject.projectDir
        val patterns = listOf("import android.", "import androidx.")
        val srcRoots = listOf(
            repoRoot.resolve("core-domain/src"),
            repoRoot.resolve("core-data/src")
        ).filter { it.exists() }

        val violations = mutableListOf<String>()
        srcRoots.forEach { root ->
            root.walkTopDown().filter { it.isFile && it.extension == "kt" }.forEach { file ->
                file.readLines().forEachIndexed { idx, line ->
                    if (line.trimStart().startsWith("import ") && patterns.any { line.contains(it) }) {
                        violations += "${file.path}:${idx + 1} forbidden import"
                    }
                }
            }
        }

        if (violations.isNotEmpty()) {
            violations.forEach { logger.error(it) }
            error("Forbidden imports found: ${violations.size}")
        }

        logger.lifecycle("Forbidden import checks passed")
    }
}

tasks.register("checkArchitecture", CheckArchitectureTask::class.java)
tasks.register("checkForbiddenImports", CheckForbiddenImportsTask::class.java)
