package es.upm.etsisi.mad.bioquiet.ui.account

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import es.upm.etsisi.mad.bioquiet.data.repository.NoiseRepository
import es.upm.etsisi.mad.bioquiet.model.Statistics
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class AccountUiState(
    val statistics: Statistics? = null,
    val isLoading: Boolean = false,
    val isEmpty: Boolean = false
)

class AccountViewModel(private val noiseRepository: NoiseRepository) : ViewModel() {

    private val _uiState = MutableStateFlow(AccountUiState())
    val uiState: StateFlow<AccountUiState> = _uiState.asStateFlow()

    init {
        loadStatistics()
    }

    fun loadStatistics() {
        _uiState.update { it.copy(isLoading = true) }
        viewModelScope.launch {
            val stats = noiseRepository.getStatistics()
            _uiState.update {
                it.copy(
                    isLoading = false,
                    statistics = stats,
                    isEmpty = stats == null
                )
            }
        }
    }

    class Factory(private val noiseRepository: NoiseRepository) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T =
            AccountViewModel(noiseRepository) as T
    }
}
