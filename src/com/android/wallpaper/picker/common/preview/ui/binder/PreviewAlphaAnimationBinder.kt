/*
 * Copyright (C) 2025 The Android Open Source Project
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

package com.android.wallpaper.picker.common.preview.ui.binder

import android.view.View
import android.widget.TextView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.android.wallpaper.R
import com.android.wallpaper.config.BaseFlags
import com.android.wallpaper.model.Screen.HOME_SCREEN
import com.android.wallpaper.model.Screen.LOCK_SCREEN
import com.android.wallpaper.picker.customization.ui.util.ViewAlphaAnimator.animateToAlpha
import com.android.wallpaper.picker.customization.ui.view.PreviewPagerViews
import com.android.wallpaper.picker.customization.ui.viewmodel.CustomizationPickerViewModel2
import com.android.wallpaper.picker.preview.ui.view.ClickableMotionLayout
import kotlinx.coroutines.launch

/**
 * Animates the preview and the preview label to a target alpha. The timing to call bind() is
 * critical. Avoid calling it during Activity or Fragment transition that also takes care of the
 * alpha fade of the screen.
 */
object PreviewAlphaAnimationBinder {

    fun bind(
        previewPagerViews: PreviewPagerViews,
        viewModel: CustomizationPickerViewModel2,
        lifecycleOwner: LifecycleOwner,
    ) {
        val previewPager = previewPagerViews.previewPager
        val lockPreview: View = previewPagerViews.lockPreview
        val lockPreviewLabel: TextView = previewPagerViews.lockPreviewLabel
        val lockPreviewLabelContainer: View = previewPagerViews.lockPreviewLabelContainer
        val lockPreviewShade: View = previewPagerViews.lockPreviewShade
        val homePreview: View = previewPagerViews.homePreview
        val homePreviewLabel: TextView = previewPagerViews.homePreviewLabel
        val homePreviewLabelContainer: View = previewPagerViews.homePreviewLabelContainer
        val homePreviewShade: View = previewPagerViews.homePreviewShade
        val showDesktopUi =
            BaseFlags.get(previewPager.context).shouldShowDesktopUi(previewPager.context)

        lifecycleOwner.lifecycleScope.launch {
            lifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    viewModel.lockPreviewAlpha.collect { (alpha, showLabel, shouldAnimate) ->
                        val shadeAlpha = 1 - alpha
                        val labelAlpha = if (showLabel) alpha else 0f
                        val labelVisibility = if (showLabel) View.VISIBLE else View.GONE
                        if (showDesktopUi) {
                            val motionLayout = previewPager as ClickableMotionLayout
                            if (showLabel) {
                                motionLayout.addClickableViewId(R.id.lock_preview_label_container)
                                lockPreviewLabelContainer.setOnClickListener {
                                    viewModel.selectPreviewScreen(LOCK_SCREEN)
                                }
                            } else {
                                motionLayout.removeClickableViewId(
                                    R.id.lock_preview_label_container
                                )
                                lockPreviewLabelContainer.setOnClickListener(null)
                            }
                        }
                        if (shouldAnimate) {
                            lockPreviewLabelContainer.visibility = View.VISIBLE
                            lockPreviewLabelContainer.animateToAlpha(labelAlpha) {
                                lockPreviewLabelContainer.visibility = labelVisibility
                            }
                            lockPreviewShade.animateToAlpha(shadeAlpha)
                        } else {
                            lockPreviewLabelContainer.alpha = labelAlpha
                            lockPreviewShade.alpha = shadeAlpha
                            lockPreviewLabelContainer.visibility = labelVisibility
                        }
                    }
                }

                launch {
                    viewModel.homePreviewAlpha.collect { (alpha, showLabel, shouldAnimate) ->
                        val shadeAlpha = 1 - alpha
                        val labelAlpha = if (showLabel) alpha else 0f
                        val labelVisibility = if (showLabel) View.VISIBLE else View.GONE
                        if (showDesktopUi) {
                            val motionLayout = previewPager as ClickableMotionLayout
                            if (showLabel) {
                                motionLayout.addClickableViewId(R.id.home_preview_label_container)
                                homePreviewLabelContainer.setOnClickListener {
                                    viewModel.selectPreviewScreen(HOME_SCREEN)
                                }
                            } else {
                                motionLayout.removeClickableViewId(
                                    R.id.home_preview_label_container
                                )
                                homePreviewLabelContainer.setOnClickListener(null)
                            }
                        }
                        if (shouldAnimate) {
                            homePreviewLabelContainer.visibility = View.VISIBLE
                            homePreviewLabelContainer.animateToAlpha(labelAlpha) {
                                homePreviewLabelContainer.visibility = labelVisibility
                            }
                            homePreviewShade.animateToAlpha(shadeAlpha)
                        } else {
                            homePreviewLabelContainer.alpha = labelAlpha
                            homePreviewShade.alpha = shadeAlpha
                            homePreviewLabelContainer.visibility = labelVisibility
                        }
                    }
                }

                launch {
                    viewModel.lockPreviewLabelTextAppearance.collect {
                        lockPreviewLabel.setTextAppearance(it)
                    }
                }

                launch {
                    viewModel.homePreviewLabelTextAppearance.collect {
                        homePreviewLabel.setTextAppearance(it)
                    }
                }
            }
        }
    }
}
