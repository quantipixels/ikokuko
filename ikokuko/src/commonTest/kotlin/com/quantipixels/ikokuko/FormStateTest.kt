package com.quantipixels.ikokuko

import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.SaverScope
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class FormStateTest {

    private val saveScope = SaverScope { true }

    @Test
    fun validity_is_strict_and_error_display_is_separate() {
        val field = Field.Text("email")
        val state = FormState()
        val scope = FormScope(state) {}
        state.values[field] = ""

        with(scope) {
            field.error = "required"

            assertFalse(field.isValid)
            assertFalse(isValid)
            assertEquals("required", field.error)
            assertFalse(field.shouldDisplayError)

            field.isDirty = true
            assertFalse(field.shouldDisplayError)

            shouldShowErrors = true
            assertTrue(field.shouldDisplayError)
            assertFalse(field.isValid)
            assertFalse(isValid)
        }
    }

    @Test
    fun value_setter_marks_only_initialized_value_changes_dirty() {
        val field = Field.Text("name")
        val state = FormState()
        val scope = FormScope(state) {}
        state.values[field] = "initial"

        with(scope) {
            field.value = "initial"
            assertFalse(field.isDirty)

            field.value = "changed"
            assertTrue(field.isDirty)

            field.isDirty = false
            assertFalse(field.isDirty)

            field.isDirty = true
            assertTrue(field.isDirty)
        }
    }

    @Test
    fun reset_clears_values_errors_dirty_state_and_visibility() {
        val field = Field.Text("email")
        val state = FormState(shouldShowErrors = true)
        val scope = FormScope(state) {}
        state.values[field] = "value"

        with(scope) {
            field.isDirty = true
            field.error = "error"
            reset()

            assertFalse(field.isInitialized)
            assertFalse(field in fields)
            assertFalse(field.isDirty)
            assertNull(field.error)
            assertFalse(shouldShowErrors)
            assertTrue(isValid)
        }
    }

    @Test
    fun submit_uses_strict_validity_and_marks_initialized_fields_dirty() {
        val field = Field.Text("email")
        val state = FormState()
        var submitted = false
        var invalid = false
        val scope = FormScope(state) { submitted = true }
        state.values[field] = ""

        with(scope) {
            field.error = "required"
            submit { invalid = true }

            assertFalse(submitted)
            assertTrue(invalid)
            assertTrue(field.isDirty)
            assertTrue(field.shouldDisplayError)

            field.error = null
            submit()
            assertTrue(submitted)
        }
    }

    @Test
    fun default_saver_restores_values_dirty_errors_and_visibility() {
        val name = Field.Text("name")
        val age = Field<Int>("age")
        val state = FormState(shouldShowErrors = true)
        state.values[name] = "Ada"
        state.values[age] = 37
        state.dirtyFields.add(name)
        state.errors[name.name] = "invalid"

        val restored = FormState.Saver.roundTrip(state)
        val scope = FormScope(restored) {}

        with(scope) {
            assertEquals("Ada", name.value)
            assertEquals(37, age.value)
            assertTrue(name.isDirty)
            assertEquals("invalid", name.error)
            assertTrue(shouldShowErrors)
        }
    }

    @Test
    fun custom_values_saver_restores_non_saveable_field_values() {
        val profile = Field<Profile>("profile")
        val state = FormState()
        state.values[profile] = Profile("Ada")
        val valuesSaver = Saver<Map<String, Any>, List<String>>(
            save = { values ->
                values.flatMap { (name, value) -> listOf(name, (value as Profile).name) }
            },
            restore = { saved ->
                saved.chunked(2).associate { (name, value) -> name to Profile(value) }
            }
        )

        val restored = FormState.saver(valuesSaver).roundTrip(state)
        val scope = FormScope(restored) {}

        with(scope) {
            assertEquals(Profile("Ada"), profile.value)
        }
    }

    private fun <Saveable : Any> Saver<FormState, Saveable>.roundTrip(
        state: FormState
    ): FormState {
        val saved = with(this) { saveScope.save(state) } ?: error("State was not saved")
        return restore(saved) ?: error("State was not restored")
    }

    private data class Profile(val name: String)
}
