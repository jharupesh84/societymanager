package com.society.app.data.repository

import com.society.app.data.local.SocietyDao
import com.society.app.data.model.BlockSummary
import com.society.app.data.model.CollectionEntity
import com.society.app.data.model.ExpenseEntity
import kotlinx.coroutines.flow.Flow

class SocietyRepository(private val dao: SocietyDao) {

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
