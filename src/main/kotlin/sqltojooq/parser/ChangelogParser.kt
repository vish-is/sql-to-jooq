package sqltojooq.parser

import java.io.File

object ChangelogParser {

    /**
     * Recursively resolves all `include`/`includeAll` directives and
     * `changeSet` → `sqlFile` references starting from the master changelog
     * file, returning SQL files in document order (matching Liquibase's own
     * execution sequence).
     */
    fun parseSqlFiles(changelogFile: File): List<File> {
        val visited = mutableSetOf<String>()
        return resolveFile(changelogFile.canonicalFile, visited)
    }

    private fun resolveFile(file: File, visited: MutableSet<String>): List<File> {
        val canonical = file.canonicalPath
        if (canonical in visited || !file.exists()) return emptyList()
        visited.add(canonical)

        return when (file.extension.lowercase()) {
            "yml", "yaml" -> resolveChangelog(file, visited)
            "sql" -> listOf(file)
            else -> emptyList()
        }
    }

    // - include:
    //     file: some/path.yml
    //     relativeToChangelogFile: true
    private val includeRegex = Regex(
        """-\s+include:\s*\n\s+file:\s*(\S.*?)\s*$(?:\s+relativeToChangelogFile:\s*(true|false))?""",
        RegexOption.MULTILINE
    )

    // - includeAll:
    //     path: some/directory/
    private val includeAllRegex = Regex(
        """-\s+includeAll:\s*\n\s+path:\s*(\S.*?)\s*$""",
        RegexOption.MULTILINE
    )

    // - sqlFile:
    //     path: changes/CMSYS-1234.sql
    //     relativeToChangelogFile: true
    private val sqlFileRegex = Regex(
        """-\s+sqlFile:\s*\n\s+path:\s*(\S.*?)\s*$(?:\s+relativeToChangelogFile:\s*(true|false))?""",
        RegexOption.MULTILINE
    )

    /**
     * Parses a single YAML changelog file and resolves, in document order, all
     * `include`, `includeAll` and `changeSet` → `sqlFile` references. Each
     * directive is recorded with its position in the file so the resulting SQL
     * file list preserves Liquibase's execution order even when directive types
     * are interleaved.
     *
     * The trailing newline after a `file:`/`path:` value is optional, so a
     * changelog without a final newline is still resolved correctly.
     */
    private fun resolveChangelog(changelogFile: File, visited: MutableSet<String>): List<File> {
        val content = changelogFile.readText()
        val changelogDir = changelogFile.parentFile

        val directives = mutableListOf<Pair<Int, List<File>>>()

        includeRegex.findAll(content).forEach { match ->
            val path = match.groupValues[1].trim()
            // Liquibase default for relativeToChangelogFile is false (classpath-relative).
            val isRelative = match.groupValues[2] == "true"
            val resolved = resolveIncludePath(path, changelogDir, isRelative, changelogFile)
            directives += match.range.first to resolveFile(resolved, visited)
        }

        includeAllRegex.findAll(content).forEach { match ->
            val dirPath = match.groupValues[1].trim()
            val dir = resolveIncludePath(dirPath, changelogDir, isRelative = true, changelogFile)
            val files = dir.takeIf { it.isDirectory }
                ?.listFiles()
                ?.filter { it.extension.lowercase() in setOf("yml", "yaml", "sql") }
                ?.sortedBy { it.name }
                ?.flatMap { resolveFile(it, visited) }
                ?: emptyList()
            directives += match.range.first to files
        }

        sqlFileRegex.findAll(content).forEach { match ->
            val path = match.groupValues[1].trim()
            val isRelative = match.groupValues[2] == "true"
            val resolved = resolveIncludePath(path, changelogDir, isRelative, changelogFile)
            directives += match.range.first to resolveFile(resolved, visited)
        }

        return directives.sortedBy { it.first }.flatMap { it.second }
    }

    private fun resolveIncludePath(
        path: String,
        changelogDir: File,
        isRelative: Boolean,
        changelogFile: File
    ): File {
        if (isRelative) return File(changelogDir, path)

        // relativeToChangelogFile: false — classpath-relative, walk up to resources root
        var candidate = changelogFile.parentFile
        repeat(10) {
            val resolved = File(candidate, path)
            if (resolved.exists()) return resolved
            candidate = candidate.parentFile ?: return File(changelogDir, path)
        }
        return File(changelogDir, path)
    }
}
