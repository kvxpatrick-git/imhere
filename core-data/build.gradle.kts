plugins {
    kotlin("jvm") version "1.9.24"
}

kotlin {
    jvmToolchain(17)
}

dependencies {
    implementation(project(":core-domain"))
    implementation(project(":core-model"))
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.8.1")
    testImplementation(kotlin("test"))
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.8.1")
}

val roomSchemaDir = layout.projectDirectory.dir("schemas")

// This task is wired as the CI entrypoint until Android Room plugin integration lands.
tasks.register("exportRoomSchema") {
    group = "verification"
    description = "Exports Room-like schema json artifacts to core-data/schemas"

    outputs.dir(roomSchemaDir)

    doLast {
        val outDir = roomSchemaDir.asFile.resolve("com.imhere.core.data.AppDatabase")
        outDir.mkdirs()

        val schemaFile = outDir.resolve("1.json")
        val schema = """
            {
              "formatVersion": 1,
              "database": {
                "version": 1,
                "identityHash": "imhere_v1",
                "entities": [
                  {
                    "tableName": "keyword_entries",
                    "createSql": "CREATE TABLE IF NOT EXISTS `keyword_entries` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `phrase` TEXT NOT NULL, `normalized_phrase` TEXT NOT NULL, `created_at_epoch_ms` INTEGER NOT NULL, `updated_at_epoch_ms` INTEGER NOT NULL)"
                  },
                  {
                    "tableName": "find_sessions",
                    "createSql": "CREATE TABLE IF NOT EXISTS `find_sessions` (`id` TEXT NOT NULL, `triggered_keyword` TEXT NOT NULL, `trigger_confidence` REAL, `started_at_epoch_ms` INTEGER NOT NULL, `ended_at_epoch_ms` INTEGER, `stop_reason` TEXT, `stage_reached` INTEGER NOT NULL, `force_playback_applied` INTEGER NOT NULL, `battery_saver_active` INTEGER NOT NULL, `detection_latency_ms` INTEGER, `stop_latency_ms` INTEGER, `created_at_epoch_ms` INTEGER NOT NULL, PRIMARY KEY(`id`))"
                  }
                ]
              }
            }
        """.trimIndent()

        schemaFile.writeText(schema)
        logger.lifecycle("Exported schema: ${schemaFile.path}")
    }
}
