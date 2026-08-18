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

package com.android.wallpaper.customization.ui

import android.content.Context
import android.content.Intent
import androidx.activity.viewModels
import androidx.test.core.app.ActivityScenario
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.ViewMatchers.isDescendantOfA
import androidx.test.espresso.matcher.ViewMatchers.isDisplayed
import androidx.test.espresso.matcher.ViewMatchers.withId
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.android.wallpaper.R
import com.android.wallpaper.model.Screen
import com.android.wallpaper.model.Screen.HOME_SCREEN
import com.android.wallpaper.module.InjectorProvider
import com.android.wallpaper.picker.customization.ui.CustomizationPickerActivity
import com.android.wallpaper.picker.customization.ui.viewmodel.CustomizationPickerViewModel2
import com.android.wallpaper.testing.TestInjector
import com.android.wallpaper.util.LaunchSourceUtils.LAUNCH_SOURCE_LAUNCHER
import com.android.wallpaper.util.LaunchSourceUtils.LAUNCH_SOURCE_SETTINGS
import com.android.wallpaper.util.LaunchSourceUtils.WALLPAPER_LAUNCH_SOURCE
import com.google.common.truth.Truth.assertThat
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import javax.inject.Inject
import org.hamcrest.Matchers.allOf
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Tests for [CustomizationPickerActivity] */
@HiltAndroidTest
@RunWith(AndroidJUnit4::class)
class CustomizationPickerActivityTest {
    @get:Rule var hiltRule = HiltAndroidRule(this)

    @Inject @ApplicationContext lateinit var context: Context
    @Inject lateinit var testInjector: TestInjector

    @Before
    fun setUp() {
        hiltRule.inject()
        InjectorProvider.setInjector(testInjector)
    }

    @After
    fun tearDown() {
        // TODO(b/467381197) Perhaps move this call to ClockRegistryProvider, etc.
        Thread.setUncaughtExceptionPreHandler(null)
    }

    @Test
    fun launchSuccess() {
        ActivityScenario.launch(CustomizationPickerActivity::class.java)

        onView(withId(R.id.preview_header)).check(matches(isDisplayed()))
    }

    @Test
    fun launchFromLauncher_showsHomeInPager() {
        val intent =
            Intent(context, CustomizationPickerActivity::class.java).apply {
                putExtra(WALLPAPER_LAUNCH_SOURCE, LAUNCH_SOURCE_LAUNCHER)
            }
        val scenario = ActivityScenario.launch<CustomizationPickerActivity>(intent)

        scenario.onActivity { activity ->
            val customizationPickerViewModel =
                activity.viewModels<CustomizationPickerViewModel2>().value
            assertThat(customizationPickerViewModel.selectedPreviewScreen.value)
                .isEqualTo(HOME_SCREEN)
        }
    }

    @Test
    fun launchFromSettings_showsLockInPager() {
        val intent =
            Intent(context, CustomizationPickerActivity::class.java).apply {
                putExtra(WALLPAPER_LAUNCH_SOURCE, LAUNCH_SOURCE_SETTINGS)
            }
        val scenario = ActivityScenario.launch<CustomizationPickerActivity>(intent)

        scenario.onActivity { activity ->
            val customizationPickerViewModel =
                activity.viewModels<CustomizationPickerViewModel2>().value
            assertThat(customizationPickerViewModel.selectedPreviewScreen.value)
                .isEqualTo(Screen.LOCK_SCREEN)
        }
    }

    @Test
    fun launch_bothPreviewsShownSideBySide() {
        ActivityScenario.launch(CustomizationPickerActivity::class.java)

        onView(allOf(withId(R.id.preview_card), isDescendantOfA(withId(R.id.lock_preview))))
            .check(matches(isDisplayed()))
        onView(allOf(withId(R.id.preview_card), isDescendantOfA(withId(R.id.home_preview))))
            .check(matches(isDisplayed()))
    }

    @Test
    fun clickHomePreview_selectsHomeScreen() {
        val intent =
            Intent(context, CustomizationPickerActivity::class.java).apply {
                putExtra(WALLPAPER_LAUNCH_SOURCE, LAUNCH_SOURCE_SETTINGS)
            }
        val scenario = ActivityScenario.launch<CustomizationPickerActivity>(intent)

        onView(allOf(withId(R.id.preview_card), isDescendantOfA(withId(R.id.home_preview))))
            .perform(click())

        scenario.onActivity { activity ->
            val customizationPickerViewModel =
                activity.viewModels<CustomizationPickerViewModel2>().value
            assertThat(customizationPickerViewModel.selectedPreviewScreen.value)
                .isEqualTo(HOME_SCREEN)
        }
    }
}
