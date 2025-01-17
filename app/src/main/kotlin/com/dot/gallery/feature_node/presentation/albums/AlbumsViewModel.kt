/*
 * SPDX-FileCopyrightText: 2023 IacobIacob01
 * SPDX-License-Identifier: Apache-2.0
 */

package com.dot.gallery.feature_node.presentation.albums

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dot.gallery.R
import com.dot.gallery.core.AlbumState
import com.dot.gallery.core.Resource
import com.dot.gallery.core.Settings
import com.dot.gallery.core.presentation.components.FilterOption
import com.dot.gallery.feature_node.domain.model.Album
import com.dot.gallery.feature_node.domain.use_case.MediaUseCases
import com.dot.gallery.feature_node.domain.util.MediaOrder
import com.dot.gallery.feature_node.domain.util.OrderType
import com.dot.gallery.feature_node.presentation.util.Screen
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class AlbumsViewModel @Inject constructor(
    private val mediaUseCases: MediaUseCases
) : ViewModel() {

    val albumsState = mutableStateOf(AlbumState())
    val pinnedAlbumState = mutableStateOf(AlbumState())

    fun onAlbumClick(navigate: (String) -> Unit): (Album) -> Unit = { album ->
        navigate(Screen.AlbumViewScreen.route + "?albumId=${album.id}&albumName=${album.label}")
    }
    val onAlbumLongClick: (Album) -> Unit = { album ->
        toggleAlbumPin(album, !album.isPinned)
    }

    init {
        getAlbums()
    }

    @Composable
    fun rememberFilters(): SnapshotStateList<FilterOption> {
        val lastValue by Settings.Album.rememberLastSort()
        return remember(lastValue) {
            mutableStateListOf(
                FilterOption(
                    titleRes = R.string.filter_recent,
                    mediaOrder = MediaOrder.Date(OrderType.Descending),
                    onClick = { updateOrder(it) },
                    selected = lastValue == 0
                ),
                FilterOption(
                    titleRes = R.string.filter_old,
                    mediaOrder = MediaOrder.Date(OrderType.Ascending),
                    onClick = { updateOrder(it) },
                    selected = lastValue == 1
                ),

                FilterOption(
                    titleRes = R.string.filter_nameAZ,
                    mediaOrder = MediaOrder.Label(OrderType.Ascending),
                    onClick = { updateOrder(it) },
                    selected = lastValue == 2
                ),
                FilterOption(
                    titleRes = R.string.filter_nameZA,
                    mediaOrder = MediaOrder.Label(OrderType.Descending),
                    onClick = { updateOrder(it) },
                    selected = lastValue == 3
                )
            )
        }
    }

    private fun updateOrder(mediaOrder: MediaOrder) {
        viewModelScope.launch {
            val newState = albumsState.value.copy(
                albums = mediaOrder.sortAlbums(albumsState.value.albums)
            )
            if (albumsState.value != newState) {
                albumsState.value = newState
            }
        }
    }

    private fun toggleAlbumPin(album: Album, isPinned: Boolean = true) {
        viewModelScope.launch {
            val newAlbum = album.copy(isPinned = isPinned)
            if (isPinned) {
                // Insert pinnedAlbumId to Database
                mediaUseCases.insertPinnedAlbumUseCase(newAlbum)
                // Remove original Album from unpinned List
                albumsState.value = albumsState.value.copy(
                    albums = albumsState.value.albums.minus(album)
                )
                // Add 'pinned' version of the album object to the pinned List
                pinnedAlbumState.value = pinnedAlbumState.value.copy(
                    albums = pinnedAlbumState.value.albums.toMutableList().apply { add(newAlbum) }
                )
            } else {
                // Delete pinnedAlbumId from Database
                mediaUseCases.deletePinnedAlbumUseCase(album)
                // Add 'un-pinned' version of the album object to the pinned List
                albumsState.value = albumsState.value.copy(
                    albums = albumsState.value.albums.toMutableList().apply { add(newAlbum) }
                )
                // Remove original Album from pinned List
                pinnedAlbumState.value = pinnedAlbumState.value.copy(
                    albums = pinnedAlbumState.value.albums.minus(album)
                )
            }
        }
    }

    private fun getAlbums(mediaOrder: MediaOrder = MediaOrder.Date(OrderType.Descending)) {
        viewModelScope.launch {
            mediaUseCases.getAlbumsUseCase(mediaOrder).onEach { result ->
                // Result data list
                val data = result.data ?: emptyList()
                val error = if (result is Resource.Error) result.message ?: "An error occurred" else ""
                val newAlbumState = AlbumState(error = error, albums = data.filter { !it.isPinned })
                val newPinnedState = AlbumState(error = error, albums = data.filter { it.isPinned }.sortedBy { it.label })
                if (albumsState.value != newAlbumState) {
                    albumsState.value = newAlbumState
                }
                if (pinnedAlbumState.value != newPinnedState) {
                    pinnedAlbumState.value = newPinnedState
                }
            }.flowOn(Dispatchers.IO).collect()
        }
    }

}