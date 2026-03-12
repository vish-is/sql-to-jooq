package sqltojooq

import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.MapProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputDirectory
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.Optional
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.TaskAction
import sqltojooq.generator.NameUtils
import sqltojooq.generator.PojoGenerator
import sqltojooq.generator.RecordGenerator
import sqltojooq.generator.RepositoryGenerator
import sqltojooq.generator.TablesGenerator
import sqltojooq.parser.ChangelogParser
import sqltojooq.parser.FlywayMigrationScanner
import sqltojooq.parser.SqlParser
import java.io.File

abstract class SqlToJooqTask : DefaultTask() {

    @get:Input
    @get:Optional
    abstract val migrationTool: Property<MigrationTool>

    @get:InputFile
    @get:Optional
    abstract val changelogFile: RegularFileProperty

    @get:InputDirectory
    @get:Optional
    abstract val migrationsDir: DirectoryProperty

    /** Directory for always-regenerated sources (build/generated/sources/jooq/main/kotlin). */
    @get:OutputDirectory
    abstract val generatedDir: DirectoryProperty

    /** Directory for hand-written stubs that are never overwritten (src/main/kotlin). */
    @get:OutputDirectory
    abstract val stubDir: DirectoryProperty

    @get:Input
    abstract val outputPackage: Property<String>

    @get:Input
    @get:Optional
    abstract val jsonbMappings: MapProperty<String, String>

    @get:Input
    @get:Optional
    abstract val enumFields: MapProperty<String, String>

    @get:Input
    @get:Optional
    abstract val excludeTables: ListProperty<String>

    @TaskAction
    fun generate() {
        val tool = migrationTool.getOrElse(MigrationTool.LIQUIBASE)
        val sqlFiles = when (tool) {
            MigrationTool.LIQUIBASE -> {
                if (!changelogFile.isPresent) {
                    throw GradleException("changelogFile is required when migrationTool = LIQUIBASE")
                }
                ChangelogParser.parseSqlFiles(changelogFile.get().asFile)
            }
            MigrationTool.FLYWAY -> {
                if (!migrationsDir.isPresent) {
                    throw GradleException("migrationsDir is required when migrationTool = FLYWAY")
                }
                FlywayMigrationScanner.scanSqlFiles(migrationsDir.get().asFile)
            }
        }

        if (sqlFiles.isEmpty()) {
            logger.warn("No SQL migration files found")
            return
        }

        logger.lifecycle("Parsing ${sqlFiles.size} SQL migration files ($tool)...")
        sqlFiles.forEach { logger.lifecycle("  - ${it.name}") }

        val excluded = excludeTables.getOrElse(emptyList()).toSet()
        val tables = SqlParser().parse(sqlFiles).filter { it.name !in excluded }
        logger.lifecycle("Found ${tables.size} tables: ${tables.joinToString { it.name }}")

        val mappings = jsonbMappings.getOrElse(emptyMap())
        val enums = enumFields.getOrElse(emptyMap())

        val overlap = mappings.keys.intersect(enums.keys)
        if (overlap.isNotEmpty()) {
            throw GradleException(
                "Column(s) ${overlap.joinToString()} found in both jsonbMappings and enumFields. Each column must be in only one mapping."
            )
        }

        val pkg = outputPackage.get()
        val genBase = generatedDir.get().asFile
        val stubBase = stubDir.get().asFile

        val genPackageDir = File(genBase, pkg.replace(".", "/"))
        val stubPackageDir = File(stubBase, pkg.replace(".", "/"))

        // --- Always-regenerated output (build/generated/) ---
        if (genPackageDir.exists()) genPackageDir.deleteRecursively()

        val recordDir = File(genPackageDir, "record").also { it.mkdirs() }
        val pojoDir = File(genPackageDir, "pojo").also { it.mkdirs() }
        val repoDir = File(genPackageDir, "repository").also { it.mkdirs() }
        genPackageDir.mkdirs()

        // Records & Pojos
        tables.forEach { table ->
            val recordCode = RecordGenerator.generate(table, "$pkg.record", enums)
            val recordFile = File(recordDir, "${NameUtils.toPascalCase(table.name)}Record.kt")
            recordFile.writeText(recordCode)
            logger.lifecycle("Generated: ${recordFile.relativeTo(genBase)}")

            val pojoCode = PojoGenerator.generate(table, "$pkg.pojo", mappings, enums)
            val pojoFile = File(pojoDir, "${NameUtils.toPascalCase(table.name)}.kt")
            pojoFile.writeText(pojoCode)
            logger.lifecycle("Generated: ${pojoFile.relativeTo(genBase)}")
        }

        // Tables.kt — package root
        val tablesCode = TablesGenerator.generate(tables, pkg)
        val tablesFile = File(genPackageDir, "Tables.kt")
        tablesFile.writeText(tablesCode)
        logger.lifecycle("Generated: ${tablesFile.relativeTo(genBase)}")

        // Infrastructure classes — package root
        File(genPackageDir, "JooqRepository.kt")
            .also { it.writeText(RepositoryGenerator.generateInterface(pkg)) }
            .also { logger.lifecycle("Generated: ${it.relativeTo(genBase)}") }

        File(genPackageDir, "AbstractJooqRepository.kt")
            .also { it.writeText(RepositoryGenerator.generateAbstractBase(pkg)) }
            .also { logger.lifecycle("Generated: ${it.relativeTo(genBase)}") }

        File(genPackageDir, "PageFilter.kt")
            .also { it.writeText(RepositoryGenerator.generatePageFilter(pkg)) }
            .also { logger.lifecycle("Generated: ${it.relativeTo(genBase)}") }

        File(genPackageDir, "PageResult.kt")
            .also { it.writeText(RepositoryGenerator.generatePageResult(pkg)) }
            .also { logger.lifecycle("Generated: ${it.relativeTo(genBase)}") }

        File(genPackageDir, "AbstractPageQuery.kt")
            .also { it.writeText(RepositoryGenerator.generateAbstractPageQuery(pkg)) }
            .also { logger.lifecycle("Generated: ${it.relativeTo(genBase)}") }

        // Generated concrete repository classes — repository/ package
        tables.forEach { table ->
            val generatedCode = RepositoryGenerator.generateConcrete(table, pkg)
            val generatedFile = File(repoDir, "Generated${NameUtils.toPascalCase(table.name)}Repository.kt")
            generatedFile.writeText(generatedCode)
            logger.lifecycle("Generated: ${generatedFile.relativeTo(genBase)}")
        }

        // --- Hand-written stubs (src/main/kotlin) — written ONLY IF not exists ---
        val stubRepoDir = File(stubPackageDir, "repository")
        stubRepoDir.mkdirs()

        tables.forEach { table ->
            val stubFile = File(stubRepoDir, "${NameUtils.toPascalCase(table.name)}Repository.kt")
            if (!stubFile.exists()) {
                stubFile.writeText(RepositoryGenerator.generateHandWrittenStub(table, pkg))
                logger.lifecycle("Generated (stub): ${stubFile.relativeTo(stubBase)}")
            }
        }

        val generatedFiles = tables.size * 4 + 6
        logger.lifecycle("Code generation complete: ${tables.size} tables, $generatedFiles generated files")
    }
}
