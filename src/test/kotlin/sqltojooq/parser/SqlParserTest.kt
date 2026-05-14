package sqltojooq.parser

import org.junit.jupiter.api.io.TempDir
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SqlParserTest {

    @TempDir
    lateinit var tempDir: File

    /** Writes each SQL snippet to its own migration file (preserving order) and parses them. */
    private fun parse(vararg sqls: String): Map<String, TableDefinition> {
        val files = sqls.mapIndexed { i, sql ->
            File(tempDir, "m%03d.sql".format(i)).apply { writeText(sql) }
        }
        return SqlParser().parse(files).associateBy { it.name }
    }

    private fun TableDefinition.col(name: String): ColumnDefinition =
        columns.firstOrNull { it.name == name }
            ?: error("column '$name' not found in '${this.name}': ${columns.map { it.name }}")

    // --- sanity ---

    @Test
    fun `parses columns, types, inline primary key and foreign key`() {
        val tables = parse(
            """
            CREATE TABLE account (
                id BIGSERIAL PRIMARY KEY,
                email VARCHAR(255) NOT NULL,
                org_id INTEGER REFERENCES organization(id),
                active BOOLEAN
            );
            """.trimIndent()
        )

        val account = assertNotNull(tables["account"])
        assertEquals(4, account.columns.size)
        assertTrue(account.col("id").isPrimaryKey)
        assertFalse(account.col("id").nullable)
        assertEquals("bigserial", account.col("id").sqlType)
        assertFalse(account.col("email").nullable)
        assertEquals("varchar(255)", account.col("email").sqlType)
        assertEquals("organization", account.col("org_id").referencedTable)
        assertTrue(account.col("active").nullable)
    }

    // --- Bug 1: CREATE TABLE terminator (`)` and `;` not adjacent / missing) ---

    @Test
    fun `bug1 - CREATE TABLE with semicolon on its own line`() {
        val tables = parse(
            "CREATE TABLE t (\n    id serial NOT NULL,\n    name varchar\n)\n;"
        )
        val t = assertNotNull(tables["t"])
        assertEquals(listOf("id", "name"), t.columns.map { it.name })
    }

    @Test
    fun `bug1 - CREATE TABLE with no trailing semicolon`() {
        val tables = parse(
            "CREATE TABLE if not exists shedlock (\n    name varchar(64),\n    locked_by varchar(255)\n)"
        )
        val t = assertNotNull(tables["shedlock"])
        assertEquals(listOf("name", "locked_by"), t.columns.map { it.name })
    }

    @Test
    fun `bug1 - two tables each terminated by semicolon on its own line are not merged`() {
        val tables = parse(
            """
            CREATE TABLE a (
                a_id serial NOT NULL
            )
            ;

            CREATE TABLE b (
                b_id serial NOT NULL
            )
            ;
            """.trimIndent()
        )
        assertEquals(listOf("a_id"), assertNotNull(tables["a"]).columns.map { it.name })
        assertEquals(listOf("b_id"), assertNotNull(tables["b"]).columns.map { it.name })
    }

    // --- Bug 2: ALTER TABLE ... RENAME TO ---

    @Test
    fun `bug2 - ALTER TABLE RENAME TO re-keys the table under its new name`() {
        val tables = parse(
            "CREATE TABLE old_name (id serial NOT NULL, data varchar);",
            "ALTER TABLE old_name RENAME TO new_name;"
        )
        assertNull(tables["old_name"])
        val renamed = assertNotNull(tables["new_name"])
        assertEquals(listOf("id", "data"), renamed.columns.map { it.name })
    }

    // --- Bug 3: ALTER ... ADD COLUMN formatted across multiple lines ---

    @Test
    fun `bug3 - ADD COLUMN with newline between ADD and COLUMN is recognised`() {
        val tables = parse(
            "CREATE TABLE t (id serial NOT NULL);",
            "ALTER TABLE\n    t\nADD\n    COLUMN foo text,\nADD\n    COLUMN bar integer NOT NULL;"
        )
        val t = assertNotNull(tables["t"])
        assertEquals(listOf("id", "foo", "bar"), t.columns.map { it.name })
        assertTrue(t.col("foo").nullable)
        assertFalse(t.col("bar").nullable)
    }

    // --- Bug 4: DROP TABLE + document-order processing ---

    @Test
    fun `bug4 - DROP TABLE removes the table`() {
        val tables = parse(
            "CREATE TABLE t (id serial NOT NULL);",
            "DROP TABLE t;"
        )
        assertNull(tables["t"])
    }

    @Test
    fun `bug4 - DROP TABLE then CREATE TABLE in the same file keeps the recreated table`() {
        val tables = parse(
            """
            DROP TABLE IF EXISTS t CASCADE;

            CREATE TABLE IF NOT EXISTS t (
                id serial NOT NULL,
                name varchar
            )
            ;
            """.trimIndent()
        )
        val t = assertNotNull(tables["t"])
        assertEquals(listOf("id", "name"), t.columns.map { it.name })
    }

    // --- Bug 5: PRIMARY KEY via named constraint / ALTER ---

    @Test
    fun `bug5 - named CONSTRAINT PRIMARY KEY inside CREATE TABLE is detected`() {
        val tables = parse(
            """
            CREATE TABLE t (
                id integer,
                name varchar,
                CONSTRAINT t_pkey PRIMARY KEY (id)
            );
            """.trimIndent()
        )
        val t = assertNotNull(tables["t"])
        assertTrue(t.col("id").isPrimaryKey)
        assertFalse(t.col("id").nullable)
        assertFalse(t.col("name").isPrimaryKey)
    }

    @Test
    fun `bug5 - ALTER TABLE ADD PRIMARY KEY marks the column`() {
        val tables = parse(
            "CREATE TABLE t (id integer, name varchar);",
            "ALTER TABLE t ADD PRIMARY KEY (id);"
        )
        val t = assertNotNull(tables["t"])
        assertTrue(t.col("id").isPrimaryKey)
        assertFalse(t.col("id").nullable)
    }

    @Test
    fun `bug5 - ALTER TABLE ADD CONSTRAINT PRIMARY KEY marks the column`() {
        val tables = parse(
            "CREATE TABLE t (id integer, name varchar);",
            "ALTER TABLE t ADD CONSTRAINT t_pkey PRIMARY KEY (id);"
        )
        assertTrue(assertNotNull(tables["t"]).col("id").isPrimaryKey)
    }

    @Test
    fun `bug5 - ALTER TABLE ADD PRIMARY KEY replaces the previous primary key`() {
        val tables = parse(
            "CREATE TABLE t (old_id integer PRIMARY KEY, new_id integer);",
            "ALTER TABLE t ADD PRIMARY KEY (new_id);"
        )
        val t = assertNotNull(tables["t"])
        assertFalse(t.col("old_id").isPrimaryKey)
        assertTrue(t.col("new_id").isPrimaryKey)
        assertFalse(t.col("new_id").nullable)
    }

    // --- Bug 7: serial/bigserial imply NOT NULL ---

    @Test
    fun `bug7 - serial column is implicitly NOT NULL`() {
        val tables = parse(
            "CREATE TABLE t (id serial, big bigserial, plain integer);"
        )
        val t = assertNotNull(tables["t"])
        assertFalse(t.col("id").nullable)
        assertFalse(t.col("big").nullable)
        assertTrue(t.col("plain").nullable)
    }

    // --- Bug 8: `not null` inside a CHECK expression is not a column constraint ---

    @Test
    fun `bug8 - NOT NULL inside a CHECK expression does not make the column NOT NULL`() {
        val tables = parse(
            """
            CREATE TABLE t (
                source varchar(50)
                    CONSTRAINT check_source CHECK (source IS NOT NULL),
                other varchar NOT NULL
            );
            """.trimIndent()
        )
        val t = assertNotNull(tables["t"])
        assertTrue(t.col("source").nullable)
        assertEquals("varchar(50)", t.col("source").sqlType)
        assertFalse(t.col("other").nullable)
    }
}
