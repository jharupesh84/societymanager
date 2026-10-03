package com.society.app.ui.dialogs

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.society.app.data.model.FundCategory
import com.society.app.util.DateUtil
import com.society.app.util.ParsedCollectionRow

@Composable
fun BatchCollectionReviewDialog(
    initialRows: List<ParsedCollectionRow>,
    selectedCategory: String,
    onDismiss: () -> Unit,
    onConfirmImport: (List<ParsedCollectionRow>, String) -> Unit
) {
    val isMonthly = selectedCategory == FundCategory.CATEGORY_MAINTENANCE
    var monthYearInput by remember { mutableStateOf(DateUtil.getCurrentMonthYear()) }

    // Mutable list of items for in-place editing
    val editableRows = remember {
        mutableStateListOf<MutableCollectionRow>().apply {
            addAll(initialRows.map {
                MutableCollectionRow(
                    block = it.block,
                    flatNo = it.flatNo,
                    ownerName = it.ownerName,
                    amount = if (it.amount > 0) it.amount.toString() else "",
                    paymentMode = it.paymentMode
                )
            })
        }
    }

    val totalAmount = editableRows.sumOf { it.amount.toDoubleOrNull() ?: 0.0 }
    val totalFormatted = "%.0f".format(totalAmount)
    val paidCount = editableRows.count { (it.amount.toDoubleOrNull() ?: 0.0) > 0 }
    val validCount = editableRows.count {
        val amt = it.amount.toDoubleOrNull() ?: 0.0
        amt > 0.0 && it.flatNo.any { c -> c.isDigit() }
    }

    var showOnlyPaid by remember { mutableStateOf(paidCount > 0 && paidCount < editableRows.size) }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth(0.96f)
                .fillMaxHeight(0.92f)
                .padding(vertical = 12.dp),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White),
            elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp)
            ) {
                // Header
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(
                        imageVector = Icons.Default.AutoAwesome,
                        contentDescription = null,
                        tint = Color(0xFFD97706),
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Batch Collection Review",
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF0F172A)
                        )
                        Text(
                            text = "$validCount valid records • Total: ₹$totalFormatted",
                            fontSize = 13.sp,
                            color = Color(0xFF16A34A),
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Target category and month selection
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color(0xFFF8FAFC), RoundedCornerShape(8.dp))
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text("Category", fontSize = 11.sp, color = Color(0xFF64748B))
                        Text(selectedCategory, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Color(0xFF1E293B))
                    }

                    if (isMonthly) {
                        OutlinedTextField(
                            value = monthYearInput,
                            onValueChange = { monthYearInput = it },
                            label = { Text("Target Month", fontSize = 10.sp) },
                            singleLine = true,
                            modifier = Modifier.width(160.dp)
                        )
                    }
                }

                if (paidCount in 1 until editableRows.size) {
                    Spacer(modifier = Modifier.height(6.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        FilterChip(
                            selected = showOnlyPaid,
                            onClick = { showOnlyPaid = !showOnlyPaid },
                            label = { Text(if (showOnlyPaid) "Only Paid ($paidCount)" else "Show All (${editableRows.size})", fontSize = 11.sp) },
                            shape = RoundedCornerShape(8.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                val displayedRows = if (showOnlyPaid) {
                    editableRows.filter { (it.amount.toDoubleOrNull() ?: 0.0) > 0.0 }
                } else {
                    editableRows
                }

                // Editable List of Rows
                LazyColumn(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    itemsIndexed(displayedRows, key = { _, row -> row.hashCode() }) { index, row ->
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .border(1.dp, Color(0xFFE2E8F0), RoundedCornerShape(10.dp)),
                            colors = CardDefaults.cardColors(containerColor = Color(0xFFFAFAFA)),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Column(
                                modifier = Modifier.padding(10.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "#${index + 1}",
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color(0xFF64748B),
                                        modifier = Modifier.width(28.dp)
                                    )

                                    // Flat input (e.g. B-504, G-302)
                                    OutlinedTextField(
                                        value = row.flatNo,
                                        onValueChange = {
                                            row.flatNo = it
                                            if (it.contains("-")) {
                                                row.block = it.substringBefore("-").trim().uppercase()
                                            }
                                        },
                                        label = { Text("Flat", fontSize = 11.sp) },
                                        singleLine = true,
                                        modifier = Modifier.width(105.dp)
                                    )

                                    Spacer(modifier = Modifier.width(8.dp))

                                    // Name input
                                    OutlinedTextField(
                                        value = row.ownerName,
                                        onValueChange = { row.ownerName = it },
                                        label = { Text("Resident Name", fontSize = 11.sp) },
                                        singleLine = true,
                                        modifier = Modifier.weight(1f)
                                    )

                                    IconButton(
                                        onClick = { editableRows.remove(row) },
                                        modifier = Modifier.size(32.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Delete,
                                            contentDescription = "Remove row",
                                            tint = Color(0xFFDC2626),
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
                                }

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Spacer(modifier = Modifier.width(28.dp))

                                    // Amount input
                                    OutlinedTextField(
                                        value = row.amount,
                                        onValueChange = { row.amount = it },
                                        label = { Text("Amount (₹)", fontSize = 11.sp) },
                                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                        singleLine = true,
                                        modifier = Modifier.width(130.dp)
                                    )

                                    Spacer(modifier = Modifier.width(12.dp))

                                    // Payment Mode Toggle: Online / Cash
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier
                                            .background(Color(0xFFE2E8F0), RoundedCornerShape(8.dp))
                                            .padding(2.dp)
                                    ) {
                                        FilterChip(
                                            selected = row.paymentMode.equals("Online", ignoreCase = true),
                                            onClick = { row.paymentMode = "Online" },
                                            label = { Text("Online", fontSize = 11.sp) },
                                            shape = RoundedCornerShape(6.dp)
                                        )
                                        FilterChip(
                                            selected = row.paymentMode.equals("Cash", ignoreCase = true),
                                            onClick = { row.paymentMode = "Cash" },
                                            label = { Text("Cash", fontSize = 11.sp) },
                                            shape = RoundedCornerShape(6.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }

                    item {
                        OutlinedButton(
                            onClick = {
                                editableRows.add(
                                    MutableCollectionRow(
                                        block = "General",
                                        flatNo = "",
                                        ownerName = "",
                                        amount = "",
                                        paymentMode = "Online"
                                    )
                                )
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Add Another Row", fontSize = 12.sp)
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Bottom actions
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                        onClick = onDismiss,
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text("Cancel")
                    }

                    Button(
                        onClick = {
                            val verified = editableRows.mapNotNull { row ->
                                val amt = row.amount.toDoubleOrNull() ?: 0.0
                                val hasDigits = row.flatNo.any { it.isDigit() }
                                if (hasDigits && amt > 0.0) {
                                    var blk = row.block.trim().uppercase()
                                    if (blk.isBlank() && row.flatNo.contains("-")) {
                                        blk = row.flatNo.substringBefore("-").trim().uppercase()
                                    }
                                    val cleanFlat = if (blk.isNotBlank() && blk != "GENERAL" && !row.flatNo.startsWith(blk)) {
                                        "$blk-${row.flatNo.removePrefix("-")}"
                                    } else {
                                        row.flatNo.trim()
                                    }
                                    ParsedCollectionRow(
                                        block = if (blk.isNotBlank()) blk else "General",
                                        flatNo = cleanFlat,
                                        ownerName = row.ownerName.trim().ifBlank { "Resident" },
                                        amount = amt,
                                        paymentMode = row.paymentMode
                                    )
                                } else null
                            }
                            if (verified.isNotEmpty()) {
                                onConfirmImport(verified, monthYearInput.trim())
                            }
                        },
                        enabled = validCount > 0,
                        modifier = Modifier.weight(2f),
                        shape = RoundedCornerShape(8.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF16A34A))
                    ) {
                        Text("Import ($validCount Records)", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

class MutableCollectionRow(
    block: String = "General",
    flatNo: String = "",
    ownerName: String = "",
    amount: String = "",
    paymentMode: String = "Online"
) {
    var block by mutableStateOf(block)
    var flatNo by mutableStateOf(flatNo)
    var ownerName by mutableStateOf(ownerName)
    var amount by mutableStateOf(amount)
    var paymentMode by mutableStateOf(paymentMode)
}
