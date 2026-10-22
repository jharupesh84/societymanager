package com.society.app.data.model

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "flat_owners",
    indices = [
        Index(value = ["block", "flatNo"], unique = true),
        Index(value = ["flatKey"])
    ]
)
data class FlatOwnerEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val block: String,     // e.g. "B"
    val flatNo: String,    // e.g. "504"
    val flatKey: String,   // e.g. "B-504"
    val ownerName: String  // e.g. "Rupesh Jha"
)
