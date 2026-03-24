package sqltojooq.generator

object TypeMapper {

    data class MappedType(
        val kotlinType: String,
        val jooqDataType: String,
        val defaultValue: String
    )

    fun map(sqlType: String): MappedType {
        val normalized = sqlType.lowercase().trim()

        return when {
            normalized == "uuid" -> MappedType(
                "java.util.UUID",
                "org.jooq.impl.SQLDataType.UUID",
                "java.util.UUID.randomUUID()"
            )
            normalized.startsWith("varchar") || normalized.startsWith("character varying") -> {
                val length = extractLength(normalized)
                MappedType(
                    "String",
                    if (length != null) "org.jooq.impl.SQLDataType.VARCHAR($length)" else "org.jooq.impl.SQLDataType.VARCHAR",
                    "\"\""
                )
            }
            normalized == "text" -> MappedType(
                "String",
                "org.jooq.impl.SQLDataType.VARCHAR",
                "\"\""
            )
            normalized.startsWith("numeric") || normalized.startsWith("decimal") -> {
                val (precision, scale) = extractPrecisionScale(normalized)
                MappedType(
                    "java.math.BigDecimal",
                    if (precision != null && scale != null)
                        "org.jooq.impl.SQLDataType.NUMERIC($precision, $scale)"
                    else "org.jooq.impl.SQLDataType.NUMERIC",
                    "java.math.BigDecimal.ZERO"
                )
            }
            normalized == "integer" || normalized == "int" || normalized == "int4" || normalized == "serial" -> MappedType(
                "Int",
                "org.jooq.impl.SQLDataType.INTEGER",
                "0"
            )
            normalized == "bigint" || normalized == "int8" || normalized == "bigserial" -> MappedType(
                "Long",
                "org.jooq.impl.SQLDataType.BIGINT",
                "0L"
            )
            normalized == "smallserial" -> MappedType(
                "Short",
                "org.jooq.impl.SQLDataType.SMALLINT",
                "0"
            )
            normalized == "smallint" || normalized == "int2" -> MappedType(
                "Short",
                "org.jooq.impl.SQLDataType.SMALLINT",
                "0"
            )
            normalized == "boolean" || normalized == "bool" -> MappedType(
                "Boolean",
                "org.jooq.impl.SQLDataType.BOOLEAN",
                "false"
            )
            normalized == "date" -> MappedType(
                "java.time.LocalDate",
                "org.jooq.impl.SQLDataType.LOCALDATE",
                "java.time.LocalDate.now()"
            )
            normalized == "timestamp" || normalized == "timestamp without time zone" -> MappedType(
                "java.time.LocalDateTime",
                "org.jooq.impl.SQLDataType.LOCALDATETIME",
                "java.time.LocalDateTime.now()"
            )
            normalized == "timestamptz" || normalized == "timestamp with time zone" -> MappedType(
                "java.time.OffsetDateTime",
                "org.jooq.impl.SQLDataType.TIMESTAMPWITHTIMEZONE",
                "java.time.OffsetDateTime.now()"
            )
            normalized == "jsonb" -> MappedType(
                "String",
                "org.jooq.impl.SQLDataType.JSONB",
                "\"\""
            )
            normalized == "json" -> MappedType(
                "String",
                "org.jooq.impl.SQLDataType.JSON",
                "\"\""
            )
            normalized == "bytea" -> MappedType(
                "ByteArray",
                "org.jooq.impl.SQLDataType.BLOB",
                "byteArrayOf()"
            )
            normalized == "real" || normalized == "float4" -> MappedType(
                "Float",
                "org.jooq.impl.SQLDataType.REAL",
                "0.0f"
            )
            normalized == "double precision" || normalized == "float8" -> MappedType(
                "Double",
                "org.jooq.impl.SQLDataType.DOUBLE",
                "0.0"
            )
            else -> MappedType(
                "String",
                "org.jooq.impl.SQLDataType.VARCHAR",
                "\"\""
            )
        }
    }

    /** Returns true for SQL types that represent auto-increment columns (serial, bigserial, smallserial). */
    fun isAutoIncrement(sqlType: String): Boolean =
        sqlType.lowercase().trim() in setOf("serial", "bigserial", "smallserial")

    private fun extractLength(type: String): Int? =
        Regex("""\((\d+)\)""").find(type)?.groupValues?.get(1)?.toIntOrNull()

    private fun extractPrecisionScale(type: String): Pair<Int?, Int?> {
        val match = Regex("""\((\d+)\s*,\s*(\d+)\)""").find(type)
        return if (match != null) {
            Pair(match.groupValues[1].toIntOrNull(), match.groupValues[2].toIntOrNull())
        } else {
            Pair(null, null)
        }
    }
}
