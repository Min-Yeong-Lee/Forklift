package com.example.forklift_phone.ui.theme

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.text.font.FontWeight

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomePage(
    padding: PaddingValues,
    onMove: (pallet: String, dest: String) -> Unit
) {
    var palletExpanded by remember { mutableStateOf(false) }
    var selectedPallet by remember { mutableStateOf("") }
    val pallets = listOf("Pallet A", "Pallet B", "Pallet C")

    var destExpanded by remember { mutableStateOf(false) }
    var selectedDest by remember { mutableStateOf("") }
    val destinations = listOf("Sector A", "Sector B", "Sector C")

    val canMove = selectedPallet.isNotEmpty() && selectedDest.isNotEmpty()

    Surface(
        modifier = Modifier.fillMaxSize().padding(padding),
        color = Color(0xFFF7F7F7)
    ) {
        Column(
            modifier = Modifier.fillMaxSize().padding(20.dp).verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(28.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFFF2F2F2))
            ) {
                Column(Modifier.fillMaxWidth().padding(24.dp)) {
                    Text("Select Pallet", fontSize = 24.sp, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(8.dp))

                    // 📌 Pallet 선택
                    ExposedDropdownMenuBox(
                        expanded = palletExpanded,
                        onExpandedChange = { palletExpanded = !palletExpanded },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        TextField(
                            value = if (selectedPallet.isEmpty()) "Choose a pallet" else selectedPallet,
                            onValueChange = {},
                            readOnly = true,
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = palletExpanded) },
                            modifier = Modifier.menuAnchor().fillMaxWidth(),
                            colors = TextFieldDefaults.colors(
                                focusedContainerColor = Color.White,
                                unfocusedContainerColor = Color.White
                            )
                        )
                        ExposedDropdownMenu(
                            expanded = palletExpanded,
                            onDismissRequest = { palletExpanded = false }
                        ) {
                            pallets.forEach { item ->
                                DropdownMenuItem(
                                    text = { Text(item) },
                                    onClick = {
                                        selectedPallet = item
                                        palletExpanded = false
                                    }
                                )
                            }
                        }
                    }

                    Spacer(Modifier.height(28.dp))
                    Text("Select Destination", fontSize = 24.sp, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(8.dp))

                    // 📌 목적지 선택
                    ExposedDropdownMenuBox(
                        expanded = destExpanded,
                        onExpandedChange = { destExpanded = !destExpanded },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        TextField(
                            value = if (selectedDest.isEmpty()) "Choose a destination" else selectedDest,
                            onValueChange = {},
                            readOnly = true,
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = destExpanded) },
                            modifier = Modifier.menuAnchor().fillMaxWidth(),
                            colors = TextFieldDefaults.colors(
                                focusedContainerColor = Color.White,
                                unfocusedContainerColor = Color.White
                            )
                        )
                        ExposedDropdownMenu(
                            expanded = destExpanded,
                            onDismissRequest = { destExpanded = false }
                        ) {
                            destinations.forEach { item ->
                                DropdownMenuItem(
                                    text = { Text(item) },
                                    onClick = {
                                        selectedDest = item
                                        destExpanded = false
                                    }
                                )
                            }
                        }
                    }

                    Spacer(Modifier.height(28.dp))
                    Button(
                        onClick = { onMove(selectedPallet, selectedDest) },
                        enabled = canMove,
                        modifier = Modifier.fillMaxWidth().height(60.dp),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF007AFF))
                    ) {
                        Text("MOVE", color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}
