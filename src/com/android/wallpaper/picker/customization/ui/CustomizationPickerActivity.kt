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

package com.android.wallpaper.picker.customization.ui

import android.annotation.TargetApi
import android.content.Intent
import android.content.pm.ActivityInfo
import android.content.res.Configuration
import android.os.Bundle
import android.util.Log
import androidx.activity.addCallback
import androidx.activity.compose.setContent
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.WindowCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentContainerView
import androidx.fragment.app.FragmentManager
import androidx.fragment.app.commit
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.android.compose.theme.PlatformTheme
import com.android.customization.picker.clock.ui.view.ClockViewFactory
import com.android.customization.picker.icon.ui.util.IconStyleViewUtil
import com.android.wallpaper.R
import com.android.wallpaper.config.BaseFlags
import com.android.wallpaper.module.DailyLoggingAlarmScheduler
import com.android.wallpaper.module.MultiPanesChecker
import com.android.wallpaper.module.logging.UserEventLogger
import com.android.wallpaper.picker.AppbarFragment
import com.android.wallpaper.picker.MyPhotosStarter
import com.android.wallpaper.picker.category.ui.view.MyPhotosStarterImpl
import com.android.wallpaper.picker.category.ui.view.providers.IndividualPickerFactory
import com.android.wallpaper.picker.category.ui.viewmodel.CategoriesViewModel
import com.android.wallpaper.picker.common.preview.data.repository.PersistentWallpaperModelRepository
import com.android.wallpaper.picker.common.preview.ui.binder.WorkspaceBinder
import com.android.wallpaper.picker.customization.ui.binder.CustomizationOptionsBinder
import com.android.wallpaper.picker.customization.ui.binder.PackThemeSuggestedEntryBinder
import com.android.wallpaper.picker.customization.ui.binder.ToolbarBinder
import com.android.wallpaper.picker.customization.ui.compose.CustomizationPickerScreen
import com.android.wallpaper.picker.customization.ui.compose.CustomizationPickerUiState
import com.android.wallpaper.picker.customization.ui.util.CustomizationOptionUtil
import com.android.wallpaper.picker.customization.ui.util.CustomizationOptionViewUtil
import com.android.wallpaper.picker.customization.ui.viewmodel.ColorUpdateViewModel
import com.android.wallpaper.picker.customization.ui.viewmodel.CustomizationPickerViewModel2
import com.android.wallpaper.picker.di.modules.MainDispatcher
import com.android.wallpaper.picker.wallpapers.data.repository.CategoryWallpapersRepository
import com.android.wallpaper.util.ActivityUtils
import com.android.wallpaper.util.CuratedPhotosTimeUtil
import com.android.wallpaper.util.DisplayUtils
import com.android.wallpaper.util.LaunchSourceUtils.WALLPAPER_LAUNCH_SOURCE
import com.android.wallpaper.util.wallpaperconnection.WallpaperConnectionUtils
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

@AndroidEntryPoint(AppCompatActivity::class)
class CustomizationPickerActivity :
    Hilt_CustomizationPickerActivity(), AppbarFragment.AppbarFragmentHost {

    interface ActivityEnterAnimationCallback {
        /** Called after the activity's enter animation completes on first creation. */
        fun onEnterAnimationCompleteAfterActivityCreated()
    }

    @Inject lateinit var multiPanesChecker: MultiPanesChecker
    @Inject lateinit var customizationOptionsBinder: CustomizationOptionsBinder
    @Inject lateinit var customizationOptionUtil: CustomizationOptionUtil
    @Inject lateinit var customizationOptionViewUtil: CustomizationOptionViewUtil
    @Inject lateinit var packThemeSuggestedEntryBinder: PackThemeSuggestedEntryBinder
    @Inject lateinit var toolbarBinder: ToolbarBinder
    @Inject lateinit var workspaceBinder: WorkspaceBinder
    @Inject lateinit var persistentWallpaperModelRepository: PersistentWallpaperModelRepository
    @Inject lateinit var displayUtils: DisplayUtils
    @Inject lateinit var wallpaperConnectionUtils: WallpaperConnectionUtils
    @Inject lateinit var colorUpdateViewModel: ColorUpdateViewModel
    @Inject lateinit var clockViewFactory: ClockViewFactory
    @Inject lateinit var logger: UserEventLogger
    @Inject lateinit var individualPickerFactory: IndividualPickerFactory
    @Inject lateinit var categoryWallpapersRepository: CategoryWallpapersRepository
    @Inject lateinit var myPhotosStarterImpl: MyPhotosStarterImpl
    @Inject lateinit var curatedPhotosTimeUtil: CuratedPhotosTimeUtil
    @Inject lateinit var iconStyleViewUtil: IconStyleViewUtil
    @Inject @MainDispatcher lateinit var mainScope: CoroutineScope

    private var configuration: Configuration? = null
    private val categoriesViewModel: CategoriesViewModel by viewModels()
    private val customizationPickerViewModel: CustomizationPickerViewModel2 by viewModels()
    private val uiState = CustomizationPickerUiState()
    private lateinit var legacyHost: CustomizationPickerLegacyHost
    private val backStackChangedListener =
        FragmentManager.OnBackStackChangedListener {
            if (
                supportFragmentManager.backStackEntryCount == 0 &&
                    uiState.overlayVisible &&
                    uiState.pendingFragment == null
            ) {
                uiState.overlayVisible = false
            }
        }

    private val previewLauncher: ActivityResultLauncher<Intent> =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == RESULT_OK) {
                closeOverlay()
            }
        }

    private val photoPickerLauncher: ActivityResultLauncher<Intent> =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {}

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enforcePortraitForHandheldAndFoldedDisplay()

        DailyLoggingAlarmScheduler.setAlarm(applicationContext)

        if (intent != null) {
            logger.logAppLaunched(intent)
        }
        uiState.overlayVisible = savedInstanceState?.getBoolean(KEY_OVERLAY_VISIBLE) ?: false

        if (
            multiPanesChecker.isMultiPanesEnabled(this) &&
                !ActivityUtils.isLaunchedFromSettingsTrampoline(intent) &&
                !ActivityUtils.isLaunchedFromSettingsRelated(intent)
        ) {
            multiPanesChecker.getMultiPanesIntent(intent)?.let { multiPanesIntent ->
                ActivityUtils.startActivityForResultSafely(
                    activity = this,
                    intent = multiPanesIntent,
                    requestCode = 0,
                )
            }
                ?: Log.w(
                    CUSTOMIZATION_PICKER_FRAGMENT_TAG,
                    "multiPanesIntent was null, not starting multi-pane activity.",
                )
            finish()
            return
        }

        categoriesViewModel.initialize()
        configuration = Configuration(resources.configuration)
        colorUpdateViewModel.updateDarkModeAndColors()
        colorUpdateViewModel.setPreviewEnabled(!displayUtils.isLargeScreenOrUnfoldedDisplay(this))
        bindWindowColors()

        WindowCompat.setDecorFitsSystemWindows(window, ActivityUtils.isSUWMode(this))
        installLegacyPlaceholderFragment()
        supportFragmentManager.addOnBackStackChangedListener(backStackChangedListener)

        legacyHost =
            CustomizationPickerLegacyHost(
                activity = this,
                viewModel = customizationPickerViewModel,
                colorUpdateViewModel = colorUpdateViewModel,
                toolbarBinder = toolbarBinder,
                customizationOptionsBinder = customizationOptionsBinder,
                customizationOptionUtil = customizationOptionUtil,
                customizationOptionViewUtil = customizationOptionViewUtil,
                packThemeSuggestedEntryBinder = packThemeSuggestedEntryBinder,
                workspaceBinder = workspaceBinder,
                displayUtils = displayUtils,
                wallpaperConnectionUtils = wallpaperConnectionUtils,
                persistentWallpaperModelRepository = persistentWallpaperModelRepository,
                multiPanesChecker = multiPanesChecker,
                individualPickerFactory = individualPickerFactory,
                categoryWallpapersRepository = categoryWallpapersRepository,
                myPhotosStarterImpl = myPhotosStarterImpl,
                photoPickerLauncher = photoPickerLauncher,
                curatedPhotosTimeUtil = curatedPhotosTimeUtil,
                userEventLogger = logger,
                iconStyleViewUtil = iconStyleViewUtil,
                clockViewFactory = clockViewFactory,
                mainScope = mainScope,
                callbacks =
                    object : CustomizationPickerLegacyHost.Callbacks {
                        override fun onBack() {
                            onBackPressedDispatcher.onBackPressed()
                        }

                        override fun onShowFloatingSheet() {
                            uiState.floatingSheetVisible = true
                        }

                        override fun onHideFloatingSheet() {
                            uiState.floatingSheetVisible = false
                        }

                        override fun onOpenFragment(fragment: Fragment) {
                            openFragment(fragment)
                        }

                        override fun launchForResult(intent: Intent) {
                            previewLauncher.launch(intent)
                        }
                    },
            )

        // Bind while the preview views are detached so the SurfaceHolder callbacks are registered
        // before Compose attaches the SurfaceViews.
        legacyHost.bindPreview()

        onBackPressedDispatcher.addCallback(this) {
            when {
                uiState.overlayVisible -> popOverlay()
                legacyHost.handleBackPressed() -> Unit
                else -> {
                    isEnabled = false
                    onBackPressedDispatcher.onBackPressed()
                    isEnabled = true
                }
            }
        }

        setContent {
            PlatformTheme {
                CustomizationPickerScreen(
                    host = legacyHost,
                    viewModel = customizationPickerViewModel,
                    colorUpdateViewModel = colorUpdateViewModel,
                    state = uiState,
                    onWallpapersClick = {
                        openFragment(
                            com.android.wallpaper.picker.category.ui.view.CategoriesFragment
                                .newInstance(
                                    destinationScreen =
                                        customizationPickerViewModel.selectedPreviewScreen.value,
                                    wallpaperLaunchSource =
                                        intent.extras?.getString(WALLPAPER_LAUNCH_SOURCE) ?: "",
                                )
                        )
                    },
                    onAttachOverlay = ::attachOverlay,
                )
            }
        }
    }

    override fun onResume() {
        super.onResume()
        categoriesViewModel.refreshCuratedPhotos()
    }

    override fun onEnterAnimationComplete() {
        super.onEnterAnimationComplete()
    }

    override fun onUpArrowPressed() {
        onBackPressedDispatcher.onBackPressed()
    }

    override fun isUpArrowSupported(): Boolean {
        return BaseFlags.get(baseContext).shouldShowDesktopUi(baseContext) ||
            !ActivityUtils.isSUWMode(baseContext)
    }

    @TargetApi(36)
    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        configuration?.let {
            val diff: Int = it.diff(newConfig)
            if (diff and ActivityInfo.CONFIG_UI_MODE != 0) {
                colorUpdateViewModel.updateDarkModeAndColors()
            } else if (diff and ActivityInfo.CONFIG_ASSETS_PATHS != 0) {
                colorUpdateViewModel.updateColors()
            }
        }
        configuration?.setTo(newConfig)
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putBoolean(KEY_OVERLAY_VISIBLE, uiState.overlayVisible)
        super.onSaveInstanceState(outState)
    }

    override fun onDestroy() {
        supportFragmentManager.removeOnBackStackChangedListener(backStackChangedListener)
        if (this::legacyHost.isInitialized) {
            legacyHost.disconnect()
        }
        super.onDestroy()
    }

    private fun bindWindowColors() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                colorUpdateViewModel.colorSurfaceContainer.collect { color ->
                    window.statusBarColor = color
                    window.setStatusBarContrastEnforced(false)
                    window.navigationBarColor = android.graphics.Color.TRANSPARENT
                    window.setNavigationBarContrastEnforced(false)
                    WindowCompat.getInsetsController(window, window.decorView)
                        .isAppearanceLightStatusBars =
                        android.graphics.Color.luminance(color) > 0.5f
                    WindowCompat.getInsetsController(window, window.decorView)
                        .isAppearanceLightNavigationBars =
                        android.graphics.Color.luminance(color) > 0.5f
                }
            }
        }
    }

    private fun installLegacyPlaceholderFragment() {
        val fragment = supportFragmentManager.findFragmentByTag(CUSTOMIZATION_PICKER_FRAGMENT_TAG)
        if (fragment is CustomizationPickerPlaceholderFragment) return
        if (fragment != null) {
            supportFragmentManager
                .beginTransaction()
                .remove(fragment)
                .commitNowAllowingStateLoss()
        }
        supportFragmentManager
            .beginTransaction()
            .add(CustomizationPickerPlaceholderFragment(), CUSTOMIZATION_PICKER_FRAGMENT_TAG)
            .commitNowAllowingStateLoss()
    }

    private fun openFragment(fragment: Fragment) {
        if (uiState.overlayVisible) {
            supportFragmentManager.commit {
                setReorderingAllowed(true)
                replace(R.id.fragment_container, fragment)
                addToBackStack(null)
            }
        } else {
            uiState.pendingFragment = fragment
            uiState.overlayVisible = true
        }
    }

    private fun attachOverlay(container: FragmentContainerView) {
        if (!uiState.overlayVisible) return
        val fragment = uiState.pendingFragment ?: return
        if (!container.isAttachedToWindow) {
            container.post { attachOverlay(container) }
            return
        }
        uiState.pendingFragment = null
        if (supportFragmentManager.findFragmentById(R.id.fragment_container) != null) return
        supportFragmentManager.commit {
            setReorderingAllowed(true)
            replace(container.id, fragment)
            addToBackStack(null)
        }
    }

    private fun popOverlay() {
        if (supportFragmentManager.backStackEntryCount > 1) {
            supportFragmentManager.popBackStack()
        } else {
            closeOverlay()
        }
    }

    private fun closeOverlay() {
        supportFragmentManager.popBackStack(null, FragmentManager.POP_BACK_STACK_INCLUSIVE)
        uiState.pendingFragment = null
        uiState.overlayVisible = false
        uiState.floatingSheetVisible = false
    }

    private fun enforcePortraitForHandheldAndFoldedDisplay() {
        val wantedOrientation =
            if (displayUtils.isLargeScreenOrUnfoldedDisplay(this))
                ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
            else ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        if (requestedOrientation != wantedOrientation) {
            requestedOrientation = wantedOrientation
        }
    }

    /** Compatibility fragment for state saved by the first Compose host build. */
    class PlaceholderFragment : Fragment()

    companion object {
        private const val KEY_OVERLAY_VISIBLE = "customization_overlay_visible"
        const val CUSTOMIZATION_PICKER_FRAGMENT_TAG = "customization_picker_fragment"
        const val PREVIEW_LIVE_WALLPAPER_REQUEST_CODE = 4
        const val VIEW_ONLY_PREVIEW_WALLPAPER_REQUEST_CODE = 2
        const val PREVIEW_WALLPAPER_REQUEST_CODE = 1
    }
}
