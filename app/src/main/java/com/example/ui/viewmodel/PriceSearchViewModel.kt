package com.example.ui.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.model.PriceProductItem
import com.example.data.model.PriceSortOrder
import com.example.data.repository.PriceSearchService
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class PriceSearchUiState(
    val query: String = "",
    val lastSearchedQuery: String = "",
    val sortOrder: PriceSortOrder = PriceSortOrder.CHEAPEST_FIRST,
    val isLoading: Boolean = false,
    val hasSearched: Boolean = false,
    val items: List<PriceProductItem> = emptyList(),
    val errorMessage: String? = null
) {
    val sortedItems: List<PriceProductItem>
        get() = when (sortOrder) {
            PriceSortOrder.CHEAPEST_FIRST -> items.sortedWith(
                compareBy<PriceProductItem> { if (it.priceToman <= 0L) Long.MAX_VALUE else it.priceToman }
                    .thenBy { it.title }
            )
            PriceSortOrder.EXPENSIVE_FIRST -> items.sortedWith(
                compareByDescending<PriceProductItem> { it.priceToman }
                    .thenBy { it.title }
            )
        }
}

class PriceSearchViewModel(application: Application) : AndroidViewModel(application) {

    private val service = PriceSearchService()
    private var activeSearchJob: Job? = null

    private val _uiState = MutableStateFlow(PriceSearchUiState())
    val uiState: StateFlow<PriceSearchUiState> = _uiState.asStateFlow()

    fun onQueryChange(newQuery: String) {
        _uiState.update { it.copy(query = newQuery) }
    }

    fun clearQuery() {
        activeSearchJob?.cancel()
        _uiState.update {
            it.copy(
                query = "",
                isLoading = false,
                errorMessage = null
            )
        }
    }

    fun toggleSortOrder() {
        _uiState.update { state ->
            val next = if (state.sortOrder == PriceSortOrder.CHEAPEST_FIRST) {
                PriceSortOrder.EXPENSIVE_FIRST
            } else {
                PriceSortOrder.CHEAPEST_FIRST
            }
            state.copy(sortOrder = next)
        }
    }

    fun search() {
        val trimmed = _uiState.value.query.trim()
        if (trimmed.isBlank()) {
            _uiState.update {
                it.copy(
                    errorMessage = "لطفاً نام کالا را وارد کنید (مثلاً: گوشی سامسونگ، هدفون یا لپ‌تاپ)."
                )
            }
            return
        }

        activeSearchJob?.cancel()
        activeSearchJob = viewModelScope.launch {
            _uiState.update {
                it.copy(
                    isLoading = true,
                    hasSearched = true,
                    lastSearchedQuery = trimmed,
                    errorMessage = null
                )
            }

            val response = service.searchBasalam(trimmed)

            _uiState.update {
                it.copy(
                    isLoading = false,
                    items = response.items,
                    errorMessage = response.errorMessage
                )
            }
        }
    }
}
