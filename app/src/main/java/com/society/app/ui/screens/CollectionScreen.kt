package com.society.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.MonetizationOn
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.society.app.data.model.CollectionEntity
import com.society.app.data.model.FundCategory
import com.society.app.ui.viewmodel.SocietyViewModel
import com.society.app.util.DateUtil

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CollectionScreen(viewModel: SocietyViewModel) {
    val selectedCategory by viewModel.selectedCategory.collectAsState()
    val collections by viewModel.collections.collectAsState()

    var flatInput by remember { mutableStateOf("") }
    var ownerName by remember { mutableStateOf("") }
    var amountText by remember { mutableStateOf("") }
    var paymentMode by remember { mutableStateOf("Online") } // "Online" or "Cash"
    var collectionMonth by remember { mutableStateOf(DateUtil.getCurrentMonthYear()) }
    var monthDropdownExpanded by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    val monthOptions = remember { DateUtil.getMonthYearList() }
    val isMonthlyMaintenance = selectedCategory == FundCategory.CATEGORY_MAINTENANCE

    // Live preview of parsed block and flat
    val parsedPreview = remember(flatInput) {
        if (flatInput.isNotBlank()) viewModel.parseBlockAndFlat(flatInput) else null
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Active Fund Banner
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(
                    containerColor = if (isMonthlyMaintenance) Color(0xFFEFF6FF) else Color(0xFFFFF7ED)
                )
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "ACTIVE COLLECTION FUND",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (isMonthlyMaintenance) Color(0xFF1D4ED8) else Color(0xFFC2410C),
                            letterSpacing = 0.5.sp
                        )
                        Text(
                            text = selectedCategory,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = if (isMonthlyMaintenance) Color(0xFF1E3A8A) else Color(0xFF9A3412)
                        )
                    }

                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = if (isMonthlyMaintenance) Color(0xFFDBEAFE) else Color(0xFFFFEDD5)
                    ) {
                        Text(
                            text = if (isMonthlyMaintenance) "Regular Fund" else "Festival Fund",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = if (isMonthlyMaintenance) Color(0xFF1D4ED8) else Color(0xFFC2410C),
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }
                }
            }
        }

        // Form Card
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(18.dp),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(modifier = Modifier.padding(18.dp)) {
                    Text(
                        text = if (isMonthlyMaintenance) "Enter Monthly Maintenance" else "Enter $selectedCategory Collection",
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF1565C0)
                    )
                    Spacer(modifier = Modifier.height(14.dp))

                    // Month Selector for Monthly Maintenance
                    if (isMonthlyMaintenance) {
                        ExposedDropdownMenuBox(
                            expanded = monthDropdownExpanded,
                            onExpandedChange = { monthDropdownExpanded = it }
                        ) {
                            OutlinedTextField(
                                value = collectionMonth,
                                onValueChange = {},
                                readOnly = true,
                                label = { Text("Maintenance Month") },
                                leadingIcon = {
                                    Icon(
                                        imageVector = Icons.Default.CalendarMonth,
                                        contentDescription = null,
                                        tint = Color(0xFF1565C0)
                                    )
                                },
                                trailingIcon = {
                                    ExposedDropdownMenuDefaults.TrailingIcon(expanded = monthDropdownExpanded)
                                },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .menuAnchor(),
                                shape = RoundedCornerShape(12.dp)
                            )

                            ExposedDropdownMenu(
                                expanded = monthDropdownExpanded,
                                onDismissRequest = { monthDropdownExpanded = false }
                            ) {
                                monthOptions.forEach { monthOption ->
                                    DropdownMenuItem(
                                        text = {
                                            Text(
                                                text = monthOption,
                                                fontWeight = if (monthOption == collectionMonth) FontWeight.Bold else FontWeight.Normal
                                            )
                                        },
                                        onClick = {
                                            collectionMonth = monthOption
                                            monthDropdownExpanded = false
                                        }
                                    )
                                }
                            }
                        }
                        Spacer(modifier = Modifier.height(12.dp))
                    }

                    // Combined Flat input (e.g. B-504)
                    OutlinedTextField(
                        value = flatInput,
                        onValueChange = { flatInput = it },
                        label = { Text("Flat (e.g. B-504, A 101, C203)") },
                        placeholder = { Text("Enter B-504") },
                        leadingIcon = {
                            Icon(imageVector = Icons.Default.Home, contentDescription = null, tint = Color(0xFF1565C0))
                        },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Characters)
                    )

                    // Helper label showing parsed Block and Flat
                    parsedPreview?.let { (block, flat) ->
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Detected: Block $block, Flat $flat",
                            color = Color(0xFF0284C7),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    OutlinedTextField(
                        value = ownerName,
                        onValueChange = { ownerName = it },
                        label = { Text("Owner / Resident Name") },
                        leadingIcon = {
                            Icon(imageVector = Icons.Default.Person, contentDescription = null, tint = Color(0xFF1565C0))
                        },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words)
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    OutlinedTextField(
                        value = amountText,
                        onValueChange = { amountText = it },
                        label = { Text("Amount (₹)") },
                        leadingIcon = {
                            Icon(imageVector = Icons.Default.MonetizationOn, contentDescription = null, tint = Color(0xFF16A34A))
                        },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal)
                    )

                    Spacer(modifier = Modifier.height(14.dp))

                    // Payment Mode Selector (Cash / Online)
                    Text(
                        text = "Payment Mode",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = Color(0xFF475569)
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        FilterChip(
                            selected = paymentMode == "Online",
                            onClick = { paymentMode = "Online" },
                            label = { Text("Online (UPI / Bank)") },
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.weight(1f)
                        )
                        FilterChip(
                            selected = paymentMode == "Cash",
                            onClick = { paymentMode = "Cash" },
                            label = { Text("Cash") },
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.weight(1f)
                        )
                    }

                    errorMessage?.let {
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(text = it, color = MaterialTheme.colorScheme.error, fontSize = 12.sp)
                    }

                    Spacer(modifier = Modifier.height(18.dp))

                    Button(
                        onClick = {
                            val amt = amountText.toDoubleOrNull()
                            if (flatInput.isBlank() || ownerName.isBlank()) {
                                errorMessage = "Please enter flat and owner name"
                            } else if (amt == null || amt <= 0.0) {
                                errorMessage = "Enter a valid positive amount"
                            } else {
                                errorMessage = null
                                viewModel.addCollectionFromCombinedInput(
                                    rawFlatInput = flatInput,
                                    ownerName = ownerName,
                                    amount = amt,
                                    paymentMode = paymentMode,
                                    category = selectedCategory,
                                    monthYear = if (isMonthlyMaintenance) collectionMonth else DateUtil.getCurrentMonthYear()
                                )
                                // Clear inputs after addition
                                flatInput = ""
                                ownerName = ""
                                amountText = ""
                            }
                        },
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1565C0)),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(50.dp)
                    ) {
                        Text(
                            text = if (isMonthlyMaintenance) "Save Maintenance" else "Save Collection",
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }

        item {
            Text(
                text = "$selectedCategory Contributions (${collections.size})",
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF1E293B)
            )
        }

        if (collections.isEmpty()) {
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "No collection records for $selectedCategory yet.",
                        color = Color.Gray,
                        fontSize = 14.sp
                    )
                }
            }
        } else {
            items(collections, key = { it.id }) { item ->
                CollectionItemCard(
                    collection = item,
                    onDelete = { viewModel.deleteCollection(item) }
                )
            }
        }
    }
}

@Composable
fun CollectionItemCard(
    collection: CollectionEntity,
    onDelete: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    SuggestionChip(
                        onClick = {},
                        label = { Text("Block ${collection.block} - ${collection.flatNo}", fontWeight = FontWeight.SemiBold) },
                        shape = RoundedCornerShape(8.dp)
                    )
                    SuggestionChip(
                        onClick = {},
                        label = {
                            Text(
                                text = collection.paymentMode,
                                color = if (collection.paymentMode == "Online") Color(0xFF1565C0) else Color(0xFFD97706),
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Medium
                            )
                        },
                        shape = RoundedCornerShape(8.dp)
                    )
                }

                if (collection.monthYear.isNotBlank()) {
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = "Period: ${collection.monthYear}",
                        color = Color(0xFF0369A1),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium
                    )
                }

                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = collection.ownerName,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 15.sp,
                    color = Color(0xFF1E293B)
                )
                Text(
                    text = "Paid on: ${collection.date}",
                    color = Color(0xFF64748B),
                    fontSize = 11.sp
                )
            }

            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = "₹${collection.amount}",
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF16A34A)
                )
                IconButton(onClick = onDelete) {
                    Icon(
                        imageVector = Icons.Default.Delete,
                        contentDescription = "Delete",
                        tint = Color(0xFF94A3B8)
                    )
                }
            }
        }
    }
}
