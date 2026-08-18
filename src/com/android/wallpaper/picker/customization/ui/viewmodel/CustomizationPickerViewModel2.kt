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

package com.android.wallpaper.picker.customization.ui.viewmodel

import android.content.Context
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.android.wallpaper.R
import com.android.wallpaper.config.BaseFlags
import com.android.wallpaper.model.Screen
import com.android.wallpaper.model.Screen.HOME_SCREEN
import com.android.wallpaper.model.Screen.LOCK_SCREEN
import com.android.wallpaper.picker.common.preview.ui.viewmodel.BasePreviewViewModel
import com.android.wallpaper.picker.customization.ui.util.CustomizationOptionUtil
import com.android.wallpaper.util.ActivityUtils
import com.android.wallpaper.util.LaunchSourceUtils.WALLPAPER_LAUNCH_SOURCE
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.shareIn
import kotlinx.coroutines.flow.stateIn

@HiltViewModel
class CustomizationPickerViewModel2
@Inject
constructor(
    @ApplicationContext private val context: Context,
    customizationOptionUtil: CustomizationOptionUtil,
    customizationOptionsViewModelFactory: CustomizationOptionsViewModelFactory,
    basePreviewViewModelFactory: BasePreviewViewModel.Factory,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {

    /**
     * Data class defining the target alpha that a preview should be set to. If shouldAnimate is
     * true, the preview will animate to the target alpha value.
     */
    data class PreviewAlpha(val alpha: Float, val showLabel: Boolean, val shouldAnimate: Boolean)

    private val initialDestination: String? = savedStateHandle[KEY_DESTINATION]
    private val initialShortcutSlotId: String? = savedStateHandle[KEY_SHORTCUT_SLOT_ID]
    private val launchSource: String? = savedStateHandle[WALLPAPER_LAUNCH_SOURCE]

    val customizationOptionsViewModel =
        customizationOptionsViewModelFactory.create(
            viewModelScope = viewModelScope,
            initialDeepLinkDestination = initialDestination,
            initialDeepLinkShortcutSlotId = initialShortcutSlotId,
        )
    val basePreviewViewModel = basePreviewViewModelFactory.create(viewModelScope)

    enum class PickerScreen {
        MAIN,
        CUSTOMIZATION_OPTION,
    }

    private val initialPreviewScreen =
        initialDestination
            ?.let { customizationOptionUtil.getCustomizationOptionFromDestination(it) }
            ?.let { customizationOptionUtil.getScreenFromOption(it) }
    private val isLaunchedFromLauncher = ActivityUtils.isLaunchedFromLauncher(launchSource)
    private val _selectedPreviewScreen =
        MutableStateFlow(
            initialPreviewScreen
                ?: if (
                    isLaunchedFromLauncher || BaseFlags.get(context).shouldShowDesktopUi(context)
                )
                    HOME_SCREEN
                else LOCK_SCREEN
        )
    val selectedPreviewScreen = _selectedPreviewScreen.asStateFlow()

    fun selectPreviewScreen(screen: Screen) {
        _selectedPreviewScreen.value = screen
    }

    val screen =
        customizationOptionsViewModel.selectedOption
            .map {
                if (it != null) {
                    Pair(PickerScreen.CUSTOMIZATION_OPTION, it)
                } else {
                    Pair(PickerScreen.MAIN, null)
                }
            }
            .stateIn(
                viewModelScope,
                SharingStarted.WhileSubscribed(),
                Pair(PickerScreen.MAIN, null),
            )

    private val isLockPreviewReady: MutableStateFlow<Boolean> = MutableStateFlow(false)
    private val isHomePreviewReady: MutableStateFlow<Boolean> = MutableStateFlow(false)

    fun setPreviewReady(screen: Screen, isReady: Boolean) {
        when (screen) {
            LOCK_SCREEN -> isLockPreviewReady.value = isReady
            HOME_SCREEN -> isHomePreviewReady.value = isReady
        }
    }

    /** Flow of float that emits to trigger the lock screen preview to animate to an alpha value. */
    val lockPreviewAlpha: Flow<PreviewAlpha> =
        combine(isLockPreviewReady, screen, selectedPreviewScreen) {
                isPreviewReady,
                navigationScreen,
                previewScreen ->
                getPreviewAlpha(
                    isPreviewReady = isPreviewReady,
                    navigationScreen = navigationScreen.first,
                    previewScreen = previewScreen,
                    targetScreen = LOCK_SCREEN,
                )
            }
            .distinctUntilChanged()
            .shareIn(viewModelScope, SharingStarted.WhileSubscribed(), 1)

    /** Flow of a style res Id that represents the home preview label text appearance. */
    val lockPreviewLabelTextAppearance: Flow<Int> =
        selectedPreviewScreen.map {
            if (it == LOCK_SCREEN) {
                R.style.TextAppearance_Preview_Label_Selected
            } else {
                R.style.TextAppearance_Preview_Label_Unselected
            }
        }

    /** Flow of float that emits to trigger the home screen preview to animate to an alpha value. */
    val homePreviewAlpha: Flow<PreviewAlpha> =
        combine(isHomePreviewReady, screen, selectedPreviewScreen) {
                isPreviewReady,
                navigationScreen,
                previewScreen ->
                getPreviewAlpha(
                    isPreviewReady = isPreviewReady,
                    navigationScreen = navigationScreen.first,
                    previewScreen = previewScreen,
                    targetScreen = HOME_SCREEN,
                )
            }
            .distinctUntilChanged()
            .shareIn(viewModelScope, SharingStarted.WhileSubscribed(), 1)

    /** Flow of a style res Id that represents the home preview label text appearance. */
    val homePreviewLabelTextAppearance: Flow<Int> =
        selectedPreviewScreen.map {
            if (it == HOME_SCREEN) {
                R.style.TextAppearance_Preview_Label_Selected
            } else {
                R.style.TextAppearance_Preview_Label_Unselected
            }
        }

    /**
     * Get the preview's target alpha value to animate or set to.
     *
     * @return [PreviewAlpha] contains the target alpha value and shouldAnimate. If shouldAnimate is
     *   true, the view should animate to the target alpha; otherwise, directly set to the alpha.
     */
    private fun getPreviewAlpha(
        isPreviewReady: Boolean,
        navigationScreen: PickerScreen,
        previewScreen: Screen,
        targetScreen: Screen,
    ): PreviewAlpha {
        return if (isPreviewReady) {
            when (navigationScreen) {
                PickerScreen.MAIN ->
                    if (previewScreen == targetScreen)
                        PreviewAlpha(
                            alpha = PREVIEW_SHOW_ALPHA,
                            showLabel = true,
                            shouldAnimate = true,
                        )
                    else
                        PreviewAlpha(
                            alpha = PREVIEW_FADE_ALPHA,
                            showLabel = true,
                            shouldAnimate = true,
                        )
                PickerScreen.CUSTOMIZATION_OPTION -> {
                    when (previewScreen) {
                        targetScreen ->
                            PreviewAlpha(
                                alpha = PREVIEW_SHOW_ALPHA,
                                showLabel = false,
                                shouldAnimate = true,
                            )
                        else ->
                            PreviewAlpha(
                                alpha =
                                    if (BaseFlags.get(context).shouldShowDesktopUi(context)) {
                                        PREVIEW_HIDE_ALPHA
                                    } else {
                                        PREVIEW_FADE_ALPHA
                                    },
                                showLabel = false,
                                shouldAnimate = true,
                            )
                    }
                }
            }
        } else {
            PreviewAlpha(alpha = PREVIEW_HIDE_ALPHA, showLabel = false, shouldAnimate = false)
        }
    }

    val isPagerInteractable: Flow<Boolean> =
        customizationOptionsViewModel.selectedOption.map { it == null }

    val isPreviewClickable: Flow<Boolean> =
        combine(basePreviewViewModel.wallpapers, isPagerInteractable) {
            wallpapers,
            isPagerInteractable ->
            wallpapers != null && isPagerInteractable
        }

    companion object {
        const val PREVIEW_SHOW_ALPHA = 1F
        const val PREVIEW_HIDE_ALPHA = 0F
        const val PREVIEW_FADE_ALPHA = 0.4F

        const val KEY_DESTINATION = "destination"
        const val KEY_SHORTCUT_SLOT_ID = "slot_id"
    }
}
