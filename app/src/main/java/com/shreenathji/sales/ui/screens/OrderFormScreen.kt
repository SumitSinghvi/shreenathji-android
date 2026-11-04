package com.shreenathji.sales.ui.screens

import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import com.shreenathji.sales.BuildConfig
import com.shreenathji.sales.ui.components.BadgeVariant
import com.shreenathji.sales.ui.components.ImageViewerDialog
import com.shreenathji.sales.ui.components.ShadcnBadge
import com.shreenathji.sales.ui.components.ShadcnHeader
import com.shreenathji.sales.ui.components.ShadcnOutlineButton
import com.shreenathji.sales.ui.screens.order.ImageSourceBottomSheet
import com.shreenathji.sales.ui.screens.order.ImageUploadSection
import com.shreenathji.sales.ui.screens.order.ItemDetailsCard
import com.shreenathji.sales.ui.screens.order.OrderStickySubmitBar
import com.shreenathji.sales.ui.screens.order.PartyDetailsSection
import com.shreenathji.sales.ui.theme.*
import com.shreenathji.sales.ui.util.ImageUtils
import com.shreenathji.sales.ui.viewmodels.OrderFormViewModel
import com.shreenathji.sales.ui.viewmodels.OrderSavedSummary
import java.text.NumberFormat
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OrderFormScreen(
    viewModel: OrderFormViewModel,
    onOrderSubmitted: () -> Unit,
    onPickFromDesigns: (() -> Unit)? = null
) {
    val context = LocalContext.current
    val parties by viewModel.parties.collectAsState()
    val products by viewModel.products.collectAsState()
    val selectedParty by viewModel.selectedParty.collectAsState()

    val partyName by viewModel.partyName.collectAsState()
    val partyAddress by viewModel.partyAddress.collectAsState()
    val agent by viewModel.agent.collectAsState()
    val poNumber by viewModel.poNumber.collectAsState()
    val deliveryAddress by viewModel.deliveryAddress.collectAsState()
    val imageUris by viewModel.imageUris.collectAsState()
    val items by viewModel.items.collectAsState()
    val orderNotes by viewModel.orderNotes.collectAsState()
    val isSubmitting by viewModel.isSubmitting.collectAsState()
    val canSubmit by viewModel.canSubmit.collectAsState()
    val submitBlockReason by viewModel.submitBlockReason.collectAsState()

    var showImageSourceModal by remember { mutableStateOf(false) }
    var colorPhotoPickerMode by remember { mutableStateOf(false) }
    var currentCameraPhotoUri by remember { mutableStateOf<Uri?>(null) }
    var viewingImageUri by remember { mutableStateOf<Uri?>(null) }
    var notesExpanded by remember { mutableStateOf(false) }
    var showSuccessDialog by remember { mutableStateOf(false) }
    var savedOrderSummary by remember { mutableStateOf<OrderSavedSummary?>(null) }
    var bannerError by remember { mutableStateOf<String?>(null) }

    val currencyFormatter = remember { NumberFormat.getCurrencyInstance(Locale("en", "IN")) }
    val grandTotal = items.sumOf { it.totalPrice }

    val coroutineScope = rememberCoroutineScope()

    val galleryPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetMultipleContents()
    ) { uris ->
        if (uris.isNotEmpty()) {
            coroutineScope.launch(Dispatchers.IO) {
                val compressedUris = uris.map { ImageUtils.compressImage(context, it) }
                withContext(Dispatchers.Main) {
                    val target = viewModel.pendingColorAttach.value
                    if (target != null && compressedUris.isNotEmpty()) {
                        viewModel.setColorPhotoAttachment(target.itemIndex, target.colorIndex, compressedUris.first())
                    } else {
                        viewModel.addImageUris(compressedUris)
                    }
                }
            }
        } else {
            viewModel.clearPendingColorAttach()
        }
    }

    val singleGalleryLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri ->
        if (uri != null) {
            coroutineScope.launch(Dispatchers.IO) {
                val compressed = ImageUtils.compressImage(context, uri)
                withContext(Dispatchers.Main) {
                    val target = viewModel.pendingColorAttach.value
                    if (target != null) {
                        viewModel.setColorPhotoAttachment(target.itemIndex, target.colorIndex, compressed)
                    } else {
                        viewModel.addImageUris(listOf(compressed))
                    }
                }
            }
        } else {
            viewModel.clearPendingColorAttach()
        }
    }

    val cameraLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicture()
    ) { success ->
        if (success && currentCameraPhotoUri != null) {
            coroutineScope.launch(Dispatchers.IO) {
                val compressed = ImageUtils.compressImage(context, currentCameraPhotoUri!!)
                withContext(Dispatchers.Main) {
                    val target = viewModel.pendingColorAttach.value
                    if (target != null) {
                        viewModel.setColorPhotoAttachment(target.itemIndex, target.colorIndex, compressed)
                    } else {
                        viewModel.addImageUris(listOf(compressed))
                    }
                }
            }
        } else {
            viewModel.clearPendingColorAttach()
        }
    }

    LaunchedEffect(Unit) {
        viewModel.orderSubmittedEvent.collect { summary ->
            savedOrderSummary = summary
            showSuccessDialog = true
        }
    }

    LaunchedEffect(Unit) {
        viewModel.errorMessage.collect { message ->
            bannerError = message
            Toast.makeText(context, message, Toast.LENGTH_LONG).show()
        }
    }

    LaunchedEffect(canSubmit) {
        if (canSubmit) bannerError = null
    }

    if (showSuccessDialog) {
        val summary = savedOrderSummary
        Dialog(onDismissRequest = { /* require Done */ }) {
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = ShadcnWhite,
                tonalElevation = 4.dp
            ) {
                Column(
                    modifier = Modifier.padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(
                        imageVector = Icons.Default.CheckCircle,
                        contentDescription = null,
                        tint = EmeraldSuccess,
                        modifier = Modifier.size(48.dp)
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = "Order saved",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = Slate950
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "You can find it under Orders.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = Slate500
                    )
                    if (summary != null) {
                        Spacer(modifier = Modifier.height(16.dp))
                        OrderSavedSummaryBlock(
                            partyName = summary.partyName,
                            totalMeters = summary.totalMeters,
                            rates = summary.rates,
                            currencyFormatter = currencyFormatter
                        )
                    }
                    Spacer(modifier = Modifier.height(20.dp))
                    Button(
                        onClick = {
                            showSuccessDialog = false
                            savedOrderSummary = null
                            onOrderSubmitted()
                        },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = BrandOrange,
                            contentColor = Color.White
                        )
                    ) {
                        Text("Done", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }

    Scaffold(
        topBar = {
            ShadcnHeader(
                title = "Book Order",
                subtitle = "Store and fabric details",
                actions = {
                    ShadcnBadge(
                        text = "v${BuildConfig.VERSION_NAME}",
                        variant = BadgeVariant.SECONDARY
                    )
                }
            )
        },
        bottomBar = {
            OrderStickySubmitBar(
                grandTotal = grandTotal,
                qualityCount = items.size,
                colorCount = items.sumOf { it.colors.size },
                currencyFormatter = currencyFormatter,
                isSubmitting = isSubmitting,
                enabled = canSubmit,
                onSubmit = {
                    bannerError = null
                    viewModel.submitOrder(context)
                }
            )
        },
        containerColor = Slate50
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .background(Slate50),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Status / validation banner (top)
            item {
                val bannerMessage = bannerError ?: submitBlockReason?.message
                val isWarning = bannerError != null || submitBlockReason?.isWarning == true
                if (bannerMessage != null && (!canSubmit || bannerError != null)) {
                    OrderFormStatusBanner(
                        message = bannerMessage,
                        isWarning = isWarning || submitBlockReason?.isWarning == true
                    )
                }
            }

            // 1. Store first
            item {
                PartyDetailsSection(
                    parties = parties,
                    selectedParty = selectedParty,
                    partyName = partyName,
                    partyAddress = partyAddress,
                    agent = agent,
                    poNumber = poNumber,
                    deliveryAddress = deliveryAddress,
                    currentStaffId = viewModel.currentStaff?.id,
                    onPartySelected = viewModel::selectParty,
                    onPartyNameChanged = viewModel::onPartyNameChanged,
                    onPartyAddressChanged = viewModel::onPartyAddressChanged,
                    onAgentChanged = viewModel::onAgentChanged,
                    onPoNumberChanged = viewModel::onPoNumberChanged,
                    onDeliveryAddressChanged = viewModel::onDeliveryAddressChanged
                )
            }

            // 2. Fabric items
            item {
                val totalColors = items.sumOf { it.colors.size }
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "Fabric items",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            color = Slate950
                        )
                        Text(
                            text = "${items.size} fabric${if (items.size != 1) "s" else ""} · $totalColors color${if (totalColors != 1) "s" else ""}",
                            fontSize = 13.sp,
                            color = Slate500
                        )
                    }
                    ShadcnOutlineButton(
                        onClick = viewModel::addItem,
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 7.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Add,
                            contentDescription = null,
                            modifier = Modifier.size(15.dp),
                            tint = BrandOrange
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "Add fabric",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = Slate900
                        )
                    }
                }
            }

            itemsIndexed(items, key = { _, item -> item.id }) { index, item ->
                ItemDetailsCard(
                    itemIndex = index,
                    item = item,
                    availableProducts = products,
                    currencyFormatter = currencyFormatter,
                    canDelete = items.size > 1,
                    onProductSelected = { prod -> viewModel.selectProductForItem(index, prod) },
                    onToggleMatching = { viewModel.toggleMatching(index) },
                    onToggleTopBottom = { viewModel.toggleTopBottom(index) },
                    onNameChanged = { name -> viewModel.updateItemName(index, name) },
                    onPriceChanged = { price -> viewModel.updateItemPrice(index, price) },
                    onBottomPriceChanged = { price -> viewModel.updateItemBottomPrice(index, price) },
                    onMetersPerColorChanged = { meters -> viewModel.updateMetersPerColor(index, meters) },
                    onBottomMetersPerColorChanged = { meters -> viewModel.updateBottomMetersPerColor(index, meters) },
                    onColorCountChanged = { count -> viewModel.setColorCount(index, count) },
                    onAddColor = { viewModel.addColor(index) },
                    onRemoveColor = { colorIdx -> viewModel.removeColor(index, colorIdx) },
                    onColorLabelChanged = { colorIdx, label -> viewModel.updateColorLabel(index, colorIdx, label) },
                    onColorMeterChanged = { colorIdx, meter -> viewModel.updateColorMeter(index, colorIdx, meter) },
                    onColorBottomMeterChanged = { colorIdx, meter -> viewModel.updateColorBottomMeter(index, colorIdx, meter) },
                    onChooseDesignForColor = { colorIdx ->
                        viewModel.beginColorDesignAttach(index, colorIdx)
                        onPickFromDesigns?.invoke()
                    },
                    onPhotoForColor = { colorIdx ->
                        viewModel.beginColorPhotoAttach(index, colorIdx)
                        colorPhotoPickerMode = true
                        showImageSourceModal = true
                    },
                    onClearColorAttachment = { colorIdx ->
                        viewModel.clearColorAttachment(index, colorIdx)
                    },
                    onDelete = { viewModel.removeItem(index) }
                )
            }

            // 3. Photos (optional)
            item {
                ImageUploadSection(
                    imageUris = imageUris,
                    onAddImagesClick = {
                        colorPhotoPickerMode = false
                        viewModel.clearPendingColorAttach()
                        showImageSourceModal = true
                    },
                    onRemoveImage = viewModel::removeImageUri,
                    onImageClick = { uri -> viewingImageUri = uri }
                )
            }

            // 4. Notes (optional, collapsed by default)
            item {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    color = ShadcnWhite,
                    border = BorderStroke(1.dp, Slate200)
                ) {
                    Column {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { notesExpanded = !notesExpanded }
                                .padding(horizontal = 14.dp, vertical = 12.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = if (orderNotes.isBlank()) {
                                    "Notes (optional)"
                                } else {
                                    "Notes"
                                },
                                fontSize = 14.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = Slate900
                            )
                            Icon(
                                imageVector = if (notesExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                                contentDescription = if (notesExpanded) "Hide notes" else "Show notes",
                                tint = Slate500
                            )
                        }
                        AnimatedVisibility(visible = notesExpanded || orderNotes.isNotBlank()) {
                            OutlinedTextField(
                                value = orderNotes,
                                onValueChange = {
                                    notesExpanded = true
                                    viewModel.onOrderNotesChanged(it)
                                },
                                placeholder = {
                                    Text(
                                        "e.g. 30 days credit, dispatch via Surat transport...",
                                        fontSize = 13.sp,
                                        color = Slate400
                                    )
                                },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(start = 12.dp, end = 12.dp, bottom = 12.dp),
                                shape = RoundedCornerShape(12.dp),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedContainerColor = ShadcnWhite,
                                    unfocusedContainerColor = ShadcnWhite,
                                    focusedBorderColor = BrandOrange,
                                    unfocusedBorderColor = Slate200,
                                    focusedTextColor = Slate950,
                                    unfocusedTextColor = Slate950
                                ),
                                maxLines = 3
                            )
                        }
                    }
                }
            }
        }
    }

    viewingImageUri?.let { uri ->
        ImageViewerDialog(
            imageUri = uri,
            onDismiss = { viewingImageUri = null }
        )
    }

    if (showImageSourceModal) {
        ImageSourceBottomSheet(
            onDismiss = {
                showImageSourceModal = false
                if (colorPhotoPickerMode) {
                    viewModel.clearPendingColorAttach()
                    colorPhotoPickerMode = false
                }
            },
            onSelectCamera = {
                showImageSourceModal = false
                try {
                    val uri = ImageUtils.createTempImageUri(context)
                    currentCameraPhotoUri = uri
                    cameraLauncher.launch(uri)
                } catch (e: Exception) {
                    Toast.makeText(context, "Camera error: ${e.message}", Toast.LENGTH_SHORT).show()
                    viewModel.clearPendingColorAttach()
                    colorPhotoPickerMode = false
                }
            },
            onSelectGallery = {
                showImageSourceModal = false
                if (colorPhotoPickerMode) {
                    singleGalleryLauncher.launch("image/*")
                } else {
                    galleryPickerLauncher.launch("image/*")
                }
                colorPhotoPickerMode = false
            },
            onSelectDesigns = if (colorPhotoPickerMode) {
                null
            } else {
                onPickFromDesigns?.let { pick ->
                    {
                        showImageSourceModal = false
                        pick()
                    }
                }
            }
        )
    }
}

@Composable
private fun OrderSavedSummaryBlock(
    partyName: String,
    totalMeters: Double,
    rates: List<Double>,
    currencyFormatter: NumberFormat
) {
    val metersText = if (totalMeters == totalMeters.toLong().toDouble()) {
        "${totalMeters.toLong()} Mtrs"
    } else {
        "$totalMeters Mtrs"
    }
    val rateText = rates.joinToString(" · ") { currencyFormatter.format(it) }

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        color = Slate50,
        border = BorderStroke(1.dp, Slate200)
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            OrderSummaryRow(label = "Party", value = partyName)
            OrderSummaryRow(label = "Meters", value = metersText)
            if (rateText.isNotBlank()) {
                OrderSummaryRow(label = "Rate", value = rateText)
            }
        }
    }
}

@Composable
private fun OrderSummaryRow(
    label: String,
    value: String
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.Top
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = Slate500
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold,
            color = Slate950,
            modifier = Modifier.padding(start = 12.dp)
        )
    }
}

@Composable
private fun OrderFormStatusBanner(
    message: String,
    isWarning: Boolean
) {
    val bg = if (isWarning) AmberWarningBg else SkyInfoBg
    val border = if (isWarning) AmberWarningBorder else SkyInfoBorder
    val tint = if (isWarning) AmberWarningText else SkyInfoText
    val icon = if (isWarning) Icons.Default.Warning else Icons.Default.Info

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        color = bg,
        border = BorderStroke(1.dp, border)
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.Top
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = tint,
                modifier = Modifier.size(18.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = message,
                style = MaterialTheme.typography.bodyMedium,
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
                color = tint
            )
        }
    }
}
