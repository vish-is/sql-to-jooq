package sqltojooq

import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.tasks.SourceSetContainer

class SqlToJooqPlugin : Plugin<Project> {

    override fun apply(project: Project) {
        val extension = project.extensions.create("sqlToJooq", SqlToJooqExtension::class.java)
        extension.migrationTool.convention(MigrationTool.LIQUIBASE)

        val generatedDir = project.layout.buildDirectory.dir("generated/sources/jooq/main/kotlin")

        val generateTask = project.tasks.register("generateJooq", SqlToJooqTask::class.java) { task ->
            task.group = "generation"
            task.description = "Generate jOOQ Record, Pojo, Tables and Repository classes from SQL migrations"
            task.migrationTool.set(extension.migrationTool)
            task.changelogFile.set(extension.changelogFile)
            task.migrationsDir.set(extension.migrationsDir)
            task.stubDir.set(extension.outputDir)
            task.generatedDir.set(generatedDir)
            task.outputPackage.set(extension.outputPackage)
            task.jsonbMappings.set(extension.jsonbMappings)
            task.enumFields.set(extension.enumFields)
            task.excludeTables.set(extension.excludeTables)
        }

        project.pluginManager.withPlugin("org.jetbrains.kotlin.jvm") {
            project.extensions.getByType(SourceSetContainer::class.java)
                .named("main") { it.java.srcDir(generatedDir) }

            project.tasks.named("compileKotlin") { it.dependsOn(generateTask) }
        }
    }
}
