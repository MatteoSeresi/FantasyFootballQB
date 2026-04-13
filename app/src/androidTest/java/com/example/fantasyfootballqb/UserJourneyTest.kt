package com.example.fantasyfootballqb

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.espresso.Espresso
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class UserJourneyTest {

    @get:Rule
    val composeTestRule = createAndroidComposeRule<MainActivity>()

    @Test
    fun testUserFullJourney_Register_Profile_Delete_LoginFail() {
        val testEmail = "test@test.com"
        val testPass = "password123"
        val testUsername = "UserTest"

        // REGISTRAZIONE
        composeTestRule.onNodeWithText("Non sei registrato? Registrati adesso").performClick()

        composeTestRule.onNodeWithText("Email").performTextInput(testEmail)
        composeTestRule.onNodeWithText("Username").performTextInput(testUsername)
        composeTestRule.onNodeWithText("Password").performTextInput(testPass)
        composeTestRule.onNodeWithText("Conferma Password").performTextInput(testPass)

        Espresso.closeSoftKeyboard()
        composeTestRule.waitForIdle()

        composeTestRule.onNode(hasText("Registrati") and hasClickAction()).performClick()

        // NAVIGAZIONE AL PROFILO
        composeTestRule.waitUntil(timeoutMillis = 15000) {
            composeTestRule.onAllNodesWithText("Profile").fetchSemanticsNodes().isNotEmpty()
        }
        composeTestRule.onNodeWithText("Profile").performClick()

        // 3. LOGOUT
        composeTestRule.onNodeWithText(testUsername).assertIsDisplayed()
        Thread.sleep(3000)
        composeTestRule.onNodeWithText("Logout").performClick()

        // SECONDO LOGIN
        composeTestRule.onNodeWithText("Email").performTextInput(testEmail)
        composeTestRule.onNodeWithText("Password").performTextInput(testPass)

        Espresso.closeSoftKeyboard()
        composeTestRule.onNode(hasText("Accedi") and hasClickAction()).performClick()

        // ELIMINAZIONE ACCOUNT
        composeTestRule.waitUntil(timeoutMillis = 10000) {
            composeTestRule.onAllNodesWithText("Profile").fetchSemanticsNodes().isNotEmpty()
        }
        composeTestRule.onNodeWithText("Profile").performClick()
        composeTestRule.onNodeWithText("Elimina account").performClick()
        Thread.sleep(3000)
        composeTestRule.onNodeWithText("Elimina").performClick()
        Thread.sleep(3000)

        // VERIFICA ACCESSO NEGATO
        composeTestRule.waitUntil(timeoutMillis = 10000) {
            composeTestRule.onAllNodesWithText("Accedi").fetchSemanticsNodes().isNotEmpty()
        }

        composeTestRule.onNodeWithText("Email").performTextReplacement(testEmail)
        composeTestRule.onNodeWithText("Password").performTextReplacement(testPass)

        Espresso.closeSoftKeyboard()
        composeTestRule.onNode(hasText("Accedi") and hasClickAction()).performClick()

        // Messaggio d'errore di Firebase
        val firebaseErrorMessage = "The supplied auth credential is incorrect, malformed or has expired."

        composeTestRule.waitUntil(timeoutMillis = 8000) {
            composeTestRule.onAllNodesWithText(firebaseErrorMessage, substring = true)
                .fetchSemanticsNodes().isNotEmpty()
        }

        composeTestRule.onNodeWithText(firebaseErrorMessage, substring = true).assertIsDisplayed()
        Thread.sleep(3000)
    }
}