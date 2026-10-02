package com.society.app.data.local

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.society.app.data.model.BlockSummary
import com.society.app.data.model.CollectionEntity
import com.society.app.data.model.ExpenseEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface SocietyDao {

    // === COLLECTIONS ===

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCollection(collection: CollectionEntity): Long

    @Delete
    suspend fun deleteCollection(collection: CollectionEntity)

    // Category only
    @Query("SELECT * FROM collections WHERE category = :category ORDER BY timestamp DESC")
    fun getCollections(category: String): Flow<List<CollectionEntity>>

    // Category + Month
    @Query("SELECT * FROM collections WHERE category = :category AND monthYear = :monthYear ORDER BY timestamp DESC")
    fun getCollectionsByMonth(category: String, monthYear: String): Flow<List<CollectionEntity>>

    // Block
    @Query("SELECT * FROM collections WHERE category = :category AND block = :block ORDER BY flatNo ASC")
    fun getCollectionsByBlock(category: String, block: String): Flow<List<CollectionEntity>>

    @Query("SELECT * FROM collections WHERE category = :category AND block = :block AND monthYear = :monthYear ORDER BY flatNo ASC")
    fun getCollectionsByBlockAndMonth(category: String, block: String, monthYear: String): Flow<List<CollectionEntity>>

    // Block Summaries
    @Query("""
        SELECT block, COUNT(*) as flatCount, SUM(amount) as totalAmount 
        FROM collections 
        WHERE category = :category
        GROUP BY block 
        ORDER BY block ASC
    """)
    fun getBlockSummaries(category: String): Flow<List<BlockSummary>>

    @Query("""
        SELECT block, COUNT(*) as flatCount, SUM(amount) as totalAmount 
        FROM collections 
        WHERE category = :category AND monthYear = :monthYear
        GROUP BY block 
        ORDER BY block ASC
    """)
    fun getBlockSummariesByMonth(category: String, monthYear: String): Flow<List<BlockSummary>>

    // Total Collection
    @Query("SELECT SUM(amount) FROM collections WHERE category = :category")
    fun getTotalCollectionAmount(category: String): Flow<Double?>

    @Query("SELECT SUM(amount) FROM collections WHERE category = :category AND monthYear = :monthYear")
    fun getTotalCollectionAmountByMonth(category: String, monthYear: String): Flow<Double?>

    // Total Cash
    @Query("SELECT SUM(amount) FROM collections WHERE category = :category AND paymentMode = 'Cash'")
    fun getTotalCashCollectionAmount(category: String): Flow<Double?>

    @Query("SELECT SUM(amount) FROM collections WHERE category = :category AND monthYear = :monthYear AND paymentMode = 'Cash'")
    fun getTotalCashCollectionAmountByMonth(category: String, monthYear: String): Flow<Double?>

    // Total Online
    @Query("SELECT SUM(amount) FROM collections WHERE category = :category AND paymentMode = 'Online'")
    fun getTotalOnlineCollectionAmount(category: String): Flow<Double?>

    @Query("SELECT SUM(amount) FROM collections WHERE category = :category AND monthYear = :monthYear AND paymentMode = 'Online'")
    fun getTotalOnlineCollectionAmountByMonth(category: String, monthYear: String): Flow<Double?>

    @Query("SELECT DISTINCT block FROM collections WHERE category = :category ORDER BY block ASC")
    fun getDistinctBlocks(category: String): Flow<List<String>>

    @Query("SELECT DISTINCT monthYear FROM collections WHERE category = :category AND monthYear != '' ORDER BY timestamp DESC")
    fun getDistinctMonths(category: String): Flow<List<String>>

    // Synchronous queries for export
    @Query("SELECT * FROM collections WHERE category = :category ORDER BY block ASC, flatNo ASC")
    suspend fun getCollectionsForExportAll(category: String): List<CollectionEntity>

    @Query("SELECT * FROM collections WHERE category = :category AND monthYear = :monthYear ORDER BY block ASC, flatNo ASC")
    suspend fun getCollectionsForExportByMonth(category: String, monthYear: String): List<CollectionEntity>

    @Query("SELECT * FROM collections WHERE category = :category AND block = :block ORDER BY flatNo ASC")
    suspend fun getCollectionsForExportByBlock(category: String, block: String): List<CollectionEntity>

    @Query("SELECT * FROM collections WHERE category = :category AND block = :block AND monthYear = :monthYear ORDER BY flatNo ASC")
    suspend fun getCollectionsForExportByBlockAndMonth(category: String, block: String, monthYear: String): List<CollectionEntity>

    @Query("""
        SELECT block, COUNT(*) as flatCount, SUM(amount) as totalAmount 
        FROM collections 
        WHERE category = :category
        GROUP BY block 
        ORDER BY block ASC
    """)
    suspend fun getBlockSummariesForExportAll(category: String): List<BlockSummary>

    @Query("""
        SELECT block, COUNT(*) as flatCount, SUM(amount) as totalAmount 
        FROM collections 
        WHERE category = :category AND monthYear = :monthYear
        GROUP BY block 
        ORDER BY block ASC
    """)
    suspend fun getBlockSummariesForExportByMonth(category: String, monthYear: String): List<BlockSummary>

    // === EXPENSES ===

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertExpense(expense: ExpenseEntity): Long

    @Delete
    suspend fun deleteExpense(expense: ExpenseEntity)

    @Query("SELECT * FROM expenses WHERE category = :category ORDER BY timestamp DESC")
    fun getExpenses(category: String): Flow<List<ExpenseEntity>>

    @Query("SELECT * FROM expenses WHERE category = :category AND monthYear = :monthYear ORDER BY timestamp DESC")
    fun getExpensesByMonth(category: String, monthYear: String): Flow<List<ExpenseEntity>>

    @Query("SELECT SUM(amount) FROM expenses WHERE category = :category")
    fun getTotalExpenseAmount(category: String): Flow<Double?>

    @Query("SELECT SUM(amount) FROM expenses WHERE category = :category AND monthYear = :monthYear")
    fun getTotalExpenseAmountByMonth(category: String, monthYear: String): Flow<Double?>

    @Query("SELECT * FROM expenses WHERE category = :category ORDER BY timestamp DESC")
    suspend fun getExpensesForExportAll(category: String): List<ExpenseEntity>

    @Query("SELECT * FROM expenses WHERE category = :category AND monthYear = :monthYear ORDER BY timestamp DESC")
    suspend fun getExpensesForExportByMonth(category: String, monthYear: String): List<ExpenseEntity>

    @Query("SELECT DISTINCT category FROM collections UNION SELECT DISTINCT category FROM expenses")
    fun getAllRecordedCategories(): Flow<List<String>>
}
