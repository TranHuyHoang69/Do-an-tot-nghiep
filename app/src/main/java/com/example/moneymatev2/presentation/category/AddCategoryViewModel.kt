package com.example.moneymatev2.presentation.category

import androidx.compose.runtime.MutableState
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.moneymatev2.core.util.CategoryColorPalette
import com.example.moneymatev2.data.local.entity.TransactionType
import com.example.moneymatev2.domain.model.AppResult
import com.example.moneymatev2.domain.model.TransactionError
import com.example.moneymatev2.domain.usecase.category.CreateCategoryUseCase
import com.example.moneymatev2.domain.repository.AuthRepository
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.distinctUntilChanged
import com.example.moneymatev2.ui.item.CategoryIconMap
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject


data class AddCategoryFormState(
    val userId: String? = null,
    val name: String = "",
    val type: TransactionType = TransactionType.EXPENSE,
    val selectedColorHex: String = CategoryColorPalette.colors.first(),
    val selectedIconKey: String = CategoryIconMap.allKeys().first(),
    val isSaving: Boolean = false
)

sealed class AddCategoryEvent{
    object SaveSuccessfully: AddCategoryEvent()
    data class SaveFailed(val error: TransactionError) : AddCategoryEvent()
}

@HiltViewModel
class AddCategoryViewModel @Inject constructor(
    private val createCategoryUseCase: CreateCategoryUseCase,
    private val authRepository: AuthRepository
) : ViewModel() {
    private val _formState = MutableStateFlow(AddCategoryFormState())
    val formState: StateFlow<AddCategoryFormState> = _formState

    init {
        viewModelScope.launch {
            authRepository.observeAuthState().map { it?.userId }.distinctUntilChanged().collect { userId ->
                _formState.value = AddCategoryFormState(userId = userId)
            }
        }
    }

    private val _events = Channel<AddCategoryEvent>(Channel.BUFFERED)
    val events = _events

    val availableColors: List<String> = CategoryColorPalette.colors
    val availableIcons: List<String> = CategoryIconMap.allKeys()

    private fun ownsForm(userId: String?) = userId != null &&
        userId == _formState.value.userId && userId == authRepository.getCurrentUserId()

    fun onNameChange(name: String, userId: String?){
        if (!ownsForm(userId)) return
        _formState.value = _formState.value.copy(name = name)
    }

    fun onTypeChange(type: TransactionType, userId: String?){
        if (!ownsForm(userId)) return
        _formState.value = _formState.value.copy(type = type)
    }

    fun onColorChange(colorHex: String, userId: String?){
        if (!ownsForm(userId)) return
        _formState.value = _formState.value.copy(selectedColorHex = colorHex)
    }

    fun onIconChange(iconKey: String, userId: String?){
        if (!ownsForm(userId)) return
        _formState.value = _formState.value.copy(selectedIconKey = iconKey)
    }

    fun save(expectedUserId: String?){
        val state = _formState.value
        if (expectedUserId == null || expectedUserId != state.userId || expectedUserId != authRepository.getCurrentUserId()) return
        viewModelScope.launch {
            _formState.value = state.copy(isSaving = true)

            val result = createCategoryUseCase(
                userId = expectedUserId,
                name = state.name,
                type = state.type,
                colorHex = state.selectedColorHex,
                iconKey = state.selectedIconKey
            )

            if (_formState.value.userId != expectedUserId || authRepository.getCurrentUserId() != expectedUserId) return@launch
            _formState.value = state.copy(isSaving = false)

            when(result){
                is AppResult.Success -> {
                    _formState.value = AddCategoryFormState(userId = expectedUserId)
                    _events.send(AddCategoryEvent.SaveSuccessfully)
                }
                is AppResult.Failure -> {
                    _events.send(AddCategoryEvent.SaveFailed(result.error as TransactionError))
                }
                is AppResult.Loading -> {}
            }
        }
    }
}
