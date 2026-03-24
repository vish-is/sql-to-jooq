package sqltojooq.generator

import sqltojooq.parser.TableDefinition

object ValueClassGenerator {

    /**
     * Returns the value class name for the given table, or null if the table
     * is not eligible (no PK or composite PK).
     */
    fun valueClassName(table: TableDefinition): String? {
        val pkColumns = table.columns.filter { it.isPrimaryKey }
        if (pkColumns.size != 1) return null
        return NameUtils.toPascalCase(table.name) + "Id"
    }

    /**
     * Returns the underlying Kotlin type of the PK column (e.g. "Long", "UUID", "String"),
     * or null if not eligible.
     */
    fun pkKotlinType(table: TableDefinition): String? {
        val pkColumns = table.columns.filter { it.isPrimaryKey }
        if (pkColumns.size != 1) return null
        return TypeMapper.map(pkColumns.first().sqlType).kotlinType
    }

    /**
     * Builds a map of "table.column" → value class name for all FK columns
     * that reference a table with a single-column PK (eligible for value class).
     *
     * Example: if column `order.tariff_id` references table `tariff`,
     * and `tariff` has a single PK, the map will contain "order.tariff_id" → "TariffId".
     */
    fun buildFkValueClassMap(tables: List<TableDefinition>): Map<String, String> {
        val tableByName = tables.associateBy { it.name }
        val result = mutableMapOf<String, String>()

        tables.forEach { table ->
            table.columns.forEach columnLoop@{ column ->
                val refTableName = column.referencedTable ?: return@columnLoop
                val refTable = tableByName[refTableName] ?: return@columnLoop
                val vcName = valueClassName(refTable) ?: return@columnLoop
                result["${table.name}.${column.name}"] = vcName
            }
        }

        return result
    }

    /**
     * Generates a `@JvmInline value class {Table}Id(val value: {PkType})` file.
     * The value class lives in the record package, dependency-free.
     */
    fun generate(table: TableDefinition, packageName: String): String? {
        val className = valueClassName(table) ?: return null
        val fullKotlinType = pkKotlinType(table) ?: return null
        val shortType = fullKotlinType.substringAfterLast(".")

        return buildString {
            appendLine("package $packageName")
            if (fullKotlinType.contains(".")) {
                appendLine()
                appendLine("import $fullKotlinType")
            }
            appendLine()
            appendLine("@JvmInline")
            appendLine("value class $className(val value: $shortType)")
        }
    }
}
