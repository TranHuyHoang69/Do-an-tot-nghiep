package com.example.moneymatev2.presentation.category

import android.app.Application
import android.content.res.Configuration
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import com.example.moneymatev2.R
import com.example.moneymatev2.data.local.entity.TransactionType
import com.example.moneymatev2.domain.model.CategoryModel
import com.example.moneymatev2.domain.model.TransactionError
import com.example.moneymatev2.domain.model.UserModel
import com.example.moneymatev2.domain.repository.AuthRepository
import com.example.moneymatev2.domain.repository.CategoryRepository
import com.example.moneymatev2.domain.usecase.category.CreateCategoryUseCase
import com.example.moneymatev2.domain.usecase.category.EditCategoryUseCase
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.*
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mockito.*
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.RuntimeEnvironment
import java.util.Locale

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class CategoryEditingViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private val auth = mock(AuthRepository::class.java)
    private val categories = mock(CategoryRepository::class.java)
    private val authState = MutableStateFlow<UserModel?>(UserModel("a", null, null, null))
    private val viewModels = mutableListOf<AddCategoryViewModel>()
    private var uid = "a"
    private val category = CategoryModel("a", "existing", null, "Original", TransactionType.INCOME,
        "ic_money", "#009688", false, false)

    @Before fun setup() = runTest(dispatcher) {
        Dispatchers.setMain(dispatcher)
        `when`(auth.getCurrentUserId()).thenAnswer { uid }
        `when`(auth.observeAuthState()).thenReturn(authState)
        `when`(categories.getCategory("a", category.id)).thenReturn(category)
    }

    @After fun cleanup() {
        viewModels.forEach { it.viewModelScope.cancel() }
        Dispatchers.resetMain()
    }

    private fun viewModel(repository: CategoryRepository = categories, editMode: Boolean = true): AddCategoryViewModel {
        val handle = if (editMode) SavedStateHandle(mapOf("categoryId" to category.id, "categoryOwnerId" to "a"))
            else SavedStateHandle()
        return AddCategoryViewModel(CreateCategoryUseCase(repository, auth), EditCategoryUseCase(repository, auth),
            repository, auth, handle).also { viewModels += it }
    }

    @Test fun prefillLocksTypeAndNameOnlySaveKeepsOriginalIconColorAndIdentity() = runTest(dispatcher) {
        val vm = viewModel()
        advanceUntilIdle()
        assertEquals(category.name, vm.formState.value.name)
        assertEquals(category.id, vm.formState.value.editingCategoryId)
        assertEquals(category.iconKey, vm.formState.value.selectedIconKey)
        assertEquals(category.colorHex, vm.formState.value.selectedColorHex)
        vm.onTypeChange(TransactionType.EXPENSE, "a")
        assertEquals(TransactionType.INCOME, vm.formState.value.type)
        vm.onNameChange("  Changed  ", "a")
        vm.save("a")
        advanceUntilIdle()
        verify(categories).updateCategory(category.copy(name = "Changed"))
        verify(categories, never()).createCategory(anyCategory(), eq("a") ?: "a")
        assertEquals(AddCategoryEvent.SaveSuccessfully, vm.events.receive())
    }

    @Test fun saveFailureKeepsFormAndNeverEmitsSuccessThenRetryWorks() = runTest(dispatcher) {
        val edited = category.copy(name = "Changed", iconKey = "ic_car", colorHex = "#2196F3")
        `when`(categories.updateCategory(edited)).thenThrow(IllegalStateException("disk failed")).thenAnswer { null }
        val vm = viewModel()
        advanceUntilIdle()
        vm.onNameChange(edited.name, "a")
        vm.onIconChange(edited.iconKey, "a")
        vm.onColorChange(edited.colorHex, "a")
        vm.save("a")
        advanceUntilIdle()
        assertTrue(vm.events.receive() is AddCategoryEvent.SaveFailed)
        assertTrue(vm.events.tryReceive().isFailure)
        assertFalse(vm.formState.value.isSaving)
        assertEquals(edited.name, vm.formState.value.name)
        assertEquals(edited.iconKey, vm.formState.value.selectedIconKey)
        assertEquals(edited.colorHex, vm.formState.value.selectedColorHex)
        vm.save("a")
        advanceUntilIdle()
        assertEquals(AddCategoryEvent.SaveSuccessfully, vm.events.receive())
        verify(categories, times(2)).updateCategory(edited)
    }

    @Test fun repeatedSaveWhileWriteIsSuspendedProducesExactlyOneUpdate() = runTest(dispatcher) {
        val gate = CompletableDeferred<Unit>()
        var writes = 0
        val repository = object : CategoryRepository by categories {
            override suspend fun updateCategory(category: CategoryModel) {
                writes++
                gate.await()
            }
        }
        val vm = viewModel(repository)
        advanceUntilIdle()
        vm.onNameChange("Changed", "a")
        vm.save("a")
        vm.save("a")
        runCurrent()
        assertTrue(vm.formState.value.isSaving)
        vm.save("a")
        vm.onIconChange("unexpected", "a")
        assertEquals(category.iconKey, vm.formState.value.selectedIconKey)
        assertEquals(1, writes)
        gate.complete(Unit)
        advanceUntilIdle()
        assertEquals(AddCategoryEvent.SaveSuccessfully, vm.events.receive())
        assertTrue(vm.events.tryReceive().isFailure)
    }

    @Test fun whitespaceNameCannotSave() = runTest(dispatcher) {
        val vm = viewModel()
        advanceUntilIdle()
        clearInvocations(categories)
        vm.onNameChange(" \n ", "a")
        vm.save("a")
        advanceUntilIdle()
        verifyNoInteractions(categories)
        assertFalse(vm.formState.value.isSaving)
        assertTrue(vm.events.tryReceive().isFailure)
    }

    @Test fun missingCategoryOrLoadFailureCannotFallBackToCreatingCategory() = runTest(dispatcher) {
        `when`(categories.getCategory("a", category.id)).thenReturn(null)
        val missing = viewModel()
        advanceUntilIdle()
        assertTrue(missing.formState.value.loadFailed)
        missing.onNameChange("New", "a")
        missing.save("a")
        `when`(categories.getCategory("a", category.id)).thenThrow(IllegalStateException("read failed"))
        val failed = viewModel()
        advanceUntilIdle()
        assertTrue(failed.formState.value.loadFailed)
        failed.save("a")
        verify(categories, never()).createCategory(anyCategory(), eq("a") ?: "a")
        verify(categories, never()).updateCategory(anyCategory())
    }

    @Test fun accountSwitchWithSameLocalIdRejectsOldScreenAndStaleSave() = runTest(dispatcher) {
        val vm = viewModel()
        advanceUntilIdle()
        clearInvocations(categories)
        uid = "b"
        vm.save("a")
        authState.value = UserModel("b", null, null, null)
        advanceUntilIdle()
        assertTrue(vm.formState.value.loadFailed)
        vm.onNameChange("Wrong owner", "b")
        vm.save("b")
        advanceUntilIdle()
        verifyNoInteractions(categories)
        assertTrue(vm.events.tryReceive().isFailure)
    }

    @Test fun createModeStillCreatesInsteadOfUpdating() = runTest(dispatcher) {
        val vm = viewModel(editMode = false)
        advanceUntilIdle()
        vm.onNameChange("  Personal  ", "a")
        vm.onIconChange(category.iconKey, "a")
        vm.onColorChange(category.colorHex, "a")
        vm.save("a")
        advanceUntilIdle()
        verify(categories).createCategory(category.copy(id = "", name = "Personal", type = TransactionType.EXPENSE), "a")
        verify(categories, never()).updateCategory(anyCategory())
        assertEquals(AddCategoryEvent.SaveSuccessfully, vm.events.receive())
    }

    @Test fun editorActionsAndLoadErrorHaveVietnameseAndEnglishResources() {
        val app = RuntimeEnvironment.getApplication()
        val vi = app.createConfigurationContext(Configuration(app.resources.configuration).apply {
            setLocale(Locale.forLanguageTag("vi"))
        })
        val en = app.createConfigurationContext(Configuration(app.resources.configuration).apply {
            setLocale(Locale.forLanguageTag("en"))
        })
        listOf(R.string.edit_category_action, R.string.edit_category_title,
            R.string.save_category_changes, R.string.category_load_failed).forEach { id ->
            assertTrue(vi.getString(id).isNotBlank())
            assertTrue(en.getString(id).isNotBlank())
            assertNotEquals(vi.getString(id), en.getString(id))
        }
    }

    private fun anyCategory(): CategoryModel = any(CategoryModel::class.java) ?: category
}
