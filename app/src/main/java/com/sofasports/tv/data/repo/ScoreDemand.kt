package com.sofasports.tv.data.repo

import com.sofasports.tv.domain.League
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Leagues another feature needs scores for, beyond the ones the user follows: the selected
 * fantasy league's sport. Kept separate so ScoresRepository doesn't depend on FantasyRepository
 * (which already depends on it).
 */
@Singleton
class ScoreDemand @Inject constructor() {
    private val _fantasy = MutableStateFlow<League?>(null)
    val fantasy: StateFlow<League?> = _fantasy.asStateFlow()

    fun setFantasySport(sport: League?) {
        _fantasy.value = sport
    }
}
