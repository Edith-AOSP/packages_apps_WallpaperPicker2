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

package com.android.wallpaper.picker.customization.ui

import android.content.Intent
import android.graphics.Outline
import android.graphics.Path
import android.provider.Settings
import android.view.LayoutInflater
import android.view.SurfaceView
import android.view.View
import android.view.ViewGroup
import android.view.ViewOutlineProvider
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import androidx.activity.result.ActivityResultLauncher
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.isInvisible
import androidx.core.widget.NestedScrollView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.android.customization.picker.clock.ui.view.ClockViewFactory
import com.android.customization.picker.icon.ui.util.IconStyleViewUtil
import com.android.wallpaper.R
import com.android.wallpaper.config.BaseFlags
import com.android.wallpaper.model.Screen
import com.android.wallpaper.model.Screen.HOME_SCREEN
import com.android.wallpaper.model.Screen.LOCK_SCREEN
import com.android.wallpaper.module.MultiPanesChecker
import com.android.wallpaper.module.logging.UserEventLogger
import com.android.wallpaper.picker.MyPhotosStarter
import com.android.wallpaper.picker.category.ui.view.CategoriesFragment
import com.android.wallpaper.picker.category.ui.view.MyPhotosStarterImpl
import com.android.wallpaper.picker.category.ui.view.PhotoPickerFragment
import com.android.wallpaper.picker.category.ui.view.providers.IndividualPickerFactory
import com.android.wallpaper.picker.common.preview.data.repository.PersistentWallpaperModelRepository
import com.android.wallpaper.picker.common.preview.ui.binder.BasePreviewBinder
import com.android.wallpaper.picker.common.preview.ui.binder.PreviewAlphaAnimationBinder
import com.android.wallpaper.picker.common.preview.ui.binder.WorkspaceBinder
import com.android.wallpaper.picker.customization.ui.binder.ColorUpdateBinder
import com.android.wallpaper.picker.customization.ui.binder.CustomizationOptionsBinder
import com.android.wallpaper.picker.customization.ui.binder.CustomizationPickerBinder2
import com.android.wallpaper.picker.customization.ui.binder.PackThemeSuggestedEntryBinder
import com.android.wallpaper.picker.customization.ui.binder.PreviewPagerBinder
import com.android.wallpaper.picker.customization.ui.binder.ToolbarBinder
import com.android.wallpaper.picker.customization.ui.util.CustomizationOptionUtil
import com.android.wallpaper.picker.customization.ui.util.CustomizationOptionUtil.CustomizationOption
import com.android.wallpaper.picker.customization.ui.util.CustomizationOptionViewUtil
import com.android.wallpaper.picker.customization.ui.view.DeviceRadiusPreviewCardView
import com.android.wallpaper.picker.customization.ui.view.PreviewPagerViews
import com.android.wallpaper.picker.customization.ui.viewmodel.ColorUpdateViewModel
import com.android.wallpaper.picker.customization.ui.viewmodel.CustomizationOptionsData
import com.android.wallpaper.picker.customization.ui.viewmodel.CustomizationPickerViewModel2
import com.android.wallpaper.picker.preview.ui.WallpaperPreviewActivity
import com.android.wallpaper.picker.wallpapers.data.repository.CategoryWallpapersRepository
import com.android.wallpaper.picker.wallpapers.ui.view.CategoryWallpapersFragment
import com.android.wallpaper.util.CuratedPhotosTimeUtil
import com.android.wallpaper.util.DisplayUtils
import com.android.wallpaper.util.LaunchSourceUtils.WALLPAPER_LAUNCH_SOURCE
import com.android.wallpaper.util.WallpaperConnection
import com.android.wallpaper.util.wallpaperconnection.WallpaperConnectionUtils
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * Hosts the shared View-based customization surfaces that have not moved to Compose yet.
 *
 * The preview cards, option entries, floating sheets, and toolbar come from ThemePicker. Compose
 * owns their placement, navigation, and the main-screen preview transition; this host only creates
 * and binds the existing views. Keeping the host separate makes it possible to replace one surface
 * at a time without changing the public customization contracts.
 */
class CustomizationPickerLegacyHost(
    private val activity: AppCompatActivity,
    private val viewModel: CustomizationPickerViewModel2,
    private val colorUpdateViewModel: ColorUpdateViewModel,
    private val toolbarBinder: ToolbarBinder,
    private val customizationOptionsBinder: CustomizationOptionsBinder,
    private val customizationOptionUtil: CustomizationOptionUtil,
    private val customizationOptionViewUtil: CustomizationOptionViewUtil,
    private val packThemeSuggestedEntryBinder: PackThemeSuggestedEntryBinder,
    private val workspaceBinder: WorkspaceBinder,
    private val displayUtils: DisplayUtils,
    private val wallpaperConnectionUtils: WallpaperConnectionUtils,
    private val persistentWallpaperModelRepository: PersistentWallpaperModelRepository,
    private val multiPanesChecker: MultiPanesChecker,
    private val individualPickerFactory: IndividualPickerFactory,
    private val categoryWallpapersRepository: CategoryWallpapersRepository,
    private val myPhotosStarterImpl: MyPhotosStarterImpl,
    private val photoPickerLauncher: ActivityResultLauncher<Intent>,
    private val curatedPhotosTimeUtil: CuratedPhotosTimeUtil,
    private val userEventLogger: UserEventLogger,
    private val iconStyleViewUtil: IconStyleViewUtil,
    private val clockViewFactory: ClockViewFactory,
    private val mainScope: CoroutineScope,
    private val callbacks: Callbacks,
) {

    interface Callbacks {
        fun onBack()
        fun onShowFloatingSheet()
        fun onHideFloatingSheet()
        fun onOpenFragment(fragment: androidx.fragment.app.Fragment)
        fun launchForResult(intent: Intent)
    }

    private val isDesktopUi by lazy {
        BaseFlags.get(activity).shouldShowDesktopUi(activity)
    }
    private val inflater: LayoutInflater = activity.layoutInflater
    private val optionsBound = AtomicBoolean(false)

    val toolbarView: View by lazy {
        (inflater.inflate(R.layout.customization_picker2_toolbar, null) as LinearLayout).also {
            toolbarBinder.bind(
                it,
                viewModel.customizationOptionsViewModel,
                colorUpdateViewModel,
                activity,
            ) {
                callbacks.onBack()
            }
        }
    }

    val optionsHostView: View by lazy {
        inflater.inflate(R.layout.compose_customization_options, null).also { root ->
            root.requireViewById<View>(R.id.home_customization_option_container).isInvisible = true
            root.requireViewById<View>(R.id.customization_option_container).apply {
                // Compose owns the horizontal inset so the top clip starts at the same edge as
                // the preference cards.
                setPadding(0, paddingTop, 0, paddingBottom)
            }
            val scroll = root.requireViewById<NestedScrollView>(R.id.customization_options_scroll_view)
            scroll.clipToOutline = true
            scroll.outlineProvider =
                object : ViewOutlineProvider() {
                    override fun getOutline(view: View, outline: Outline) {
                        val radius =
                            OPTIONS_TOP_CORNER_RADIUS_DP * view.resources.displayMetrics.density
                        val path =
                            Path().apply {
                                addRoundRect(
                                    0f,
                                    0f,
                                    view.width.toFloat(),
                                    view.height.toFloat() + radius,
                                    floatArrayOf(radius, radius, radius, radius, 0f, 0f, 0f, 0f),
                                    Path.Direction.CW,
                                )
                            }
                        outline.setPath(path)
                    }
                }
            ViewCompat.setOnApplyWindowInsetsListener(root) { view, insets ->
                val navBarBottom =
                    insets.getInsets(WindowInsetsCompat.Type.navigationBars()).bottom
                val bottomPadding =
                    navBarBottom +
                        (OPTIONS_BOTTOM_PADDING_DP * view.resources.displayMetrics.density).toInt()
                scroll.setPadding(
                    scroll.paddingLeft,
                    scroll.paddingTop,
                    scroll.paddingRight,
                    bottomPadding,
                )
                insets
            }
            root.requestApplyInsets()
            ColorUpdateBinder.bind(
                setColor = { color -> root.setBackgroundColor(color) },
                color = colorUpdateViewModel.colorSurfaceContainer,
                shouldAnimate = { true },
                lifecycleOwner = activity,
            )
        }
    }

    val floatingSheetContainer: FrameLayout by lazy {
        FrameLayout(activity).apply {
            id = R.id.customization_option_floating_sheet_container
            visibility = View.GONE
        }
    }

    val previewViews: PreviewPagerViews by lazy { createPreviewViews() }

    private var previewBound = false
    private var hasSecondaryScreen = false

    fun bindPreview() {
        if (previewBound) return
        previewBound = true
        bindPreviewPager()
    }

    fun bindOptions() {
        activity.lifecycleScope.launch {
            val data = viewModel.customizationOptionsViewModel.customizationOptionsData.first()
            if (!optionsBound.compareAndSet(false, true)) return@launch
            bindOptions(data)
        }
    }

    fun handleBackPressed(): Boolean = viewModel.customizationOptionsViewModel.handleBackPressed()

    fun disconnect() {
        mainScope.launch { wallpaperConnectionUtils.disconnectAll() }
    }

    private fun createPreviewViews(): PreviewPagerViews {
        val lockPreview = inflatePreviewCard(R.id.lock_preview)
        val homePreview = inflatePreviewCard(R.id.home_preview)
        val lockPreviewLabelContainer = inflatePreviewLabel(R.id.lock_preview_label_container)
        val homePreviewLabelContainer = inflatePreviewLabel(R.id.home_preview_label_container)
        val lockPreviewLabel =
            lockPreviewLabelContainer.requireViewById<TextView>(R.id.preview_label).apply {
                id = R.id.lock_preview_label
                setText(R.string.lock_screen_tab)
            }
        val homePreviewLabel =
            homePreviewLabelContainer.requireViewById<TextView>(R.id.preview_label).apply {
                id = R.id.home_preview_label
                setText(R.string.home_screen_tab)
            }

        val lockPreviewContainer = lockPreview.requireViewById<ViewGroup>(R.id.wallpaper_preview_crop)
        val homePreviewContainer = homePreview.requireViewById<ViewGroup>(R.id.wallpaper_preview_crop)

        val clockHostView =
            customizationOptionViewUtil.createClockPreviewAndAddToParent(
                lockPreviewContainer,
                inflater,
            )
        val clockFaceClickDelegate =
            View(activity).apply {
                id = R.id.clock_face_click_delegate
                isClickable = true
            }
        lockPreviewContainer.addView(clockFaceClickDelegate)

        val lockPreviewShade =
            inflater.inflate(R.layout.preview_shade, lockPreviewContainer, false).also {
                lockPreviewContainer.addView(it)
            }
        val homePreviewShade =
            inflater.inflate(R.layout.preview_shade, homePreviewContainer, false).also {
                homePreviewContainer.addView(it)
            }

        lockPreview
            .requireViewById<SurfaceView>(R.id.workspace_surface)
            .setAccessibilityHierarchyEmbeddingEnabled(false)
        homePreview
            .requireViewById<SurfaceView>(R.id.workspace_surface)
            .setAccessibilityHierarchyEmbeddingEnabled(false)

        return PreviewPagerViews(
            previewPager = FrameLayout(activity),
            lockPreviewLabel = lockPreviewLabel,
            homePreviewLabel = homePreviewLabel,
            lockPreviewLabelContainer = lockPreviewLabelContainer,
            homePreviewLabelContainer = homePreviewLabelContainer,
            lockPreview = lockPreview,
            homePreview = homePreview,
            lockPreviewShade = lockPreviewShade,
            homePreviewShade = homePreviewShade,
            clockHostView = clockHostView,
            clockFaceClickDelegateView = clockFaceClickDelegate,
        )
    }

    private fun inflatePreviewCard(id: Int): View =
        inflater.inflate(R.layout.wallpaper_preview_card2, null).apply { this.id = id }

    private fun inflatePreviewLabel(id: Int): View =
        inflater.inflate(R.layout.preview_label, null).apply { this.id = id }

    private fun bindPreviewPager() {
        val views = previewViews
        val previewViewModel = viewModel.basePreviewViewModel
        previewViewModel.setWhichPreview(WallpaperConnection.WhichPreview.PREVIEW_CURRENT)
        previewViewModel.setIsWallpaperColorPreviewEnabled(false)
        previewViewModel.updateDisplayConfiguration(displayUtils.getActiveDisplaySize(activity))

        if (!isDesktopUi) {
            PreviewPagerBinder.bind(views, viewModel, activity)
            PreviewAlphaAnimationBinder.bind(views, viewModel, activity)
        } else {
            bindDesktopSelectionOutline()
            views.lockPreviewShade.alpha = 0f
            views.homePreviewShade.alpha = 0f
        }

        ColorUpdateBinder.bind(
            setColor = { color -> views.lockPreviewLabel.setTextColor(color) },
            color = colorUpdateViewModel.colorOnSurface,
            shouldAnimate = { true },
            lifecycleOwner = activity,
        )
        ColorUpdateBinder.bind(
            setColor = { color -> views.homePreviewLabel.setTextColor(color) },
            color = colorUpdateViewModel.colorOnSurface,
            shouldAnimate = { true },
            lifecycleOwner = activity,
        )
        ColorUpdateBinder.bind(
            setColor = { color -> views.lockPreviewShade.setBackgroundColor(color) },
            color = colorUpdateViewModel.colorSurfaceContainer,
            shouldAnimate = { views.lockPreviewShade.alpha != 0f },
            lifecycleOwner = activity,
        )
        ColorUpdateBinder.bind(
            setColor = { color -> views.homePreviewShade.setBackgroundColor(color) },
            color = colorUpdateViewModel.colorSurfaceContainer,
            shouldAnimate = { views.homePreviewShade.alpha != 0f },
            lifecycleOwner = activity,
        )

        val lockOutlineCard =
            views.lockPreview.requireViewById<DeviceRadiusPreviewCardView>(R.id.preview_card)
        val homeOutlineCard =
            views.homePreview.requireViewById<DeviceRadiusPreviewCardView>(R.id.preview_card)
        ColorUpdateBinder.bind(
            setColor = { color -> lockOutlineCard.setSelectionOutlineColor(color) },
            color = colorUpdateViewModel.colorPrimary,
            shouldAnimate = { lockOutlineCard.isSelectionOutlined },
            lifecycleOwner = activity,
        )
        ColorUpdateBinder.bind(
            setColor = { color -> homeOutlineCard.setSelectionOutlineColor(color) },
            color = colorUpdateViewModel.colorPrimary,
            shouldAnimate = { homeOutlineCard.isSelectionOutlined },
            lifecycleOwner = activity,
        )

        views.clockHostView?.let { clockHostView ->
            views.clockFaceClickDelegateView?.let { clockFaceClickDelegateView ->
                customizationOptionsBinder.bindClockPreview(
                    context = activity,
                    rootView = views.lockPreview,
                    clockHostView = clockHostView,
                    clockFaceClickDelegateView = clockFaceClickDelegateView,
                    viewModel = viewModel,
                    colorUpdateViewModel = colorUpdateViewModel,
                    lifecycleOwner = activity,
                    clockViewFactory = clockViewFactory,
                )
            }
        }

        bindPreview(LOCK_SCREEN, views.lockPreview)
        bindPreview(HOME_SCREEN, views.homePreview)
    }

    private fun bindDesktopSelectionOutline() {
        val lockCard =
            previewViews.lockPreview.requireViewById<DeviceRadiusPreviewCardView>(R.id.preview_card)
        val homeCard =
            previewViews.homePreview.requireViewById<DeviceRadiusPreviewCardView>(R.id.preview_card)
        activity.lifecycleScope.launch {
            activity.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.selectedPreviewScreen.collect { screen ->
                    lockCard.isSelectionOutlined = screen == LOCK_SCREEN
                    homeCard.isSelectionOutlined = screen == HOME_SCREEN
                }
            }
        }
    }

    private fun bindPreview(screen: Screen, preview: View) {
        val previewViewModel = viewModel.basePreviewViewModel
        BasePreviewBinder.bind(
            applicationContext = activity.applicationContext,
            view = preview.requireViewById(R.id.preview_card),
            viewModel = viewModel,
            colorUpdateViewModel = colorUpdateViewModel,
            workspaceBinder = workspaceBinder,
            screen = screen,
            deviceDisplayType = displayUtils.getCurrentDisplayType(activity),
            displaySize =
                if (displayUtils.isOnWallpaperDisplay(activity)) {
                    previewViewModel.wallpaperDisplaySize.value
                } else {
                    previewViewModel.smallerDisplaySize
                },
            mainScope = mainScope,
            lifecycleOwner = activity,
            wallpaperConnectionUtils = wallpaperConnectionUtils,
            isFirstBindingDeferred = CompletableDeferred(true),
            onLaunchPreview = { wallpaperModel ->
                persistentWallpaperModelRepository.setWallpaperModel(wallpaperModel)
                callbacks.launchForResult(
                    WallpaperPreviewActivity.intentBuilder(activity.applicationContext, false)
                        .viewAsHome(screen == HOME_SCREEN)
                        .newTask(multiPanesChecker.isMultiPanesEnabled(activity))
                        // Attribution is not refreshed when the language changes.
                        .hideInfoSheet(true)
                        .wallpaperLaunchSource(
                            activity.intent.extras?.getString(WALLPAPER_LAUNCH_SOURCE) ?: ""
                        )
                        .build()
                )
            },
            onTransitionToScreen = { toScreen -> viewModel.selectPreviewScreen(toScreen) },
            onPreviewReady = { previewScreen -> viewModel.setPreviewReady(previewScreen, true) },
            onPreviewSurfaceDestroyed = { previewScreen ->
                viewModel.setPreviewReady(previewScreen, false)
            },
            clockViewFactory = clockViewFactory,
        )
    }

    private fun bindOptions(data: CustomizationOptionsData) {
        val lockEntries = initOptionEntries(data, LOCK_SCREEN)
        val homeEntries = initOptionEntries(data, HOME_SCREEN)
        val floatingSheets =
            customizationOptionViewUtil.initFloatingSheet(data, floatingSheetContainer, inflater)

        CustomizationPickerBinder2.bind(
            customizationOptionsData = data,
            view = optionsHostView,
            lockScreenCustomizationOptionEntries = lockEntries,
            homeScreenCustomizationOptionEntries = homeEntries,
            customizationOptionFloatingSheetViewMap = floatingSheets,
            viewModel = viewModel,
            colorUpdateViewModel = colorUpdateViewModel,
            customizationOptionsBinder = customizationOptionsBinder,
            lifecycleOwner = activity,
            navigateToPrimary = {
                hideFloatingSheet()
                if (hasSecondaryScreen) {
                    hasSecondaryScreen = false
                    activity.window.decorView.postDelayed(
                        { viewModel.customizationOptionsViewModel.resetPreview() },
                        PRIMARY_TRANSITION_MS,
                    )
                }
            },
            navigateToSecondary = { option ->
                customizationOptionUtil.getScreenFromOption(option)?.let { screen ->
                    viewModel.selectPreviewScreen(screen)
                }
                showFloatingSheet(option, floatingSheets[option])
            },
            navigateToWallpaperCategoriesScreen = { screen ->
                callbacks.onOpenFragment(
                    CategoriesFragment.newInstance(
                        destinationScreen = screen,
                        wallpaperLaunchSource =
                            activity.intent.extras?.getString(WALLPAPER_LAUNCH_SOURCE) ?: "",
                    )
                )
            },
            navigateToMoreLockScreenSettingsActivity = {
                activity.startActivity(Intent(Settings.ACTION_LOCKSCREEN_SETTINGS))
            },
            navigateToColorContrastSettingsActivity = {
                activity.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_COLOR_CONTRAST_SETTINGS))
            },
            navigateToLockScreenNotificationsSettingsActivity = {
                activity.startActivity(Intent(Settings.ACTION_LOCKSCREEN_NOTIFICATIONS_SETTINGS))
            },
            navigateToPreviewScreen = { wallpaperModel, setWallpaperEntryPoint ->
                persistentWallpaperModelRepository.setWallpaperModel(wallpaperModel)
                callbacks.launchForResult(
                    WallpaperPreviewActivity.intentBuilder(activity.applicationContext, false)
                        .viewAsHome(!isDesktopUi)
                        .newTask(multiPanesChecker.isMultiPanesEnabled(activity))
                        .hideInfoSheet(true)
                        .entryPoint(setWallpaperEntryPoint)
                        .wallpaperLaunchSource(
                            activity.intent.extras?.getString(WALLPAPER_LAUNCH_SOURCE) ?: ""
                        )
                        .build()
                )
            },
            navigateToPackThemeActivity = { intent -> activity.startActivity(intent) },
            navigateToScreenSaverSettingsActivity = {
                activity.startActivity(Intent(Settings.ACTION_DREAM_SETTINGS))
            },
            navigateToWallpaperCollectionScreen = { categoryModel, categoryType ->
                if (BaseFlags.get(activity).isWallpapersFragmentEnabled()) {
                    categoryWallpapersRepository.setSelectedCategory(category = categoryModel)
                    callbacks.onOpenFragment(CategoryWallpapersFragment())
                } else {
                    callbacks.onOpenFragment(
                        individualPickerFactory.getIndividualPickerInstance(
                            categoryModel.commonCategoryData.collectionId,
                            categoryType,
                            viewModel.selectedPreviewScreen.value,
                        )
                    )
                }
            },
            navigateToExtendedWallpaperEffects = {
                if (BaseFlags.get(activity).isPhotoPickerEnabled()) {
                    callbacks.onOpenFragment(
                        PhotoPickerFragment.newInstance(
                            shouldNavigateToExtendedWallpaperEffects = true,
                            wallpaperLaunchSource =
                                activity.intent.extras?.getString(WALLPAPER_LAUNCH_SOURCE) ?: "",
                        )
                    )
                } else {
                    myPhotosStarterImpl.requestCustomPhotoPicker(
                        object : MyPhotosStarter.PermissionChangedListener {
                            override fun onPermissionsGranted() {}

                            override fun onPermissionsDenied(dontAskAgain: Boolean) {}
                        },
                        activity,
                        photoPickerLauncher,
                    )
                }
            },
            packThemeSuggestedChip = null,
            packThemeSuggestedEntryBinder = packThemeSuggestedEntryBinder,
            curatedPhotosTimeUtil = curatedPhotosTimeUtil,
            userEventLogger = userEventLogger,
            iconStyleViewUtil = iconStyleViewUtil,
        )

        customizationOptionsBinder.bindDiscardChangesDialog(
            customizationOptionsViewModel = viewModel.customizationOptionsViewModel,
            lifecycleOwner = activity,
            activity = activity,
        )
    }

    private fun initOptionEntries(
        data: CustomizationOptionsData,
        screen: Screen,
    ): List<Pair<CustomizationOption, View>> {
        val container =
            optionsHostView.requireViewById<LinearLayout>(
                when (screen) {
                    LOCK_SCREEN -> R.id.lock_customization_option_container
                    HOME_SCREEN -> R.id.home_customization_option_container
                }
            )
        val entries =
            customizationOptionViewUtil.getOptionEntries(
                customizationOptionsData = data,
                screen = screen,
                optionContainer = container,
                layoutInflater = inflater,
            )
        entries.forEachIndexed { index, (_, view) ->
            val isFirst = index == 0
            val isLast = index == entries.lastIndex
            view.setBackgroundResource(
                when {
                    isFirst && isLast -> R.drawable.customization_option_entry_singleton_background
                    isFirst -> R.drawable.customization_option_entry_top_background
                    isLast -> R.drawable.customization_option_entry_bottom_background
                    else -> R.drawable.customization_option_entry_background
                }
            )
            container.addView(view)
        }
        return entries
    }

    private fun showFloatingSheet(option: CustomizationOption, content: View?) {
        if (content == null) return
        hasSecondaryScreen = true
        floatingSheetContainer.removeAllViews()
        floatingSheetContainer.addView(content)
        floatingSheetContainer.visibility = View.VISIBLE
        floatingSheetContainer.alpha = 1f
        floatingSheetContainer.translationY = 0f
        callbacks.onShowFloatingSheet()
        userEventLogger.logEnterScreen(
            userEventLogger.transformCustomizationOptionToScreenForLogging(option)
        )
        activity.window.decorView.postDelayed(
            { viewModel.customizationOptionsViewModel.onTransitionToSecondaryScreenComplete() },
            FLOATING_SHEET_TRANSITION_MS,
        )
    }

    private fun hideFloatingSheet() {
        floatingSheetContainer.removeAllViews()
        floatingSheetContainer.visibility = View.GONE
        callbacks.onHideFloatingSheet()
    }

    private companion object {
        const val OPTIONS_BOTTOM_PADDING_DP = 16
        const val OPTIONS_TOP_CORNER_RADIUS_DP = 28
        const val PRIMARY_TRANSITION_MS = 350L
        const val FLOATING_SHEET_TRANSITION_MS = 500L
    }
}
