package sqltojooq.parser

import org.junit.jupiter.api.io.TempDir
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals

class ChangelogParserTest {

    @TempDir
    lateinit var tempDir: File

    /** Writes [content] to [path] (relative to [tempDir]), creating parent directories. */
    private fun write(path: String, content: String): File =
        File(tempDir, path).apply {
            parentFile.mkdirs()
            writeText(content)
        }

    private fun names(files: List<File>) = files.map { it.name }

    // --- Bug 6: changeSet/sqlFile support, missing trailing newline, path resolution ---

    @Test
    fun `bug6 - resolves changeSet sqlFile entries through an include, even without a trailing newline`() {
        // master sits under db/changelog/ and references the inner changelog by a
        // resources-root-relative path with no `relativeToChangelogFile` and no final newline.
        write(
            "db/changelog/db.changelog-master.yaml",
            "databaseChangeLog:\n" +
                "  - include:\n" +
                "      file: db/changelog/changes/changelog.yaml"
        )
        write(
            "db/changelog/changes/changelog.yaml",
            """
            databaseChangeLog:
              - changeSet:
                  id: c1
                  author: t
                  changes:
                    - sqlFile:
                        path: V1.sql
                        relativeToChangelogFile: true
              - changeSet:
                  id: c2
                  author: t
                  changes:
                    - sqlFile:
                        path: V2.sql
                        relativeToChangelogFile: true
            """.trimIndent()
        )
        write("db/changelog/changes/V1.sql", "create table v1 (id serial);")
        write("db/changelog/changes/V2.sql", "create table v2 (id serial);")

        val master = File(tempDir, "db/changelog/db.changelog-master.yaml")
        assertEquals(listOf("V1.sql", "V2.sql"), names(ChangelogParser.parseSqlFiles(master)))
    }

    @Test
    fun `bug6 - a master changelog with a trailing newline still resolves`() {
        write(
            "master.yaml",
            "databaseChangeLog:\n" +
                "  - changeSet:\n" +
                "      id: c1\n" +
                "      changes:\n" +
                "        - sqlFile:\n" +
                "            path: V1.sql\n" +
                "            relativeToChangelogFile: true\n"
        )
        write("V1.sql", "create table v1 (id serial);")

        val master = File(tempDir, "master.yaml")
        assertEquals(listOf("V1.sql"), names(ChangelogParser.parseSqlFiles(master)))
    }

    @Test
    fun `includeAll resolves every SQL file in the directory, sorted by name`() {
        write(
            "master.yaml",
            """
            databaseChangeLog:
              - includeAll:
                  path: changes/
            """.trimIndent()
        )
        write("changes/V2.sql", "create table v2 (id serial);")
        write("changes/V1.sql", "create table v1 (id serial);")

        val master = File(tempDir, "master.yaml")
        assertEquals(listOf("V1.sql", "V2.sql"), names(ChangelogParser.parseSqlFiles(master)))
    }

    @Test
    fun `bug6 - sqlFile and include directives are resolved in document order`() {
        write(
            "master.yaml",
            """
            databaseChangeLog:
              - changeSet:
                  id: c1
                  changes:
                    - sqlFile:
                        path: A.sql
                        relativeToChangelogFile: true
              - include:
                  file: sub.yaml
                  relativeToChangelogFile: true
              - changeSet:
                  id: c2
                  changes:
                    - sqlFile:
                        path: C.sql
                        relativeToChangelogFile: true
            """.trimIndent()
        )
        write(
            "sub.yaml",
            """
            databaseChangeLog:
              - changeSet:
                  id: s1
                  changes:
                    - sqlFile:
                        path: B.sql
                        relativeToChangelogFile: true
            """.trimIndent()
        )
        write("A.sql", "create table a (id serial);")
        write("B.sql", "create table b (id serial);")
        write("C.sql", "create table c (id serial);")

        val master = File(tempDir, "master.yaml")
        assertEquals(listOf("A.sql", "B.sql", "C.sql"), names(ChangelogParser.parseSqlFiles(master)))
    }
}
