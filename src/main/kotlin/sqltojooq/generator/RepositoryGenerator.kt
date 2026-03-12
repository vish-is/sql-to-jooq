package sqltojooq.generator

import sqltojooq.parser.TableDefinition

object RepositoryGenerator {

    /**
     * Generates JooqRepository<P, ID> interface — the contract.
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
            appendLine(" * @param P  the generated Pojo class")
            appendLine(" * @param ID the primary key type")
            appendLine(" */")
            appendLine("interface JooqRepository<P : Any, ID : Any> {")
            appendLine()
            appendLine("    fun findById(id: ID): P?")
            appendLine()
            appendLine("    fun findAll(): List<P>")
            appendLine()
            appendLine("    fun findAllByPage(pageable: Pageable): Page<P>")
            appendLine()
            appendLine("    fun save(pojo: P): P")
            appendLine()
            appendLine("    fun saveAll(pojos: List<P>): List<P>")
            appendLine()
            appendLine("    fun deleteById(id: ID)")
            appendLine()
            appendLine("    fun existsById(id: ID): Boolean")
            appendLine("}")
        }
    }

    /**
     * Generates AbstractJooqRepository<P, ID> — implements the interface with
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
            appendLine(" * Concrete generated subclasses provide [table], [pojoClass], and [pkField].")
            appendLine(" * Developers add custom queries in hand-written subclasses.")
            appendLine(" */")
            appendLine("abstract class AbstractJooqRepository<P : Any, ID : Any>(")
            appendLine("    protected val dsl: DSLContext")
            appendLine(") : JooqRepository<P, ID> {")
            appendLine()
            appendLine("    protected abstract val table: TableImpl<Record>")
            appendLine("    protected abstract val pojoClass: Class<P>")
            appendLine("    protected abstract val pkField: Field<ID>")
            appendLine()
            appendLine("    override fun findById(id: ID): P? =")
            appendLine("        dsl.selectFrom(table)")
            appendLine("            .where(pkField.eq(id))")
            appendLine("            .fetchOneInto(pojoClass)")
            appendLine()
            appendLine("    override fun findAll(): List<P> =")
            appendLine("        dsl.selectFrom(table).fetchInto(pojoClass)")
            appendLine()
            appendLine("    override fun findAllByPage(pageable: Pageable): Page<P> {")
            appendLine("        val total = dsl.fetchCount(table).toLong()")
            appendLine("        val records = dsl.selectFrom(table)")
            appendLine("            .limit(pageable.pageSize)")
            appendLine("            .offset(pageable.offset)")
            appendLine("            .fetchInto(pojoClass)")
            appendLine("        return PageImpl(records, pageable, total)")
            appendLine("    }")
            appendLine()
            appendLine("    override fun save(pojo: P): P {")
            appendLine("        val record = dsl.newRecord(table, pojo)")
            appendLine("        dsl.insertInto(table)")
            appendLine("            .set(record)")
            appendLine("            .onConflict(pkField)")
            appendLine("            .doUpdate()")
            appendLine("            .setAllToExcluded()")
            appendLine("            .execute()")
            appendLine("        return pojo")
            appendLine("    }")
            appendLine()
            appendLine("    override fun saveAll(pojos: List<P>): List<P> =")
            appendLine("        pojos.map { save(it) }")
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
    fun generateConcrete(table: TableDefinition, packageName: String): String {
        val className = NameUtils.toPascalCase(table.name)
        val recordClass = "${className}Record"
        val generatedClass = "Generated${className}Repository"
        val instanceName = NameUtils.toUpperSnakeCase(table.name)

        val pkColumn = table.columns.firstOrNull { it.isPrimaryKey }
            ?: table.columns.first()
        val pkFieldName = NameUtils.toUpperSnakeCase(pkColumn.name)
        val pkKotlinType = TypeMapper.map(pkColumn.sqlType).kotlinType.substringAfterLast(".")

        val recordPkg = "$packageName.record"
        val pojoPkg = "$packageName.pojo"
        val repoPkg = "$packageName.repository"

        return buildString {
            appendLine("package $repoPkg")
            appendLine()
            appendLine("import org.jooq.DSLContext")
            appendLine("import org.jooq.Field")
            appendLine("import org.jooq.Record")
            appendLine("import org.jooq.impl.TableImpl")
            appendLine("import $recordPkg.$recordClass")
            appendLine("import $pojoPkg.$className")
            appendLine("import $packageName.AbstractJooqRepository")

            val pkImport = TypeMapper.map(pkColumn.sqlType).kotlinType
            if (pkImport.contains(".")) {
                appendLine("import $pkImport")
            }

            appendLine()
            appendLine("/**")
            appendLine(" * GENERATED — do not edit. Regenerated by the sql-to-jooq plugin on each run.")
            appendLine(" * Add custom query methods in [${className}Repository] which extends this class.")
            appendLine(" */")
            appendLine("open class $generatedClass(dsl: DSLContext) : AbstractJooqRepository<$className, $pkKotlinType>(dsl) {")
            appendLine()
            appendLine("    override val table: TableImpl<Record> = $recordClass.$instanceName as TableImpl<Record>")
            appendLine("    override val pojoClass: Class<$className> = $className::class.java")
            appendLine("    override val pkField: Field<$pkKotlinType> = $recordClass.$instanceName.$pkFieldName as Field<$pkKotlinType>")
            appendLine("}")
        }
    }

    /**
     * Generates PageFilter — abstract base class for all paginated query filters.
     * Lives in the root jOOQ package.
     */
    fun generatePageFilter(packageName: String): String {
        return buildString {
            appendLine("package $packageName")
            appendLine()
            appendLine("import org.jooq.SortField")
            appendLine()
            appendLine("const val DEFAULT_PAGE = 1")
            appendLine()
            appendLine("data class PageRange(")
            appendLine("    val from: Int = 1,")
            appendLine("    val to: Int = 2")
            appendLine(")")
            appendLine()
            appendLine("/**")
            appendLine(" * Abstract base for paginated query filters.")
            appendLine(" *")
            appendLine(" * Concrete filters extend this class and add table-specific filter fields.")
            appendLine(" * Pagination is page-based: use [page] for a single page or [pageRange] for a range of pages.")
            appendLine(" * [sortFields] overrides the default sort defined in [AbstractPageQuery.defaultSort].")
            appendLine(" */")
            appendLine("abstract class PageFilter(")
            appendLine("    open val limit: Int = 30,")
            appendLine("    open val page: Int? = null,")
            appendLine("    open val pageRange: PageRange? = null,")
            appendLine("    open val sortFields: List<SortField<*>>? = null")
            appendLine(")")
        }
    }

    /**
     * Generates PageResult<T> — the result of a paginated query.
     * Lives in the root jOOQ package.
     */
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

    /**
     * Generates AbstractPageQuery<F, T> — abstract class for building paginated
     * list queries with filtering and sorting, plus a builder for ad-hoc queries.
     * Lives in the root jOOQ package.
     */
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
            appendLine(" * [mapRow] for record-to-pojo mapping, and [defaultSort] for the default ORDER BY.")
            appendLine(" *")
            appendLine(" * @param F the concrete [PageFilter] subclass")
            appendLine(" * @param T the result item type (pojo or projection)")
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
            appendLine("    /**")
            appendLine("     * Builds the base SELECT query with WHERE/GROUP BY conditions derived from [filter].")
            appendLine("     * Do NOT apply ORDER BY, LIMIT, or OFFSET here — the framework handles that.")
            appendLine("     */")
            appendLine("    protected abstract fun buildSelect(filter: F): SelectHavingStep<*>")
            appendLine()
            appendLine("    /**")
            appendLine("     * Maps a single jOOQ [Record] to the result type [T].")
            appendLine("     */")
            appendLine("    protected abstract fun mapRow(record: Record): T")
            appendLine()
            appendLine("    /**")
            appendLine("     * Default sort order when [PageFilter.sortFields] is null.")
            appendLine("     */")
            appendLine("    protected abstract fun defaultSort(): List<SortField<*>>")
            appendLine()
            appendLine("    /**")
            appendLine("     * Validates the filter before executing the query.")
            appendLine("     * Override to add custom validation.")
            appendLine("     */")
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
            appendLine("    private fun calculateOffset(filter: F): Int =")
            appendLine("        filter.pageRange?.let { (it.from - 1) * filter.limit } ?: (((filter.page ?: DEFAULT_PAGE) - 1) * filter.limit)")
            appendLine()
            appendLine("    private fun calculateLimit(filter: F): Int =")
            appendLine("        filter.pageRange?.let { (it.to - it.from + 1) * filter.limit } ?: filter.limit")
            appendLine()
            appendLine("    companion object {")
            appendLine("        fun <F : PageFilter, T : Any> builder(dsl: DSLContext) = PageQueryBuilder<F, T>(dsl)")
            appendLine("    }")
            appendLine("}")
            appendLine()
            appendLine("/**")
            appendLine(" * Builder for creating [AbstractPageQuery] instances without subclassing.")
            appendLine(" * Useful for simple ad-hoc queries.")
            appendLine(" */")
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
    fun generateHandWrittenStub(table: TableDefinition, packageName: String): String {
        val className = NameUtils.toPascalCase(table.name)
        val generatedClass = "Generated${className}Repository"
        val concreteClass = "${className}Repository"

        val repoPkg = "$packageName.repository"
        val pojoPkg = "$packageName.pojo"

        val pkColumn = table.columns.firstOrNull { it.isPrimaryKey }
            ?: table.columns.first()

        return buildString {
            appendLine("package $repoPkg")
            appendLine()
            appendLine("import org.jooq.DSLContext")
            appendLine("import org.springframework.stereotype.Repository")
            appendLine("import $pojoPkg.$className")

            val pkImport = TypeMapper.map(pkColumn.sqlType).kotlinType
            if (pkImport.contains(".")) {
                appendLine("import $pkImport")
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
