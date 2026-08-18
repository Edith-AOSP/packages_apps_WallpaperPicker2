/*
 * Copyright (C) 2024 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.wallpaper.picker.customization.ui.binder

import android.content.Intent
import android.view.View
import android.widget.LinearLayout
import androidx.core.view.isInvisible
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.android.customization.picker.icon.ui.util.IconStyleViewUtil
import com.android.wallpaper.R
import com.android.wallpaper.config.BaseFlags
import com.android.wallpaper.model.Screen
import com.android.wallpaper.model.Screen.HOME_SCREEN
import com.android.wallpaper.model.Screen.LOCK_SCREEN
import com.android.wallpaper.module.logging.UserEventLogger
import com.android.wallpaper.picker.customization.shared.model.CategoryType
import com.android.wallpaper.picker.customization.ui.CustomizationPickerActivity
import com.android.wallpaper.picker.customization.ui.util.CustomizationOptionUtil.CustomizationOption
import com.android.wallpaper.picker.customization.ui.view.PackThemeSuggestedChip
import com.android.wallpaper.picker.customization.ui.viewmodel.ColorUpdateViewModel
import com.android.wallpaper.picker.customization.ui.viewmodel.CustomizationOptionsData
import com.android.wallpaper.picker.customization.ui.viewmodel.CustomizationPickerViewModel2
import com.android.wallpaper.picker.customization.ui.viewmodel.CustomizationPickerViewModel2.PickerScreen.CUSTOMIZATION_OPTION
import com.android.wallpaper.picker.customization.ui.viewmodel.CustomizationPickerViewModel2.PickerScreen.MAIN
import com.android.wallpaper.picker.data.WallpaperModel
import com.android.wallpaper.picker.data.category.CategoryModel
import com.android.wallpaper.picker.preview.ui.view.ClickableMotionLayout
import com.android.wallpaper.util.CuratedPhotosTimeUtil
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

object CustomizationPickerBinder2 {
    /**
     * @return Callback for the [CustomizationPickerActivity] to set
     *   [CustomizationPickerViewModel2]'s screen state to null, which infers to the main screen. We
     *   need this callback to handle the back navigation in [CustomizationPickerActivity].
     */
    fun bind(
        customizationOptionsData: CustomizationOptionsData,
        view: View,
        lockScreenCustomizationOptionEntries: List<Pair<CustomizationOption, View>>,
        homeScreenCustomizationOptionEntries: List<Pair<CustomizationOption, View>>,
        customizationOptionFloatingSheetViewMap: Map<CustomizationOption, View>?,
        viewModel: CustomizationPickerViewModel2,
        colorUpdateViewModel: ColorUpdateViewModel,
        customizationOptionsBinder: CustomizationOptionsBinder,
        lifecycleOwner: LifecycleOwner,
        navigateToPrimary: () -> Unit,
        navigateToSecondary: (option: CustomizationOption) -> Unit,
        navigateToWallpaperCategoriesScreen: (screen: Screen) -> Unit,
        navigateToMoreLockScreenSettingsActivity: () -> Unit,
        navigateToColorContrastSettingsActivity: () -> Unit,
        navigateToLockScreenNotificationsSettingsActivity: () -> Unit,
        navigateToPreviewScreen:
            ((wallpaperModel: WallpaperModel, setWallpaperEntryPoint: Int) -> Unit)?,
        navigateToPackThemeActivity: (Intent) -> Unit,
        navigateToScreenSaverSettingsActivity: () -> Unit,
        navigateToWallpaperCollectionScreen:
            ((categoryModel: CategoryModel, categoryType: CategoryType) -> Unit)?,
        navigateToExtendedWallpaperEffects: (() -> Unit)?,
        packThemeSuggestedChip: PackThemeSuggestedChip?,
        packThemeSuggestedEntryBinder: PackThemeSuggestedEntryBinder,
        curatedPhotosTimeUtil: CuratedPhotosTimeUtil,
        userEventLogger: UserEventLogger,
        iconStyleViewUtil: IconStyleViewUtil,
    ) {
        val lockCustomizationOptionContainer: LinearLayout =
            view.requireViewById(R.id.lock_customization_option_container)
        val homeCustomizationOptionContainer: LinearLayout =
            view.requireViewById(R.id.home_customization_option_container)
        val previewPager: ClickableMotionLayout = view.requireViewById(R.id.preview_pager)
        val lockPreview: View = previewPager.requireViewById(R.id.lock_preview)
        val homePreview: View = previewPager.requireViewById(R.id.home_preview)
        val lockPreviewContent: View = lockPreview.requireViewById(R.id.wallpaper_preview_crop)
        val homePreviewContent: View = homePreview.requireViewById(R.id.wallpaper_preview_crop)
        val showDesktopUi = BaseFlags.get(view.context).shouldShowDesktopUi(view.context)

        previewPager.setOnTransitionCompleted { currentId ->
            when (currentId) {
                R.id.lock_preview_centered -> {
                    homePreviewContent.visibility = View.INVISIBLE
                    lockPreviewContent.visibility = View.VISIBLE
                }
                R.id.home_preview_centered -> {
                    lockPreviewContent.visibility = View.INVISIBLE
                    homePreviewContent.visibility = View.VISIBLE
                }
                R.id.lock_preview_selected,
                R.id.home_preview_selected -> {
                    lockPreviewContent.visibility = View.VISIBLE
                    homePreviewContent.visibility = View.VISIBLE
                    lockPreview.alpha = 1f
                    homePreview.alpha = 1f
                }
            }
            val screen =
                when (currentId) {
                    R.id.lock_preview_selected -> LOCK_SCREEN
                    R.id.home_preview_selected -> HOME_SCREEN
                    else -> return@setOnTransitionCompleted
                }
            viewModel.selectPreviewScreen(screen)
        }

        if (!showDesktopUi) {
            previewPager.setOnTransitionChanged { startId, endId, progress ->
                val isOpeningLock = endId == R.id.lock_preview_centered
                val isOpeningHome = endId == R.id.home_preview_centered
                val isClosing =
                    endId == R.id.lock_preview_selected || endId == R.id.home_preview_selected
                if (isOpeningLock || isOpeningHome) {
                    if (progress >= 0.38f) {
                        if (isOpeningLock) homePreviewContent.visibility = View.INVISIBLE
                        else lockPreviewContent.visibility = View.INVISIBLE
                    } else {
                        lockPreviewContent.visibility = View.VISIBLE
                        homePreviewContent.visibility = View.VISIBLE
                    }
                } else if (isClosing && progress >= 0.38f) {
                    lockPreviewContent.visibility = View.VISIBLE
                    homePreviewContent.visibility = View.VISIBLE
                    lockPreview.alpha = 1f
                    homePreview.alpha = 1f
                }
            }
        }

        lifecycleOwner.lifecycleScope.launch {
            lifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    viewModel.screen.collect { (screen, option) ->
                        when (screen) {
                            MAIN -> {
                                navigateToPrimary()
                            }
                            CUSTOMIZATION_OPTION -> {
                                option?.let(navigateToSecondary)
                            }
                        }
                    }
                }

                launch {
                    combine(viewModel.selectedPreviewScreen, viewModel.screen) {
                            selectedScreen,
                            pickerScreen ->
                            selectedScreen to pickerScreen.first
                        }
                        .collect { (selectedScreen, pickerScreen) ->
                            if (
                                !showDesktopUi &&
                                    pickerScreen == MAIN &&
                                    (previewPager.currentState == R.id.lock_preview_selected ||
                                        previewPager.currentState == R.id.home_preview_selected)
                            ) {
                                lockPreviewContent.visibility = View.VISIBLE
                                homePreviewContent.visibility = View.VISIBLE
                                lockPreview.alpha = 1f
                                homePreview.alpha = 1f
                            }
                            val targetState =
                                when {
                                    !showDesktopUi && pickerScreen == CUSTOMIZATION_OPTION ->
                                        when (selectedScreen) {
                                            LOCK_SCREEN -> R.id.lock_preview_centered
                                            HOME_SCREEN -> R.id.home_preview_centered
                                        }
                                    else ->
                                        when (selectedScreen) {
                                            LOCK_SCREEN -> R.id.lock_preview_selected
                                            HOME_SCREEN -> R.id.home_preview_selected
                                        }
                                }
                            if (previewPager.currentState != targetState) {
                                previewPager.transitionToState(
                                    targetState,
                                    PREVIEW_TRANSITION_DURATION,
                                )
                            }

                            when (selectedScreen) {
                                LOCK_SCREEN -> {
                                    lockCustomizationOptionContainer.isInvisible = false
                                    homeCustomizationOptionContainer.isInvisible = true
                                }
                                HOME_SCREEN -> {
                                    lockCustomizationOptionContainer.isInvisible = true
                                    homeCustomizationOptionContainer.isInvisible = false
                                }
                            }
                        }
                }
            }
        }

        if (BaseFlags.get(view.context).isPackThemeEnabled()) {
            packThemeSuggestedChip?.let {
                packThemeSuggestedEntryBinder.bind(
                    view = it,
                    viewModel = viewModel,
                    colorUpdateViewModel = colorUpdateViewModel,
                    lifecycleOwner = lifecycleOwner,
                    navigateToPackThemeActivity = navigateToPackThemeActivity,
                )
            }
        }

        customizationOptionsBinder.bind(
            customizationOptionsData,
            view,
            lockScreenCustomizationOptionEntries,
            homeScreenCustomizationOptionEntries,
            customizationOptionFloatingSheetViewMap,
            viewModel,
            colorUpdateViewModel,
            lifecycleOwner,
            navigateToMoreLockScreenSettingsActivity,
            navigateToColorContrastSettingsActivity,
            navigateToLockScreenNotificationsSettingsActivity,
            navigateToPackThemeActivity,
            navigateToScreenSaverSettingsActivity,
            iconStyleViewUtil,
        )
    }

    private const val PREVIEW_TRANSITION_DURATION = 360
}
