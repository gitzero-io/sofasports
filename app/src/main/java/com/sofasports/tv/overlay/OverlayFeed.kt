package com.sofasports.tv.overlay

import com.sofasports.tv.domain.FantasyMatchup
import com.sofasports.tv.domain.Game
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

data class OverlayState(
    val matchup: FantasyMatchup? = null,
    val game: Game? = null,
    /** Favorite teams, so the pinned game is coloured from "my" side. */
    val favorites: List<String> = emptyList(),
)

/** What the overlay shows. Data wiring lands in step 10; for now it's an empty card. */
@Singleton
class OverlayFeed @Inject constructor() {
    private val _state = MutableStateFlow(OverlayState())
    val state: StateFlow<OverlayState> = _state.asStateFlow()
    private var job: Job? = null

    fun start(scope: CoroutineScope) {
        job?.cancel()
        job = null
    }
}
