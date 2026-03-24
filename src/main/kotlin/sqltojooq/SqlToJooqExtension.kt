package sqltojooq

import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.MapProperty
import org.gradle.api.provider.Property
import org.gradle.api.provider.SetProperty

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

    /** Set of table names to exclude from generation. */
    abstract val excludeTables: SetProperty<String>

    /** Set of "table.column" entries that become `lateinit var` in the Record body instead of constructor params. */
    abstract val lateinitFields: SetProperty<String>

    /**
     * When true, generates `@JvmInline value class {Table}Id(val value: {PkType})`
     * for every table with a single-column primary key.
     * The value class is used as the PK type in Record, Table (with converter) and Repository.
     * Composite primary keys are skipped. Requires jOOQ 3.18+.
     */
    abstract val generateValueClassIds: Property<Boolean>

    /**
     * When true (default), Record constructor properties are generated as `val` (immutable).
     * When false, they are generated as `var` (mutable).
     * Note: `lateinit` fields are always `var` regardless of this setting.
     */
    abstract val immutableRecordFields: Property<Boolean>
}
