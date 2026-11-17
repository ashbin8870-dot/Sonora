package com.example.ui.navigation

import kotlinx.serialization.Serializable

@Serializable
data object DiscoverRoute

@Serializable
data object LibraryRoute

@Serializable
data object PlaylistsRoute

@Serializable
data class PlaylistDetailRoute(val playlistId: Long)

@Serializable
data object SearchRoute
