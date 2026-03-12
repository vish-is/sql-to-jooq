package sqltojooq.generator

object NameUtils {

    // snake_case to PascalCase
    fun toPascalCase(snakeCase: String): String =
        snakeCase.split("_").joinToString("") { it.replaceFirstChar { c -> c.uppercase() } }

    // snake_case → camelCase
    fun toCamelCase(snakeCase: String): String =
        toPascalCase(snakeCase).replaceFirstChar { it.lowercase() }

    // snake_case → UPPER_SNAKE_CASE
    fun toUpperSnakeCase(snakeCase: String): String =
        snakeCase.uppercase()
}
