package sqltojooq.generator

import sqltojooq.parser.TableDefinition

object TablesGenerator {

    fun generate(tables: List<TableDefinition>, packageName: String): String {
        val recordPackage = "$packageName.record"

        val sb = StringBuilder()
        sb.appendLine("package $packageName")
        sb.appendLine()

        tables.forEach { table ->
            val className = NameUtils.toPascalCase(table.name) + "Record"
            sb.appendLine("import $recordPackage.$className")
        }

        sb.appendLine()
        sb.appendLine("object Tables {")

        tables.forEach { table ->
            val className = NameUtils.toPascalCase(table.name) + "Record"
            val instanceName = NameUtils.toUpperSnakeCase(table.name)
            sb.appendLine("    val $instanceName = $className.$instanceName")
        }

        sb.appendLine("}")

        return sb.toString()
    }
}
