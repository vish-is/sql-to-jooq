package sqltojooq.parser

import java.io.File

object FlywayMigrationScanner {

    private val versionedRegex = Regex("""^[Vv](\d+(?:[._]\d+)*)__.*\.sql$""")

    /**
     * Scans a directory for Flyway versioned SQL migration files (V*__*.sql)
     * and returns them sorted by version number.
     */
    fun scanSqlFiles(migrationsDir: File): List<File> =
        migrationsDir.listFiles()
            ?.filter { it.isFile && versionedRegex.matches(it.name) }
            ?.sortedWith(compareBy { parseVersion(it.name) })
            ?: emptyList()

    private fun parseVersion(fileName: String): List<Int> {
        val match = versionedRegex.find(fileName) ?: return emptyList()
        return match.groupValues[1].split("[._]".toRegex()).map { it.toIntOrNull() ?: 0 }
    }

    private operator fun List<Int>.compareTo(other: List<Int>): Int {
        val maxLen = maxOf(this.size, other.size)
        for (i in 0 until maxLen) {
            val a = this.getOrElse(i) { 0 }
            val b = other.getOrElse(i) { 0 }
            if (a != b) return a.compareTo(b)
        }
        return 0
    }

    private fun compareBy(selector: (File) -> List<Int>): Comparator<File> =
        Comparator { a, b ->
            val va = selector(a)
            val vb = selector(b)
            va.compareTo(vb)
        }
}
