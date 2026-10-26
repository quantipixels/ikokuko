package com.quantipixels.ikokuko.verification

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import org.junit.Rule
import org.junit.Test

class RestorationTest {
    @get:Rule val compose = createAndroidComposeRule<VerificationActivity>()

    @Test fun realRegistryRestoresTextAndSelectedIdsBeforeImmediateSubmit() {
        compose.onNodeWithTag("name").performTextReplacement("Yétúndé")
        compose.onNodeWithTag("select").performClick()
        // These preconditions distinguish missing input from a restoration failure.
        compose.onNodeWithTag("name").assertTextContains("Yétúndé")
        compose.onNodeWithTag("selection").assertTextEquals("second-id")
        compose.activityRule.scenario.recreate()
        compose.onNodeWithTag("name").assertTextContains("Yétúndé")
        compose.onNodeWithTag("selection").assertTextEquals("second-id")
        compose.activityRule.scenario.onActivity { it.submitCurrent() }
        compose.onNodeWithTag("outcome").assertTextEquals("submitted:Yétúndé:second-id")
    }

    @Test fun sameEventEditsAreValidatedBeforeSubmit() {
        compose.onNodeWithTag("select").performClick()
        compose.onNodeWithTag("name").performTextReplacement("valid")
        compose.onNodeWithTag("fresh-invalid").performClick()
        compose.onNodeWithTag("outcome").assertTextEquals("invalid")
        compose.onNodeWithTag("fresh-valid").performClick()
        compose.onNodeWithTag("outcome").assertTextEquals("submitted:fresh:second-id")
    }

    @Test fun multilineUnicodeInputAndImeRespectTheBoundWithoutTruncation() {
        compose.onNodeWithTag("select").performClick()
        compose.onNodeWithTag("name").performClick().assertIsFocused()
        compose.onNodeWithTag("name").performTextInput("Ẹ káàárọ̀\n🙂")
        compose.onNodeWithTag("name").performImeAction()
        compose.onNodeWithTag("outcome").assertTextEquals("submitted:Ẹ káàárọ̀\n🙂:second-id")
        val overLimit = "🙂".repeat(9) // 18 UTF-16 code units; the bound is 16.
        compose.onNodeWithTag("name").performTextReplacement(overLimit)
        compose.onNodeWithTag("name").assertTextContains(overLimit)
        compose.onNodeWithTag("name").performImeAction()
        compose.onNodeWithTag("outcome").assertTextEquals("invalid")
    }
}
