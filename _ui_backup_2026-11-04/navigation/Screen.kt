package com.shreenathji.sales.ui.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ListAlt
import androidx.compose.material.icons.filled.ReceiptLong
import androidx.compose.material.icons.filled.Storefront
import androidx.compose.material.icons.filled.Style
import androidx.compose.material.icons.outlined.ListAlt
import androidx.compose.material.icons.outlined.ReceiptLong
import androidx.compose.material.icons.outlined.Storefront
import androidx.compose.material.icons.outlined.Style
import androidx.compose.ui.graphics.vector.ImageVector

sealed class Screen(
    val route: String,
    val title: String,
    val icon: ImageVector,
    val unselectedIcon: ImageVector = icon
) {
    data object PartyList : Screen("party_list", "Parties", Icons.Filled.Storefront, Icons.Outlined.Storefront)
    data object OrderForm : Screen("order_form?partyId={partyId}", "Book Order", Icons.Filled.ListAlt, Icons.Outlined.ListAlt) {
        fun createRoute(partyId: String? = null): String {
            return if (partyId != null) "order_form?partyId=$partyId" else "order_form"
        }
    }
    data object Designs : Screen("design_gallery?returnToOrder={returnToOrder}", "Designs", Icons.Filled.Style, Icons.Outlined.Style) {
        fun createRoute(returnToOrder: Boolean = false): String {
            return "design_gallery?returnToOrder=$returnToOrder"
        }
    }
    data object Orders : Screen("orders", "Orders", Icons.Filled.ReceiptLong, Icons.Outlined.ReceiptLong)
}
