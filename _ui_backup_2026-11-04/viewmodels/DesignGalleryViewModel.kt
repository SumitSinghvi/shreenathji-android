package com.shreenathji.sales.ui.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.shreenathji.sales.data.local.entities.DesignEntity
import com.shreenathji.sales.data.local.entities.DesignFolderEntity
import com.shreenathji.sales.data.repository.PendingDesignAttachment
import com.shreenathji.sales.data.repository.SalesRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

enum class DesignSortMode {
    LATEST,
    OLDEST,
    NAME
}

/** Browse location in the design library. */
sealed class DesignBrowseLocation {
    data object Root : DesignBrowseLocation()
    data object Unfiled : DesignBrowseLocation()
    data class Folder(val id: String) : DesignBrowseLocation()
}

data class DesignAlbumRow(
    val id: String,
    val title: String,
    val count: Int,
    val coverUrl: String?,
    val location: DesignBrowseLocation
)

class DesignGalleryViewModel(
    private val repository: SalesRepository
) : ViewModel() {

    companion object {
        const val PAGE_SIZE = 30
    }

    private val _sort = MutableStateFlow(DesignSortMode.LATEST)
    val sort: StateFlow<DesignSortMode> = _sort.asStateFlow()

    private val _location = MutableStateFlow<DesignBrowseLocation>(DesignBrowseLocation.Root)
    val location: StateFlow<DesignBrowseLocation> = _location.asStateFlow()

    private val _selectedIds = MutableStateFlow<Set<String>>(emptySet())
    val selectedIds: StateFlow<Set<String>> = _selectedIds.asStateFlow()

    private val _selectionMode = MutableStateFlow(false)
    val selectionMode: StateFlow<Boolean> = _selectionMode.asStateFlow()

    private val _page = MutableStateFlow(0)
    val page: StateFlow<Int> = _page.asStateFlow()

    val designs: StateFlow<List<DesignEntity>> = repository.allDesigns
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val folders: StateFlow<List<DesignFolderEntity>> = repository.allDesignFolders
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    /** Album list shown only on Library home. */
    val homeAlbums: StateFlow<List<DesignAlbumRow>> = combine(folders, designs) { folderList, designList ->
        val rootFolders = folderList
            .filter { it.parentId == null }
            .sortedWith(compareBy({ it.sortOrder }, { it.name }))
            .map { folder ->
                val inFolder = designList.filter { it.folderId == folder.id }
                DesignAlbumRow(
                    id = folder.id,
                    title = folder.name,
                    count = inFolder.size,
                    coverUrl = inFolder.maxByOrNull { it.capturedOn }?.let { it.thumbUrl ?: it.imageUrl },
                    location = DesignBrowseLocation.Folder(folder.id)
                )
            }

        val unfiled = designList.filter { it.folderId == null }

        buildList {
            addAll(rootFolders)
            add(
                DesignAlbumRow(
                    id = "unfiled",
                    title = "Unfiled",
                    count = unfiled.size,
                    coverUrl = unfiled.maxByOrNull { it.capturedOn }?.let { it.thumbUrl ?: it.imageUrl },
                    location = DesignBrowseLocation.Unfiled
                )
            )
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val childFolders: StateFlow<List<DesignFolderEntity>> = combine(folders, _location) { list, loc ->
        when (loc) {
            is DesignBrowseLocation.Folder -> list.filter { it.parentId == loc.id }
            else -> emptyList()
        }.sortedWith(compareBy({ it.sortOrder }, { it.name }))
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val locationTitle: StateFlow<String> = combine(folders, _location) { list, loc ->
        when (loc) {
            DesignBrowseLocation.Root -> "Design Library"
            DesignBrowseLocation.Unfiled -> "Unfiled"
            is DesignBrowseLocation.Folder -> list.find { it.id == loc.id }?.name ?: "Folder"
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "Design Library")

    val filteredDesigns: StateFlow<List<DesignEntity>> =
        combine(designs, _sort, _location) { list, sort, loc ->
            if (loc is DesignBrowseLocation.Root) emptyList()
            else sortDesigns(designsInLocation(list, loc), sort)
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val visibleDesigns: StateFlow<List<DesignEntity>> = combine(filteredDesigns, _page) { list, page ->
        list.take((page + 1) * PAGE_SIZE)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val totalFilteredCount: StateFlow<Int> = filteredDesigns
        .map { it.size }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    val hasMore: StateFlow<Boolean> = combine(filteredDesigns, _page) { list, page ->
        list.size > (page + 1) * PAGE_SIZE
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    fun setSort(mode: DesignSortMode) {
        _sort.value = mode
        clearSelection()
        _page.value = 0
    }

    fun setLocation(loc: DesignBrowseLocation) {
        _location.value = loc
        clearSelection()
        _page.value = 0
    }

    fun openFolder(id: String) = setLocation(DesignBrowseLocation.Folder(id))

    fun goUp() {
        when (val loc = _location.value) {
            DesignBrowseLocation.Root -> Unit
            DesignBrowseLocation.Unfiled -> setLocation(DesignBrowseLocation.Root)
            is DesignBrowseLocation.Folder -> {
                val parentId = folders.value.find { it.id == loc.id }?.parentId
                if (parentId == null) setLocation(DesignBrowseLocation.Root)
                else openFolder(parentId)
            }
        }
    }

    fun loadMore() {
        if (hasMore.value) {
            _page.value = _page.value + 1
        }
    }

    fun startSelection(id: String) {
        _selectionMode.value = true
        _selectedIds.value = setOf(id)
    }

    fun toggleSelect(id: String) {
        if (!_selectionMode.value) {
            startSelection(id)
            return
        }
        val next = _selectedIds.value.toMutableSet()
        if (!next.add(id)) next.remove(id)
        _selectedIds.value = next
        if (next.isEmpty()) {
            _selectionMode.value = false
        }
    }

    fun clearSelection() {
        _selectedIds.value = emptySet()
        _selectionMode.value = false
    }

    fun commitSelectionToOrder(): Int {
        val selected = filteredDesigns.value.filter { _selectedIds.value.contains(it.id) }
        if (selected.isEmpty()) return 0
        repository.setPendingDesignAttachments(
            selected.map { PendingDesignAttachment(it.id, it.imageUrl) }
        )
        clearSelection()
        return selected.size
    }

    private fun designsInLocation(
        list: List<DesignEntity>,
        loc: DesignBrowseLocation
    ): List<DesignEntity> {
        return when (loc) {
            DesignBrowseLocation.Root -> emptyList()
            DesignBrowseLocation.Unfiled -> list.filter { it.folderId == null }
            is DesignBrowseLocation.Folder -> list.filter { it.folderId == loc.id }
        }
    }

    private fun sortDesigns(list: List<DesignEntity>, sort: DesignSortMode): List<DesignEntity> {
        return when (sort) {
            DesignSortMode.LATEST ->
                list.sortedWith(
                    compareByDescending<DesignEntity> { it.capturedOn }
                        .thenByDescending { it.createdAt.orEmpty() }
                )
            DesignSortMode.OLDEST ->
                list.sortedWith(
                    compareBy<DesignEntity> { it.capturedOn }
                        .thenBy { it.createdAt.orEmpty() }
                )
            DesignSortMode.NAME ->
                list.sortedBy { (it.originalFilename ?: it.id).lowercase() }
        }
    }
}

class DesignGalleryViewModelFactory(
    private val repository: SalesRepository
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        return DesignGalleryViewModel(repository) as T
    }
}
