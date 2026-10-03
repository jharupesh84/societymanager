package com.society.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.society.app.data.model.BlockSummary
import com.society.app.data.model.FundCategory
import com.society.app.ui.viewmodel.SocietyViewModel
import com.society.app.util.DateUtil

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReportScreen(viewModel: SocietyViewModel) {
    val context = LocalContext.current
    val selectedCategory by viewModel.selectedCategory.collectAsState()
    val selectedMonth by viewModel.selectedMonth.collectAsState()
    val categories by viewModel.categories.collectAsState()
    val totalCollection by viewModel.totalCollection.collectAsState()
    val totalCash by viewModel.totalCash.collectAsState()
    val totalOnline by viewModel.totalOnline.collectAsState()
    val totalExpense by viewModel.totalExpense.collectAsState()
    val netBalance by viewModel.netBalance.collectAsState()
    val blockSummaries by viewModel.blockSummaries.collectAsState()
    val recordedMonths by viewModel.distinctMonths.collectAsState()

    val isMonthlyMaintenance = selectedCategory == FundCategory.CATEGORY_MAINTENANCE

    // Month filter options (combining recorded months with standard recent months list)
    val availableMonths = remember(recordedMonths) {
        val list = mutableListOf("All Months")
        val currentAndRecent = DateUtil.getMonthYearList()
        currentAndRecent.forEach { m ->
            if (!list.contains(m)) list.add(m)
        }
        recordedMonths.forEach { m ->
            if (!list.contains(m)) list.add(m)
        }
        list
    }

    var monthDropdownExpanded by remember { mutableStateOf(false) }

    // Dropdown report types for current category & month
    val wholeSocietyOption = "Collections: Whole Society (All Blocks)"
    val reportOptions = remember(blockSummaries, selectedCategory) {
        val list = mutableListOf(wholeSocietyOption)
        blockSummaries.forEach { bs ->
            list.add("Collections: Block ${bs.block}")
        }
        list.add("Expenses: Society Expenses")
        list.add("Consolidated: Full Financial Statement")
        list
    }

    var selectedReport by remember { mutableStateOf(wholeSocietyOption) }
    var reportDropdownExpanded by remember { mutableStateOf(false) }

    // Reset selected report when block list or category changes
    LaunchedEffect(selectedCategory) {
        selectedReport = wholeSocietyOption
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // Title & Category Selection Tabs
        item {
            Text(
                text = "Financial Reports & Statement",
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF1E293B)
            )
            Spacer(modifier = Modifier.height(10.dp))

            // Horizontal Category Selector Chips
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                categories.forEach { cat ->
                    val isSelected = cat.id == selectedCategory
                    FilterChip(
                        selected = isSelected,
                        onClick = { viewModel.selectCategory(cat.id) },
                        label = {
                            Text(
                                text = cat.displayName,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                            )
                        },
                        shape = RoundedCornerShape(10.dp),
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = Color(0xFF1565C0),
                            selectedLabelColor = Color.White
                        )
                    )
                }
            }
        }

        // Monthly Filter Card (for Monthly Maintenance or any category)
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(
                    containerColor = if (isMonthlyMaintenance) Color(0xFFEFF6FF) else Color(0xFFFFF7ED)
                )
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.CalendarMonth,
                                contentDescription = null,
                                tint = if (isMonthlyMaintenance) Color(0xFF1D4ED8) else Color(0xFFC2410C),
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = if (isMonthlyMaintenance) "Select Monthly Report Period:" else "Filter By Period:",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (isMonthlyMaintenance) Color(0xFF1E3A8A) else Color(0xFF9A3412)
                            )
                        }

                        if (selectedMonth != null) {
                            TextButton(
                                onClick = { viewModel.selectMonth(null) },
                                contentPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp)
                            ) {
                                Text("View All Months", fontSize = 12.sp, color = Color(0xFF1565C0))
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    // Month Exposed Dropdown
                    ExposedDropdownMenuBox(
                        expanded = monthDropdownExpanded,
                        onExpandedChange = { monthDropdownExpanded = it }
                    ) {
                        OutlinedTextField(
                            value = selectedMonth ?: "All Months (Overall)",
                            onValueChange = {},
                            readOnly = true,
                            trailingIcon = {
                                ExposedDropdownMenuDefaults.TrailingIcon(expanded = monthDropdownExpanded)
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .menuAnchor(),
                            shape = RoundedCornerShape(10.dp),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedContainerColor = Color.White,
                                unfocusedContainerColor = Color.White
                            )
                        )

                        ExposedDropdownMenu(
                            expanded = monthDropdownExpanded,
                            onDismissRequest = { monthDropdownExpanded = false }
                        ) {
                            availableMonths.forEach { mOption ->
                                val isChosen = if (mOption == "All Months") selectedMonth == null else selectedMonth == mOption
                                DropdownMenuItem(
                                    text = {
                                        Text(
                                            text = mOption,
                                            fontWeight = if (isChosen) FontWeight.Bold else FontWeight.Normal,
                                            color = if (isChosen) Color(0xFF1565C0) else Color.Black
                                        )
                                    },
                                    onClick = {
                                        if (mOption == "All Months") {
                                            viewModel.selectMonth(null)
                                        } else {
                                            viewModel.selectMonth(mOption)
                                        }
                                        monthDropdownExpanded = false
                                    }
                                )
                            }
                        }
                    }
                }
            }
        }

        // Summary Metric Cards
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Card(
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.White),
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Text(
                            text = "Collection ${if (selectedMonth != null) "(${selectedMonth?.take(8)})" else ""}",
                            fontSize = 12.sp,
                            color = Color(0xFF16A34A),
                            fontWeight = FontWeight.SemiBold
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(text = "₹$totalCollection", fontSize = 19.sp, fontWeight = FontWeight.Bold, color = Color(0xFF16A34A))
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(text = "Online: ₹$totalOnline", fontSize = 11.sp, color = Color(0xFF0284C7))
                        Text(text = "Cash: ₹$totalCash", fontSize = 11.sp, color = Color(0xFFD97706))
                    }
                }

                Card(
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.White),
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Text(
                            text = "Expense ${if (selectedMonth != null) "(${selectedMonth?.take(8)})" else ""}",
                            fontSize = 12.sp,
                            color = Color(0xFFDC2626),
                            fontWeight = FontWeight.SemiBold
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(text = "₹$totalExpense", fontSize = 19.sp, fontWeight = FontWeight.Bold, color = Color(0xFFDC2626))
                        Spacer(modifier = Modifier.height(18.dp))
                        Text(
                            text = if (isMonthlyMaintenance) "Regular Expenses" else "Festival Expenses",
                            fontSize = 11.sp,
                            color = Color.Gray
                        )
                    }
                }
            }
        }

        // Net Balance Card
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "Net Balance (${selectedMonth ?: "All Months"})",
                        fontSize = 12.sp,
                        color = Color(0xFF64748B),
                        fontWeight = FontWeight.SemiBold
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "₹$netBalance",
                        fontSize = 26.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (netBalance >= 0) Color(0xFF1565C0) else Color(0xFFDC2626)
                    )
                }
            }
        }

        // ==========================================
        // EXPORT REPORT SECTION (PDF & CSV)
        // ==========================================
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(18.dp),
                elevation = CardDefaults.cardElevation(defaultElevation = 3.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(modifier = Modifier.padding(18.dp)) {
                    Text(
                        text = "Generate Report (${selectedCategory})",
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF1565C0)
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Select report scope (All Blocks, Block-wise, Expenses, or Consolidated):",
                        fontSize = 12.sp,
                        color = Color(0xFF64748B)
                    )
                    Spacer(modifier = Modifier.height(12.dp))

                    // Exposed Dropdown Menu for Report Types
                    ExposedDropdownMenuBox(
                        expanded = reportDropdownExpanded,
                        onExpandedChange = { reportDropdownExpanded = it }
                    ) {
                        OutlinedTextField(
                            value = selectedReport,
                            onValueChange = {},
                            readOnly = true,
                            label = { Text("Report Scope") },
                            trailingIcon = {
                                ExposedDropdownMenuDefaults.TrailingIcon(expanded = reportDropdownExpanded)
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .menuAnchor(),
                            shape = RoundedCornerShape(12.dp)
                        )

                        ExposedDropdownMenu(
                            expanded = reportDropdownExpanded,
                            onDismissRequest = { reportDropdownExpanded = false }
                        ) {
                            reportOptions.forEach { option ->
                                DropdownMenuItem(
                                    text = {
                                        Text(
                                            text = option,
                                            fontWeight = if (option == selectedReport) FontWeight.Bold else FontWeight.Normal
                                        )
                                    },
                                    onClick = {
                                        selectedReport = option
                                        reportDropdownExpanded = false
                                    }
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // Action Button: Export PDF Report
                    Button(
                        onClick = {
                            when {
                                selectedReport == wholeSocietyOption -> {
                                    viewModel.exportCollectionsPdf(context, blockFilter = null)
                                }
                                selectedReport.startsWith("Collections: Block ") -> {
                                    val blockName = selectedReport.removePrefix("Collections: Block ").trim()
                                    viewModel.exportCollectionsPdf(context, blockFilter = blockName)
                                }
                                selectedReport.startsWith("Expenses:") -> {
                                    viewModel.exportExpensesPdf(context)
                                }
                                selectedReport.startsWith("Consolidated:") -> {
                                    viewModel.exportConsolidatedSummaryPdf(context)
                                }
                            }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(50.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF16A34A)),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(imageVector = Icons.Default.PictureAsPdf, contentDescription = null, modifier = Modifier.size(20.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Export & Open PDF Report",
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }

        // Block-wise Breakdown Title
        item {
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "Block-wise Collection Breakdown (${selectedMonth ?: "All Months"})",
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF1E293B)
            )
        }

        if (blockSummaries.isEmpty()) {
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(20.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "No collection records found for this period.",
                        color = Color.Gray,
                        fontSize = 14.sp
                    )
                }
            }
        } else {
            items(blockSummaries, key = { it.block }) { summary ->
                BlockPdfCard(
                    summary = summary,
                    onExportPdf = { viewModel.exportCollectionsPdf(context, blockFilter = summary.block) }
                )
            }
        }
    }
}

@Composable
fun BlockPdfCard(
    summary: BlockSummary,
    onExportPdf: () -> Unit
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
                Text(
                    text = "Block ${summary.block}",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF1565C0)
                )
                Text(
                    text = "${summary.flatCount} flats recorded",
                    fontSize = 12.sp,
                    color = Color(0xFF64748B)
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = "Total Collection: ₹${summary.totalAmount}",
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = Color(0xFF16A34A)
                )
            }

            Button(
                onClick = onExportPdf,
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0284C7)),
                shape = RoundedCornerShape(10.dp),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.PictureAsPdf,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text("Block PDF", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
            }
        }
    }
}
