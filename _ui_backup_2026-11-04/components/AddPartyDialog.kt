package com.shreenathji.sales.ui.components

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Storefront
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.shreenathji.sales.ui.theme.*

@Composable
fun AddPartyDialog(
    onDismiss: () -> Unit,
    onSave: (
        name: String,
        contactPerson: String?,
        phone: String?,
        addressLine1: String?,
        addressLine2: String?,
        defaultAgent: String?
    ) -> Unit
) {
    val context = LocalContext.current
    var businessName by remember { mutableStateOf("") }
    var contactPerson by remember { mutableStateOf("") }
    var phone by remember { mutableStateOf("") }
    var addressLine1 by remember { mutableStateOf("") }
    var addressLine2 by remember { mutableStateOf("") }
    var defaultAgent by remember { mutableStateOf("") }
    var isSaving by remember { mutableStateOf(false) }

    Dialog(
        onDismissRequest = { if (!isSaving) onDismiss() },
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.95f)
                .padding(vertical = 24.dp),
            shape = RoundedCornerShape(20.dp),
            color = ShadcnWhite,
            tonalElevation = 6.dp
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(20.dp)
            ) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = Slate100,
                            modifier = Modifier.size(38.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Default.Storefront,
                                    contentDescription = null,
                                    tint = BrandOrange,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text(
                                text = "Register New Store",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = Slate950
                            )
                            Text(
                                text = "Add client store to party directory",
                                style = MaterialTheme.typography.labelSmall,
                                color = Slate500
                            )
                        }
                    }

                    IconButton(
                        onClick = { if (!isSaving) onDismiss() },
                        enabled = !isSaving,
                        modifier = Modifier.size(28.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Close",
                            tint = Slate400,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))
                HorizontalDivider(thickness = 1.dp, color = Slate200)
                Spacer(modifier = Modifier.height(14.dp))

                // 1. Store / Business Name (Required)
                OutlinedTextField(
                    value = businessName,
                    onValueChange = { businessName = it },
                    label = { Text("Store / Business Name *", fontSize = 12.sp, color = Slate600) },
                    placeholder = { Text("e.g. Shree Ambaji Fashions", color = Slate400) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = ShadcnWhite,
                        unfocusedContainerColor = ShadcnWhite,
                        focusedBorderColor = Slate900,
                        unfocusedBorderColor = Slate200,
                        focusedTextColor = Slate950,
                        unfocusedTextColor = Slate950
                    )
                )

                Spacer(modifier = Modifier.height(10.dp))

                // 2. Contact Person & Phone Number
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    OutlinedTextField(
                        value = contactPerson,
                        onValueChange = { contactPerson = it },
                        label = { Text("Contact Person", fontSize = 12.sp, color = Slate600) },
                        placeholder = { Text("Owner name", color = Slate400) },
                        singleLine = true,
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(12.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedContainerColor = ShadcnWhite,
                            unfocusedContainerColor = ShadcnWhite,
                            focusedBorderColor = Slate900,
                            unfocusedBorderColor = Slate200,
                            focusedTextColor = Slate950,
                            unfocusedTextColor = Slate950
                        )
                    )

                    OutlinedTextField(
                        value = phone,
                        onValueChange = { phone = it },
                        label = { Text("Phone Number", fontSize = 12.sp, color = Slate600) },
                        placeholder = { Text("+91 9825...", color = Slate400) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                        singleLine = true,
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(12.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedContainerColor = ShadcnWhite,
                            unfocusedContainerColor = ShadcnWhite,
                            focusedBorderColor = Slate900,
                            unfocusedBorderColor = Slate200,
                            focusedTextColor = Slate950,
                            unfocusedTextColor = Slate950
                        )
                    )
                }

                Spacer(modifier = Modifier.height(10.dp))

                // 3. Address Line 1 (Required)
                OutlinedTextField(
                    value = addressLine1,
                    onValueChange = { addressLine1 = it },
                    label = { Text("Address Line 1 *", fontSize = 12.sp, color = Slate600) },
                    placeholder = { Text("Shop no, building, floor (e.g. Shop A-102, 1st Floor)", color = Slate400) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = ShadcnWhite,
                        unfocusedContainerColor = ShadcnWhite,
                        focusedBorderColor = Slate900,
                        unfocusedBorderColor = Slate200,
                        focusedTextColor = Slate950,
                        unfocusedTextColor = Slate950
                    )
                )

                Spacer(modifier = Modifier.height(10.dp))

                // 4. Address Line 2 (Optional)
                OutlinedTextField(
                    value = addressLine2,
                    onValueChange = { addressLine2 = it },
                    label = { Text("Address Line 2 (Optional)", fontSize = 12.sp, color = Slate600) },
                    placeholder = { Text("Market complex, street, area, city (e.g. RKTM, Surat)", color = Slate400) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = ShadcnWhite,
                        unfocusedContainerColor = ShadcnWhite,
                        focusedBorderColor = Slate900,
                        unfocusedBorderColor = Slate200,
                        focusedTextColor = Slate950,
                        unfocusedTextColor = Slate950
                    )
                )

                Spacer(modifier = Modifier.height(10.dp))

                // 5. Default Agent / Broker (Optional)
                OutlinedTextField(
                    value = defaultAgent,
                    onValueChange = { defaultAgent = it },
                    label = { Text("Broker / Agent (Optional)", fontSize = 12.sp, color = Slate600) },
                    placeholder = { Text("Broker name associated with store", color = Slate400) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = ShadcnWhite,
                        unfocusedContainerColor = ShadcnWhite,
                        focusedBorderColor = Slate900,
                        unfocusedBorderColor = Slate200,
                        focusedTextColor = Slate950,
                        unfocusedTextColor = Slate950
                    )
                )

                Spacer(modifier = Modifier.height(20.dp))

                // Action Buttons
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    ShadcnOutlineButton(
                        onClick = onDismiss,
                        enabled = !isSaving,
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 10.dp)
                    ) {
                        Text("Cancel", fontSize = 13.sp, fontWeight = FontWeight.Medium)
                    }

                    Spacer(modifier = Modifier.width(10.dp))

                    ShadcnPrimaryButton(
                        onClick = {
                            if (isSaving) return@ShadcnPrimaryButton
                            if (businessName.isBlank()) {
                                Toast.makeText(context, "Please enter Store Name", Toast.LENGTH_SHORT).show()
                                return@ShadcnPrimaryButton
                            }
                            if (addressLine1.isBlank()) {
                                Toast.makeText(context, "Please enter Address Line 1", Toast.LENGTH_SHORT).show()
                                return@ShadcnPrimaryButton
                            }
                            isSaving = true
                            onSave(
                                businessName.trim(),
                                contactPerson.trim().ifBlank { null },
                                phone.trim().ifBlank { null },
                                addressLine1.trim().ifBlank { null },
                                addressLine2.trim().ifBlank { null },
                                defaultAgent.trim().ifBlank { null }
                            )
                        },
                        enabled = !isSaving,
                        containerColor = BrandOrange,
                        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 10.dp)
                    ) {
                        if (isSaving) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(14.dp),
                                color = Color.White,
                                strokeWidth = 2.dp
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Saving Store...", fontSize = 13.sp, fontWeight = FontWeight.Bold)
                        } else {
                            Text("Save Store", fontSize = 13.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }
}

