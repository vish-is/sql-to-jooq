package sqltojooq.generator

import sqltojooq.parser.TableDefinition

object RepositoryGenerator {

    /**
     * Generates JooqRepository<R, ID> interface — the contract.
     * Lives in the root jOOQ package (e.g. db.jooq).
     */
    fun generateInterface(packageName: String): String {
        return buildString {
            appendLine("package $packageName")
            appendLine()
            appendLine("import org.springframework.data.domain.Page")
            appendLine("import org.springframework.data.domain.Pageable")
            appendLine()
            appendLine("/**")
            appendLine(" * Contract for all jOOQ-backed repositories.")
            appendLine(" *")
            appendLine(" * @param R  the generated Record class")
            appendLine(" * @param ID the primary key type")
            appendLine(" */")
            appendLine("interface JooqRepository<R : Any, ID : Any> {")
            appendLine()
            appendLine("    fun findById(id: ID): R?")
            appendLine()
            appendLine("    fun findAll(): List<R>")
            appendLine()
            appendLine("    fun findAllByPage(pageable: Pageable): Page<R>")
            appendLine()
            appendLine("    fun save(record: R): R")
            appendLine()
            appendLine("    fun saveAll(records: List<R>): List<R>")
            appendLine()
            appendLine("    fun deleteById(id: ID)")
            appendLine()
            appendLine("    fun existsById(id: ID): Boolean")
            appendLine("}")
        }
    }

    /**
     * Generates AbstractJooqRepository<R, ID> — implements the interface with
     * generic jOOQ DSL calls. Lives in the root jOOQ package.
     */
    fun generateAbstractBase(packageName: String): String {
        return buildString {
            appendLine("package $packageName")
            appendLine()
            appendLine("import org.jooq.DSLContext")
            appendLine("import org.jooq.Field")
            appendLine("import org.jooq.Record")
            appendLine("import org.jooq.impl.TableImpl")
            appendLine("import org.springframework.data.domain.Page")
            appendLine("import org.springframework.data.domain.PageImpl")
            appendLine("import org.springframework.data.domain.Pageable")
            appendLine()
            appendLine("/**")
            appendLine(" * Abstract base for all jOOQ-backed repositories.")
            appendLine(" * Provides generic CRUD operations via [DSLContext].")
            appendLine(" *")
            appendLine(" * Concrete generated subclasses provide [table], [recordClass], and [pkField].")
            appendLine(" * Developers add custom queries in hand-written subclasses.")
            appendLine(" */")
            appendLine("abstract class AbstractJooqRepository<R : Any, ID : Any>(")
            appendLine("    protected val dsl: DSLContext")
            appendLine(") : JooqRepository<R, ID> {")
            appendLine()
            appendLine("    protected abstract val table: TableImpl<Record>")
            appendLine("    protected abstract val recordClass: Class<R>")
            appendLine("    protected abstract val pkField: Field<ID>")
            appendLine()
            appendLine("    override fun findById(id: ID): R? =")
            appendLine("        dsl.selectFrom(table)")
            appendLine("            .where(pkField.eq(id))")
            appendLine("            .fetchOneInto(recordClass)")
            appendLine()
            appendLine("    override fun findAll(): List<R> =")
            appendLine("        dsl.selectFrom(table).fetchInto(recordClass)")
            appendLine()
            appendLine("    override fun findAllByPage(pageable: Pageable): Page<R> {")
            appendLine("        val total = dsl.fetchCount(table).toLong()")
            appendLine("        val records = dsl.selectFrom(table)")
            appendLine("            .limit(pageable.pageSize)")
            appendLine("            .offset(pageable.offset)")
            appendLine("            .fetchInto(recordClass)")
            appendLine("        return PageImpl(records, pageable, total)")
            appendLine("    }")
            appendLine()
            appendLine("    override fun save(record: R): R {")
            appendLine("        val jooqRecord = dsl.newRecord(table, record)")
            appendLine("        dsl.insertInto(table)")
            appendLine("            .set(jooqRecord)")
            appendLine("            .onConflict(pkField)")
            appendLine("            .doUpdate()")
            appendLine("            .setAllToExcluded()")
            appendLine("            .execute()")
            appendLine("        return record")
            appendLine("    }")
            appendLine()
            appendLine("    override fun saveAll(records: List<R>): List<R> =")
            appendLine("        records.map { save(it) }")
            appendLine()
            appendLine("    override fun deleteById(id: ID) {")
            appendLine("        dsl.deleteFrom(table)")
            appendLine("            .where(pkField.eq(id))")
            appendLine("            .execute()")
            appendLine("    }")
            appendLine()
            appendLine("    override fun existsById(id: ID): Boolean =")
            appendLine("        dsl.fetchExists(")
            appendLine("            dsl.selectOne().from(table).where(pkField.eq(id))")
            appendLine("        )")
            appendLine("}")
        }
    }

    /**
     * Generates a concrete Generated*Repository class for a single table.
     * This file lives in the `repository/` package and is always overwritten.
     */
    fun generateConcrete(table: TableDefinition, packageName: String, generateValueClassIds: Boolean = false): String {
        val recordClassName = NameUtils.toPascalCase(table.name) + "Record"
        val tableClassName = NameUtils.toPascalCase(table.name) + "Table"
        val generatedClass = "Generated${NameUtils.toPascalCase(table.name)}Repository"
        val instanceName = NameUtils.toUpperSnakeCase(table.name)

        val pkColumn = table.columns.firstOrNull { it.isPrimaryKey }
            ?: table.columns.first()
        val pkFieldName = NameUtils.toUpperSnakeCase(pkColumn.name)

        val valueClassName = if (generateValueClassIds) ValueClassGenerator.valueClassName(table) else null
        val pkKotlinType = valueClassName ?: TypeMapper.map(pkColumn.sqlType).kotlinType.substringAfterLast(".")

        val tablePkg = "$packageName.table"
        val recordPkg = "$packageName.record"
        val repoPkg = "$packageName.repository"

        return buildString {
            appendLine("package $repoPkg")
            appendLine()
            appendLine("import org.jooq.DSLContext")
            appendLine("import org.jooq.Field")
            appendLine("import org.jooq.Record")
            appendLine("import org.jooq.impl.TableImpl")
            appendLine("import $tablePkg.$tableClassName")
            appendLine("import $recordPkg.$recordClassName")
            appendLine("import $packageName.AbstractJooqRepository")

            if (valueClassName != null) {
                appendLine("import $recordPkg.$valueClassName")
            } else {
                val pkImport = TypeMapper.map(pkColumn.sqlType).kotlinType
                if (pkImport.contains(".")) {
                    appendLine("import $pkImport")
                }
            }

            appendLine()
            appendLine("/**")
            appendLine(" * GENERATED — do not edit. Regenerated by the sql-to-jooq plugin on each run.")
            appendLine(" * Add custom query methods in [${NameUtils.toPascalCase(table.name)}Repository] which extends this class.")
            appendLine(" */")
            appendLine("open class $generatedClass(dsl: DSLContext) : AbstractJooqRepository<$recordClassName, $pkKotlinType>(dsl) {")
            appendLine()
            appendLine("    override val table: TableImpl<Record> = $tableClassName.$instanceName as TableImpl<Record>")
            appendLine("    override val recordClass: Class<$recordClassName> = $recordClassName::class.java")
            appendLine("    override val pkField: Field<$pkKotlinType> = $tableClassName.$instanceName.$pkFieldName as Field<$pkKotlinType>")
            appendLine("}")
        }
    }

    fun generatePageFilter(packageName: String): String {
        return buildString {
            appendLine("package $packageName")
            appendLine()
            appendLine("import org.jooq.SortField")
            appendLine()
            appendLine("/**")
            appendLine(" * Abstract base for paginated query filters.")
            appendLine(" *")
            appendLine(" * Concrete filters extend this class and add table-specific filter fields.")
            appendLine(" * [sortFields] overrides the default sort defined in [AbstractPageQuery.defaultSort].")
            appendLine(" */")
            appendLine("abstract class PageFilter(")
            appendLine("    open val limit: Int = 30,")
            appendLine("    open val offset: Int = 0,")
            appendLine("    open val sortFields: List<SortField<*>>? = null")
            appendLine(")")
        }
    }

    fun generatePageResult(packageName: String): String {
        return buildString {
            appendLine("package $packageName")
            appendLine()
            appendLine("/**")
            appendLine(" * Result of a paginated query.")
            appendLine(" *")
            appendLine(" * @param T the type of items in the result list")
            appendLine(" * @property total total number of records matching the filter (before pagination)")
            appendLine(" * @property hasNext true if there are more records after the current page")
            appendLine(" * @property items the records on the current page")
            appendLine(" */")
            appendLine("data class PageResult<T>(")
            appendLine("    val total: Int,")
            appendLine("    val hasNext: Boolean,")
            appendLine("    val items: List<T>")
            appendLine(")")
        }
    }

    fun generateAbstractPageQuery(packageName: String): String {
        return buildString {
            appendLine("package $packageName")
            appendLine()
            appendLine("import org.jooq.DSLContext")
            appendLine("import org.jooq.Record")
            appendLine("import org.jooq.SelectHavingStep")
            appendLine("import org.jooq.SortField")
            appendLine()
            appendLine("/**")
            appendLine(" * Abstract base for paginated list queries with filtering and sorting.")
            appendLine(" *")
            appendLine(" * Subclasses implement [buildSelect] to define the SELECT + WHERE/GROUP BY,")
            appendLine(" * [mapRow] for record-to-result mapping, and [defaultSort] for the default ORDER BY.")
            appendLine(" *")
            appendLine(" * @param F the concrete [PageFilter] subclass")
            appendLine(" * @param T the result item type")
            appendLine(" */")
            appendLine("abstract class AbstractPageQuery<F : PageFilter, T : Any>(")
            appendLine("    protected open val dsl: DSLContext")
            appendLine(") {")
            appendLine()
            appendLine("    /**")
            appendLine("     * Executes the paginated query: validates, counts total, fetches a page of items,")
            appendLine("     * and computes [PageResult.hasNext].")
            appendLine("     */")
            appendLine("    fun execute(filter: F): PageResult<T> {")
            appendLine("        validate(filter)")
            appendLine()
            appendLine("        val total = countTotal(filter)")
            appendLine("        return if (total == 0) {")
            appendLine("            PageResult(total = 0, hasNext = false, items = emptyList())")
            appendLine("        } else {")
            appendLine("            val items = fetchItems(filter)")
            appendLine("            val hasNext = calculateOffset(filter) + calculateLimit(filter) < total")
            appendLine("            PageResult(total = total, hasNext = hasNext, items = items)")
            appendLine("        }")
            appendLine("    }")
            appendLine()
            appendLine("    protected abstract fun buildSelect(filter: F): SelectHavingStep<*>")
            appendLine()
            appendLine("    protected abstract fun mapRow(record: Record): T")
            appendLine()
            appendLine("    protected abstract fun defaultSort(): List<SortField<*>>")
            appendLine()
            appendLine("    protected open fun validate(filter: F) {}")
            appendLine()
            appendLine("    private fun countTotal(filter: F): Int = dsl")
            appendLine("        .selectCount()")
            appendLine("        .from(buildSelect(filter))")
            appendLine("        .fetchOneInto(Int::class.java) ?: 0")
            appendLine()
            appendLine("    private fun fetchItems(filter: F): List<T> {")
            appendLine("        val sort = filter.sortFields ?: defaultSort()")
            appendLine("        return buildSelect(filter)")
            appendLine("            .orderBy(sort)")
            appendLine("            .offset(calculateOffset(filter))")
            appendLine("            .limit(calculateLimit(filter))")
            appendLine("            .fetch()")
            appendLine("            .map(::mapRow)")
            appendLine("    }")
            appendLine()
            appendLine("    private fun calculateOffset(filter: F): Int = filter.offset")
            appendLine()
            appendLine("    private fun calculateLimit(filter: F): Int = filter.limit")
            appendLine()
            appendLine("    companion object {")
            appendLine("        fun <F : PageFilter, T : Any> builder(dsl: DSLContext) = PageQueryBuilder<F, T>(dsl)")
            appendLine("    }")
            appendLine("}")
            appendLine()
            appendLine("class PageQueryBuilder<F : PageFilter, T : Any>(")
            appendLine("    private val dsl: DSLContext")
            appendLine(") {")
            appendLine("    private var selectBuilder: ((F) -> SelectHavingStep<*>)? = null")
            appendLine("    private var mapper: ((Record) -> T)? = null")
            appendLine("    private var orderByFields: (() -> List<SortField<*>>)? = null")
            appendLine("    private var validator: ((F) -> Unit)? = null")
            appendLine()
            appendLine("    fun select(builder: (F) -> SelectHavingStep<*>) = apply {")
            appendLine("        this.selectBuilder = builder")
            appendLine("    }")
            appendLine()
            appendLine("    fun map(mapper: (Record) -> T) = apply {")
            appendLine("        this.mapper = mapper")
            appendLine("    }")
            appendLine()
            appendLine("    fun orderBy(fields: () -> List<SortField<*>>) = apply {")
            appendLine("        this.orderByFields = fields")
            appendLine("    }")
            appendLine()
            appendLine("    fun validate(validator: (F) -> Unit) = apply {")
            appendLine("        this.validator = validator")
            appendLine("    }")
            appendLine()
            appendLine("    fun build(): AbstractPageQuery<F, T> {")
            appendLine("        require(selectBuilder != null) { \"select builder is required\" }")
            appendLine("        require(mapper != null) { \"mapper is required\" }")
            appendLine("        require(orderByFields != null) { \"orderBy fields are required\" }")
            appendLine()
            appendLine("        return ConcretePageQuery(dsl, selectBuilder!!, mapper!!, orderByFields!!, validator)")
            appendLine("    }")
            appendLine("}")
            appendLine()
            appendLine("private class ConcretePageQuery<F : PageFilter, T : Any>(")
            appendLine("    dsl: DSLContext,")
            appendLine("    private val selectBuilder: (F) -> SelectHavingStep<*>,")
            appendLine("    private val mapper: (Record) -> T,")
            appendLine("    private val orderByFields: () -> List<SortField<*>>,")
            appendLine("    private val validator: ((F) -> Unit)?")
            appendLine(") : AbstractPageQuery<F, T>(dsl) {")
            appendLine()
            appendLine("    override fun buildSelect(filter: F): SelectHavingStep<*> = selectBuilder(filter)")
            appendLine()
            appendLine("    override fun mapRow(record: Record): T = mapper(record)")
            appendLine()
            appendLine("    override fun defaultSort(): List<SortField<*>> = orderByFields()")
            appendLine()
            appendLine("    override fun validate(filter: F) {")
            appendLine("        validator?.invoke(filter)")
            appendLine("    }")
            appendLine("}")
        }
    }

    /**
     * Generates a hand-written stub *Repository class. Emitted ONLY IF the file
     * does not already exist. Developers own this file — never overwritten.
     */
    fun generateHandWrittenStub(table: TableDefinition, packageName: String, generateValueClassIds: Boolean = false): String {
        val recordClassName = NameUtils.toPascalCase(table.name) + "Record"
        val generatedClass = "Generated${NameUtils.toPascalCase(table.name)}Repository"
        val concreteClass = "${NameUtils.toPascalCase(table.name)}Repository"

        val repoPkg = "$packageName.repository"
        val recordPkg = "$packageName.record"

        val pkColumn = table.columns.firstOrNull { it.isPrimaryKey }
            ?: table.columns.first()

        val valueClassName = if (generateValueClassIds) ValueClassGenerator.valueClassName(table) else null

        return buildString {
            appendLine("package $repoPkg")
            appendLine()
            appendLine("import org.jooq.DSLContext")
            appendLine("import org.springframework.stereotype.Repository")
            appendLine("import $recordPkg.$recordClassName")

            if (valueClassName != null) {
                appendLine("import $recordPkg.$valueClassName")
            } else {
                val pkImport = TypeMapper.map(pkColumn.sqlType).kotlinType
                if (pkImport.contains(".")) {
                    appendLine("import $pkImport")
                }
            }

            appendLine()
            appendLine("/**")
            appendLine(" * Repository for the [${table.name}] table.")
            appendLine(" * Add custom query methods here. This file is NEVER overwritten by the plugin.")
            appendLine(" */")
            appendLine("@Repository")
            appendLine("class $concreteClass(dsl: DSLContext) : $generatedClass(dsl)")
        }
    }
}
