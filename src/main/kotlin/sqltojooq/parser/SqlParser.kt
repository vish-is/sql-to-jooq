package sqltojooq.parser

import java.io.File

class SqlParser {

    private val multiWordTypes = listOf(
        "double precision",
        "character varying",
        "timestamp with time zone",
        "timestamp without time zone",
        "time with time zone"
    )

    fun parse(migrationFiles: List<File>): List<TableDefinition> {
        val tables = mutableMapOf<String, TableDefinition>()

        migrationFiles.forEach { file ->
            val sql = stripComments(file.readText()).lowercase()
            parseCreateTables(sql, tables)
            parseAlterTables(sql, tables)
        }

        return tables.values.toList()
    }

    private fun stripComments(sql: String): String =
        sql.lines()
            .joinToString("\n") { line ->
                // Don't strip -- inside single quotes
                var inString = false
                var commentStart = -1
                for (i in line.indices) {
                    when {
                        line[i] == '\'' -> inString = !inString
                        !inString && i + 1 < line.length && line[i] == '-' && line[i + 1] == '-' -> {
                            commentStart = i
                            break
                        }
                    }
                }
                if (commentStart >= 0) line.substring(0, commentStart) else line
            }

    private fun parseCreateTables(sql: String, tables: MutableMap<String, TableDefinition>) {
        val createTableRegex = Regex(
            """create\s+table\s+(?:if\s+not\s+exists\s+)?(?:\w+\.)?(\w+)\s*\((.*?)\);""",
            setOf(RegexOption.DOT_MATCHES_ALL)
        )

        createTableRegex.findAll(sql).forEach { match ->
            val tableName = match.groupValues[1]
            val body = match.groupValues[2]
            val table = TableDefinition(tableName)

            val primaryKeys = extractPrimaryKeys(body)
            val foreignKeys = extractForeignKeys(body)

            parseColumnLines(body).forEach { line ->
                parseColumn(line, primaryKeys)?.let { col ->
                    // Merge table-level FK info if inline REFERENCES was not present
                    val merged = if (col.referencedTable == null && foreignKeys.containsKey(col.name)) {
                        col.copy(referencedTable = foreignKeys[col.name])
                    } else col
                    table.columns.add(merged)
                }
            }

            tables[tableName] = table
        }
    }

    private fun parseAlterTables(sql: String, tables: MutableMap<String, TableDefinition>) {
        val alterTableRegex = Regex(
            """alter\s+table\s+(?:if\s+exists\s+)?(?:only\s+)?(?:\w+\.)?(\w+)\s+(.*?);""",
            setOf(RegexOption.DOT_MATCHES_ALL)
        )

        alterTableRegex.findAll(sql).forEach { match ->
            val tableName = match.groupValues[1]
            val body = match.groupValues[2].trim()
            val table = tables[tableName] ?: return@forEach

            val clauses = splitByTopLevelComma(body)
            clauses.forEach { clause ->
                processAlterClause(clause.trim(), table)
            }
        }
    }

    private val addColumnRegex = Regex(
        """add\s+(?:column\s+)?(?:if\s+not\s+exists\s+)?(\w+)\s+(.+)""",
        RegexOption.DOT_MATCHES_ALL
    )
    private val dropColumnRegex = Regex(
        """drop\s+column\s+(?:if\s+exists\s+)?(\w+)"""
    )
    private val renameColumnRegex = Regex(
        """rename\s+(?:column\s+)?(\w+)\s+to\s+(\w+)"""
    )
    private val alterColumnTypeRegex = Regex(
        """alter\s+column\s+(\w+)\s+(?:set\s+data\s+)?type\s+(.+?)(?:\s+using\s+.*)?$"""
    )
    private val alterColumnDropNotNullRegex = Regex(
        """alter\s+column\s+(\w+)\s+drop\s+not\s+null"""
    )
    private val alterColumnSetNotNullRegex = Regex(
        """alter\s+column\s+(\w+)\s+set\s+not\s+null"""
    )

    private val alterClauseKeywords = setOf(
        "constraint", "index", "unique", "primary", "foreign", "check"
    )

    private fun processAlterClause(clause: String, table: TableDefinition) {
        when {
            clause.startsWith("add ") -> {
                addColumnRegex.find(clause)?.let { m ->
                    val columnName = m.groupValues[1]
                    if (columnName in alterClauseKeywords) return
                    val columnDef = m.groupValues[2].trim()
                    val column = parseColumnType(columnName, columnDef)
                    val existing = table.columns.indexOfFirst { it.name == columnName }
                    if (existing >= 0) {
                        table.columns[existing] = column
                    } else {
                        table.columns.add(column)
                    }
                }
            }
            clause.startsWith("drop column") -> {
                dropColumnRegex.find(clause)?.let { m ->
                    val columnName = m.groupValues[1]
                    table.columns.removeAll { it.name == columnName }
                }
            }
            clause.startsWith("rename ") -> {
                renameColumnRegex.find(clause)?.let { m ->
                    val oldName = m.groupValues[1]
                    val newName = m.groupValues[2]
                    val idx = table.columns.indexOfFirst { it.name == oldName }
                    if (idx >= 0) {
                        table.columns[idx] = table.columns[idx].copy(name = newName)
                    }
                }
            }
            clause.startsWith("alter column") -> {
                alterColumnTypeRegex.find(clause)?.let { m ->
                    val columnName = m.groupValues[1]
                    val newType = extractSqlType(m.groupValues[2].trim())
                    val idx = table.columns.indexOfFirst { it.name == columnName }
                    if (idx >= 0) {
                        table.columns[idx] = table.columns[idx].copy(sqlType = newType)
                    }
                    return
                }
                alterColumnDropNotNullRegex.find(clause)?.let { m ->
                    val columnName = m.groupValues[1]
                    val idx = table.columns.indexOfFirst { it.name == columnName }
                    if (idx >= 0) {
                        table.columns[idx] = table.columns[idx].copy(nullable = true)
                    }
                    return
                }
                alterColumnSetNotNullRegex.find(clause)?.let { m ->
                    val columnName = m.groupValues[1]
                    val idx = table.columns.indexOfFirst { it.name == columnName }
                    if (idx >= 0) {
                        table.columns[idx] = table.columns[idx].copy(nullable = false)
                    }
                }
            }
            // ALTER TABLE ... ADD CONSTRAINT ... FOREIGN KEY (col) REFERENCES table
            clause.startsWith("add constraint") || clause.startsWith("add foreign") -> {
                val fkMatch = Regex("""foreign\s+key\s*\((\w+)\)\s*references\s+(?:\w+\.)?(\w+)""").find(clause)
                if (fkMatch != null) {
                    val colName = fkMatch.groupValues[1]
                    val refTable = fkMatch.groupValues[2]
                    val idx = table.columns.indexOfFirst { it.name == colName }
                    if (idx >= 0 && table.columns[idx].referencedTable == null) {
                        table.columns[idx] = table.columns[idx].copy(referencedTable = refTable)
                    }
                }
            }
            // drop constraint, etc. — ignored
        }
    }

    private fun extractPrimaryKeys(body: String): Set<String> {
        val keys = mutableSetOf<String>()

        // constraint-level "primary key (col1, col2)"
        val constraintPkRegex = Regex("""(?:^|,)\s*primary\s+key\s*\(([^)]+)\)""")
        constraintPkRegex.find(body)?.let { match ->
            match.groupValues[1].split(",").forEach { keys.add(it.trim()) }
        }

        // inline "column_name type primary key" — per column line
        body.lines().forEach { line ->
            val trimmed = line.trim().trimEnd(',')
            if (trimmed.contains(Regex("""\bprimary\s+key\b""")) && !trimmed.startsWith("primary")) {
                val colName = trimmed.split("\\s+".toRegex()).firstOrNull()
                if (colName != null) keys.add(colName)
            }
        }

        return keys
    }

    private fun parseColumnLines(body: String): List<String> =
        splitByTopLevelComma(body)
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .filter { line ->
                val lower = line.trimStart()
                !lower.startsWith("constraint ") &&
                    !lower.startsWith("primary key") &&
                    !lower.startsWith("unique") &&
                    !lower.startsWith("foreign key") &&
                    !lower.startsWith("check ")
            }

    private fun splitByTopLevelComma(text: String): List<String> {
        val result = mutableListOf<String>()
        var depth = 0
        var start = 0
        text.forEachIndexed { i, c ->
            when (c) {
                '(' -> depth++
                ')' -> depth--
                ',' -> if (depth == 0) {
                    result.add(text.substring(start, i))
                    start = i + 1
                }
            }
        }
        result.add(text.substring(start))
        return result
    }

    private fun parseColumn(line: String, primaryKeys: Set<String>): ColumnDefinition? {
        val tokens = line.trim().split("\\s+".toRegex(), limit = 3)
        if (tokens.size < 2) return null

        val name = tokens[0]
        if (name == "primary" || name == "constraint" || name == "unique" || name == "foreign") return null

        val rest = tokens.drop(1).joinToString(" ")
        val isPk = primaryKeys.contains(name) || rest.contains("primary key")
        val isNotNull = rest.contains("not null") || isPk
        val sqlType = extractSqlType(rest)
        val referencedTable = extractReferencedTable(rest)

        return ColumnDefinition(
            name = name,
            sqlType = sqlType,
            nullable = !isNotNull,
            isPrimaryKey = isPk,
            referencedTable = referencedTable
        )
    }

    private fun parseColumnType(columnName: String, definition: String): ColumnDefinition {
        val isNotNull = definition.contains("not null")
        val sqlType = extractSqlType(definition)
        val referencedTable = extractReferencedTable(definition)
        return ColumnDefinition(
            name = columnName,
            sqlType = sqlType,
            nullable = !isNotNull,
            isPrimaryKey = false,
            referencedTable = referencedTable
        )
    }

    /** Extracts the referenced table name from an inline `REFERENCES table_name(...)` clause. */
    private fun extractReferencedTable(definition: String): String? {
        val match = Regex("""\breferences\s+(?:\w+\.)?(\w+)""").find(definition)
        return match?.groupValues?.get(1)
    }

    /**
     * Extracts foreign key relationships from table-level `FOREIGN KEY (col) REFERENCES other_table(col)` constraints.
     * Returns a map of column name → referenced table name.
     */
    private fun extractForeignKeys(body: String): Map<String, String> {
        val fks = mutableMapOf<String, String>()
        val fkRegex = Regex("""foreign\s+key\s*\((\w+)\)\s*references\s+(?:\w+\.)?(\w+)""")
        // Also handle: constraint name foreign key (col) references table
        val constraintFkRegex = Regex("""constraint\s+\w+\s+foreign\s+key\s*\((\w+)\)\s*references\s+(?:\w+\.)?(\w+)""")

        fkRegex.findAll(body).forEach { match ->
            fks[match.groupValues[1]] = match.groupValues[2]
        }
        constraintFkRegex.findAll(body).forEach { match ->
            fks[match.groupValues[1]] = match.groupValues[2]
        }
        return fks
    }

    private fun extractSqlType(definition: String): String {
        val cleaned = definition
            .replace(Regex("""\bprimary\s+key\b"""), "")
            .replace(Regex("""\bnot\s+null\b"""), "")
            .replace(Regex("""\bnull\b"""), "")
            .replace(Regex("""\bdefault\s+(?:'[^']*'|\S+(?:\([^)]*\))?)"""), "")
            .replace(Regex("""\breferences\s+\w+"""), "")
            .replace(Regex("""\bgenerated\s+.*"""), "")
            .trim()

        // Normalize timestamp precision: timestamp(N) → timestamp for consistent type matching
        val normalized = cleaned.replace(Regex("""timestamp\(\d+\)"""), "timestamp")

        // Check multi-word types first
        val lower = normalized.lowercase()
        multiWordTypes.firstOrNull { lower.startsWith(it) }?.let { return it }

        // Extract type including parenthesized args: varchar(12), numeric(20, 2)
        val typeMatch = Regex("""^(\w+(?:\([^)]*\))?)""").find(normalized)
        return typeMatch?.groupValues?.get(1)?.trim()?.trimEnd(',')
            ?: normalized.split("\\s+".toRegex()).first().trimEnd(',')
    }
}
