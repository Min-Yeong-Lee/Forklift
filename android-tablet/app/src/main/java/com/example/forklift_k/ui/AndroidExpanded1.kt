package com.example.forklift_k.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.forklift_k.R
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.ui.res.painterResource

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AndroidExpanded1(
    modifier: Modifier = Modifier,
    onPalletSelected: (String) -> Unit,
    onDestinationSelected: (String) -> Unit,
    onSendCommand: () -> Unit
) {
    val ctx = LocalContext.current

    var palletExpanded by remember { mutableStateOf(false) }
    var selectedPallet by remember { mutableStateOf("") }
    val pallets = listOf("Pallet A", "Pallet B", "Pallet C")

    var destExpanded by remember { mutableStateOf(false) }
    var selectedDest by remember { mutableStateOf("") }
    val destinations = listOf("Sector A", "Sector B", "Sector C")

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(Color(0xFFF2F2F2))
            .padding(32.dp)
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // 바깥 카드
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .shadow(4.dp, RoundedCornerShape(30.dp)),
            shape = RoundedCornerShape(30.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(32.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Pallet 섹션
                Text(
                    text = "Select Pallet",
                    style = TextStyle(fontSize = 28.sp, fontWeight = FontWeight.Bold),
                    modifier = Modifier.padding(bottom = 8.dp)
                )

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp)
                        .background(Color.White, RoundedCornerShape(10.dp))
                        .shadow(2.dp, RoundedCornerShape(10.dp))
                        .clickable { palletExpanded = true }
                        .padding(horizontal = 16.dp),
                    contentAlignment = Alignment.CenterStart
                ) {
                    Row(
                        modifier = Modifier.fillMaxSize(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = if (selectedPallet.isEmpty()) "Choose a pallet" else selectedPallet,
                            color = Color(0xFF7E7E7E),
                            fontSize = 18.sp
                        )
                        Icon(
                            painter = painterResource(id = R.drawable.ic_polygon1),
                            contentDescription = null,
                            modifier = Modifier.size(20.dp, 26.dp)
                        )

                    }

                    DropdownMenu(
                        expanded = palletExpanded,
                        onDismissRequest = { palletExpanded = false }
                    ) {
                        pallets.forEach { item ->
                            DropdownMenuItem(
                                onClick = {
                                    selectedPallet = item
                                    palletExpanded = false
                                    onPalletSelected(item)
                                },
                                text = { Text(item) }
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(40.dp))

                // Destination 섹션
                Text(
                    text = "Select Destination",
                    style = TextStyle(fontSize = 28.sp, fontWeight = FontWeight.Bold),
                    modifier = Modifier.padding(bottom = 8.dp)
                )

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp)
                        .background(Color.White, RoundedCornerShape(10.dp))
                        .shadow(2.dp, RoundedCornerShape(10.dp))
                        .clickable { destExpanded = true }
                        .padding(horizontal = 16.dp),
                    contentAlignment = Alignment.CenterStart
                ) {
                    Row(
                        modifier = Modifier.fillMaxSize(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = if (selectedDest.isEmpty()) "Choose a destination" else selectedDest,
                            color = Color(0xFF7E7E7E),
                            fontSize = 18.sp
                        )
                        Icon(
                            painter = painterResource(id = R.drawable.ic_polygon2),
                            contentDescription = null,
                            modifier = Modifier.size(20.dp, 26.dp)
                        )

                    }

                    DropdownMenu(
                        expanded = destExpanded,
                        onDismissRequest = { destExpanded = false }
                    ) {
                        destinations.forEach { item ->
                            DropdownMenuItem(
                                onClick = {
                                    selectedDest = item
                                    destExpanded = false
                                    onDestinationSelected(item)
                                },
                                text = { Text(item) }
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(48.dp))

                // MOVE 버튼
                Button(
                    onClick = { onSendCommand() },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(64.dp),
                    shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF007AFF))
                ) {
                    Text(
                        text = "MOVE",
                        color = Color.White,
                        fontSize = 24.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}

@Preview(
    showBackground = true,
    widthDp = 768,
    heightDp = 1024,
    device = "spec:width=768dp,height=1024dp,dpi=320"
)
@Composable
fun AndroidExpanded1Preview() {
    AndroidExpanded1(
        onPalletSelected = { println("Selected Pallet: $it") },
        onDestinationSelected = { println("Selected Destination: $it") },
        onSendCommand = { println("MOVE command sent") }
    )
}
