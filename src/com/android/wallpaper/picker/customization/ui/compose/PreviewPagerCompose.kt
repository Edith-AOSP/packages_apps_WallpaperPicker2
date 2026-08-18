/*
 * Copyright (C) 2026 The Android Open Source Project
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

import android.view.View
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.android.wallpaper.R
import com.android.wallpaper.model.Screen.LOCK_SCREEN
import com.android.wallpaper.picker.customization.ui.viewmodel.CustomizationPickerViewModel2
import com.android.wallpaper.picker.customization.ui.viewmodel.CustomizationPickerViewModel2.PickerScreen.CUSTOMIZATION_OPTION
import com.android.wallpaper.util.ScreenSizeCalculator
import kotlin.math.min

/**
 * Compose replacement for the inner preview pager that was previously driven by a
 * [androidx.constraintlayout.motion.widget.MotionLayout] scene.
 *
 * The pager shows two states:
 * - **Side-by-side** (main screen): both preview cards are visible next to each other, at 25% and
 *   75% of the container width.
 * - **Centered** (customization sub-setting): only the selected screen's preview is visible,
 *   centered at 50% of the container width.
 *
 * The transition uses a two-phase slide so the two [android.view.SurfaceView]-backed cards never
 * overlap — trying to fade or z-order overlapping SurfaceView surfaces produces ghosting because
 * their surfaces composite in SurfaceFlinger below the window and cannot be reliably clipped or
 * reordered from the view hierarchy.
 *
 * Progress: `0f` = side-by-side, `1f` = centered.
 * - **[0f, 0.5f]** — inactive card slides horizontally off-screen (past its side-by-side edge).
 *   Active card stays at its side-by-side position.
 * - **[0.5f, 1f]** — inactive card stays off-screen (surfaces set INVISIBLE). Active card slides
 *   from its side-by-side position to the container's center.
 *
 * Reversing the animation reverses the phases: on exit the active card slides back to its
 * side-by-side position first, then the inactive card slides back in from off-screen.
 *
 * The label containers and preview cards are Android views owned by the fragment; this composable
 * only positions and animates them. The views must be retained across recompositions because the
 * preview cards contain [android.view.SurfaceView] children whose surfaces are expensive to
 * recreate.
 */
@Composable
fun PreviewPagerCompose(
    lockLabelView: View,
    homeLabelView: View,
    lockPreviewCard: View,
    homePreviewCard: View,
    viewModel: CustomizationPickerViewModel2,
    snapToCenteredOnEnter: Boolean = false,
    modifier: Modifier = Modifier,
) {
    val selectedScreen by viewModel.selectedPreviewScreen.collectAsStateWithLifecycle()
    val screen by viewModel.screen.collectAsStateWithLifecycle()

    val isCentered = screen.first == CUSTOMIZATION_OPTION

    // progress: 0f = side-by-side (main), 1f = centered (sub-setting).
    val progress = remember { Animatable(if (isCentered) 1f else 0f) }

    LaunchedEffect(isCentered, snapToCenteredOnEnter) {
        val target = if (isCentered) 1f else 0f
        if (isCentered && snapToCenteredOnEnter) {
            // Entering from the collapsed header: the pager is translated up and clipped, so
            // animating would make the preview travel through clipped positions. Settle on the
            // centered layout directly instead.
            progress.snapTo(target)
        } else {
            progress.animateTo(
                targetValue = target,
                animationSpec = tween(durationMillis = 360, easing = FastOutSlowInEasing),
            )
        }
    }

    val labelBand = 28.dp
    val density = LocalDensity.current
    val context = LocalContext.current
    val screenAspectRatio = ScreenSizeCalculator.getInstance().getScreenAspectRatio(context)
    // Total horizontal margin reserved for a preview card (selected: 24dp + 8dp; centered:
    // 16dp + 16dp).
    val horizontalMargin = 32.dp

    BoxWithConstraints(modifier = modifier) {
        val containerWidth = maxWidth
        val containerHeight = maxHeight
        val p = progress.value
        val lockIsActive = selectedScreen == LOCK_SCREEN

        // The preview card keeps the display aspect ratio and fits within its horizontal and
        // vertical bounds, exactly like the DisplayAspectRatioFrameLayout did under the old
        // MotionScene constraints.
        val availableHeight = (containerHeight - labelBand).coerceAtLeast(0.dp)
        val maxCardWidth = (containerWidth / 2 - horizontalMargin).coerceAtLeast(0.dp)
        val maxCardWidthPx = with(density) { maxCardWidth.toPx() }
        val availableHeightPx = with(density) { availableHeight.toPx() }
        val cardWidthPx = min(maxCardWidthPx, (availableHeightPx / screenAspectRatio))
        val cardHeightPx = cardWidthPx * screenAspectRatio
        val cardWidth = with(density) { cardWidthPx.toDp() }
        val cardHeight = with(density) { cardHeightPx.toDp() }

        // Split progress into two phases so the two SurfaceView-backed cards never overlap.
        // Phase 1 (0f..0.5f): the inactive card slides off-screen; the active stays put.
        // Phase 2 (0.5f..1f): the active card slides to center; the inactive stays off-screen.
        val phase1 = (p / 0.5f).coerceIn(0f, 1f) // 0..1 across the first half
        val phase2 = ((p - 0.5f) / 0.5f).coerceIn(0f, 1f) // 0..1 across the second half

        // Fractional center positions. Lock's side-by-side center is at 0.25; home's at 0.75.
        // Off-screen centers push the card fully outside the container: lock exits to the left
        // (past 0), home exits to the right (past 1). Using half-card offsets keeps the maths in
        // terms of the container width regardless of card width.
        val cardHalfFraction = if (containerWidth > 0.dp) (cardWidth / 2) / containerWidth else 0f
        val lockSideBySide = 0.25f
        val homeSideBySide = 0.75f
        val lockOffScreen = -cardHalfFraction
        val homeOffScreen = 1f + cardHalfFraction
        val center = 0.5f

        val lockCenterFraction =
            if (lockIsActive) {
                // Active during phase 2 only: stays at side-by-side, then slides to center.
                lockSideBySide + (center - lockSideBySide) * phase2
            } else {
                // Inactive during phase 1 only: slides from side-by-side to off-screen.
                lockSideBySide + (lockOffScreen - lockSideBySide) * phase1
            }
        val homeCenterFraction =
            if (!lockIsActive) {
                homeSideBySide + (center - homeSideBySide) * phase2
            } else {
                homeSideBySide + (homeOffScreen - homeSideBySide) * phase1
            }
        val lockOffsetX = containerWidth * lockCenterFraction - cardWidth / 2
        val homeOffsetX = containerWidth * homeCenterFraction - cardWidth / 2

        // Once the inactive card is fully off-screen (phase 2), hide its surfaces so SurfaceFlinger
        // stops compositing them. Reveal them again as soon as it starts sliding back in.
        SideEffect {
            val lockShouldHide = !lockIsActive && p >= 0.5f
            val homeShouldHide = lockIsActive && p >= 0.5f
            setPreviewSurfacesVisibility(
                lockPreviewCard,
                if (lockShouldHide) View.INVISIBLE else View.VISIBLE,
            )
            setPreviewSurfacesVisibility(
                homePreviewCard,
                if (homeShouldHide) View.INVISIBLE else View.VISIBLE,
            )
            // Cards themselves stay fully opaque throughout — no alpha work is needed since the
            // inactive is either at its side-by-side spot, off-screen, or hidden outright.
            lockPreviewCard.alpha = 1f
            homePreviewCard.alpha = 1f
        }

        // Each label + card pair is packed together and centered vertically, mirroring the packed
        // vertical chain (chainStyle="packed") of the previous MotionScene. The label sits inside a
        // fixed-height slot so the card's position stays stable when PreviewAlphaAnimationBinder
        // toggles the label between VISIBLE and GONE — otherwise the Column would shrink when the
        // label disappears and the card would jump upward at the start of the sub-setting entry.
        Box(modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier =
                    Modifier.align(Alignment.CenterStart).offset(x = lockOffsetX).width(cardWidth)
            ) {
                // Labels sit above the preview cards; their alpha/visibility is driven by
                // PreviewAlphaAnimationBinder, so no alpha is applied here.
                Box(modifier = Modifier.fillMaxWidth().height(labelBand)) {
                    AndroidView(factory = { lockLabelView }, modifier = Modifier.fillMaxWidth())
                }
                AndroidView(
                    factory = { lockPreviewCard },
                    modifier = Modifier.fillMaxWidth().height(cardHeight),
                )
            }

            Column(
                modifier =
                    Modifier.align(Alignment.CenterStart).offset(x = homeOffsetX).width(cardWidth)
            ) {
                Box(modifier = Modifier.fillMaxWidth().height(labelBand)) {
                    AndroidView(factory = { homeLabelView }, modifier = Modifier.fillMaxWidth())
                }
                AndroidView(
                    factory = { homePreviewCard },
                    modifier = Modifier.fillMaxWidth().height(cardHeight),
                )
            }
        }
    }
}

private fun setPreviewSurfacesVisibility(preview: View, visibility: Int) {
    val wallpaper = preview.findViewById<View>(R.id.wallpaper_surface)
    val workspace = preview.findViewById<View>(R.id.workspace_surface)
    if (wallpaper.visibility != visibility) wallpaper.visibility = visibility
    if (workspace.visibility != visibility) workspace.visibility = visibility
}
