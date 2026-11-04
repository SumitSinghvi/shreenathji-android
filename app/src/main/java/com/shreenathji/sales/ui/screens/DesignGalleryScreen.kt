package com.shreenathji.sales.ui.screens

import android.net.Uri
import androidx.compose.foundation.BorderStroke
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Inbox
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.shreenathji.sales.data.local.entities.DesignFolderEntity
import com.shreenathji.sales.ui.components.ImageViewerDialog
import com.shreenathji.sales.ui.theme.*
import com.shreenathji.sales.ui.viewmodels.DesignAlbumRow
import com.shreenathji.sales.ui.viewmodels.DesignBrowseLocation
import com.shreenathji.sales.ui.viewmodels.DesignGalleryViewModel
import com.shreenathji.sales.ui.viewmodels.DesignSortMode

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DesignGalleryScreen(
    viewModel: DesignGalleryViewModel,
    onAddToOrder: () -> Unit,
    returnToOrderForm: Boolean = false
) {
    val sort by viewModel.sort.collectAsState()
    val location by viewModel.location.collectAsState()
    val selectedIds by viewModel.selectedIds.collectAsState()
    val selectionMode by viewModel.selectionMode.collectAsState()
    val designs by viewModel.visibleDesigns.collectAsState()
    val childFolders by viewModel.childFolders.collectAsState()
    val homeAlbums by viewModel.homeAlbums.collectAsState()
    val locationTitle by viewModel.locationTitle.collectAsState()
    val totalCount by viewModel.totalFilteredCount.collectAsState()
    val hasMore by viewModel.hasMore.collectAsState()
    var previewUri by remember { mutableStateOf<Uri?>(null) }
    var sortMenuExpanded by remember { mutableStateOf(false) }

    val isHome = location is DesignBrowseLocation.Root
    val canGoUp = !isHome

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Slate50)
    ) {
        Surface(
            color = ShadcnWhite,
            shadowElevation = 2.dp
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(horizontal = 16.dp, vertical = 12.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (canGoUp) {
                        IconButton(
                            onClick = { viewModel.goUp() },
                            modifier = Modifier.size(32.dp)
                        ) {
                            Icon(
                                Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "Back",
                                tint = Slate700
                            )
                        }
                        Spacer(modifier = Modifier.width(4.dp))
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = locationTitle,
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            color = Slate950,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = when {
                                isHome ->
                                    if (homeAlbums.isEmpty()) "No folders yet"
                                    else "${homeAlbums.size} album${if (homeAlbums.size == 1) "" else "s"}"
                                selectionMode ->
                                    "${selectedIds.size} selected · ${designs.size} of $totalCount"
                                else ->
                                    if (totalCount == 0 && childFolders.isEmpty()) "Empty"
                                    else "$totalCount design${if (totalCount == 1) "" else "s"} · Hold to select"
                            },
                            style = MaterialTheme.typography.labelMedium,
                            color = Slate500
                        )
                    }

                    if (!isHome) {
                        Box {
                            TextButton(onClick = { sortMenuExpanded = true }) {
                                Text(
                                    text = when (sort) {
                                        DesignSortMode.LATEST -> "Latest"
                                        DesignSortMode.OLDEST -> "Oldest"
                                        DesignSortMode.NAME -> "Name"
                                    },
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = Slate700
                                )
                            }
                            DropdownMenu(
                                expanded = sortMenuExpanded,
                                onDismissRequest = { sortMenuExpanded = false }
                            ) {
                                DropdownMenuItem(
                                    text = { Text("Latest first") },
                                    onClick = {
                                        viewModel.setSort(DesignSortMode.LATEST)
                                        sortMenuExpanded = false
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text("Oldest first") },
                                    onClick = {
                                        viewModel.setSort(DesignSortMode.OLDEST)
                                        sortMenuExpanded = false
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text("Name A–Z") },
                                    onClick = {
                                        viewModel.setSort(DesignSortMode.NAME)
                                        sortMenuExpanded = false
                                    }
                                )
                            }
                        }
                    }
                }
            }
        }

        when {
            isHome -> {
                if (homeAlbums.isEmpty()) {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "No designs yet.\nAsk HQ to upload from the web dashboard.",
                            color = Slate400,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                } else {
                    LazyColumn(
                        contentPadding = PaddingValues(12.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                        modifier = Modifier.fillMaxSize()
                    ) {
                        items(homeAlbums, key = { it.id }) { album ->
                            AlbumRow(
                                album = album,
                                onClick = { viewModel.setLocation(album.location) }
                            )
                        }
                    }
                }
            }

            designs.isEmpty() && childFolders.isEmpty() -> {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = when (location) {
                            is DesignBrowseLocation.Unfiled -> "No unfiled designs."
                            else -> "This folder is empty."
                        },
                        color = Slate400,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
            }

            else -> {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(1),
                    contentPadding = PaddingValues(12.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                ) {
                    if (childFolders.isNotEmpty()) {
                        items(childFolders, key = { "folder-${it.id}" }) { folder ->
                            SubfolderRow(
                                folder = folder,
                                onClick = { viewModel.openFolder(folder.id) }
                            )
                        }
                    }

                    items(designs, key = { it.id }) { design ->
                        val selected = selectedIds.contains(design.id)
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .aspectRatio(16f / 10f)
                                .clip(RoundedCornerShape(14.dp))
                                .border(
                                    width = if (selected) 2.dp else 1.dp,
                                    color = if (selected) BrandOrange else Slate200,
                                    shape = RoundedCornerShape(14.dp)
                                )
                                .pointerInput(selectionMode, design.id) {
                                    detectTapGestures(
                                        onLongPress = {
                                            viewModel.startSelection(design.id)
                                        },
                                        onTap = {
                                            if (selectionMode) {
                                                viewModel.toggleSelect(design.id)
                                            } else {
                                                previewUri = Uri.parse(design.imageUrl)
                                            }
                                        }
                                    )
                                }
                        ) {
                            AsyncImage(
                                model = design.thumbUrl ?: design.imageUrl,
                                contentDescription = design.originalFilename,
                                modifier = Modifier.fillMaxSize(),
                                contentScale = ContentScale.Crop
                            )

                            if (selectionMode) {
                                Box(
                                    modifier = Modifier
                                        .align(Alignment.TopEnd)
                                        .padding(10.dp)
                                        .size(28.dp)
                                        .background(
                                            if (selected) BrandOrange else Slate950.copy(alpha = 0.45f),
                                            CircleShape
                                        ),
                                    contentAlignment = Alignment.Center
                                ) {
                                    if (selected) {
                                        Icon(
                                            imageVector = Icons.Default.Check,
                                            contentDescription = null,
                                            tint = Slate950,
                                            modifier = Modifier.size(16.dp)
                                        )
                                    }
                                }
                            }

                            Box(
                                modifier = Modifier
                                    .align(Alignment.BottomStart)
                                    .fillMaxWidth()
                                    .background(Slate950.copy(alpha = 0.55f))
                                    .padding(horizontal = 12.dp, vertical = 8.dp)
                            ) {
                                Text(
                                    text = design.capturedOn,
                                    color = ShadcnWhite,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Medium,
                                    maxLines = 1
                                )
                            }
                        }
                    }

                    if (hasMore) {
                        item(span = { GridItemSpan(1) }) {
                            OutlinedButton(
                                onClick = { viewModel.loadMore() },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 8.dp)
                            ) {
                                Text(
                                    text = "Load more (${totalCount - designs.size} left)",
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                        }
                    }
                }
            }
        }

        if (selectionMode && selectedIds.isNotEmpty()) {
            Surface(
                color = ShadcnWhite,
                shadowElevation = 8.dp,
                tonalElevation = 2.dp
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .navigationBarsPadding()
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    OutlinedButton(
                        onClick = { viewModel.clearSelection() },
                        modifier = Modifier.weight(1f).defaultMinSize(minHeight = 48.dp),
                        shape = RoundedCornerShape(12.dp),
                        border = BorderStroke(1.dp, Slate200),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = Slate900)
                    ) {
                        Text("Cancel")
                    }
                    Button(
                        onClick = {
                            val count = viewModel.commitSelectionToOrder()
                            if (count > 0) onAddToOrder()
                        },
                        modifier = Modifier.weight(2f).defaultMinSize(minHeight = 48.dp),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = BrandOrange,
                            contentColor = Color.White
                        )
                    ) {
                        Icon(
                            Icons.Default.ShoppingCart,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            if (returnToOrderForm) "Add to order" else "Use in order",
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
    }

    previewUri?.let { uri ->
        ImageViewerDialog(
            imageUri = uri,
            onDismiss = { previewUri = null }
        )
    }
}

@Composable
private fun AlbumRow(album: DesignAlbumRow, onClick: () -> Unit) {
    val isUnfiled = album.id == "unfiled"
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(16.dp),
        color = ShadcnWhite,
        border = androidx.compose.foundation.BorderStroke(1.dp, Slate200),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(64.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(Slate100),
                contentAlignment = Alignment.Center
            ) {
                if (album.coverUrl != null) {
                    AsyncImage(
                        model = album.coverUrl,
                        contentDescription = null,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop
                    )
                } else {
                    Icon(
                        imageVector = if (isUnfiled) Icons.Default.Inbox else Icons.Default.Folder,
                        contentDescription = null,
                        tint = if (isUnfiled) Slate400 else BrandOrange,
                        modifier = Modifier.size(28.dp)
                    )
                }
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = album.title,
                    fontWeight = FontWeight.SemiBold,
                    color = Slate950,
                    fontSize = 15.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = "${album.count} design${if (album.count == 1) "" else "s"}",
                    color = Slate500,
                    fontSize = 12.sp
                )
            }
        }
    }
}

@Composable
private fun SubfolderRow(folder: DesignFolderEntity, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(14.dp),
        color = ShadcnWhite,
        border = androidx.compose.foundation.BorderStroke(1.dp, Slate200),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .background(BrandOrange.copy(alpha = 0.15f), RoundedCornerShape(10.dp)),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Default.Folder, contentDescription = null, tint = BrandOrange)
            }
            Text(
                text = folder.name,
                fontWeight = FontWeight.SemiBold,
                color = Slate950,
                fontSize = 14.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
        }
    }
}
