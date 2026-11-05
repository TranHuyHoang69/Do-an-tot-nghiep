package com.example.moneymatev2.presentation.category

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.lifecycle.viewModelScope
import androidx.test.espresso.Espresso.pressBack
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.moneymatev2.R
import com.example.moneymatev2.data.local.entity.TransactionType
import com.example.moneymatev2.domain.model.CategoryModel
import com.example.moneymatev2.domain.model.UserModel
import com.example.moneymatev2.domain.repository.AuthRepository
import com.example.moneymatev2.domain.repository.CategoryRepository
import com.example.moneymatev2.domain.usecase.category.DeleteCategoryUseCase
import com.example.moneymatev2.presentation.theme.MoneyMatev2Theme
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.flowOf
import org.junit.After
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.lang.reflect.Proxy

/** In-memory fixtures only. Run on a dedicated emulator/test device. */
@RunWith(AndroidJUnit4::class)
class CategoryContextMenuAndroidTest {
    @get:Rule val rule = createComposeRule()
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext
    private val editLabel get() = context.getString(R.string.edit_category_action)
    private val deleteLabel get() = context.getString(R.string.delete)
    private val personal = CategoryModel("fixture-user", "personal", null, "Personal",
        TransactionType.EXPENSE, "ic_food", "#F44336", false, false)
    private val default = personal.copy(id = "default-food", stableId = "spend_food",
        name = "Built-in", isDefault = true)
    private var edited: CategoryModel? = null
    private var deleteRequested: CategoryModel? = null
    private var picked: CategoryModel? = null
    private var additions = 0
    private var defaultNotices = 0
    private var archiveCalls = 0
    private var deletionInspections = 0
    private var viewModel: CategoryManagementViewModel? = null

    @After fun cleanup() { viewModel?.viewModelScope?.cancel() }

    private fun showGrid(rows: List<CategoryModel> = listOf(personal, default), pickMode: Boolean = false) {
        rule.setContent {
            MoneyMatev2Theme {
                CategoryGrid(rows, isManageMode = !pickMode, onTap = { picked = it },
                    onEdit = { edited = it }, onDeleteRequested = { deleteRequested = it },
                    onAddClick = { additions++ }, onDefaultLongPress = { defaultNotices++ })
            }
        }
    }

    private fun openPersonalMenu() {
        rule.onNodeWithText(personal.name).performTouchInput { longClick() }
        rule.onAllNodes(hasClickAction() and hasAnyAncestor(isPopup())).assertCountEquals(2)
        rule.onNodeWithText(editLabel).assertIsDisplayed()
        rule.onNodeWithText(deleteLabel).assertIsDisplayed()
        rule.runOnIdle { assertEquals(0, defaultNotices) }
    }

    @Test fun singleTapHasNoManagementActionAndLongPressEditsExactlyTheSelectedCategory() {
        showGrid()
        rule.onNodeWithText(editLabel).assertDoesNotExist()
        rule.onNodeWithText(deleteLabel).assertDoesNotExist()
        rule.onNodeWithText(personal.name).performTouchInput { click() }
        rule.onNode(isPopup()).assertDoesNotExist()
        rule.runOnIdle { assertNull(edited); assertNull(deleteRequested); assertNull(picked) }
        openPersonalMenu()
        rule.onNodeWithText(editLabel).performClick()
        rule.onNode(isPopup()).assertDoesNotExist()
        rule.runOnIdle { assertEquals(personal, edited); assertNull(deleteRequested) }
    }

    @Test fun deleteMenuItemOnlyRequestsConfirmationForItsCategory() {
        showGrid()
        openPersonalMenu()
        rule.onNodeWithText(deleteLabel).performClick()
        rule.onNode(isPopup()).assertDoesNotExist()
        rule.runOnIdle { assertEquals(personal, deleteRequested); assertNull(edited) }
    }

    @Test fun defaultLongPressOnlyExplainsRestrictionWithoutMenuBadgeOrEditDeleteActions() {
        showGrid()
        rule.onNodeWithText(default.name).performTouchInput { click() }
        rule.runOnIdle { assertEquals(0, defaultNotices) }
        rule.onNodeWithText(default.name).performTouchInput { longClick() }
        rule.onNode(isPopup()).assertDoesNotExist()
        rule.runOnIdle { assertEquals(1, defaultNotices); assertNull(edited); assertNull(deleteRequested) }
        val config = rule.onNodeWithText(default.name).fetchSemanticsNode().config
        assertNull(config.getOrNull(SemanticsActions.CustomActions))
        assertEquals(context.getString(R.string.category_default_actions_blocked),
            config[SemanticsActions.OnLongClick].label)
        assertNull(config.getOrNull(SemanticsActions.OnClick))
        listOf("Mặc định", "Default", "Nhấn giữ danh mục tự tạo để xóa.",
            "Long-press a custom category to delete it.").forEach {
            rule.onNodeWithText(it).assertDoesNotExist()
        }
    }

    @Test fun personalCategoryWithDefaultNameStillHasExactlyTwoActions() {
        val sameName = personal.copy(name = default.name)
        showGrid(listOf(sameName, default))
        rule.onNode(hasText(default.name) and SemanticsMatcher.keyIsDefined(SemanticsActions.CustomActions))
            .performTouchInput { longClick() }
        rule.onAllNodes(hasClickAction() and hasAnyAncestor(isPopup())).assertCountEquals(2)
        rule.onNodeWithText(editLabel).performClick()
        rule.runOnIdle { assertEquals(sameName, edited); assertEquals(0, defaultNotices) }
    }

    @Test fun menuDismissesOnBackAndOutsideTouchWithoutInvokingActions() {
        showGrid()
        openPersonalMenu()
        pressBack()
        rule.onNode(isPopup()).assertDoesNotExist()
        openPersonalMenu()
        rule.onNode(isPopup()).performTouchInput { click(Offset(-1f, -1f)) }
        rule.onNode(isPopup()).assertDoesNotExist()
        rule.runOnIdle { assertNull(edited); assertNull(deleteRequested) }
    }

    @Test fun accessibilityActionsUseTheSameEditAndDeleteCallbacks() {
        showGrid()
        val actions = rule.onNodeWithText(personal.name).fetchSemanticsNode().config[SemanticsActions.CustomActions]
        assertEquals(listOf(editLabel, deleteLabel), actions.map { it.label })
        rule.runOnIdle { assertTrue(actions.first().action()); assertEquals(personal, edited) }
        rule.runOnIdle { assertTrue(actions.last().action()); assertEquals(personal, deleteRequested) }
        rule.onNode(isPopup()).assertDoesNotExist()
    }

    @Test fun addAndPickerTapContinueWorkingWithoutManagementMenu() {
        showGrid(pickMode = true)
        rule.onNodeWithText(personal.name).performClick()
        rule.runOnIdle { assertEquals(personal, picked) }
        rule.onNodeWithText(default.name).performTouchInput { longClick() }
        rule.onNode(isPopup()).assertDoesNotExist()
        rule.onNodeWithText(context.getString(R.string.create)).performClick()
        rule.runOnIdle { assertEquals(1, additions); assertNull(edited); assertNull(deleteRequested) }
    }

    @Test fun selectingDeleteOnManagementScreenShowsConfirmationBeforeAnyArchive() {
        showManagement()
        openPersonalMenu()
        rule.onNodeWithText(deleteLabel).performClick()
        rule.onNodeWithText(context.getString(R.string.category_delete_title)).assertIsDisplayed()
        rule.runOnIdle { assertEquals(0, archiveCalls) }
        rule.onNodeWithText(context.getString(R.string.cancel)).performClick()
        rule.runOnIdle { assertEquals(0, archiveCalls) }
    }

    @Test fun repeatedDefaultLongPressShowsShortSnackbarWithoutMenuOrDataActions() {
        showManagement()
        val message = context.getString(R.string.category_default_actions_blocked)
        rule.onNodeWithText(default.name).performTouchInput { click() }
        rule.onNodeWithText(message).assertDoesNotExist()
        openPersonalMenu()
        rule.onNodeWithText(message).assertDoesNotExist()
        pressBack()
        repeat(3) {
            rule.onNodeWithText(default.name).performTouchInput { longClick() }
        }
        rule.onAllNodesWithText(message).assertCountEquals(1)
        rule.onNode(isPopup()).assertDoesNotExist()
        rule.onNodeWithText(context.getString(R.string.category_delete_title)).assertDoesNotExist()
        rule.runOnIdle {
            assertNull(edited)
            assertEquals(0, archiveCalls)
            assertEquals(0, deletionInspections)
        }
        rule.waitUntil(8_000) { rule.onAllNodesWithText(message).fetchSemanticsNodes().isEmpty() }
    }

    private fun showManagement() {
        val auth = fake<AuthRepository> {
            when (it) {
                "observeAuthState" -> flowOf(UserModel(personal.userId, null, null, null))
                "getCurrentUserId" -> personal.userId
                else -> error("Unexpected auth call: $it")
            }
        }
        val categories = fake<CategoryRepository> {
            when (it) {
                "getActiveCategories" -> flowOf(listOf(personal, default))
                "getDeletionUsage" -> {
                    deletionInspections++
                    CategoryRepository.DeletionUsage(0, false, false)
                }
                "archiveWithReplacement" -> {
                    archiveCalls++
                    CategoryRepository.ArchiveResult.ARCHIVED
                }
                else -> error("Unexpected category call: $it")
            }
        }
        val vm = CategoryManagementViewModel(categories, auth, DeleteCategoryUseCase(categories, auth))
        viewModel = vm
        rule.setContent {
            MoneyMatev2Theme {
                ManagementCategoryScreen(vm, onOpenDrawer = {}, onAddCategoryClick = {},
                    onEditCategoryClick = { edited = it })
            }
        }
        rule.waitUntil(5_000) { vm.expenseCategories.value.isNotEmpty() }
    }

    private inline fun <reified T : Any> fake(crossinline response: (String) -> Any?): T =
        Proxy.newProxyInstance(T::class.java.classLoader, arrayOf(T::class.java)) { _, method, _ ->
            response(method.name)
        } as T
}
