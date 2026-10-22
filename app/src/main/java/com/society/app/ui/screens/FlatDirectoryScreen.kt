package com.society.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Apartment
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.society.app.data.model.FlatOwnerEntity
import com.society.app.data.model.UserRole
import com.society.app.ui.viewmodel.SocietyViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FlatDirectoryScreen(viewModel: SocietyViewModel) {
    val flatOwners by viewModel.flatOwners.collectAsState()
    val userRole by viewModel.currentUserRole.collectAsState()

    var selectedBlock by remember { mutableStateOf("All") }
    var searchQuery by remember { mutableStateOf("") }

    // Dialog state for editing flat owner (Admin only)
    var editingFlat by remember { mutableStateOf<FlatOwnerEntity?>(null) }
    var newOwnerNameInput by remember { mutableStateOf("") }

    val blocks = listOf("All", "A", "B", "C", "D", "E", "F", "G")

    val filteredOwners = remember(flatOwners, selectedBlock, searchQuery) {
        flatOwners.filter { owner ->
            val matchesBlock = if (selectedBlock == "All") true else owner.block.equals(selectedBlock, ignoreCase = true)
            val cleanQuery = searchQuery.trim().lowercase().replace("-", "").replace(" ", "")
            val matchesSearch = if (cleanQuery.isBlank()) {
                true
            } else {
                val flatKeyClean = owner.flatKey.lowercase().replace("-", "").replace(" ", "")
                val flatNoClean = owner.flatNo.lowercase()
                val nameClean = owner.ownerName.lowercase()
                flatKeyClean.contains(cleanQuery) || flatNoClean.contains(cleanQuery) || nameClean.contains(cleanQuery)
            }
            matchesBlock && matchesSearch
        }
    }

    if (editingFlat != null) {
        AlertDialog(
            onDismissRequest = { editingFlat = null },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Edit,
                        contentDescription = null,
                        tint = Color(0xFF1565C0),
                        modifier = Modifier.size(22.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Edit Flat ${editingFlat?.flatKey} Owner",
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp
                    )
                }
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        text = "Update the resident or owner name for this flat. Future maintenance records will auto-fill with this updated name:",
                        fontSize = 13.sp,
                        color = Color(0xFF64748B)
                    )
                    OutlinedTextField(
                        value = newOwnerNameInput,
                        onValueChange = { newOwnerNameInput = it },
                        label = { Text("Owner / Resident Name") },
                        placeholder = { Text("Enter updated name") },
                        leadingIcon = {
                            Icon(imageVector = Icons.Default.Person, contentDescription = null, tint = Color(0xFF1565C0))
                        },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words)
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val target = editingFlat
                        if (target != null && newOwnerNameInput.isNotBlank()) {
                            viewModel.updateFlatOwnerName(
                                block = target.block,
                                flatNo = target.flatNo,
                                newOwnerName = newOwnerNameInput.trim()
                            )
                            editingFlat = null
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1565C0))
                ) {
                    Text("Save Changes")
                }
            },
            dismissButton = {
                TextButton(onClick = { editingFlat = null }) {
                    Text("Cancel")
                }
            }
        )
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // Header Title
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "Society Flat Directory",
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF1E293B)
                    )
                    Text(
                        text = "${flatOwners.size} flats registered block-wise",
                        fontSize = 12.sp,
                        color = Color(0xFF64748B)
                    )
                }

                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = if (userRole == UserRole.ADMIN) Color(0xFFFEF3C7) else Color(0xFFEFF6FF)
                ) {
                    Text(
                        text = if (userRole == UserRole.ADMIN) "Admin Mode (Can Edit)" else "User Mode (Read Only)",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (userRole == UserRole.ADMIN) Color(0xFFB45309) else Color(0xFF1D4ED8),
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                    )
                }
            }
        }

        // Informational Banner for Standard User
        if (userRole == UserRole.USER) {
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFFF1F5F9))
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Lock,
                            contentDescription = null,
                            tint = Color(0xFF64748B),
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = "Standard User: Directory is in view-only mode. Only Admin can edit flat owner names.",
                            fontSize = 12.sp,
                            color = Color(0xFF475569)
                        )
                    }
                }
            }
        }

        // Search Bar
        item {
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                label = { Text("Search flat or owner (e.g. B-504, 504, Rupesh)") },
                leadingIcon = {
                    Icon(imageVector = Icons.Default.Search, contentDescription = null, tint = Color(0xFF1565C0))
                },
                trailingIcon = {
                    if (searchQuery.isNotEmpty()) {
                        IconButton(onClick = { searchQuery = "" }) {
                            Icon(imageVector = Icons.Default.Clear, contentDescription = "Clear search")
                        }
                    }
                },
                singleLine = true,
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth()
            )
        }

        // Block Filter Chips
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                blocks.forEach { blockName ->
                    val isSelected = selectedBlock == blockName
                    FilterChip(
                        selected = isSelected,
                        onClick = { selectedBlock = blockName },
                        label = {
                            Text(
                                text = if (blockName == "All") "All Blocks" else "Block $blockName",
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

        // Results count
        item {
            Text(
                text = "Showing ${filteredOwners.size} flat${if (filteredOwners.size != 1) "s" else ""}:",
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                color = Color(0xFF64748B)
            )
        }

        if (filteredOwners.isEmpty()) {
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(32.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "No flat owners match your search.",
                        color = Color.Gray,
                        fontSize = 14.sp
                    )
                }
            }
        } else {
            items(filteredOwners, key = { it.id }) { owner ->
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
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.weight(1f)
                        ) {
                            Surface(
                                shape = RoundedCornerShape(10.dp),
                                color = Color(0xFFEFF6FF),
                                modifier = Modifier.size(48.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Text(
                                        text = owner.flatKey,
                                        fontWeight = FontWeight.ExtraBold,
                                        fontSize = 13.sp,
                                        color = Color(0xFF1D4ED8)
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.width(14.dp))

                            Column {
                                Text(
                                    text = owner.ownerName,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 15.sp,
                                    color = Color(0xFF1E293B)
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = "Block ${owner.block}  •  Flat ${owner.flatNo}",
                                    fontSize = 12.sp,
                                    color = Color(0xFF64748B)
                                )
                            }
                        }

                        // Edit Button - Admin Only!
                        if (userRole == UserRole.ADMIN) {
                            FilledTonalButton(
                                onClick = {
                                    editingFlat = owner
                                    newOwnerNameInput = owner.ownerName
                                },
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                                shape = RoundedCornerShape(8.dp),
                                colors = ButtonDefaults.filledTonalButtonColors(
                                    containerColor = Color(0xFFEFF6FF),
                                    contentColor = Color(0xFF1565C0)
                                )
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Edit,
                                    contentDescription = "Edit Name",
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Edit", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }
        }
    }
}
