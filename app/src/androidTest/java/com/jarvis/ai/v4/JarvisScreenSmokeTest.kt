package com.jarvis.ai.v4

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class JarvisScreenSmokeTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    @Test
    fun launchSettingsAndPrivacyControlsWork() {
        composeRule.onNodeWithText("J.A.R.V.I.S").assertIsDisplayed()
        composeRule.onNodeWithText("Settings").performClick()
        composeRule.onNodeWithText("WhatsApp announcements").assertIsDisplayed()

        composeRule.onNodeWithTag("announcements-enabled").assertIsOff()
        composeRule.onNodeWithTag("announcement-preview").assertIsOff()
        composeRule.onNodeWithTag("announcement-preview").performClick()
        composeRule.onNodeWithTag("announcement-preview").assertIsOn()
        composeRule.onNodeWithTag("announcement-preview").performClick()
        composeRule.onNodeWithTag("announcement-preview").assertIsOff()

        composeRule.onNodeWithText("हिन्दी").performClick()
        composeRule.onNodeWithText("हिन्दी").assertIsSelected()
        composeRule.onNodeWithText("Close").performClick()
        composeRule.onNodeWithText("J.A.R.V.I.S").assertIsDisplayed()
    }
}
