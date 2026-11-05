package com.shreenathji.sales.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.shreenathji.sales.ui.theme.*

/**
 * Full-screen search-and-pick list. Replaces an inline dropdown popup, which drifted over its
 * text field when the keyboard panned the screen. The dialog window resizes above the keyboard,
 * so the list always stays visible and scrollable.
 *
 * [onUseTyped] lets the rep keep a name that is not in the list.
 */
@Composable
fun <T> SearchPickerDialog(
    title: String,
    placeholder: String,
    items: List<T>,
    matches: (item: T, query: String) -> Boolean,
    onDismiss: () -> Unit,
    onPick: (T) -> Unit,
    onUseTyped: (String) -> Unit,
    itemContent: @Composable (T) -> Unit
) {
    var query by remember { mutableStateOf("") }
    val focusRequester = remember { FocusRequester() }
    val typed = query.trim()
    val filtered = remember(typed, items) {
        if (typed.isEmpty()) items else items.filter { matches(it, typed) }
    }

    LaunchedEffect(Unit) { focusRequester.requestFocus() }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = ShadcnWhite
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 16.dp, end = 4.dp, top = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = Slate950
                    )
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = "Close", tint = Slate700)
                    }
                }

                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    placeholder = { Text(placeholder, color = Slate400) },
                    leadingIcon = {
                        Icon(Icons.Default.Search, contentDescription = null, tint = Slate400)
                    },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    shape = RoundedCornerShape(12.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = ShadcnWhite,
                        unfocusedContainerColor = ShadcnWhite,
                        focusedBorderColor = Slate900,
                        unfocusedBorderColor = Slate200,
                        focusedTextColor = Slate950,
                        unfocusedTextColor = Slate950
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                        .focusRequester(focusRequester)
                )

                LazyColumn(modifier = Modifier.weight(1f).fillMaxWidth()) {
                    if (typed.isNotEmpty()) {
                        item {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { onUseTyped(typed) }
                                    .padding(horizontal = 16.dp, vertical = 14.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    Icons.Default.Add,
                                    contentDescription = null,
                                    tint = BrandOrange,
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(modifier = Modifier.width(10.dp))
                                Text(
                                    text = "Use \"$typed\"",
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = BrandOrangeDark
                                )
                            }
                            HorizontalDivider(thickness = 1.dp, color = Slate200)
                        }
                    }
                    items(filtered) { entry ->
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onPick(entry) }
                                .padding(horizontal = 16.dp, vertical = 12.dp)
                        ) {
                            itemContent(entry)
                        }
                        HorizontalDivider(thickness = 1.dp, color = Slate100)
                    }
                    if (filtered.isEmpty()) {
                        item {
                            Text(
                                text = "No matches",
                                fontSize = 14.sp,
                                color = Slate500,
                                modifier = Modifier.padding(16.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}
