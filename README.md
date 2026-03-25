# SQL to jOOQ Gradle Plugin

[![Gradle Plugin Portal](https://img.shields.io/gradle-plugin-portal/v/io.github.vish-is.sql-to-jooq)](https://plugins.gradle.org/plugin/io.github.vish-is.sql-to-jooq)
[![License](https://img.shields.io/badge/license-Apache%202.0-blue.svg)](LICENSE)

A Gradle plugin that generates type-safe [jOOQ](https://www.jooq.org/) code directly from SQL migration files — **no running database required**.

It parses your Liquibase or Flyway SQL migrations and produces Kotlin source code: jOOQ `TableImpl` classes, data class records, repository infrastructure with CRUD operations, and optional `@JvmInline value class` IDs for primary keys.

## Why?

The standard jOOQ code generator requires a live database with an up-to-date schema. This plugin eliminates that requirement by parsing DDL statements from your migration files at build time, making code generation:

- **Fast** — no database startup, no container orchestration
- **Reproducible** — same migrations always produce the same code
- **CI-friendly** — works in any environment without external dependencies

## Features

- **Liquibase & Flyway support** — reads YAML changelogs with `include`/`includeAll` directives or scans Flyway `V*__*.sql` versioned migrations
- **Full DDL parsing** — handles `CREATE TABLE`, `ALTER TABLE` (add/drop/rename column, change type, set/drop not null, add foreign key), primary keys, foreign keys, and all common PostgreSQL column types
- **jOOQ TableImpl generation** — one class per table with typed fields, nullability constraints, and optional converters
- **Kotlin data class records** — with sensible defaults, nullability, and configurable mutability (`val` / `var`)
- **Repository layer** — generates `JooqRepository` interface, `AbstractJooqRepository` with CRUD (find, save/upsert, delete, exists), and per-table concrete repositories
- **Pagination utilities** — `PageFilter`, `PageResult`, and `AbstractPageQuery` with fluent builder
- **Value class IDs** — optional `@JvmInline value class` for single-column primary keys with automatic jOOQ converters and FK propagation
- **JSONB mapping** — map `jsonb` columns to Kotlin types via Jackson with generated `JsonbConverter`
- **Enum mapping** — map `varchar`/`text` columns to Kotlin enum classes with jOOQ `EnumConverter`
- **Lateinit fields** — move selected columns out of the data class constructor into `lateinit var` body properties
- **Table exclusion** — skip tables you don't want generated code for
- **Safe stub generation** — hand-written repository files are created once and never overwritten

## Quick Start

### 1. Apply the plugin

<details>
<summary>Kotlin DSL (build.gradle.kts)</summary>

```kotlin
plugins {
    id("io.github.vish-is.sql-to-jooq") version "<latest>"
}
```
</details>

<details>
<summary>Groovy DSL (build.gradle)</summary>

```groovy
plugins {
    id 'io.github.vish-is.sql-to-jooq' version '<latest>'
}
```
</details>

### 2. Configure

```kotlin
sqlToJooq {
    migrationTool = sqltojooq.MigrationTool.LIQUIBASE // or FLYWAY
    changelogFile = file("src/main/resources/db/changelog/db.changelog-master.yml")
    // migrationsDir = file("src/main/resources/db/migration") // for Flyway
    outputDir = file("src/main/kotlin")
    outputPackage = "com.example.db.jooq"
}
```

### 3. Run

```bash
./gradlew generateJooq
```

Generated sources are written to `build/generated/sources/jooq/main/kotlin` and automatically added to the Kotlin source set. Repository stubs are placed in your `outputDir` and are never overwritten.

## Configuration Reference

| Property | Type | Default | Description |
|---|---|---|---|
| `migrationTool` | `MigrationTool` | `LIQUIBASE` | Migration tool: `LIQUIBASE` or `FLYWAY` |
| `changelogFile` | `RegularFileProperty` | — | Liquibase YAML master changelog file (required for Liquibase) |
| `migrationsDir` | `DirectoryProperty` | — | Flyway migrations directory (required for Flyway) |
| `outputDir` | `DirectoryProperty` | — | Source root for hand-written repository stubs (e.g. `src/main/kotlin`) |
| `outputPackage` | `Property<String>` | — | Base package for all generated code |
| `jsonbMappings` | `MapProperty<String, String>` | `{}` | Map of `"table.column"` to fully qualified Kotlin class for JSONB fields |
| `enumFields` | `MapProperty<String, String>` | `{}` | Map of `"table.column"` to fully qualified Kotlin enum class |
| `excludeTables` | `SetProperty<String>` | `[]` | Table names to exclude from generation |
| `lateinitFields` | `SetProperty<String>` | `[]` | `"table.column"` entries that become `lateinit var` in the record body |
| `generateValueClassIds` | `Property<Boolean>` | `false` | Generate `@JvmInline value class` IDs for single-column primary keys |
| `immutableRecordFields` | `Property<Boolean>` | `true` | Use `val` for record properties; set to `false` for `var` |

## Full Example

```kotlin
sqlToJooq {
    migrationTool = sqltojooq.MigrationTool.LIQUIBASE
    changelogFile = file("src/main/resources/db/changelog/db.changelog-master.yml")
    outputDir = file("src/main/kotlin")
    outputPackage = "com.example.db.jooq"
    generateValueClassIds = true
    immutableRecordFields = true

    excludeTables = setOf("flyway_schema_history", "databasechangelog")

    jsonbMappings = mapOf(
        "order.metadata" to "com.example.model.OrderMetadata",
        "user.preferences" to "com.example.model.UserPreferences"
    )

    enumFields = mapOf(
        "order.status" to "com.example.model.OrderStatus",
        "user.role" to "com.example.model.UserRole"
    )

    lateinitFields = setOf("order.total_amount")
}
```

## Generated Output

Given a SQL migration:

```sql
CREATE TABLE tariff (
    id BIGSERIAL PRIMARY KEY,
    name VARCHAR(255) NOT NULL,
    rate NUMERIC(10, 2) NOT NULL,
    active BOOLEAN NOT NULL DEFAULT true,
    created_at TIMESTAMP NOT NULL DEFAULT now()
);
```

The plugin generates:

### Table class (`table/TariffTable.kt`)

```kotlin
class TariffTable : TableImpl<Record>(DSL.name("tariff")) {
    companion object { val TARIFF = TariffTable() }

    val ID = createField(DSL.name("id"), SQLDataType.BIGINT.notNull(), this, "", Converter.ofNullable(Long::class.java, TariffId::class.java, ::TariffId, TariffId::value))
    val NAME = createField(DSL.name("name"), SQLDataType.VARCHAR(255).notNull(), this)
    val RATE = createField(DSL.name("rate"), SQLDataType.NUMERIC(10, 2).notNull(), this)
    val ACTIVE = createField(DSL.name("active"), SQLDataType.BOOLEAN.notNull(), this)
    val CREATED_AT = createField(DSL.name("created_at"), SQLDataType.LOCALDATETIME.notNull(), this)
}
```

### Value class ID (`record/TariffId.kt`)

```kotlin
@JvmInline
value class TariffId(val value: Long)
```

### Record class (`record/TariffRecord.kt`)

```kotlin
data class TariffRecord(
    val id: TariffId? = null,
    val name: String = "",
    val rate: BigDecimal = BigDecimal.ZERO,
    val active: Boolean = false,
    val createdAt: LocalDateTime = LocalDateTime.now()
)
```

### Repository (`repository/GeneratedTariffRepository.kt`)

```kotlin
open class GeneratedTariffRepository(dsl: DSLContext)
    : AbstractJooqRepository<TariffRecord, TariffId>(dsl) {

    override val table = TariffTable.TARIFF
    override val recordClass = TariffRecord::class.java
    override val pkField = TariffTable.TARIFF.ID
}
```

### Repository stub (`repository/TariffRepository.kt`)

```kotlin
// Created once, never overwritten — add your custom queries here
class TariffRepository(dsl: DSLContext) : GeneratedTariffRepository(dsl)
```

### Tables object

```kotlin
object Tables {
    val TARIFF = TariffTable.TARIFF
}
```

## Supported SQL Types

| SQL Type | Kotlin Type | Default Value |
|---|---|---|
| `UUID` | `java.util.UUID` | `UUID.randomUUID()` |
| `VARCHAR(n)` / `CHARACTER VARYING(n)` / `TEXT` | `String` | `""` |
| `INTEGER` / `INT` / `INT4` / `SERIAL` | `Int` | `0` |
| `BIGINT` / `INT8` / `BIGSERIAL` | `Long` | `0L` |
| `SMALLINT` / `INT2` / `SMALLSERIAL` | `Short` | `0` |
| `NUMERIC(p,s)` / `DECIMAL(p,s)` | `BigDecimal` | `BigDecimal.ZERO` |
| `BOOLEAN` / `BOOL` | `Boolean` | `false` |
| `DATE` | `LocalDate` | `LocalDate.now()` |
| `TIMESTAMP` / `TIMESTAMP WITHOUT TIME ZONE` | `LocalDateTime` | `LocalDateTime.now()` |
| `TIMESTAMPTZ` / `TIMESTAMP WITH TIME ZONE` | `OffsetDateTime` | `OffsetDateTime.now()` |
| `JSON` / `JSONB` | `String` (or mapped type) | `""` |
| `BYTEA` | `ByteArray` | `byteArrayOf()` |
| `REAL` / `FLOAT4` | `Float` | `0.0f` |
| `DOUBLE PRECISION` / `FLOAT8` | `Double` | `0.0` |

## Supported DDL

### CREATE TABLE
- Column definitions with types and constraints
- Inline and table-level `PRIMARY KEY`
- Inline and table-level `FOREIGN KEY ... REFERENCES`
- `NOT NULL`, `NULL`, `DEFAULT`
- `IF NOT EXISTS`
- Schema-qualified table names

### ALTER TABLE
- `ADD [COLUMN]` — add new columns
- `DROP COLUMN` — remove columns
- `RENAME [COLUMN] ... TO ...` — rename columns
- `ALTER COLUMN ... [SET DATA] TYPE ...` — change column type
- `ALTER COLUMN ... SET NOT NULL` / `DROP NOT NULL` — change nullability
- `ADD [CONSTRAINT] FOREIGN KEY ... REFERENCES ...` — add foreign keys

## Generated Package Structure

```
com.example.db.jooq/
├── Tables.kt
├── JooqRepository.kt
├── AbstractJooqRepository.kt
├── PageFilter.kt
├── PageResult.kt
├── AbstractPageQuery.kt
├── JsonbConverter.kt          # only when jsonbMappings configured
├── table/
│   └── TariffTable.kt
├── record/
│   ├── TariffRecord.kt
│   └── TariffId.kt            # only when generateValueClassIds = true
└── repository/
    ├── GeneratedTariffRepository.kt   # always regenerated
    └── TariffRepository.kt           # created once, never overwritten
```

## Requirements

- Gradle 7.0+
- Kotlin JVM plugin (`org.jetbrains.kotlin.jvm`)
- JDK 17+
- jOOQ 3.18+ (if using value class IDs with converters)

## How It Works

1. The plugin reads your migration files — either by following Liquibase YAML `include`/`includeAll` directives or by scanning a Flyway directory for `V*__*.sql` files sorted by version
2. SQL files are parsed to extract `CREATE TABLE` and `ALTER TABLE` statements, building an in-memory schema representation
3. Generators produce Kotlin source files for each table: `TableImpl`, data class record, value class ID, and repository
4. Infrastructure classes (`JooqRepository`, `AbstractJooqRepository`, pagination utilities) are generated once
5. Generated code goes to `build/generated/sources/jooq/main/kotlin` and is automatically added to the `main` Kotlin source set
6. Repository stubs are placed in your source directory and are only created if they don't already exist

The `generateJooq` task is automatically wired as a dependency of `compileKotlin`, so code generation runs before compilation.

## License

[Apache License 2.0](LICENSE)
