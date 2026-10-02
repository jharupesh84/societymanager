package com.society.app.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "collections")
data class CollectionEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val category: String = "Monthly Maintenance", // "Monthly Maintenance", "Navratri Festival", "Ganpati Festival", etc.
    val monthYear: String = "",                   // e.g. "October 2026", "September 2026"
    val block: String,
    val flatNo: String,
    val ownerName: String,
    val amount: Double,
    val paymentMode: String = "Online",           // "Online" or "Cash"
    val date: String,                             // e.g. "2026-10-02"
    val timestamp: Long = System.currentTimeMillis()
)
