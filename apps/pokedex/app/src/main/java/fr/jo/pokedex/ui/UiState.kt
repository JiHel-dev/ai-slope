package fr.jo.pokedex.ui

import fr.jo.pokedex.domain.Pokemon

enum class FailureKind { NOT_UNDERSTOOD, AMBIGUOUS, SILENCE, MICROPHONE }

sealed interface UiState {
    data object Loading : UiState
    data class Unavailable(val reason: String) : UiState
    data object Idle : UiState
    data object Listening : UiState
    data object Processing : UiState
    data class Showing(val pokemon: Pokemon, val hasImage: Boolean, val playing: Boolean) : UiState
    data class Failure(val kind: FailureKind) : UiState
    data object MicPermissionNeeded : UiState

    val canListen: Boolean get() = this is Idle || this is Showing || this is Failure || this is MicPermissionNeeded
}
