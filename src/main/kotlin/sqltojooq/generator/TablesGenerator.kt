package sqltojooq.generator

import sqltojooq.parser.TableDefinition

object TablesGenerator {

    fun generate(tables: List<TableDefinition>, packageName: String): String {
        val tablePackage = "$packageName.table"

        val sb = StringBuilder()
        sb.appendLine("package $packageName")
        sb.appendLine()

        tables.forEach { table ->
            val className = NameUtils.toPascalCase(table.name) + "Table"
            sb.appendLine("import $tablePackage.$className")
        }

        sb.appendLine()
        sb.appendLine("object Tables {")

        tables.forEach { table ->
            val className = NameUtils.toPascalCase(table.name) + "Table"
            val instanceName = NameUtils.toUpperSnakeCase(table.name)
            sb.appendLine("    val $instanceName = $className.$instanceName")
        }

        sb.appendLine("}")

        return sb.toString()
    }
}
