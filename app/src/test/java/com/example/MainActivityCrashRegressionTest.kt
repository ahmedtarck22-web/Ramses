package com.example

import android.Manifest
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class MainActivityCrashRegressionTest {

    @get:Rule
    val composeTestRule = createAndroidComposeRule<MainActivity>()

    @Test
    fun `MainActivity launches cleanly hides Studio Rig and opens 3D Artifacts and Gemini Live without crashing`() {
        Shadows.shadowOf(composeTestRule.activity.application)
            .grantPermissions(Manifest.permission.RECORD_AUDIO)

        composeTestRule.waitForIdle()

        // 1. Header, Profile Avatar, Input Bar, and 3D Avatar Viewport render without crash
        composeTestRule.onNodeWithTag("menu_button").assertIsDisplayed()
        composeTestRule.onNodeWithTag("profile_button").assertIsDisplayed()
        composeTestRule.onNodeWithTag("chat_input_field").assertIsDisplayed()
        assertTrue(
            "Expected Pharaonic 3D fallback emblem in headless JVM environment",
            composeTestRule.onAllNodesWithTag("pharaonic_3d_fallback").fetchSemanticsNodes().isNotEmpty()
        )

        // 2. Verify top "3D Rig" badge pill is hidden per user request
        assertTrue(
            "Expected open_rig_studio_pill to be hidden",
            composeTestRule.onAllNodesWithTag("open_rig_studio_pill").fetchSemanticsNodes().isEmpty()
        )

        // 3. Open Side Menu Drawer, verify Studio Rig item is hidden, and open 3D Artifacts Gallery cleanly
        composeTestRule.onNodeWithTag("menu_button").performClick()
        composeTestRule.waitForIdle()
        assertTrue(
            "Expected drawer_rig_studio_item to be hidden in side menu",
            composeTestRule.onAllNodesWithTag("drawer_rig_studio_item").fetchSemanticsNodes().isEmpty()
        )

        composeTestRule.onNodeWithTag("side_menu_drawer_scroll")
            .performScrollToNode(hasTestTag("drawer_artifacts_item"))
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("drawer_artifacts_item").performClick()
        composeTestRule.waitForIdle()

        // Verify 3D Artifact Overlay renders and closes cleanly without Filament Engine panic
        composeTestRule.onNodeWithTag("artifact_3d_overlay").assertIsDisplayed()
        composeTestRule.onNodeWithTag("dialog_close_button").assertIsDisplayed().performClick()
        composeTestRule.waitForIdle()

        // 4. Switch Gems repeatedly in 3-bars side menu: Ramses -> Gemini -> Ramses -> Gemini -> Ramses
        composeTestRule.onNodeWithTag("menu_button").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("side_menu_drawer_scroll")
            .performScrollToNode(hasTestTag("drawer_gem_gemini"))
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("drawer_gem_gemini").assertIsDisplayed().performClick()
        composeTestRule.waitForIdle()
        assertTrue(
            "Expected 3D viewport to unmount cleanly when switching from Ramses to Gemini",
            composeTestRule.onAllNodesWithTag("pharaonic_3d_fallback").fetchSemanticsNodes().isEmpty()
        )

        // Tap Gemini Live while in Gemini mode and verify classic Gemini Live sparkle orb renders and closes cleanly
        composeTestRule.onNodeWithTag("gemini_live_call_button").assertIsDisplayed().performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("gemini_live_overlay").assertIsDisplayed()
        composeTestRule.onNodeWithTag("gemini_live_orb_container").assertIsDisplayed()
        assertTrue(
            "Expected Ramses 3D avatar container not to appear when Gemini assistant is active",
            composeTestRule.onAllNodesWithTag("gemini_live_3d_avatar_container").fetchSemanticsNodes().isEmpty()
        )
        composeTestRule.onNodeWithTag("gemini_live_close_button").assertIsDisplayed().performClick()
        composeTestRule.waitForIdle()

        // Switch back to Ramses in side menu, then back to Gemini again, then back to Ramses
        composeTestRule.onNodeWithTag("menu_button").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("drawer_gem_ramses").assertIsDisplayed().performClick()
        composeTestRule.waitForIdle()
        assertTrue(
            "Expected 3D viewport to remount cleanly when switching back to Ramses",
            composeTestRule.onAllNodesWithTag("pharaonic_3d_fallback").fetchSemanticsNodes().isNotEmpty()
        )

        composeTestRule.onNodeWithTag("menu_button").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("drawer_gem_gemini").assertIsDisplayed().performClick()
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithTag("menu_button").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("drawer_gem_ramses").assertIsDisplayed().performClick()
        composeTestRule.waitForIdle()

        // 5. Tap Gemini Live button from Ramses mode and verify Live Voice overlay & 3D Ramses avatar open/close without crash
        composeTestRule.onNodeWithTag("gemini_live_call_button").assertIsDisplayed().performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("gemini_live_overlay").assertIsDisplayed()
        composeTestRule.onNodeWithTag("gemini_live_3d_avatar_container").assertIsDisplayed()
        composeTestRule.onNodeWithTag("ramses_3d_avatar_viewport").assertIsDisplayed()
        composeTestRule.onNodeWithTag("gemini_live_close_button").assertIsDisplayed().performClick()
        composeTestRule.waitForIdle()

        // 6. Open Profile / Auth Sign-In Dialog and verify user avatar & profile controls render cleanly
        composeTestRule.onNodeWithTag("profile_button").performClick()
        composeTestRule.waitForIdle()
        assertTrue(
            composeTestRule.onAllNodesWithTag("auth_dialog_scroll").fetchSemanticsNodes().isNotEmpty()
        )
        assertTrue(
            composeTestRule.onAllNodesWithTag("change_profile_photo_button").fetchSemanticsNodes().isNotEmpty()
        )
    }
}
