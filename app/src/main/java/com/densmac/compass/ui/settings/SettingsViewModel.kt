package com.densmac.compass.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.densmac.compass.data.SettingsRepository
import com.densmac.compass.domain.Settings
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val repository: SettingsRepository,
) : ViewModel() {
    val settings: StateFlow<Settings> =
        repository.settings.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), Settings())

    fun update(transform: (Settings) -> Settings) {
        viewModelScope.launch { repository.update(transform) }
    }
}
