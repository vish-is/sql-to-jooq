package sqltojooq.parser

data class ColumnDefinition(
    val name: String,
    val sqlType: String,
    val nullable: Boolean,
    val isPrimaryKey: Boolean,
    /** Table name referenced by a FOREIGN KEY constraint, if any. */
    val referencedTable: String? = null
)

data class TableDefinition(
    val name: String,
    val columns: MutableList<ColumnDefinition> = mutableListOf()
)
