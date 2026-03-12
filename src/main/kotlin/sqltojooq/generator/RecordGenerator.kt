package sqltojooq.generator

import sqltojooq.parser.ColumnDefinition
import sqltojooq.parser.TableDefinition

object RecordGenerator {

    fun generate(
        table: TableDefinition,
        packageName: String,
        enumFields: Map<String, String> = emptyMap()
    ): String {
        val className = NameUtils.toPascalCase(table.name) + "Record"
        val instanceName = NameUtils.toUpperSnakeCase(table.name)

        val hasEnums = table.columns.any { resolveEnumType(table.name, it, enumFields) != null }

        val sb = StringBuilder()
        sb.appendLine("package $packageName")
        sb.appendLine()
        sb.appendLine("import org.jooq.Record")
        sb.appendLine("import org.jooq.impl.DSL")
        sb.appendLine("import org.jooq.impl.SQLDataType")
        sb.appendLine("import org.jooq.impl.TableImpl")
        if (hasEnums) {
            sb.appendLine("import org.jooq.impl.EnumConverter")
        }

        val enumImports = table.columns
            .mapNotNull { resolveEnumType(table.name, it, enumFields) }
            .toSet()
            .sorted()
        enumImports.forEach { sb.appendLine("import $it") }

        sb.appendLine()
        sb.appendLine("class $className : TableImpl<Record>(DSL.name(\"${table.name}\")) {")
        sb.appendLine()
        sb.appendLine("    companion object {")
        sb.appendLine("        val $instanceName = $className()")
        sb.appendLine("    }")

        table.columns.forEach { column ->
            sb.appendLine()
            val enumType = resolveEnumType(table.name, column, enumFields)
            sb.append(generateField(column, enumType))
        }

        sb.appendLine("}")

        return sb.toString()
    }

    private fun resolveEnumType(
        tableName: String,
        column: ColumnDefinition,
        enumFields: Map<String, String>
    ): String? = enumFields["$tableName.${column.name}"]

    private fun generateField(column: ColumnDefinition, enumType: String?): String {
        val fieldName = NameUtils.toUpperSnakeCase(column.name)
        val mapped = TypeMapper.map(column.sqlType)
        val nullability = if (column.nullable) "" else ".notNull()"
        val shortDataType = mapped.jooqDataType.removePrefix("org.jooq.impl.")

        return if (enumType != null) {
            val shortEnum = enumType.substringAfterLast(".")
            "    val $fieldName = createField(DSL.name(\"${column.name}\"), $shortDataType$nullability, this, \"\", EnumConverter(String::class.java, $shortEnum::class.java))\n"
        } else {
            "    val $fieldName = createField(DSL.name(\"${column.name}\"), $shortDataType$nullability)\n"
        }
    }
}
