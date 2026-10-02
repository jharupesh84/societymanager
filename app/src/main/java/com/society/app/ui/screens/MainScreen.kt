package com.society.app.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Assessment
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.ExitToApp
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.MonetizationOn
import androidx.compose.material.icons.filled.ReceiptLong
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.society.app.data.model.FundCategory
import com.society.app.ui.viewmodel.SocietyViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(
    viewModel: SocietyViewModel,
    onBackToWelcome: () -> Unit = {},
    onLogout: () -> Unit = {}
) {
    var selectedTab by remember { mutableIntStateOf(0) }
    val selectedCategory by viewModel.selectedCategory.collectAsState()

    val isCustomEvent = selectedCategory != FundCategory.CATEGORY_MAINTENANCE &&
            selectedCategory != FundCategory.CATEGORY_NAVRATRI

    var showEditDialog by remember { mutableStateOf(false) }
    var editNameInput by remember { mutableStateOf("") }

    val headerColor = when (selectedCategory) {
        FundCategory.CATEGORY_MAINTENANCE -> Color(0xFF1565C0) // Royal Blue
        FundCategory.CATEGORY_NAVRATRI -> Color(0xFFC2410C)    // Festive Orange
        FundCategory.CATEGORY_GANPATI -> Color(0xFFB45309)     // Festive Amber
        else -> Color(0xFF0F766E)                             // Teal
    }

    if (showEditDialog) {
        AlertDialog(
            onDismissRequest = { showEditDialog = false },
            title = { Text("Edit Event Name", fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        "Rename this event. Existing collection and expense records will be updated automatically:",
                        fontSize = 13.sp,
                        color = Color(0xFF64748B)
                    )
                    OutlinedTextField(
                        value = editNameInput,
                        onValueChange = { editNameInput = it },
                        label = { Text("Event Name") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (editNameInput.isNotBlank()) {
                            viewModel.editCustomCategory(
                                oldName = selectedCategory,
                                newName = editNameInput
                            )
                            showEditDialog = false
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1565C0))
                ) {
                    Text("Save Changes")
                }
            },
            dismissButton = {
                TextButton(onClick = { showEditDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = selectedCategory,
                                fontWeight = FontWeight.Bold,
                                fontSize = 17.sp,
                                maxLines = 1,
                                color = Color.White
                            )
                            if (isCustomEvent) {
                                IconButton(
                                    onClick = {
                                        editNameInput = selectedCategory
                                        showEditDialog = true
                                    },
                                    modifier = Modifier.size(28.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Edit,
                                        contentDescription = "Edit Event Name",
                                        tint = Color.White.copy(alpha = 0.9f),
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            }
                        }
                        Text(
                            text = "Society Manager Portal",
                            fontSize = 11.sp,
                            color = Color.White.copy(alpha = 0.85f)
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBackToWelcome) {
                        Icon(
                            imageVector = Icons.Default.Home,
                            contentDescription = "Switch Fund",
                            tint = Color.White
                        )
                    }
                },
                actions = {
                    FilledTonalButton(
                        onClick = onBackToWelcome,
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                        shape = RoundedCornerShape(8.dp),
                        colors = ButtonDefaults.filledTonalButtonColors(
                            containerColor = Color.White.copy(alpha = 0.25f),
                            contentColor = Color.White
                        )
                    ) {
                        Text("Switch Fund", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }

                    Spacer(modifier = Modifier.width(4.dp))

                    IconButton(onClick = onLogout) {
                        Icon(
                            imageVector = Icons.Default.ExitToApp,
                            contentDescription = "Logout",
                            tint = Color.White
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = headerColor,
                    titleContentColor = Color.White
                )
            )
        },
        bottomBar = {
            NavigationBar(
                containerColor = Color.White
            ) {
                NavigationBarItem(
                    selected = selectedTab == 0,
                    onClick = { selectedTab = 0 },
                    icon = { Icon(Icons.Default.MonetizationOn, contentDescription = "Collections") },
                    label = { Text("Collections", fontWeight = if (selectedTab == 0) FontWeight.Bold else FontWeight.Normal) },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = headerColor,
                        selectedTextColor = headerColor,
                        indicatorColor = headerColor.copy(alpha = 0.12f)
                    )
                )
                NavigationBarItem(
                    selected = selectedTab == 1,
                    onClick = { selectedTab = 1 },
                    icon = { Icon(Icons.Default.ReceiptLong, contentDescription = "Expenses") },
                    label = { Text("Expenses", fontWeight = if (selectedTab == 1) FontWeight.Bold else FontWeight.Normal) },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = Color(0xFFDC2626),
                        selectedTextColor = Color(0xFFDC2626),
                        indicatorColor = Color(0xFFFEE2E2)
                    )
                )
                NavigationBarItem(
                    selected = selectedTab == 2,
                    onClick = { selectedTab = 2 },
                    icon = { Icon(Icons.Default.Assessment, contentDescription = "Reports") },
                    label = { Text("Reports", fontWeight = if (selectedTab == 2) FontWeight.Bold else FontWeight.Normal) },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = headerColor,
                        selectedTextColor = headerColor,
                        indicatorColor = headerColor.copy(alpha = 0.12f)
                    )
                )
            }
        }
    ) { innerPadding ->
        Box(modifier = Modifier.padding(innerPadding)) {
            when (selectedTab) {
                0 -> CollectionScreen(viewModel = viewModel)
                1 -> ExpenseScreen(viewModel = viewModel)
                2 -> ReportScreen(viewModel = viewModel)
            }
        }
    }
}
