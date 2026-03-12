package sqltojooq.generator

import sqltojooq.parser.ColumnDefinition
import sqltojooq.parser.TableDefinition

object PojoGenerator {

    fun generate(
        table: TableDefinition,
        packageName: String,
        jsonbMappings: Map<String, String> = emptyMap(),
        enumFields: Map<String, String> = emptyMap()
    ): String {
        val className = NameUtils.toPascalCase(table.name)

        val sb = StringBuilder()
        sb.appendLine("package $packageName")
        sb.appendLine()

        val imports = collectImports(table, jsonbMappings, enumFields)
        imports.sorted().forEach { sb.appendLine("import $it") }
        if (imports.isNotEmpty()) sb.appendLine()

        sb.appendLine("data class $className(")

        table.columns.forEachIndexed { index, column ->
            val isLast = index == table.columns.size - 1
            val jsonbType = resolveJsonbType(table.name, column, jsonbMappings)
            val enumType = resolveEnumType(table.name, column, enumFields)
            sb.append(generateProperty(column, isLast, jsonbType, enumType))
        }

        sb.appendLine(")")

        return sb.toString()
    }

    private fun resolveJsonbType(
        tableName: String,
        column: ColumnDefinition,
        jsonbMappings: Map<String, String>
    ): String? {
        if (column.sqlType.lowercase() !in listOf("jsonb", "json")) return null
        return jsonbMappings["$tableName.${column.name}"]
    }

    private fun resolveEnumType(
        tableName: String,
        column: ColumnDefinition,
        enumFields: Map<String, String>
    ): String? = enumFields["$tableName.${column.name}"]

    private fun generateProperty(
        column: ColumnDefinition,
        isLast: Boolean,
        jsonbType: String?,
        enumType: String?
    ): String {
        val propName = NameUtils.toCamelCase(column.name)
        val mapped = TypeMapper.map(column.sqlType)

        val kotlinType = enumType ?: jsonbType ?: mapped.kotlinType
        val shortType = kotlinType.substringAfterLast(".")
        val type = if (column.nullable) "$shortType?" else shortType

        val hasDefault = column.nullable ||
            column.isPrimaryKey ||
            mapped.kotlinType == "java.time.OffsetDateTime" ||
            mapped.kotlinType == "java.time.LocalDateTime"
        val defaultPart = when {
            column.nullable -> " = null"
            enumType != null -> ""
            jsonbType != null -> ""
            hasDefault -> " = ${shortDefault(mapped)}"
            else -> ""
        }
        val comma = if (isLast) "" else ","

        return "    var $propName: $type$defaultPart$comma\n"
    }

    private fun shortDefault(mapped: TypeMapper.MappedType): String =
        mapped.defaultValue
            .replace("java.util.UUID", "UUID")
            .replace("java.math.BigDecimal", "BigDecimal")
            .replace("java.time.LocalDate", "LocalDate")
            .replace("java.time.LocalDateTime", "LocalDateTime")
            .replace("java.time.OffsetDateTime", "OffsetDateTime")

    private fun collectImports(
        table: TableDefinition,
        jsonbMappings: Map<String, String>,
        enumFields: Map<String, String>
    ): Set<String> {
        val imports = mutableSetOf<String>()
        table.columns.forEach { column ->
            val enumType = resolveEnumType(table.name, column, enumFields)
            val jsonbType = resolveJsonbType(table.name, column, jsonbMappings)
            val kotlinType = enumType ?: jsonbType ?: TypeMapper.map(column.sqlType).kotlinType
            if (kotlinType.contains(".")) {
                imports.add(kotlinType)
            }
        }
        return imports
    }
}
