package com.shreenathji.sales.ui.screens

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.shreenathji.sales.data.local.auth.AuthManager
import com.shreenathji.sales.data.local.auth.StaffMember
import com.shreenathji.sales.data.remote.SupabaseClient
import com.shreenathji.sales.ui.theme.*
import kotlinx.coroutines.launch

@Composable
fun LicenseGateScreen(
    authManager: AuthManager,
    supabaseClient: SupabaseClient,
    onLicenseActivated: () -> Unit
) {
    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current
    val focusManager = LocalFocusManager.current
    val scope = rememberCoroutineScope()

    var licenseKey by remember { mutableStateOf("") }
    var isLoading by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var boundDeviceNotice by remember { mutableStateOf<String?>(null) }

    val deviceName = remember { authManager.getDeviceName() }
    val hardwareId = remember { authManager.getDeviceId() }
    val deviceModel = remember { authManager.getDeviceModel() }

    val onActivate: () -> Unit = {
        val cleanKey = licenseKey.trim().uppercase()
        if (cleanKey.isBlank()) {
            errorMessage = "Please enter your License Key."
        } else {
            isLoading = true
            errorMessage = null
            boundDeviceNotice = null
            focusManager.clearFocus()

            scope.launch {
                val result = supabaseClient.verifyAndBindLicense(
                    licenseKey = cleanKey,
                    hardwareId = hardwareId,
                    deviceModel = deviceModel,
                    deviceName = deviceName
                )

                isLoading = false
                if (result.success && result.staff != null) {
                    val staff = StaffMember(
                        id = result.staff.id,
                        name = result.staff.name,
                        role = result.staff.role,
                        pinCode = result.staff.pin_code ?: "0000",
                        phone = result.staff.phone,
                        orderPrefix = result.staff.order_prefix
                    )
                    authManager.saveActiveLicense(cleanKey, staff)
                    val authOk = supabaseClient.ensureAuthenticatedSession()
                    if (!authOk) {
                        authManager.clearLicense()
                        isLoading = false
                        errorMessage =
                            "License is valid, but auth session could not be created. Enable Anonymous Sign-Ins in Supabase Auth, then try again."
                        return@launch
                    }
                    Toast.makeText(context, "Welcome, ${staff.name}!", Toast.LENGTH_SHORT).show()
                    onLicenseActivated()
                } else {
                    errorMessage = result.message ?: "License verification failed."
                    if (!result.bound_device.isNullOrBlank()) {
                        boundDeviceNotice = "Locked to device: ${result.bound_device}"
                    }
                }
            }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.White)
            .systemBarsPadding()
            .padding(horizontal = 28.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .widthIn(max = 400.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                text = "Shreenathji Textile",
                fontSize = 26.sp,
                fontWeight = FontWeight.Bold,
                color = Slate900,
                textAlign = TextAlign.Center
            )
            Text(
                text = "Enter your license key to unlock the app",
                fontSize = 14.sp,
                color = Slate500,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(8.dp))

            OutlinedTextField(
                value = licenseKey,
                onValueChange = {
                    licenseKey = it.uppercase()
                    errorMessage = null
                    boundDeviceNotice = null
                },
                label = { Text("License key") },
                placeholder = {
                    Text("e.g. ST-REP-SUMIT", fontSize = 14.sp)
                },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedContainerColor = Color.White,
                    unfocusedContainerColor = Color.White,
                    focusedBorderColor = BrandOrange,
                    unfocusedBorderColor = Slate300,
                    focusedTextColor = Slate900,
                    unfocusedTextColor = Slate900,
                    focusedLabelColor = BrandOrange,
                    unfocusedLabelColor = Slate500,
                    cursorColor = BrandOrange
                ),
                textStyle = LocalTextStyle.current.copy(
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 15.sp,
                    letterSpacing = 1.sp
                ),
                trailingIcon = {
                    TextButton(
                        onClick = {
                            val text = clipboardManager.getText()?.text
                            if (!text.isNullOrBlank()) {
                                licenseKey = text.trim().uppercase()
                            }
                        },
                        contentPadding = PaddingValues(horizontal = 8.dp)
                    ) {
                        Text(
                            "Paste",
                            color = BrandOrange,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                },
                keyboardOptions = KeyboardOptions(
                    capitalization = KeyboardCapitalization.Characters,
                    imeAction = ImeAction.Done
                ),
                keyboardActions = KeyboardActions(
                    onDone = { onActivate() }
                ),
                isError = errorMessage != null
            )

            if (errorMessage != null) {
                Text(
                    text = buildString {
                        append(errorMessage)
                        if (!boundDeviceNotice.isNullOrBlank()) {
                            append("\n")
                            append(boundDeviceNotice)
                        }
                    },
                    color = Color(0xFFB91C1C),
                    fontSize = 13.sp,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )
            }

            Button(
                onClick = onActivate,
                enabled = !isLoading && licenseKey.isNotBlank(),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = BrandOrange,
                    disabledContainerColor = Slate200,
                    disabledContentColor = Slate500
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
            ) {
                if (isLoading) {
                    CircularProgressIndicator(
                        color = Color.White,
                        strokeWidth = 2.dp,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Activating…", color = Color.White, fontSize = 14.sp)
                } else {
                    Text(
                        "Activate",
                        fontWeight = FontWeight.SemiBold,
                        color = Color.White,
                        fontSize = 15.sp
                    )
                }
            }

            Text(
                text = deviceName,
                fontSize = 12.sp,
                color = Slate400,
                textAlign = TextAlign.Center
            )
        }
    }
}
