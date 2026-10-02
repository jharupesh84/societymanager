package com.society.app.data.repository

import android.content.Context
import com.society.app.data.local.SocietyDao
import com.society.app.data.model.BlockSummary
import com.society.app.data.model.CollectionEntity
import com.society.app.data.model.ExpenseEntity
import com.society.app.data.model.FundCategory
import kotlinx.coroutines.flow.Flow
import org.json.JSONArray
import org.json.JSONObject

class SocietyRepository(
    private val dao: SocietyDao,
    private val context: Context? = null
) {

    private val prefs by lazy {
        context?.getSharedPreferences("society_categories_prefs", Context.MODE_PRIVATE)
    }

    // === GEMINI AI API KEY ===
    fun getGeminiApiKey(): String = prefs?.getString("gemini_api_key", "") ?: ""
    fun saveGeminiApiKey(key: String) {
        prefs?.edit()?.putString("gemini_api_key", key.trim())?.apply()
    }

    // === PERSISTENCE FOR CUSTOM CATEGORIES ===

    fun loadCustomCategories(): List<FundCategory> {
        val jsonString = prefs?.getString("custom_categories_json", null) ?: return emptyList()
        return try {
            val jsonArray = JSONArray(jsonString)
            val list = mutableListOf<FundCategory>()
            for (i in 0 until jsonArray.length()) {
                val obj = jsonArray.getJSONObject(i)
                val name = obj.getString("name")
                val desc = obj.optString("desc", "$name Fund & Celebrations")
                list.add(
                    FundCategory(
                        id = name,
                        displayName = name,
                        description = desc,
                        isMonthly = false,
                        iconType = "festival"
                    )
                )
            }
            list
        } catch (e: Exception) {
            emptyList()
        }
    }

    fun saveCustomCategories(categories: List<FundCategory>) {
        val customOnly = categories.filter { cat ->
            cat.id != FundCategory.CATEGORY_MAINTENANCE && cat.id != FundCategory.CATEGORY_NAVRATRI
        }
        val jsonArray = JSONArray()
        for (cat in customOnly) {
            val obj = JSONObject().apply {
                put("name", cat.id)
                put("desc", cat.description)
            }
            jsonArray.put(obj)
        }
        prefs?.edit()?.putString("custom_categories_json", jsonArray.toString())?.apply()
    }

    suspend fun updateCategoryName(oldCategory: String, newCategory: String) {
        dao.updateCollectionCategory(oldCategory, newCategory)
        dao.updateExpenseCategory(oldCategory, newCategory)
    }

    suspend fun deleteCategoryData(category: String) {
        dao.deleteCollectionsByCategory(category)
        dao.deleteExpensesByCategory(category)
    }

    // === COLLECTIONS ===

    fun getCollections(category: String, monthYear: String? = null): Flow<List<CollectionEntity>> {
        return if (monthYear.isNullOrBlank() || monthYear.equals("All Months", ignoreCase = true)) {
            dao.getCollections(category)
        } else {
            dao.getCollectionsByMonth(category, monthYear)
        }
    }

    fun getCollectionsByBlock(category: String, block: String, monthYear: String? = null): Flow<List<CollectionEntity>> {
        return if (monthYear.isNullOrBlank() || monthYear.equals("All Months", ignoreCase = true)) {
            dao.getCollectionsByBlock(category, block)
        } else {
            dao.getCollectionsByBlockAndMonth(category, block, monthYear)
        }
    }

    fun getBlockSummaries(category: String, monthYear: String? = null): Flow<List<BlockSummary>> {
        return if (monthYear.isNullOrBlank() || monthYear.equals("All Months", ignoreCase = true)) {
            dao.getBlockSummaries(category)
        } else {
            dao.getBlockSummariesByMonth(category, monthYear)
        }
    }

    fun getTotalCollectionAmount(category: String, monthYear: String? = null): Flow<Double?> {
        return if (monthYear.isNullOrBlank() || monthYear.equals("All Months", ignoreCase = true)) {
            dao.getTotalCollectionAmount(category)
        } else {
            dao.getTotalCollectionAmountByMonth(category, monthYear)
        }
    }

    fun getTotalCashAmount(category: String, monthYear: String? = null): Flow<Double?> {
        return if (monthYear.isNullOrBlank() || monthYear.equals("All Months", ignoreCase = true)) {
            dao.getTotalCashCollectionAmount(category)
        } else {
            dao.getTotalCashCollectionAmountByMonth(category, monthYear)
        }
    }

    fun getTotalOnlineAmount(category: String, monthYear: String? = null): Flow<Double?> {
        return if (monthYear.isNullOrBlank() || monthYear.equals("All Months", ignoreCase = true)) {
            dao.getTotalOnlineCollectionAmount(category)
        } else {
            dao.getTotalOnlineCollectionAmountByMonth(category, monthYear)
        }
    }

    fun getDistinctBlocks(category: String): Flow<List<String>> =
        dao.getDistinctBlocks(category)

    fun getDistinctMonths(category: String): Flow<List<String>> =
        dao.getDistinctMonths(category)

    fun getAllRecordedCategories(): Flow<List<String>> =
        dao.getAllRecordedCategories()

    suspend fun addCollection(collection: CollectionEntity): Long {
        return dao.insertCollection(collection)
    }

    suspend fun addCollections(collections: List<CollectionEntity>): List<Long> {
        return dao.insertCollections(collections)
    }

    suspend fun deleteCollection(collection: CollectionEntity) {
        dao.deleteCollection(collection)
    }

    suspend fun getCollectionsForExport(
        category: String,
        monthYear: String? = null,
        block: String? = null
    ): List<CollectionEntity> {
        val isAllMonths = monthYear.isNullOrBlank() || monthYear.equals("All Months", ignoreCase = true)
        val isAllBlocks = block.isNullOrBlank()
        return when {
            isAllMonths && isAllBlocks -> dao.getCollectionsForExportAll(category)
            !isAllMonths && isAllBlocks -> dao.getCollectionsForExportByMonth(category, monthYear!!)
            isAllMonths && !isAllBlocks -> dao.getCollectionsForExportByBlock(category, block!!)
            else -> dao.getCollectionsForExportByBlockAndMonth(category, block!!, monthYear!!)
        }
    }

    suspend fun getBlockSummariesForExport(
        category: String,
        monthYear: String? = null
    ): List<BlockSummary> {
        return if (monthYear.isNullOrBlank() || monthYear.equals("All Months", ignoreCase = true)) {
            dao.getBlockSummariesForExportAll(category)
        } else {
            dao.getBlockSummariesForExportByMonth(category, monthYear)
        }
    }

    // === EXPENSES ===

    fun getExpenses(category: String, monthYear: String? = null): Flow<List<ExpenseEntity>> {
        return if (monthYear.isNullOrBlank() || monthYear.equals("All Months", ignoreCase = true)) {
            dao.getExpenses(category)
        } else {
            dao.getExpensesByMonth(category, monthYear)
        }
    }

    fun getTotalExpenseAmount(category: String, monthYear: String? = null): Flow<Double?> {
        return if (monthYear.isNullOrBlank() || monthYear.equals("All Months", ignoreCase = true)) {
            dao.getTotalExpenseAmount(category)
        } else {
            dao.getTotalExpenseAmountByMonth(category, monthYear)
        }
    }

    suspend fun addExpense(expense: ExpenseEntity): Long {
        return dao.insertExpense(expense)
    }

    suspend fun deleteExpense(expense: ExpenseEntity) {
        dao.deleteExpense(expense)
    }

    suspend fun getExpensesForExport(
        category: String,
        monthYear: String? = null
    ): List<ExpenseEntity> {
        return if (monthYear.isNullOrBlank() || monthYear.equals("All Months", ignoreCase = true)) {
            dao.getExpensesForExportAll(category)
        } else {
            dao.getExpensesForExportByMonth(category, monthYear)
        }
    }
}
