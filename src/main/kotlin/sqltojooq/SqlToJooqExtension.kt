package sqltojooq

import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.MapProperty
import org.gradle.api.provider.Property

abstract class SqlToJooqExtension {

    /** Migration tool: [MigrationTool.LIQUIBASE] (default) or [MigrationTool.FLYWAY]. */
    abstract val migrationTool: Property<MigrationTool>

    /** Liquibase YAML changelog file. Required when migrationTool = LIQUIBASE. */
    abstract val changelogFile: RegularFileProperty

    /** Flyway migrations directory. Required when migrationTool = FLYWAY. */
    abstract val migrationsDir: DirectoryProperty

    /** Source root for hand-written repository stubs (e.g. src/main/kotlin). */
    abstract val outputDir: DirectoryProperty

    abstract val outputPackage: Property<String>

    /** Map of "table.column" to fully qualified class name for jsonb fields. */
    abstract val jsonbMappings: MapProperty<String, String>

    /** Map of "table.column" to fully qualified enum class name. Enum stored as varchar/text in DB. */
    abstract val enumFields: MapProperty<String, String>

    /** List of table names to exclude from generation. */
    abstract val excludeTables: ListProperty<String>
}
