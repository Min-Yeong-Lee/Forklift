package com.example.forklift_phone.ui.theme

data class InventoryItem(
    val pallet: String,   // Pallet A/B/C
    val item: String,     // 품목명
    val qty: Int,         // 수량
    val unit: String      // 단위 (ea, box, bag 등)
)
