package com.society.app.ui.viewmodel

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.society.app.data.model.BlockSummary
import com.society.app.data.model.CollectionEntity
import com.society.app.data.model.ExpenseEntity
import com.society.app.data.model.FundCategory
import com.society.app.data.repository.SocietyRepository
import com.society.app.util.CsvExporter
import com.society.app.util.DateUtil
import com.society.app.util.PdfExporter
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@OptIn(ExperimentalCoroutinesApi::class)
class SocietyViewModel(private val repository: SocietyRepository) : ViewModel() {

    // Currently selected category (e.g. "Monthly Maintenance", "Navratri Festival", "Ganpati Festival")
    private val _selectedCategory = MutableStateFlow(FundCategory.CATEGORY_MAINTENANCE)
    val selectedCategory: StateFlow<String> = _selectedCategory.asStateFlow()

    // Currently selected month filter for reports / view (null means all months / overall)
    private val _selectedMonth = MutableStateFlow<String?>(null)
    val selectedMonth: StateFlow<String?> = _selectedMonth.asStateFlow()

    // List of available categories (Default + custom added by society)
    private val _categories = MutableStateFlow(FundCategory.DEFAULT_CATEGORIES)
    val categories: StateFlow<List<FundCategory>> = _categories.asStateFlow()

    fun selectCategory(category: String) {
        _selectedCategory.value = category
        // Reset month filter when switching categories
        _selectedMonth.value = null
    }

    fun selectMonth(monthYear: String?) {
        _selectedMonth.value = monthYear
    }

    fun addCustomCategory(name: String, description: String = "") {
        val trimmed = name.trim()
        if (trimmed.isBlank()) return
        if (_categories.value.none { it.id.equals(trimmed, ignoreCase = true) }) {
            val newCat = FundCategory(
                id = trimmed,
                displayName = trimmed,
                description = if (description.isNotBlank()) description else "$trimmed Fund & Celebrations",
                isMonthly = false,
                iconType = "festival"
            )
            _categories.value = _categories.value + newCat
        }
        _selectedCategory.value = trimmed
    }

    // Reactive streams scoped to selectedCategory and selectedMonth
    val collections: StateFlow<List<CollectionEntity>> = combine(_selectedCategory, _selectedMonth) { cat, month ->
        cat to month
    }.flatMapLatest { (cat, month) ->
        repository.getCollections(cat, month)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val expenses: StateFlow<List<ExpenseEntity>> = combine(_selectedCategory, _selectedMonth) { cat, month ->
        cat to month
    }.flatMapLatest { (cat, month) ->
        repository.getExpenses(cat, month)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val blockSummaries: StateFlow<List<BlockSummary>> = combine(_selectedCategory, _selectedMonth) { cat, month ->
        cat to month
    }.flatMapLatest { (cat, month) ->
        repository.getBlockSummaries(cat, month)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val totalCollection: StateFlow<Double> = combine(_selectedCategory, _selectedMonth) { cat, month ->
        cat to month
    }.flatMapLatest { (cat, month) ->
        repository.getTotalCollectionAmount(cat, month)
    }.map { it ?: 0.0 }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0.0)

    val totalCash: StateFlow<Double> = combine(_selectedCategory, _selectedMonth) { cat, month ->
        cat to month
    }.flatMapLatest { (cat, month) ->
        repository.getTotalCashAmount(cat, month)
    }.map { it ?: 0.0 }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0.0)

    val totalOnline: StateFlow<Double> = combine(_selectedCategory, _selectedMonth) { cat, month ->
        cat to month
    }.flatMapLatest { (cat, month) ->
        repository.getTotalOnlineAmount(cat, month)
    }.map { it ?: 0.0 }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0.0)

    val totalExpense: StateFlow<Double> = combine(_selectedCategory, _selectedMonth) { cat, month ->
        cat to month
    }.flatMapLatest { (cat, month) ->
        repository.getTotalExpenseAmount(cat, month)
    }.map { it ?: 0.0 }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0.0)

    val netBalance: StateFlow<Double> = combine(totalCollection, totalExpense) { col, exp ->
        col - exp
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0.0)

    val distinctBlocks: StateFlow<List<String>> = _selectedCategory.flatMapLatest { cat ->
        repository.getDistinctBlocks(cat)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val distinctMonths: StateFlow<List<String>> = _selectedCategory.flatMapLatest { cat ->
        repository.getDistinctMonths(cat)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Overall category totals (ignoring month filter) for summary cards
    val overallTotalCollection: StateFlow<Double> = _selectedCategory.flatMapLatest { cat ->
        repository.getTotalCollectionAmount(cat, null)
    }.map { it ?: 0.0 }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0.0)

    val overallTotalExpense: StateFlow<Double> = _selectedCategory.flatMapLatest { cat ->
        repository.getTotalExpenseAmount(cat, null)
    }.map { it ?: 0.0 }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0.0)

    val overallNetBalance: StateFlow<Double> = combine(overallTotalCollection, overallTotalExpense) { col, exp ->
        col - exp
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0.0)

    /**
     * Parses combined input like "B-504", "B 504", "B504", "A-102" into Pair("B", "504")
     */
    fun parseBlockAndFlat(rawInput: String): Pair<String, String> {
        val trimmed = rawInput.trim()

        // Pattern 1: B-504, B 504, Block B-504, Wing A/102
        val regexWithSep = Regex("""^(?:Block|Wing)?\s*([A-Za-z0-9]+)\s*[-/_\s]+\s*([0-9A-Za-z]+)$""", RegexOption.IGNORE_CASE)
        val matchWithSep = regexWithSep.find(trimmed)
        if (matchWithSep != null) {
            val block = matchWithSep.groupValues[1].uppercase()
            val flat = matchWithSep.groupValues[2]
            return Pair(block, flat)
        }

        // Pattern 2: B504, A102 (letters followed by numbers)
        val regexCombined = Regex("""^([A-Za-z]+)(\d+)$""")
        val matchCombined = regexCombined.find(trimmed)
        if (matchCombined != null) {
            val block = matchCombined.groupValues[1].uppercase()
            val flat = matchCombined.groupValues[2]
            return Pair(block, flat)
        }

        // Fallback: If only flat number is entered
        return Pair("General", trimmed)
    }

    fun addCollectionFromCombinedInput(
        rawFlatInput: String,
        ownerName: String,
        amount: Double,
        paymentMode: String,
        category: String = _selectedCategory.value,
        monthYear: String = ""
    ) {
        val (block, flatNo) = parseBlockAndFlat(rawFlatInput)
        viewModelScope.launch {
            val currentDate = DateUtil.getCurrentDate()
            val finalMonth = if (monthYear.isNotBlank()) monthYear else DateUtil.getCurrentMonthYear()
            val item = CollectionEntity(
                category = category,
                monthYear = finalMonth,
                block = block,
                flatNo = flatNo,
                ownerName = ownerName.trim(),
                amount = amount,
                paymentMode = paymentMode,
                date = currentDate
            )
            repository.addCollection(item)
        }
    }

    fun deleteCollection(collection: CollectionEntity) {
        viewModelScope.launch {
            repository.deleteCollection(collection)
        }
    }

    fun addExpense(
        detail: String,
        amount: Double,
        category: String = _selectedCategory.value,
        monthYear: String = ""
    ) {
        viewModelScope.launch {
            val currentDate = DateUtil.getCurrentDate()
            val finalMonth = if (monthYear.isNotBlank()) monthYear else DateUtil.getCurrentMonthYear()
            val item = ExpenseEntity(
                category = category,
                monthYear = finalMonth,
                detail = detail.trim(),
                amount = amount,
                date = currentDate
            )
            repository.addExpense(item)
        }
    }

    fun deleteExpense(expense: ExpenseEntity) {
        viewModelScope.launch {
            repository.deleteExpense(expense)
        }
    }

    // === PDF EXPORTS ===

    fun exportCollectionsPdf(
        context: Context,
        blockFilter: String? = null,
        category: String = _selectedCategory.value,
        monthFilter: String? = _selectedMonth.value
    ) {
        viewModelScope.launch {
            val list = repository.getCollectionsForExport(category, monthFilter, blockFilter)
            PdfExporter.exportCollectionsPdf(context, list, category, monthFilter, blockFilter)
        }
    }

    fun exportExpensesPdf(
        context: Context,
        category: String = _selectedCategory.value,
        monthFilter: String? = _selectedMonth.value
    ) {
        viewModelScope.launch {
            val list = repository.getExpensesForExport(category, monthFilter)
            PdfExporter.exportExpensesPdf(context, list, category, monthFilter)
        }
    }

    fun exportConsolidatedSummaryPdf(
        context: Context,
        category: String = _selectedCategory.value,
        monthFilter: String? = _selectedMonth.value
    ) {
        viewModelScope.launch {
            val summaries = repository.getBlockSummariesForExport(category, monthFilter)
            val totCol = totalCollection.value
            val totExp = totalExpense.value
            val bal = netBalance.value
            PdfExporter.exportConsolidatedSummaryPdf(
                context = context,
                blockSummaries = summaries,
                totalCollection = totCol,
                totalExpense = totExp,
                netBalance = bal,
                category = category,
                monthFilter = monthFilter
            )
        }
    }

    // === CSV EXPORTS ===

    fun exportBlockCsv(
        context: Context,
        blockName: String,
        category: String = _selectedCategory.value,
        monthFilter: String? = _selectedMonth.value
    ) {
        viewModelScope.launch {
            val list = repository.getCollectionsForExport(category, monthFilter, blockName)
            CsvExporter.exportBlockReport(context, blockName, list, category, monthFilter)
        }
    }

    fun exportConsolidatedCsv(
        context: Context,
        category: String = _selectedCategory.value,
        monthFilter: String? = _selectedMonth.value
    ) {
        viewModelScope.launch {
            val allCol = repository.getCollectionsForExport(category, monthFilter, null)
            val allExp = repository.getExpensesForExport(category, monthFilter)
            val summaries = repository.getBlockSummariesForExport(category, monthFilter)
            val totCol = totalCollection.value
            val totExp = totalExpense.value
            val bal = netBalance.value
            CsvExporter.exportConsolidatedReport(
                context = context,
                blockSummaries = summaries,
                collections = allCol,
                expenses = allExp,
                totalCollection = totCol,
                totalExpense = totExp,
                netBalance = bal,
                category = category,
                monthFilter = monthFilter
            )
        }
    }
}

class SocietyViewModelFactory(private val repository: SocietyRepository) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(SocietyViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return SocietyViewModel(repository) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
