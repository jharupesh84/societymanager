package com.society.app.ui.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.MonetizationOn
import androidx.compose.material.icons.filled.ReceiptLong
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.society.app.data.model.ExpenseEntity
import com.society.app.data.model.FundCategory
import com.society.app.ui.dialogs.AiApiKeyDialog
import com.society.app.ui.viewmodel.SocietyViewModel
import com.society.app.util.DateUtil

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExpenseScreen(viewModel: SocietyViewModel) {
    val context = LocalContext.current
    val selectedCategory by viewModel.selectedCategory.collectAsState()
    val expenses by viewModel.expenses.collectAsState()
    val totalExpense by viewModel.totalExpense.collectAsState()
    val geminiApiKey by viewModel.geminiApiKey.collectAsState()
    val isAiScanning by viewModel.isAiScanning.collectAsState()
    val aiScanError by viewModel.aiScanError.collectAsState()
    val scannedExpense by viewModel.scannedExpense.collectAsState()

    var showApiKeyDialog by remember { mutableStateOf(false) }

    var detail by remember { mutableStateOf("") }
    var amountText by remember { mutableStateOf("") }
    var expenseMonth by remember { mutableStateOf(DateUtil.getCurrentMonthYear()) }
    var monthDropdownExpanded by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var aiSuccessMessage by remember { mutableStateOf<String?>(null) }

    val monthOptions = remember { DateUtil.getMonthYearList() }
    val isMonthlyMaintenance = selectedCategory == FundCategory.CATEGORY_MAINTENANCE

    // Photo picker launcher for AI bill/receipt scanning
    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            viewModel.scanExpenseImage(context, uri)
        }
    }

    // Effect: Auto-populate inputs when AI finishes scanning an expense
    LaunchedEffect(scannedExpense) {
        scannedExpense?.let { parsed ->
            detail = parsed.detail
            amountText = if (parsed.amount > 0) parsed.amount.toString() else ""
            aiSuccessMessage = "Extracted from bill: ${parsed.detail} (₹${parsed.amount})"
            viewModel.clearAiScanState()
        }
    }

    // API Key Dialog
    if (showApiKeyDialog) {
        AiApiKeyDialog(
            viewModel = viewModel,
            onDismiss = { showApiKeyDialog = false }
        )
    }

    // AI Scanning Progress Dialog
    if (isAiScanning) {
        AlertDialog(
            onDismissRequest = { /* Prevent dismissing while running */ },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(24.dp),
                        strokeWidth = 2.5.dp,
                        color = Color(0xFFDC2626)
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Text("AI Bill Scanning", fontWeight = FontWeight.Bold, fontSize = 17.sp)
                }
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        "Gemini Vision is analyzing the bill / receipt image...",
                        fontSize = 14.sp,
                        color = Color(0xFF334155)
                    )
                    Text(
                        "Detecting vendor name, total amount, and bill details.",
                        fontSize = 12.sp,
                        color = Color(0xFF64748B)
                    )
                }
            },
            confirmButton = {}
        )
    }

    // AI Scan Error Dialog
    aiScanError?.let { errText ->
        AlertDialog(
            onDismissRequest = { viewModel.clearAiScanState() },
            title = { Text("AI Scan Result", fontWeight = FontWeight.Bold, color = Color(0xFFDC2626)) },
            text = { Text(errText, fontSize = 14.sp) },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.clearAiScanState()
                        showApiKeyDialog = true
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFDC2626))
                ) {
                    Text("Check API Key")
                }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.clearAiScanState() }) {
                    Text("Dismiss")
                }
            }
        )
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
                            text = "ACTIVE EXPENSE FUND",
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

        // Summary Banner
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFFFEF2F2))
            ) {
                Column(modifier = Modifier.padding(18.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                text = "Total Expense ($selectedCategory)",
                                fontSize = 13.sp,
                                color = Color(0xFFDC2626),
                                fontWeight = FontWeight.SemiBold
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = "₹$totalExpense",
                                fontSize = 26.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFFB91C1C)
                            )
                        }
                        Surface(
                            shape = RoundedCornerShape(20.dp),
                            color = Color(0xFFFEE2E2)
                        ) {
                            Text(
                                text = "${expenses.size} entries",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Medium,
                                color = Color(0xFF991B1B),
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                            )
                        }
                    }

                    if (expenses.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(14.dp))
                        OutlinedButton(
                            onClick = { viewModel.exportExpensesPdf(context) },
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth(),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFFDC2626))
                        ) {
                            Icon(imageVector = Icons.Default.FileDownload, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Download Expense PDF Report", fontWeight = FontWeight.SemiBold)
                        }
                    }
                }
            }
        }

        // AI Scan Bill Card
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFFFFF1F2)),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.AutoAwesome,
                                contentDescription = null,
                                tint = Color(0xFFE11D48),
                                modifier = Modifier.size(22.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "AI Scan Bill / Receipt",
                                fontWeight = FontWeight.Bold,
                                fontSize = 15.sp,
                                color = Color(0xFF9F1239)
                            )
                        }

                        IconButton(
                            onClick = { showApiKeyDialog = true },
                            modifier = Modifier.size(32.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Key,
                                contentDescription = "AI Settings",
                                tint = Color(0xFFE11D48),
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }

                    Text(
                        text = "Upload vendor invoice, contractor bill, or receipt slip. AI will auto-read the amount, vendor name, and date into the form.",
                        fontSize = 12.sp,
                        color = Color(0xFFBE123C),
                        lineHeight = 17.sp
                    )

                    Button(
                        onClick = {
                            if (geminiApiKey.isBlank()) {
                                showApiKeyDialog = true
                            } else {
                                photoPickerLauncher.launch(
                                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                )
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFE11D48)),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(
                            imageVector = Icons.Default.ReceiptLong,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Select / Scan Bill Image",
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp
                        )
                    }
                }
            }
        }

        // Add Expense Form
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(18.dp),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(modifier = Modifier.padding(18.dp)) {
                    Text(
                        text = "Enter Expense for $selectedCategory",
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFFDC2626)
                    )

                    aiSuccessMessage?.let { msg ->
                        Spacer(modifier = Modifier.height(8.dp))
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = Color(0xFFDCFCE7),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = msg,
                                fontSize = 12.sp,
                                color = Color(0xFF15803D),
                                fontWeight = FontWeight.Medium,
                                modifier = Modifier.padding(8.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    // Month Selector for Monthly Maintenance
                    if (isMonthlyMaintenance) {
                        ExposedDropdownMenuBox(
                            expanded = monthDropdownExpanded,
                            onExpandedChange = { monthDropdownExpanded = it }
                        ) {
                            OutlinedTextField(
                                value = expenseMonth,
                                onValueChange = {},
                                readOnly = true,
                                label = { Text("Expense Period / Month") },
                                leadingIcon = {
                                    Icon(
                                        imageVector = Icons.Default.CalendarMonth,
                                        contentDescription = null,
                                        tint = Color(0xFFDC2626)
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
                                                fontWeight = if (monthOption == expenseMonth) FontWeight.Bold else FontWeight.Normal
                                            )
                                        },
                                        onClick = {
                                            expenseMonth = monthOption
                                            monthDropdownExpanded = false
                                        }
                                    )
                                }
                            }
                        }
                        Spacer(modifier = Modifier.height(12.dp))
                    }

                    OutlinedTextField(
                        value = detail,
                        onValueChange = { detail = it },
                        label = { Text("Expense Details / Vendor Name") },
                        placeholder = { Text("e.g. Electrician, Lift AMC, Generator Diesel") },
                        leadingIcon = {
                            Icon(imageVector = Icons.Default.Description, contentDescription = null, tint = Color(0xFFDC2626))
                        },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences)
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    OutlinedTextField(
                        value = amountText,
                        onValueChange = { amountText = it },
                        label = { Text("Amount (₹)") },
                        leadingIcon = {
                            Icon(imageVector = Icons.Default.MonetizationOn, contentDescription = null, tint = Color(0xFFDC2626))
                        },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal)
                    )

                    errorMessage?.let {
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(text = it, color = MaterialTheme.colorScheme.error, fontSize = 12.sp)
                    }

                    Spacer(modifier = Modifier.height(18.dp))

                    Button(
                        onClick = {
                            val amt = amountText.toDoubleOrNull()
                            if (detail.isBlank()) {
                                errorMessage = "Please enter expense detail"
                            } else if (amt == null || amt <= 0.0) {
                                errorMessage = "Enter a valid positive amount"
                            } else {
                                errorMessage = null
                                aiSuccessMessage = null
                                viewModel.addExpense(
                                    detail = detail,
                                    amount = amt,
                                    category = selectedCategory,
                                    monthYear = if (isMonthlyMaintenance) expenseMonth else DateUtil.getCurrentMonthYear()
                                )
                                // Clear inputs after addition
                                detail = ""
                                amountText = ""
                            }
                        },
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFDC2626)),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(50.dp)
                    ) {
                        Text("Save Expense", fontSize = 15.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

        item {
            Text(
                text = "Recorded Expenses (${expenses.size})",
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF1E293B)
            )
        }

        if (expenses.isEmpty()) {
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "No expenses recorded for $selectedCategory yet.",
                        color = Color.Gray,
                        fontSize = 14.sp
                    )
                }
            }
        } else {
            items(expenses, key = { it.id }) { item ->
                ExpenseItemCard(
                    expense = item,
                    onDelete = { viewModel.deleteExpense(item) }
                )
            }
        }
    }
}

@Composable
fun ExpenseItemCard(
    expense: ExpenseEntity,
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
                Text(
                    text = expense.detail,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 15.sp,
                    color = Color(0xFF1E293B)
                )
                if (expense.monthYear.isNotBlank()) {
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = "Period: ${expense.monthYear}",
                        color = Color(0xFF991B1B),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
                Text(
                    text = "Date: ${expense.date}",
                    color = Color(0xFF64748B),
                    fontSize = 11.sp
                )
            }

            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = "-₹${expense.amount}",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFFDC2626)
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
