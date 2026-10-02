package com.society.app.ui.viewmodel

import android.content.Context
import android.net.Uri
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
import com.society.app.util.GeminiVisionService
import com.society.app.util.MlKitOcrService
import com.society.app.util.ParsedCollectionRow
import com.society.app.util.ParsedExpenseRow
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

    // Currently selected category (e.g. "Monthly Maintenance", "Navratri Collection", etc.)
    private val _selectedCategory = MutableStateFlow(FundCategory.CATEGORY_MAINTENANCE)
    val selectedCategory: StateFlow<String> = _selectedCategory.asStateFlow()

    // Currently selected month filter for reports / view (null means all months / overall)
    private val _selectedMonth = MutableStateFlow<String?>(null)
    val selectedMonth: StateFlow<String?> = _selectedMonth.asStateFlow()

    // List of available categories (Default + persistent custom categories added by user)
    private val _categories = MutableStateFlow(FundCategory.DEFAULT_CATEGORIES + repository.loadCustomCategories())
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
                description = if (description.isNotBlank()) description.trim() else "$trimmed Fund & Celebrations",
                isMonthly = false,
                iconType = "festival"
            )
            val updated = _categories.value + newCat
            _categories.value = updated
            repository.saveCustomCategories(updated)
        }
        _selectedCategory.value = trimmed
    }

    fun editCustomCategory(oldName: String, newName: String, newDescription: String = "") {
        val trimmedNew = newName.trim()
        if (trimmedNew.isBlank()) return
        val currentList = _categories.value.toMutableList()
        val index = currentList.indexOfFirst { it.id.equals(oldName, ignoreCase = true) }
        if (index != -1) {
            val existing = currentList[index]
            val updatedCat = existing.copy(
                id = trimmedNew,
                displayName = trimmedNew,
                description = if (newDescription.isNotBlank()) newDescription.trim() else existing.description
            )
            currentList[index] = updatedCat
            _categories.value = currentList
            repository.saveCustomCategories(currentList)

            // Update database records asynchronously so existing collections & expenses match the new name
            viewModelScope.launch {
                repository.updateCategoryName(oldName, trimmedNew)
            }

            // If this was the active category, update selectedCategory
            if (_selectedCategory.value.equals(oldName, ignoreCase = true)) {
                _selectedCategory.value = trimmedNew
            }
        }
    }

    fun deleteCustomCategory(name: String) {
        val currentList = _categories.value.filterNot { it.id.equals(name, ignoreCase = true) }
        _categories.value = currentList
        repository.saveCustomCategories(currentList)

        // Delete associated records from database
        viewModelScope.launch {
            repository.deleteCategoryData(name)
        }

        // If the deleted category was active, revert to Monthly Maintenance
        if (_selectedCategory.value.equals(name, ignoreCase = true)) {
            _selectedCategory.value = FundCategory.CATEGORY_MAINTENANCE
        }
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

    fun normalizeFlatKey(block: String, flatNo: String): String {
        val cleanBlock = block.trim().uppercase()
        val cleanFlat = flatNo.trim().uppercase().replace(" ", "").replace("-", "")
        return if (cleanBlock.isNotBlank() && !cleanFlat.startsWith(cleanBlock)) {
            "$cleanBlock$cleanFlat"
        } else {
            cleanFlat
        }
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
            val targetKey = normalizeFlatKey(block, flatNo)

            val existingList = repository.getExistingCollections(category, finalMonth)
            val existing = existingList.find { normalizeFlatKey(it.block, it.flatNo) == targetKey }

            if (existing != null) {
                if (kotlin.math.abs(existing.amount - amount) < 0.01) {
                    // Same amount -> Ignore duplicate
                    _statusMessage.value = "Duplicate ignored: Flat $flatNo already has ₹$amount recorded for $finalMonth."
                    return@launch
                } else {
                    // Different amount -> Update existing record
                    val updated = existing.copy(
                        amount = amount,
                        ownerName = ownerName.trim().ifBlank { existing.ownerName },
                        paymentMode = paymentMode,
                        date = currentDate,
                        timestamp = System.currentTimeMillis()
                    )
                    repository.updateCollection(updated)
                    _statusMessage.value = "Updated Flat $flatNo amount to ₹$amount for $finalMonth (was ₹${existing.amount})."
                    return@launch
                }
            }

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
            _statusMessage.value = "Saved collection for Flat $flatNo (₹$amount)."
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

    // === AI VISION & OFFLINE ML KIT SCANNING ===
    enum class ScanEngine {
        GEMINI_CLOUD,
        OFFLINE_MLKIT
    }

    private val _scanEngine = MutableStateFlow(ScanEngine.OFFLINE_MLKIT)
    val scanEngine: StateFlow<ScanEngine> = _scanEngine.asStateFlow()

    private val _geminiApiKey = MutableStateFlow(repository.getGeminiApiKey())
    val geminiApiKey: StateFlow<String> = _geminiApiKey.asStateFlow()

    private val _isAiScanning = MutableStateFlow(false)
    val isAiScanning: StateFlow<Boolean> = _isAiScanning.asStateFlow()

    private val _aiScanError = MutableStateFlow<String?>(null)
    val aiScanError: StateFlow<String?> = _aiScanError.asStateFlow()

    private val _scannedCollections = MutableStateFlow<List<ParsedCollectionRow>?>(null)
    val scannedCollections: StateFlow<List<ParsedCollectionRow>?> = _scannedCollections.asStateFlow()

    private val _scannedExpense = MutableStateFlow<ParsedExpenseRow?>(null)
    val scannedExpense: StateFlow<ParsedExpenseRow?> = _scannedExpense.asStateFlow()

    private val _statusMessage = MutableStateFlow<String?>(null)
    val statusMessage: StateFlow<String?> = _statusMessage.asStateFlow()

    fun clearStatusMessage() {
        _statusMessage.value = null
    }

    fun setScanEngine(engine: ScanEngine) {
        _scanEngine.value = engine
    }

    fun saveGeminiApiKey(key: String) {
        val trimmed = key.trim()
        repository.saveGeminiApiKey(trimmed)
        _geminiApiKey.value = trimmed
    }

    fun clearAiScanState() {
        _isAiScanning.value = false
        _aiScanError.value = null
        _scannedCollections.value = null
        _scannedExpense.value = null
    }

    fun setScannedCollections(rows: List<ParsedCollectionRow>) {
        _scannedCollections.value = rows
    }

    fun scanCollectionImage(
        context: Context,
        imageUri: Uri,
        forceEngine: ScanEngine? = null
    ) {
        val engine = forceEngine ?: _scanEngine.value

        viewModelScope.launch {
            _isAiScanning.value = true
            _aiScanError.value = null
            _scannedCollections.value = null

            val result = if (engine == ScanEngine.GEMINI_CLOUD) {
                GeminiVisionService.extractCollectionsFromImage(
                    context = context,
                    imageUri = imageUri,
                    apiKey = _geminiApiKey.value
                )
            } else {
                MlKitOcrService.extractCollectionsFromImage(
                    context = context,
                    imageUri = imageUri
                )
            }

            result.onSuccess { rows ->
                _scannedCollections.value = rows
            }.onFailure { err ->
                _aiScanError.value = err.message ?: "Failed to extract collection records from image."
            }

            _isAiScanning.value = false
        }
    }

    fun scanExpenseImage(context: Context, imageUri: Uri, forceEngine: ScanEngine? = null) {
        val engine = forceEngine ?: _scanEngine.value
        viewModelScope.launch {
            _isAiScanning.value = true
            _aiScanError.value = null
            _scannedExpense.value = null

            val result = if (engine == ScanEngine.GEMINI_CLOUD) {
                GeminiVisionService.extractExpenseFromImage(
                    context = context,
                    imageUri = imageUri,
                    apiKey = _geminiApiKey.value
                )
            } else {
                MlKitOcrService.extractExpenseFromImage(
                    context = context,
                    imageUri = imageUri
                )
            }

            result.onSuccess { expense ->
                _scannedExpense.value = expense
            }.onFailure { err ->
                _aiScanError.value = err.message ?: "Failed to extract expense details from bill."
            }

            _isAiScanning.value = false
        }
    }

    fun saveBatchCollections(
        items: List<ParsedCollectionRow>,
        targetMonthYear: String,
        targetCategory: String = _selectedCategory.value
    ) {
        viewModelScope.launch {
            val currentDate = DateUtil.getCurrentDate()
            val existingList = repository.getExistingCollections(targetCategory, targetMonthYear)
            val existingMap = existingList.associateBy { normalizeFlatKey(it.block, it.flatNo) }

            var insertedCount = 0
            var updatedCount = 0
            var ignoredCount = 0

            val toInsert = mutableListOf<CollectionEntity>()
            val toUpdate = mutableListOf<CollectionEntity>()

            val processedBatchKeys = mutableMapOf<String, ParsedCollectionRow>()

            for (item in items) {
                val block = item.block.ifBlank { "General" }
                val flatNo = item.flatNo
                val key = normalizeFlatKey(block, flatNo)

                val prevInBatch = processedBatchKeys[key]
                if (prevInBatch != null && kotlin.math.abs(prevInBatch.amount - item.amount) < 0.01) {
                    ignoredCount++
                    continue
                }
                processedBatchKeys[key] = item

                val existing = existingMap[key]

                if (existing != null) {
                    val isSameAmount = kotlin.math.abs(existing.amount - item.amount) < 0.01
                    val isSameMode = existing.paymentMode.equals(item.paymentMode, ignoreCase = true)
                    val isSameName = existing.ownerName.equals(item.ownerName, ignoreCase = true)

                    if (isSameAmount && isSameMode && isSameName) {
                        // Same record -> Ignore duplicate
                        ignoredCount++
                    } else {
                        // Amount, Payment Mode, or Name changed -> Update existing record
                        toUpdate.add(
                            existing.copy(
                                amount = item.amount,
                                ownerName = item.ownerName.ifBlank { existing.ownerName },
                                paymentMode = item.paymentMode,
                                date = currentDate,
                                timestamp = System.currentTimeMillis()
                            )
                        )
                        updatedCount++
                    }
                } else {
                    // New record -> Insert
                    toInsert.add(
                        CollectionEntity(
                            category = targetCategory,
                            monthYear = targetMonthYear,
                            block = block,
                            flatNo = flatNo,
                            ownerName = item.ownerName.ifBlank { "Resident" },
                            amount = item.amount,
                            paymentMode = item.paymentMode,
                            date = currentDate
                        )
                    )
                    insertedCount++
                }
            }

            if (toInsert.isNotEmpty()) {
                repository.addCollections(toInsert)
            }
            if (toUpdate.isNotEmpty()) {
                repository.updateCollections(toUpdate)
            }

            clearAiScanState()

            val msg = buildString {
                append("Import complete: ")
                val parts = mutableListOf<String>()
                if (insertedCount > 0) parts.add("$insertedCount added")
                if (updatedCount > 0) parts.add("$updatedCount updated")
                if (ignoredCount > 0) parts.add("$ignoredCount duplicates ignored")
                if (parts.isEmpty()) append("No records processed.") else append(parts.joinToString(", "))
            }
            _statusMessage.value = msg
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
