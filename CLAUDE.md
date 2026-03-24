# Project Memory

## Preferences
- При поиске решений и документации всегда используй context7 MCP (если доступен).

## Project: sql-to-jooq
Gradle plugin that generates jOOQ Table, Record, Tables, and Repository classes from SQL migrations (Liquibase or Flyway).

## Key Architecture
- `SqlParser` parses SQL migrations into `TableDefinition` / `ColumnDefinition`
- Generators: `TableGenerator` (jOOQ TableImpl → XxxTable), `RecordGenerator` (data class → XxxRecord), `RepositoryGenerator`, `TablesGenerator`, `ValueClassGenerator`
- `TypeMapper` maps SQL types to Kotlin types and jOOQ DataTypes
- `SqlToJooqTask` orchestrates generation; `SqlToJooqExtension` holds configuration
- Value class IDs (`generateValueClassIds`) generates `@JvmInline value class {Table}Id(val value: {PkType})`

## Generated Package Structure
- `xxx.table/` — jOOQ TableImpl classes (e.g. `TariffTable`)
- `xxx.record/` — data classes (e.g. `TariffRecord`) + value class IDs (e.g. `TariffId`)
- `xxx.repository/` — Repository classes
- `xxx/` — Tables object, infrastructure (JooqRepository, AbstractJooqRepository, etc.)
