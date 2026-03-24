package sqltojooq.parser

import java.io.File

object ChangelogParser {

    /**
     * Recursively resolves all `include`/`includeAll` directives starting from
     * the master changelog file, returning SQL files in DFS order (matching
     * Liquibase's own execution sequence).
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

    /**
     * Parses a single YAML changelog file and resolves all `include` and
     * `includeAll` directives found in it. Supports:
     *
     *   - include:
     *       file: some/path.yml
     *       relativeToChangelogFile: true
     *
     *   - includeAll:
     *       path: some/directory/
     */
    private fun resolveChangelog(changelogFile: File, visited: MutableSet<String>): List<File> {
        val content = changelogFile.readText()
        val changelogDir = changelogFile.parentFile
        val result = mutableListOf<File>()

        val includeRegex = Regex(
            """-\s+include:\s*\n\s+file:\s*(.+?)\s*\n(?:\s+relativeToChangelogFile:\s*(true|false)\s*\n)?""",
            RegexOption.MULTILINE
        )
        includeRegex.findAll(content).forEach { match ->
            val path = match.groupValues[1].trim()
            val isRelative = match.groupValues[2].let { it.isEmpty() || it == "true" }
            val resolved = resolveIncludePath(path, changelogDir, isRelative, changelogFile)
            result += resolveFile(resolved, visited)
        }

        val includeAllRegex = Regex(
            """-\s+includeAll:\s*\n\s+path:\s*(.+?)\s*$""",
            RegexOption.MULTILINE
        )
        includeAllRegex.findAll(content).forEach { match ->
            val dirPath = match.groupValues[1].trim()
            val dir = resolveIncludePath(dirPath, changelogDir, isRelative = true, changelogFile)
            if (dir.isDirectory) {
                dir.listFiles()
                    ?.filter { it.extension.lowercase() in setOf("yml", "yaml", "sql") }
                    ?.sortedBy { it.name }
                    ?.forEach { result += resolveFile(it, visited) }
            }
        }

        return result
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
