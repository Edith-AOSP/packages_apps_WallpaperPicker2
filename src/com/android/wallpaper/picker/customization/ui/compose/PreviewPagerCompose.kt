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
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
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
 *   75% of the container width. A "Wallpapers" row sits below the previews as an entry point into
 *   the wallpaper categories.
 * - **Centered** (customization sub-setting): only the selected screen's preview is visible,
 *   centered at 50% of the container width. The Wallpapers row fades out during entry.
 *
 * The transition moves both cards simultaneously, driven by a single progress value. The active
 * card slides between its side-by-side position and the container center; the inactive card slides
 * between its side-by-side position and fully off-screen. Because the two cards diverge (active
 * toward center, inactive toward the far edge) they never overlap — which avoids all
 * [android.view.SurfaceView] z-order/ghosting problems, since overlapping SurfaceView surfaces
 * composite in SurfaceFlinger below the window and cannot be reliably reordered from the view
 * hierarchy.
 *
 * Progress: `0f` = side-by-side, `1f` = centered. On exit both cards arrive at their side-by-side
 * positions at the same time.
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
    onWallpapersClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    val selectedScreen by viewModel.selectedPreviewScreen.collectAsStateWithLifecycle()
    val screen by viewModel.screen.collectAsStateWithLifecycle()

    val isCentered = screen.first == CUSTOMIZATION_OPTION

    // progress: 0f = side-by-side (main), 1f = centered (sub-setting).
    val progress = remember { Animatable(if (isCentered) 1f else 0f) }

    // Whether the current transition is heading toward centered (enter) or back to side-by-side
    // (exit). Enter uses a sequential two-phase slide (inactive out fast, then active to center);
    // exit slides both cards simultaneously so they land side-by-side together.
    var isEntering by remember { mutableStateOf(isCentered) }

    // Material 3 expressive-motion easing (from the M3 motion spec / MotionTokens):
    //   emphasized-decelerate = CubicBezierEasing(0.05f, 0.7f, 0.1f, 1f)
    val enterEasing = CubicBezierEasing(0.05f, 0.7f, 0.1f, 1f)

    LaunchedEffect(isCentered, snapToCenteredOnEnter) {
        val target = if (isCentered) 1f else 0f
        isEntering = isCentered
        if (isCentered && snapToCenteredOnEnter) {
            // Entering from the collapsed header: the pager is translated up and clipped, so
            // animating would make the preview travel through clipped positions. Settle on the
            // centered layout directly instead.
            progress.snapTo(target)
        } else {
            progress.animateTo(
                targetValue = target,
                // M3 expressive container transform: emphasized-decelerate, long duration
                // (500ms, DurationLong2) for both enter and exit.
                animationSpec = tween(durationMillis = 500, easing = enterEasing),
            )
        }
    }

    val labelBand = 28.dp
    // Reserved slot for the Wallpapers row below the previews. Matches the min-height used by
    // HomepagePreference/EdithHomepageContent in Settings so the row keeps the same visual weight,
    // and is kept in sync with fittedExpandedHeaderHeight in CustomizationPickerFragment via the
    // shared dimen.
    val wallpapersRowHeight =
        dimensionResource(R.dimen.customization_picker_preview_wallpapers_row_height)
    val density = LocalDensity.current
    val context = LocalContext.current
    val screenAspectRatio = ScreenSizeCalculator.getInstance().getScreenAspectRatio(context)
    // Horizontal layout: each card is inset from the container edge by `outerMargin`, and the two
    // cards are separated by `2 * halfGap`. Symmetric outer margins keep the pair centered.
    val outerMargin =
        dimensionResource(R.dimen.customization_picker_preview_overview_horizontal_margin)
    val halfGap = dimensionResource(R.dimen.customization_picker_preview_overview_half_gap)

    BoxWithConstraints(modifier = modifier) {
        val containerWidth = maxWidth
        val containerHeight = maxHeight
        val p = progress.value
        val lockIsActive = selectedScreen == LOCK_SCREEN

        // The preview card keeps the display aspect ratio and fits within its horizontal and
        // vertical bounds, exactly like the DisplayAspectRatioFrameLayout did under the old
        // MotionScene constraints. The wallpapers row below is subtracted from the available
        // vertical space so nothing overlaps.
        val previewAreaHeight = (containerHeight - wallpapersRowHeight).coerceAtLeast(0.dp)
        val availableHeight = (previewAreaHeight - labelBand).coerceAtLeast(0.dp)
        val maxCardWidth =
            ((containerWidth - outerMargin * 2 - halfGap * 2) / 2).coerceAtLeast(0.dp)
        val maxCardWidthPx = with(density) { maxCardWidth.toPx() }
        val availableHeightPx = with(density) { availableHeight.toPx() }
        val cardWidthPx = min(maxCardWidthPx, (availableHeightPx / screenAspectRatio))
        val cardHeightPx = cardWidthPx * screenAspectRatio
        val cardWidth = with(density) { cardWidthPx.toDp() }
        val cardHeight = with(density) { cardHeightPx.toDp() }

        // Direction-aware transition. Both cards are driven by progress p (0f = side-by-side,
        // 1f = centered).
        //
        // ENTER (p: 0 -> 1) — sequential two-phase slide: the inactive card slides off-screen
        // first (fast), then the active card slides to center. This keeps the inactive visually out
        // of the way before the active arrives.
        // EXIT (p: 1 -> 0) — simultaneous slide: both cards move together so they land side-by-side
        // at the same time.
        //
        // The two cards diverge (active toward center, inactive toward the far edge) so they never
        // overlap regardless of direction.
        //
        // Side-by-side centers are computed from the symmetric outer margins so the pair stays
        // centered with a fixed gap of `2 * halfGap`.
        val centerX = containerWidth / 2
        val lockSideX = outerMargin + cardWidth / 2
        val homeSideX = containerWidth - outerMargin - cardWidth / 2
        val lockOffX = -cardWidth / 2 // right edge at container start
        val homeOffX = containerWidth + cardWidth / 2 // left edge at container end

        // Sequential (enter) sub-progress: phase1 spans [0, 0.5], phase2 spans [0.5, 1].
        val enterPhase1 = (p / 0.5f).coerceIn(0f, 1f)
        val enterPhase2 = ((p - 0.5f) / 0.5f).coerceIn(0f, 1f)

        // Active card slides side <-> center; inactive card slides side <-> off-screen.
        val lockTargetX = if (lockIsActive) centerX else lockOffX
        val homeTargetX = if (lockIsActive) homeOffX else centerX

        fun activePhase(): Float = if (isEntering) enterPhase2 else p
        fun inactivePhase(): Float = if (isEntering) enterPhase1 else p

        val lockPhase = if (lockIsActive) activePhase() else inactivePhase()
        val homePhase = if (lockIsActive) inactivePhase() else activePhase()

        val lockX = lockSideX + (lockTargetX - lockSideX) * lockPhase
        val homeX = homeSideX + (homeTargetX - homeSideX) * homePhase

        // offset positions the card's left edge.
        val lockOffsetX = lockX - cardWidth / 2
        val homeOffsetX = homeX - cardWidth / 2

        // Hide the inactive card's surfaces once it is fully off-screen so SurfaceFlinger stops
        // compositing them. On enter (sequential) the inactive is off-screen from the midpoint; on
        // exit (simultaneous) it is off-screen only at the very start.
        SideEffect {
            val inactiveFullyOffScreen = if (isEntering) p >= 0.5f else p >= 0.999f
            setPreviewSurfacesVisibility(
                lockPreviewCard,
                if (!lockIsActive && inactiveFullyOffScreen) View.INVISIBLE else View.VISIBLE,
            )
            setPreviewSurfacesVisibility(
                homePreviewCard,
                if (lockIsActive && inactiveFullyOffScreen) View.INVISIBLE else View.VISIBLE,
            )
            // Cards themselves stay fully opaque throughout — no alpha work is needed since the
            // two cards never overlap.
            lockPreviewCard.alpha = 1f
            homePreviewCard.alpha = 1f
        }

        Column(modifier = Modifier.fillMaxWidth()) {
            // Each label + card pair is packed together and centered vertically, mirroring the
            // packed vertical chain (chainStyle="packed") of the previous MotionScene. The label
            // sits inside a fixed-height slot so the card's position stays stable when
            // PreviewAlphaAnimationBinder toggles the label between VISIBLE and GONE — otherwise
            // the Column would shrink when the label disappears and the card would jump upward at
            // the start of the sub-setting entry.
            Box(modifier = Modifier.fillMaxWidth().height(previewAreaHeight)) {
                Column(
                    modifier =
                        Modifier.align(Alignment.CenterStart)
                            .offset(x = lockOffsetX)
                            .width(cardWidth)
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
                        Modifier.align(Alignment.CenterStart)
                            .offset(x = homeOffsetX)
                            .width(cardWidth)
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

            // The Wallpapers row lives below the previews inside the header. It fades and stops
            // accepting input while entering the sub-setting so it doesn't compete visually with
            // the centered preview. Its top/bottom gap matches the inter-preview-card gap
            // (2 * halfGap).
            WallpapersRow(
                modifier =
                    Modifier.fillMaxWidth()
                        .height(wallpapersRowHeight)
                        .padding(horizontal = 16.dp, vertical = halfGap * 2)
                        .alpha((1f - p).coerceIn(0f, 1f)),
                onClick = onWallpapersClick?.takeIf { p < 0.5f },
            )
        }
    }
}

/**
 * A row that mirrors the visual style of `EdithHomepageContent` (see Settings' HomepagePreference):
 * a rounded surface-container pill with a 40dp leading icon, a title, and a circular trailing
 * chevron badge. Tapping anywhere on the row triggers [onClick] when non-null.
 */
@Composable
private fun WallpapersRow(modifier: Modifier = Modifier, onClick: (() -> Unit)? = null) {
    val shape = RoundedCornerShape(24.dp)
    Row(
        modifier =
            modifier
                .clip(shape)
                .background(MaterialTheme.colorScheme.surfaceContainer, shape)
                .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
                .heightIn(min = 56.dp)
                .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Leading icon. Matches HomepagePreference's iconVisible/icon slot: fixed 40dp box.
        Box(
            modifier = Modifier.padding(end = 10.dp).size(40.dp),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                painter = painterResource(R.drawable.ic_nav_wallpaper),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.size(24.dp),
            )
        }
        Text(
            text = stringResource(R.string.wallpapers),
            modifier = Modifier.weight(1f).padding(start = 6.dp),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            fontWeight = FontWeight.Normal,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Box(
            modifier =
                Modifier.size(28.dp)
                    .background(MaterialTheme.colorScheme.surfaceVariant, CircleShape)
                    .padding(8.dp),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                painter = painterResource(R.drawable.ic_arrow_forward_24px),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

private fun setPreviewSurfacesVisibility(preview: View, visibility: Int) {
    val wallpaper = preview.findViewById<View>(R.id.wallpaper_surface)
    val workspace = preview.findViewById<View>(R.id.workspace_surface)
    if (wallpaper.visibility != visibility) wallpaper.visibility = visibility
    if (workspace.visibility != visibility) workspace.visibility = visibility
}
