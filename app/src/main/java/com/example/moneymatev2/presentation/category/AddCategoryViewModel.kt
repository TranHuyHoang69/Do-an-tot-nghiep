package com.example.moneymatev2.presentation.category

import androidx.lifecycle.ViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import com.example.moneymatev2.core.util.CategoryColorPalette
import com.example.moneymatev2.data.local.entity.TransactionType
import com.example.moneymatev2.domain.model.AppResult
import com.example.moneymatev2.domain.model.TransactionError
import com.example.moneymatev2.domain.usecase.category.CreateCategoryUseCase
import com.example.moneymatev2.domain.usecase.category.EditCategoryUseCase
import com.example.moneymatev2.domain.repository.AuthRepository
import com.example.moneymatev2.domain.repository.CategoryRepository
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.distinctUntilChanged
import com.example.moneymatev2.ui.item.CategoryIconMap
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.isActive
import javax.inject.Inject


data class AddCategoryFormState(
    val userId: String? = null,
    val name: String = "",
    val type: TransactionType = TransactionType.EXPENSE,
    val selectedColorHex: String = CategoryColorPalette.colors.first(),
    val selectedIconKey: String = CategoryIconMap.allKeys().first(),
    val isSaving: Boolean = false,
    val editingCategoryId: String? = null,
    val isLoading: Boolean = false,
    val loadFailed: Boolean = false
)

sealed class AddCategoryEvent{
    object SaveSuccessfully: AddCategoryEvent()
    data class SaveFailed(val error: TransactionError) : AddCategoryEvent()
}

@HiltViewModel
class AddCategoryViewModel @Inject constructor(
    private val createCategoryUseCase: CreateCategoryUseCase,
    private val editCategoryUseCase: EditCategoryUseCase,
    private val categoryRepository: CategoryRepository,
    private val authRepository: AuthRepository,
    savedStateHandle: SavedStateHandle
) : ViewModel() {
    private val requestedCategoryId: String? = savedStateHandle["categoryId"]
    private val requestedOwnerId: String? = savedStateHandle["categoryOwnerId"]
    private val _formState = MutableStateFlow(AddCategoryFormState())
    val formState: StateFlow<AddCategoryFormState> = _formState

    init {
        viewModelScope.launch {
            authRepository.observeAuthState().map { it?.userId }.distinctUntilChanged().collect { userId ->
                _formState.value = AddCategoryFormState(userId = userId,
                    isLoading = requestedCategoryId != null && userId != null)
                if (userId != null && requestedCategoryId != null) {
                    if (requestedOwnerId != userId) {
                        _formState.value = AddCategoryFormState(userId = userId, loadFailed = true)
                        return@collect
                    }
                    try {
                        val category = categoryRepository.getCategory(userId, requestedCategoryId)
                        if (authRepository.getCurrentUserId() != userId) return@collect
                        _formState.value = if (category != null && !category.isArchived && !category.isMissing)
                            AddCategoryFormState(userId = userId, name = category.name,
                                type = category.type, selectedColorHex = category.colorHex,
                                selectedIconKey = category.iconKey, editingCategoryId = category.id)
                        else AddCategoryFormState(userId = userId, loadFailed = true)
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        if (authRepository.getCurrentUserId() == userId)
                            _formState.value = AddCategoryFormState(userId = userId, loadFailed = true)
                    }
                }
            }
        }
    }

    private val _events = Channel<AddCategoryEvent>(Channel.BUFFERED)
    val events = _events

    val availableColors: List<String> = CategoryColorPalette.colors
    val availableIcons: List<String> = CategoryIconMap.allKeys()

    private fun ownsForm(userId: String?) = userId != null &&
        userId == _formState.value.userId && userId == authRepository.getCurrentUserId() &&
        !_formState.value.isSaving && !_formState.value.isLoading && !_formState.value.loadFailed

    fun onNameChange(name: String, userId: String?){
        if (!ownsForm(userId)) return
        _formState.value = _formState.value.copy(name = name)
    }

    fun onTypeChange(type: TransactionType, userId: String?){
        if (!ownsForm(userId)) return
        if (requestedCategoryId != null) return
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
        if (state.isSaving || state.isLoading || state.loadFailed || state.name.isBlank()) return
        if (requestedCategoryId != null && state.editingCategoryId != requestedCategoryId) return
        _formState.value = state.copy(isSaving = true)
        viewModelScope.launch {
            val result = try {
                if (state.editingCategoryId != null) editCategoryUseCase(
                    expectedUserId, state.editingCategoryId, state.name,
                    state.selectedIconKey, state.selectedColorHex)
                else createCategoryUseCase(
                    userId = expectedUserId, name = state.name, type = state.type,
                    colorHex = state.selectedColorHex, iconKey = state.selectedIconKey)
            } catch (e: CancellationException) {
                if (!currentCoroutineContext().isActive) throw e
                AppResult.Failure(TransactionError.NotAuthenticated)
            } catch (e: Exception) {
                AppResult.Failure(TransactionError.UnknownError(e.message.orEmpty()))
            }

            if (_formState.value.userId != expectedUserId || authRepository.getCurrentUserId() != expectedUserId) return@launch
            _formState.value = state.copy(isSaving = false)

            when(result){
                is AppResult.Success -> {
                    if (requestedCategoryId == null) _formState.value = AddCategoryFormState(userId = expectedUserId)
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
