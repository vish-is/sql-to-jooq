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

            parseColumnLines(body).forEach { line ->
                parseColumn(line, primaryKeys)?.let { table.columns.add(it) }
            }

            tables[tableName] = table
        }
    }

    private fun parseAlterTables(sql: String, tables: MutableMap<String, TableDefinition>) {
        val alterAddColumnRegex = Regex(
            """alter\s+table\s+(?:if\s+exists\s+)?(?:only\s+)?(?:\w+\.)?(\w+)\s+add\s+(?:column\s+)?(?:if\s+not\s+exists\s+)?(\w+)\s+(.+?)(?:;|$)""",
            RegexOption.MULTILINE
        )

        alterAddColumnRegex.findAll(sql).forEach { match ->
            val tableName = match.groupValues[1]
            val columnName = match.groupValues[2]
            val columnDef = match.groupValues[3].trim()

            tables[tableName]?.let { table ->
                val existing = table.columns.indexOfFirst { it.name == columnName }
                val column = parseColumnType(columnName, columnDef)
                if (existing >= 0) {
                    table.columns[existing] = column
                } else {
                    table.columns.add(column)
                }
            }
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

        return ColumnDefinition(
            name = name,
            sqlType = sqlType,
            nullable = !isNotNull,
            isPrimaryKey = isPk
        )
    }

    private fun parseColumnType(columnName: String, definition: String): ColumnDefinition {
        val isNotNull = definition.contains("not null")
        val sqlType = extractSqlType(definition)
        return ColumnDefinition(
            name = columnName,
            sqlType = sqlType,
            nullable = !isNotNull,
            isPrimaryKey = false
        )
    }

    private fun extractSqlType(definition: String): String {
        val cleaned = definition
            .replace(Regex("""\bprimary\s+key\b"""), "")
            .replace(Regex("""\bnot\s+null\b"""), "")
            .replace(Regex("""\bnull\b"""), "")
            .replace(Regex("""\bdefault\s+(?:'[^']*'|\S+(?:\([^)]*\))?)"""), "")
            .replace(Regex("""\breferences\s+\w+"""), "")
            .trim()

        // Check multi-word types first
        val lower = cleaned.lowercase()
        multiWordTypes.firstOrNull { lower.startsWith(it) }?.let { return it }

        // Extract type including parenthesized args: varchar(12), numeric(20, 2)
        val typeMatch = Regex("""^(\w+(?:\([^)]*\))?)""").find(cleaned)
        return typeMatch?.groupValues?.get(1)?.trim()?.trimEnd(',')
            ?: cleaned.split("\\s+".toRegex()).first().trimEnd(',')
    }
}
