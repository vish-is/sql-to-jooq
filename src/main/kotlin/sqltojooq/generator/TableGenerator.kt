package sqltojooq.generator

import sqltojooq.parser.ColumnDefinition
import sqltojooq.parser.TableDefinition

object TableGenerator {

    /**
     * Generates a jOOQ TableImpl subclass (e.g. TariffTable) for the given table.
     * @param fkValueClassMap map of "table.column" → value class name for FK columns
     */
    fun generate(
        table: TableDefinition,
        packageName: String,
        jsonbMappings: Map<String, String> = emptyMap(),
        enumFields: Map<String, String> = emptyMap(),
        generateValueClassIds: Boolean = false,
        fkValueClassMap: Map<String, String> = emptyMap()
    ): String {
        val className = NameUtils.toPascalCase(table.name) + "Table"
        val instanceName = NameUtils.toUpperSnakeCase(table.name)

        val valueClassName = if (generateValueClassIds) ValueClassGenerator.valueClassName(table) else null
        val hasValueClassConverter = valueClassName != null ||
            table.columns.any { fkValueClassMap.containsKey("${table.name}.${it.name}") }

        val hasEnums = table.columns.any { resolveEnumType(table.name, it, enumFields) != null }
        val hasJsonbConverters = table.columns.any { resolveJsonbType(table.name, it, jsonbMappings) != null }

        val sb = StringBuilder()
        sb.appendLine("package $packageName")
        sb.appendLine()
        if (hasValueClassConverter) {
            sb.appendLine("import org.jooq.Converter")
        }
        sb.appendLine("import org.jooq.Name")
        sb.appendLine("import org.jooq.Record")
        sb.appendLine("import org.jooq.Table")
        sb.appendLine("import org.jooq.UniqueKey")
        sb.appendLine("import org.jooq.impl.DSL")
        sb.appendLine("import org.jooq.impl.Internal")
        sb.appendLine("import org.jooq.impl.SQLDataType")
        sb.appendLine("import org.jooq.impl.TableImpl")
        if (hasEnums) {
            sb.appendLine("import org.jooq.impl.EnumConverter")
        }
        if (hasJsonbConverters) {
            sb.appendLine("import com.fasterxml.jackson.core.type.TypeReference")
            val rootPackage = packageName.removeSuffix(".table")
            sb.appendLine("import $rootPackage.JsonbConverter")
        }

        // Collect all value class imports (PK + FK) — value classes live in .record package
        val recordPackage = packageName.removeSuffix(".table") + ".record"
        val valueClassImports = mutableSetOf<String>()
        if (valueClassName != null) {
            valueClassImports.add("$recordPackage.$valueClassName")
        }
        table.columns.forEach { column ->
            fkValueClassMap["${table.name}.${column.name}"]?.let { vcName ->
                valueClassImports.add("$recordPackage.$vcName")
            }
        }
        valueClassImports.sorted().forEach { sb.appendLine("import $it") }

        val enumImports = table.columns
            .mapNotNull { resolveEnumType(table.name, it, enumFields) }
            .toSet()
            .sorted()
        enumImports.forEach { sb.appendLine("import $it") }

        val jsonbImports = table.columns
            .mapNotNull { resolveJsonbType(table.name, it, jsonbMappings) }
            .flatMap { extractImportsFromType(it) }
            .toSet()
            .sorted()
        jsonbImports.forEach { sb.appendLine("import $it") }

        sb.appendLine()
        sb.appendLine("class $className private constructor(alias: Name?, aliased: Table<Record>?) : TableImpl<Record>(alias ?: DSL.name(\"${table.name}\"), null, null, aliased) {")
        sb.appendLine()
        sb.appendLine("    constructor() : this(null, null)")
        sb.appendLine()
        sb.appendLine("    companion object {")
        sb.appendLine("        val $instanceName = $className()")
        sb.appendLine("    }")
        sb.appendLine()
        sb.appendLine("    override fun `as`(alias: String): $className = $className(DSL.name(alias), this)")
        sb.appendLine("    override fun `as`(alias: Name): $className = $className(alias, this)")
        sb.appendLine("    override fun rename(name: String): $className = $className(DSL.name(name), null)")
        sb.appendLine("    override fun rename(name: Name): $className = $className(name, null)")

        table.columns.forEach { column ->
            sb.appendLine()
            val enumType = resolveEnumType(table.name, column, enumFields)
            val jsonbType = resolveJsonbType(table.name, column, jsonbMappings)
            val fkValueClass = fkValueClassMap["${table.name}.${column.name}"]
            sb.append(generateField(column, enumType, jsonbType, valueClassName, fkValueClass))
        }

        val pkColumns = table.columns.filter { it.isPrimaryKey }
        if (pkColumns.isNotEmpty()) {
            val pkFieldRefs = pkColumns.joinToString(", ") { NameUtils.toUpperSnakeCase(it.name) }
            sb.appendLine()
            sb.appendLine("    private val _pk: UniqueKey<Record> = Internal.createUniqueKey(this, $pkFieldRefs)")
            sb.appendLine()
            sb.appendLine("    override fun getPrimaryKey(): UniqueKey<Record> = _pk")
            sb.appendLine("    override fun getKeys(): List<UniqueKey<Record>> = listOf(_pk)")
        }

        sb.appendLine("}")

        return sb.toString()
    }

    /**
     * Generates the JsonbConverter utility class in the root generated package.
     * Uses Jackson with KotlinModule for JSON ↔ Kotlin data class conversion.
     */
    fun generateJsonbConverter(packageName: String): String {
        return buildString {
            appendLine("package $packageName")
            appendLine()
            appendLine("import com.fasterxml.jackson.core.type.TypeReference")
            appendLine("import com.fasterxml.jackson.databind.ObjectMapper")
            appendLine("import com.fasterxml.jackson.module.kotlin.KotlinModule")
            appendLine("import org.jooq.Converter")
            appendLine("import org.jooq.JSONB")
            appendLine()
            appendLine("/**")
            appendLine(" * GENERATED — do not edit. Regenerated by the sql-to-jooq plugin on each run.")
            appendLine(" *")
            appendLine(" * jOOQ converter for JSONB columns mapped to Kotlin types via [jsonbMappings].")
            appendLine(" * Supports simple types (e.g. `MyDto`) and generic types (e.g. `List<MyDto>`).")
            appendLine(" *")
            appendLine(" * Uses Jackson with [KotlinModule] for serialization/deserialization.")
            appendLine(" */")
            appendLine("class JsonbConverter<T>(")
            appendLine("    private val typeRef: TypeReference<T>")
            appendLine(") : Converter<JSONB, T> {")
            appendLine()
            appendLine("    override fun from(databaseObject: JSONB?): T? =")
            appendLine("        databaseObject?.data()?.let { MAPPER.readValue(it, typeRef) }")
            appendLine()
            appendLine("    override fun to(userObject: T?): JSONB? =")
            appendLine("        userObject?.let { JSONB.valueOf(MAPPER.writeValueAsString(it)) }")
            appendLine()
            appendLine("    override fun fromType(): Class<JSONB> = JSONB::class.java")
            appendLine()
            appendLine("    @Suppress(\"UNCHECKED_CAST\")")
            appendLine("    override fun toType(): Class<T> =")
            appendLine("        MAPPER.typeFactory.constructType(typeRef.type).rawClass as Class<T>")
            appendLine()
            appendLine("    companion object {")
            appendLine("        val MAPPER: ObjectMapper = ObjectMapper().apply {")
            appendLine("            registerModule(KotlinModule.Builder().build())")
            appendLine("        }")
            appendLine("    }")
            appendLine("}")
        }
    }

    /** Returns the Java class expression for primitives that need boxed types in Converter. */
    private fun javaTypeExpression(kotlinType: String): String = when (kotlinType) {
        "Long" -> "Long::class.javaObjectType"
        "Int" -> "Int::class.javaObjectType"
        "Short" -> "Short::class.javaObjectType"
        else -> "$kotlinType::class.java"
    }

    private fun resolveEnumType(
        tableName: String,
        column: ColumnDefinition,
        enumFields: Map<String, String>
    ): String? = enumFields["$tableName.${column.name}"]

    private fun resolveJsonbType(
        tableName: String,
        column: ColumnDefinition,
        jsonbMappings: Map<String, String>
    ): String? {
        if (column.sqlType.lowercase() !in listOf("jsonb", "json")) return null
        return jsonbMappings["$tableName.${column.name}"]
    }

    /**
     * Extracts fully qualified class names from a type expression for imports.
     */
    private fun extractImportsFromType(type: String): List<String> {
        return Regex("""[\w.]+""")
            .findAll(type)
            .map { it.value }
            .filter { it.contains(".") }
            .toList()
    }

    private fun generateField(column: ColumnDefinition, enumType: String?, jsonbType: String?, valueClassName: String? = null, fkValueClass: String? = null): String {
        val fieldName = NameUtils.toUpperSnakeCase(column.name)
        val mapped = TypeMapper.map(column.sqlType)
        val nullability = if (column.nullable) "" else ".notNull()"
        val shortDataType = mapped.jooqDataType.removePrefix("org.jooq.impl.")

        val useValueClass = valueClassName != null && column.isPrimaryKey
        val useFkValueClass = fkValueClass != null && !column.isPrimaryKey

        return when {
            useValueClass -> {
                val pkKotlinType = mapped.kotlinType.substringAfterLast(".")
                val javaType = javaTypeExpression(pkKotlinType)
                "    val $fieldName = createField(DSL.name(\"${column.name}\"), $shortDataType$nullability, this, \"\", Converter.ofNullable($javaType, $valueClassName::class.java, ::$valueClassName) { it.value })\n"
            }
            useFkValueClass -> {
                val fkKotlinType = mapped.kotlinType.substringAfterLast(".")
                val javaType = javaTypeExpression(fkKotlinType)
                "    val $fieldName = createField(DSL.name(\"${column.name}\"), $shortDataType$nullability, this, \"\", Converter.ofNullable($javaType, $fkValueClass::class.java, ::$fkValueClass) { it.value })\n"
            }
            enumType != null -> {
                val shortEnum = enumType.substringAfterLast(".")
                "    val $fieldName = createField(DSL.name(\"${column.name}\"), $shortDataType$nullability, this, \"\", EnumConverter(String::class.java, $shortEnum::class.java))\n"
            }
            jsonbType != null -> {
                val shortType = jsonbType.replace(Regex("""[\w.]+\.(\w+)"""), "$1")
                "    val $fieldName = createField(DSL.name(\"${column.name}\"), $shortDataType$nullability, this, \"\", JsonbConverter(object : TypeReference<$shortType>() {}))\n"
            }
            else -> {
                "    val $fieldName = createField(DSL.name(\"${column.name}\"), $shortDataType$nullability)\n"
            }
        }
    }
}
