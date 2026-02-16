// Template: Room Migration Test
// Place under core-data/src/test/.../MigrationTest.kt and adapt package/entity names.

import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Rule
import org.junit.Test

class MigrationTestTemplate {
    private val dbName = "migration-test"

    @get:Rule
    val helper: MigrationTestHelper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        "your.package.AppDatabase",
        FrameworkSQLiteOpenHelperFactory()
    )

    @Test
    fun migrate1To2_preservesData() {
        helper.createDatabase(dbName, 1).apply {
            // Seed v1 rows
            close()
        }

        helper.runMigrationsAndValidate(dbName, 2, true, MIGRATION_1_2)
    }
}
