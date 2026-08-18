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

import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.android.wallpaper.R
import com.android.wallpaper.config.BaseFlags
import com.android.wallpaper.model.Screen.HOME_SCREEN
import com.android.wallpaper.model.Screen.LOCK_SCREEN
import com.android.wallpaper.picker.customization.ui.view.DeviceRadiusPreviewCardView
import com.android.wallpaper.picker.customization.ui.view.PreviewPagerViews
import com.android.wallpaper.picker.customization.ui.viewmodel.CustomizationPickerViewModel2
import com.android.wallpaper.picker.preview.ui.view.ClickableMotionLayout
import kotlinx.coroutines.launch

/** Binds the main screen home and lock screen preview. */
object PreviewPagerBinder {

    fun bind(
        previewPagerViews: PreviewPagerViews,
        viewModel: CustomizationPickerViewModel2,
        lifecycleOwner: LifecycleOwner,
    ) {
        val previewPager = previewPagerViews.previewPager
        val showDesktopUi =
            BaseFlags.get(previewPager.context).shouldShowDesktopUi(previewPager.context)
        if (!showDesktopUi) {
            val lockPreviewCard =
                previewPagerViews.lockPreview.requireViewById<DeviceRadiusPreviewCardView>(
                    R.id.preview_card
                )
            val homePreviewCard =
                previewPagerViews.homePreview.requireViewById<DeviceRadiusPreviewCardView>(
                    R.id.preview_card
                )
            lifecycleOwner.lifecycleScope.launch {
                lifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                    viewModel.selectedPreviewScreen.collect { selectedScreen ->
                        lockPreviewCard.isSelectionOutlined = selectedScreen == LOCK_SCREEN
                        homePreviewCard.isSelectionOutlined = selectedScreen == HOME_SCREEN
                    }
                }
            }
        }
        if (showDesktopUi) {
            val motionLayout = previewPager as ClickableMotionLayout
            lifecycleOwner.lifecycleScope.launch {
                lifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                    launch {
                        viewModel.isPagerInteractable.collect {
                            motionLayout.getTransition(R.id.preview_swipe_transition).isEnabled = it
                            motionLayout.shouldInterceptTouch = it
                        }
                    }
                }
            }
        }
    }
}
