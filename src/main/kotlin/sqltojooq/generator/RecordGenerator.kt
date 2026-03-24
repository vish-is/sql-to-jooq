package sqltojooq.generator

import sqltojooq.parser.ColumnDefinition
import sqltojooq.parser.TableDefinition

object RecordGenerator {

    private val PRIMITIVE_TYPES = setOf("Int", "Long", "Short", "Boolean", "Float", "Double")

    /**
     * Generates a data class record (e.g. TariffRecord) for the given table.
     * @param fkValueClassMap map of "table.column" → value class name for FK columns
     */
    fun generate(
        table: TableDefinition,
        packageName: String,
        jsonbMappings: Map<String, String> = emptyMap(),
        enumFields: Map<String, String> = emptyMap(),
        lateinitFields: Set<String> = emptySet(),
        generateValueClassIds: Boolean = false,
        fkValueClassMap: Map<String, String> = emptyMap(),
        immutableFields: Boolean = true
    ): String {
        val className = NameUtils.toPascalCase(table.name) + "Record"

        val valueClassName = if (generateValueClassIds) ValueClassGenerator.valueClassName(table) else null

        val constructorColumns = table.columns.filter { "${table.name}.${it.name}" !in lateinitFields }
        val bodyColumns = table.columns.filter { "${table.name}.${it.name}" in lateinitFields }

        require(constructorColumns.isNotEmpty()) {
            "Table '${table.name}': all columns are listed in lateinitFields. " +
                "A data class requires at least one constructor parameter."
        }

        val sb = StringBuilder()
        sb.appendLine("package $packageName")
        sb.appendLine()

        val imports = collectImports(table, jsonbMappings, enumFields)
        imports.sorted().forEach { sb.appendLine("import $it") }
        if (imports.isNotEmpty()) sb.appendLine()

        sb.appendLine("data class $className(")

        constructorColumns.forEachIndexed { index, column ->
            val isLast = index == constructorColumns.size - 1
            val jsonbType = resolveJsonbType(table.name, column, jsonbMappings)
            val enumType = resolveEnumType(table.name, column, enumFields)
            val fkValueClass = fkValueClassMap["${table.name}.${column.name}"]
            sb.append(generateConstructorProperty(column, isLast, jsonbType, enumType, valueClassName, fkValueClass, immutableFields))
        }

        if (bodyColumns.isEmpty()) {
            sb.appendLine(")")
        } else {
            sb.appendLine(") {")
            bodyColumns.forEach { column ->
                val jsonbType = resolveJsonbType(table.name, column, jsonbMappings)
                val enumType = resolveEnumType(table.name, column, enumFields)
                sb.append(generateLateinitProperty(column, jsonbType, enumType))
            }
            sb.appendLine("}")
        }

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

    private fun generateConstructorProperty(
        column: ColumnDefinition,
        isLast: Boolean,
        jsonbType: String?,
        enumType: String?,
        valueClassName: String? = null,
        fkValueClass: String? = null,
        immutableFields: Boolean = true
    ): String {
        val propName = NameUtils.toCamelCase(column.name)
        val mapped = TypeMapper.map(column.sqlType)

        val useValueClass = valueClassName != null && column.isPrimaryKey
        val useFkValueClass = fkValueClass != null && !column.isPrimaryKey
        val isAutoIncrementPk = useValueClass && TypeMapper.isAutoIncrement(column.sqlType)
        val kotlinType = when {
            useValueClass -> valueClassName!!
            useFkValueClass -> fkValueClass!!
            enumType != null -> enumType
            jsonbType != null -> jsonbType
            else -> mapped.kotlinType
        }
        val shortType = kotlinType.substringAfterLast(".")
        // Auto-increment PK with value class → nullable (DB assigns the value)
        val isNullable = column.nullable || isAutoIncrementPk
        val type = if (isNullable) "$shortType?" else shortType

        val hasDefault = isNullable ||
            column.isPrimaryKey ||
            mapped.kotlinType == "java.time.OffsetDateTime" ||
            mapped.kotlinType == "java.time.LocalDateTime"
        val defaultPart = when {
            isNullable -> " = null"
            useValueClass -> " = $shortType(${shortDefault(mapped)})"
            useFkValueClass -> ""
            enumType != null -> ""
            jsonbType != null -> ""
            hasDefault -> " = ${shortDefault(mapped)}"
            else -> ""
        }
        val comma = if (isLast) "" else ","

        val keyword = if (immutableFields) "val" else "var"
        return "    $keyword $propName: $type$defaultPart$comma\n"
    }

    private fun generateLateinitProperty(
        column: ColumnDefinition,
        jsonbType: String?,
        enumType: String?
    ): String {
        val propName = NameUtils.toCamelCase(column.name)
        val mapped = TypeMapper.map(column.sqlType)
        val kotlinType = enumType ?: jsonbType ?: mapped.kotlinType
        val shortType = kotlinType.substringAfterLast(".")

        val isNullable = column.nullable
        val type = if (isNullable) "$shortType?" else shortType
        val default = if (isNullable) "null" else shortDefault(mapped)
        return "    var $propName: $type = $default\n"
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
        // Value class is in the same record package — no import needed
        return imports
    }
}
