package sqltojooq.parser

data class ColumnDefinition(
    val name: String,
    val sqlType: String,
    val nullable: Boolean,
    val isPrimaryKey: Boolean
)

data class TableDefinition(
    val name: String,
    val columns: MutableList<ColumnDefinition> = mutableListOf()
)
