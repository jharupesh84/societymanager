package com.society.app.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "expenses")
data class ExpenseEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val category: String = "Monthly Maintenance", // "Monthly Maintenance", "Navratri Festival", "Ganpati Festival", etc.
    val monthYear: String = "",                   // e.g. "October 2026"
    val detail: String,
    val amount: Double,
    val date: String,
    val timestamp: Long = System.currentTimeMillis()
)
