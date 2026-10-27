/*
 * Copyright (C) 2026 Edith AOSP
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

package com.android.wallpaper.picker.customization.ui.compose

import android.graphics.Color
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentContainerView
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.android.wallpaper.R
import com.android.wallpaper.picker.customization.ui.CustomizationPickerLegacyHost
import com.android.wallpaper.picker.customization.ui.viewmodel.ColorUpdateViewModel
import com.android.wallpaper.picker.customization.ui.viewmodel.CustomizationPickerViewModel2

/**
 * Compose-first layout for the customization picker.
 *
 * Compose owns the root, the preview transition, the scroll split, and navigation. The legacy host
 * is deliberately limited to the surfaces that still expose View-only contracts: the toolbar
 * (ThemePicker's apply button), the option list, and the SurfaceView-backed preview cards.
 */
@Composable
fun CustomizationPickerScreen(
    host: CustomizationPickerLegacyHost,
    viewModel: CustomizationPickerViewModel2,
    colorUpdateViewModel: ColorUpdateViewModel,
    state: CustomizationPickerUiState,
    onWallpapersClick: () -> Unit,
    onAttachOverlay: (FragmentContainerView) -> Unit,
    modifier: Modifier = Modifier,
) {
    val toolbarView = remember { host.toolbarView }
    val optionsView = remember { host.optionsHostView }
    val previewViews = remember { host.previewViews }
    val previewHeight =
        dimensionResource(R.dimen.customization_picker_compose_preview_height)

    val theme = MaterialTheme.colorScheme
    val surfaceContainer by
        colorUpdateViewModel.colorSurfaceContainer.collectAsStateWithLifecycle(
            initialValue = theme.surfaceContainer.toArgb()
        )
    val primaryContainer by
        colorUpdateViewModel.colorPrimaryContainer.collectAsStateWithLifecycle(
            initialValue = theme.primaryContainer.toArgb()
        )
    val onPrimaryContainer by
        colorUpdateViewModel.colorOnPrimaryContainer.collectAsStateWithLifecycle(
            initialValue = theme.onPrimaryContainer.toArgb()
        )
    val primary by
        colorUpdateViewModel.colorPrimary.collectAsStateWithLifecycle(
            initialValue = theme.primary.toArgb()
        )
    val onPrimary by
        colorUpdateViewModel.colorOnPrimary.collectAsStateWithLifecycle(
            initialValue = theme.onPrimary.toArgb()
        )
    val previewScheme =
        theme.copy(
            surfaceContainer = androidx.compose.ui.graphics.Color(surfaceContainer),
            primaryContainer = androidx.compose.ui.graphics.Color(primaryContainer),
            onPrimaryContainer = androidx.compose.ui.graphics.Color(onPrimaryContainer),
            primary = androidx.compose.ui.graphics.Color(primary),
            onPrimary = androidx.compose.ui.graphics.Color(onPrimary),
        )
    val navBarBottom =
        WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()

    LaunchedEffect(Unit) { host.bindOptions() }

    MaterialTheme(colorScheme = previewScheme) {
        Box(
            modifier = modifier.fillMaxSize().background(MaterialTheme.colorScheme.surfaceContainer)
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                AndroidView(
                    factory = { toolbarView },
                    modifier = Modifier.fillMaxWidth().statusBarsPadding(),
                )

                Spacer(modifier = Modifier.height(12.dp))

                PreviewPagerCompose(
                    lockLabelView = previewViews.lockPreviewLabelContainer,
                    homeLabelView = previewViews.homePreviewLabelContainer,
                    lockPreviewCard = previewViews.lockPreview,
                    homePreviewCard = previewViews.homePreview,
                    viewModel = viewModel,
                    onWallpapersClick = onWallpapersClick,
                    modifier = Modifier.fillMaxWidth().height(previewHeight),
                )

                Box(modifier = Modifier.fillMaxWidth().weight(1f)) {
                    AndroidView(
                        factory = { optionsView },
                        modifier =
                            Modifier.fillMaxSize()
                                .padding(horizontal = 16.dp)
                                .clip(RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp))
                                .alpha(if (state.floatingSheetVisible) 0f else 1f),
                    )
                    if (state.floatingSheetVisible) {
                        // The options sheet is a legacy AndroidView. This Compose layer consumes
                        // touches on transparent floating-sheet areas so they do not reach the
                        // preferences underneath.
                        Box(
                            modifier =
                                Modifier.fillMaxSize().pointerInput(Unit) {
                                    awaitPointerEventScope {
                                        while (true) {
                                            awaitPointerEvent(PointerEventPass.Final)
                                                .changes
                                                .forEach { it.consume() }
                                        }
                                    }
                                }
                        )
                    }
                }
            }

            if (!state.floatingSheetVisible) {
                Box(
                    modifier =
                        Modifier.align(Alignment.BottomCenter)
                            .fillMaxWidth()
                            .height(navBarBottom + 28.dp)
                            .background(
                                Brush.verticalGradient(
                                    0f to androidx.compose.ui.graphics.Color.Transparent,
                                    0.45f to androidx.compose.ui.graphics.Color.Transparent,
                                    1f to MaterialTheme.colorScheme.surfaceContainer,
                                )
                            )
                )
            }

            AndroidView(
                factory = { host.floatingSheetContainer },
                update = { view ->
                    view.visibility =
                        if (state.floatingSheetVisible) android.view.View.VISIBLE
                        else android.view.View.GONE
                },
                modifier =
                    Modifier.align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.surfaceContainer)
                        .navigationBarsPadding(),
            )

            if (state.overlayVisible) {
                AndroidView(
                    factory = { context ->
                        FragmentContainerView(context).apply {
                            id = R.id.fragment_container
                            setBackgroundColor(Color.TRANSPARENT)
                        }
                    },
                    update = { container -> onAttachOverlay(container) },
                    modifier =
                        Modifier.fillMaxSize()
                            .background(MaterialTheme.colorScheme.surfaceContainer),
                )
            }
        }
    }
}

/** UI state owned by the activity and shared with the Compose host. */
class CustomizationPickerUiState {
    var floatingSheetVisible by mutableStateOf(false)
    var overlayVisible by mutableStateOf(false)
    var pendingFragment: Fragment? = null
}
