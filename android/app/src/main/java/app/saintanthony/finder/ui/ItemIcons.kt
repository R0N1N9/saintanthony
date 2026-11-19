package app.saintanthony.finder.ui

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Backpack
import androidx.compose.material.icons.rounded.CameraAlt
import androidx.compose.material.icons.rounded.DirectionsBike
import androidx.compose.material.icons.rounded.DirectionsCar
import androidx.compose.material.icons.rounded.Headphones
import androidx.compose.material.icons.rounded.Inventory2
import androidx.compose.material.icons.rounded.Key
import androidx.compose.material.icons.rounded.Laptop
import androidx.compose.material.icons.rounded.Smartphone
import androidx.compose.material.icons.rounded.Umbrella
import androidx.compose.material.icons.rounded.Wallet
import androidx.compose.material.icons.rounded.Watch
import androidx.compose.ui.graphics.vector.ImageVector

/** The pictures an item can have. The key is what gets saved with the item. */
val ITEM_ICONS: List<Pair<String, ImageVector>> = listOf(
    "key" to Icons.Rounded.Key,
    "wallet" to Icons.Rounded.Wallet,
    "phone" to Icons.Rounded.Smartphone,
    "headphones" to Icons.Rounded.Headphones,
    "watch" to Icons.Rounded.Watch,
    "bag" to Icons.Rounded.Backpack,
    "bike" to Icons.Rounded.DirectionsBike,
    "car" to Icons.Rounded.DirectionsCar,
    "umbrella" to Icons.Rounded.Umbrella,
    "laptop" to Icons.Rounded.Laptop,
    "camera" to Icons.Rounded.CameraAlt,
    "box" to Icons.Rounded.Inventory2,
)

// Items saved by the first version of the app stored an emoji instead.
private val LEGACY = mapOf(
    "🔑" to "key", "👛" to "wallet", "📱" to "phone", "🎧" to "headphones", "🎒" to "bag",
    "🚲" to "bike", "🚗" to "car", "☂️" to "umbrella", "💻" to "laptop",
)

fun itemIconKey(stored: String): String =
    if (ITEM_ICONS.any { it.first == stored }) stored else LEGACY[stored] ?: "box"

fun itemIcon(stored: String): ImageVector {
    val key = itemIconKey(stored)
    return ITEM_ICONS.first { it.first == key }.second
}
