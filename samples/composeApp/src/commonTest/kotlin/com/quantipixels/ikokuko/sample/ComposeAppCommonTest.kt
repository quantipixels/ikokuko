package com.quantipixels.ikokuko.sample

import com.quantipixels.ikokuko.MaxLengthValidator
import com.quantipixels.ikokuko.ValidationScope
import com.quantipixels.ikokuko.Field
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ComposeAppCommonTest {
    private val values = mapOf<String, Any>("email" to "person@example.com")
    private val request = SubmittedRequest(2, values)
    private val response = FieldRejection(2, mapOf("email" to "Already registered"))

    @Test
    fun current_rejection_maps_only_known_initialized_fields_and_reports_unknown_fields() {
        val decision = rejectionDecision(
            request, response.copy(errors = response.errors + ("other" to "Rejected")),
            values, setOf("email"), consumed = false
        )
        assertEquals(response.errors, decision.fieldErrors)
        assertNotNull(decision.fallback)
    }

    @Test
    fun stale_request_or_changed_values_do_not_apply_a_rejection() {
        assertTrue(rejectionDecision(request, response.copy(requestId = 1), values,
            setOf("email"), false).fieldErrors.isEmpty())
        assertTrue(rejectionDecision(request, response, mapOf("email" to "edited"),
            setOf("email"), false).fieldErrors.isEmpty())
        assertTrue(rejectionDecision(null, response, values,
            setOf("email"), false).fieldErrors.isEmpty())
    }

    @Test
    fun consumed_response_cannot_reapply_after_editing_back_or_reentry() {
        val decision = rejectionDecision(request, response, values, setOf("email"), true)
        assertTrue(decision.fieldErrors.isEmpty())
        assertNull(decision.fallback)
    }

    @Test
    fun multiline_limit_validates_without_truncation_and_counter_uses_utf16_units() {
        val validator = MaxLengthValidator("Too long", NotesLimit)
        val scope = object : ValidationScope {
            override val <T : Any> Field<T>.value: T get() = error("No dependencies")
        }
        with(validator) {
            assertTrue(with(scope) { validate("") })
            assertTrue(with(scope) { validate("a".repeat(NotesLimit - 1) + "\n") })
            val pasted = "a".repeat(NotesLimit) + "\n"
            assertFalse(with(scope) { validate(pasted) })
            assertEquals(NotesLimit + 1, pasted.length)
        }
        assertEquals("2 / 240 UTF-16 units", notesCounter("😀"))
        assertEquals("2 / 240 UTF-16 units", notesCounter("e\u0301"))
    }
}
