package com.quantipixels.ikokuko

import androidx.compose.runtime.AbstractApplier
import androidx.compose.runtime.BroadcastFrameClock
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Composition
import androidx.compose.runtime.Recomposer
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.Snapshot
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestResult
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class FormRecompositionTest {

    @Test
    fun initial_validation_does_not_block_the_form_until_error_reporting_is_enabled() =
        runCompositionTest {
            val field = Field.Text("required")
            lateinit var formScope: FormScope

            setContent {
                Form {
                    ValidationEffect(
                        field = field,
                        initialValue = "",
                        validators = listOf(RequiredValidator(VALIDATION_ERROR))
                    )
                    SideEffect { formScope = this }
                }
            }
            waitForIdle()

            runOnIdle {
                with(formScope) {
                    assertEquals(VALIDATION_ERROR, field.error)
                    assertFalse(field.isValid)
                    assertTrue(isValid)

                    shouldShowErrors = true

                    assertFalse(isValid)
                }
            }
        }

    @Test
    fun equivalent_inline_validator_recreation_preserves_external_error_without_field_change() =
        runCompositionTest {
            val field = Field.Text("email")
            var recompositionTrigger by mutableStateOf(0)
            var observedRecomposition = 0
            lateinit var formScope: FormScope

            setContent {
                Form {
                    val currentRecomposition = recompositionTrigger

                    ValidationEffect(
                        field = field,
                        initialValue = "",
                        validators = listOf(RequiredValidator(VALIDATION_ERROR))
                    )

                    SideEffect {
                        formScope = this
                        observedRecomposition = currentRecomposition
                    }
                }
            }
            waitForIdle()

            runOnIdle {
                with(formScope) {
                    field.isDirty = true
                    shouldShowErrors = true
                    field.error = EXTERNAL_ERROR

                    assertEquals("", field.value)
                    assertEquals(EXTERNAL_ERROR, field.error)
                }
            }

            runOnIdle { recompositionTrigger++ }
            waitForIdle()

            runOnIdle {
                assertEquals(1, observedRecomposition)
                with(formScope) {
                    assertEquals("", field.value)
                    assertEquals(EXTERNAL_ERROR, field.error)
                }
            }
        }

    @Test
    fun replacing_form_state_disposes_the_old_form_and_validates_the_new_form() =
        runCompositionTest {
            val field = Field.Text("email")
            val firstState = FormState()
            val secondState = FormState()
            var currentState by mutableStateOf(firstState)
            lateinit var currentScope: FormScope

            setContent {
                Form(state = currentState) {
                    ValidationEffect(
                        field = field,
                        initialValue = "",
                        validators = listOf(RequiredValidator(VALIDATION_ERROR))
                    )
                    SideEffect { currentScope = this }
                }
            }
            waitForIdle()

            val firstScope = currentScope
            runOnIdle {
                with(firstScope) {
                    assertEquals(VALIDATION_ERROR, field.error)
                    field.error = EXTERNAL_ERROR
                }
                currentState = secondState
            }
            waitForIdle()

            runOnIdle {
                with(firstScope) { assertNull(field.error) }
                with(currentScope) { assertEquals(VALIDATION_ERROR, field.error) }
            }
        }

    @Test
    fun changing_a_dependency_revalidates_the_dependent_field() = runCompositionTest {
        val password = Field.Text("password")
        val confirmation = Field.Text("confirmation")
        lateinit var formScope: FormScope

        setContent {
            Form {
                ValidationEffect(password, initialValue = "same", validators = emptyList())
                ValidationEffect(
                    confirmation,
                    initialValue = "same",
                    validators = listOf(FieldEqualsValidator(MISMATCH_ERROR, password))
                )
                SideEffect { formScope = this }
            }
        }
        waitForIdle()

        runOnIdle {
            with(formScope) {
                assertNull(confirmation.error)
                password.value = "different"
            }
        }
        waitForIdle()

        runOnIdle {
            with(formScope) {
                assertEquals(MISMATCH_ERROR, confirmation.error)
            }
        }
    }

    @Test
    fun validator_waits_for_an_uninitialized_dependency() = runCompositionTest {
        val password = Field.Text("password")
        val confirmation = Field.Text("confirmation")
        var initializePassword by mutableStateOf(false)
        lateinit var formScope: FormScope

        setContent {
            Form {
                ValidationEffect(
                    confirmation,
                    initialValue = "confirmation",
                    validators = listOf(FieldEqualsValidator(MISMATCH_ERROR, password))
                )
                if (initializePassword) {
                    ValidationEffect(password, initialValue = "password", validators = emptyList())
                }
                SideEffect { formScope = this }
            }
        }
        waitForIdle()

        runOnIdle {
            with(formScope) {
                assertFalse(password.isInitialized)
                assertNull(confirmation.error)
            }
            initializePassword = true
        }
        waitForIdle()

        runOnIdle {
            with(formScope) {
                assertTrue(password.isInitialized)
                assertEquals(MISMATCH_ERROR, confirmation.error)
            }
        }
    }

    @Test
    fun validator_waits_for_two_dependencies_and_revalidates_when_either_changes() =
        runCompositionTest {
            val first = Field.Text("first")
            val second = Field.Text("second")
            val combined = Field.Text("combined")
            var initializeFirst by mutableStateOf(false)
            var initializeSecond by mutableStateOf(false)
            lateinit var formScope: FormScope
            val validator = object : Validator<String> {
                override val errorMessage = MISMATCH_ERROR
                override val dependencies = listOf(first, second)

                override fun ValidationScope.validate(value: String) =
                    value == first.value + second.value
            }

            setContent {
                Form {
                    ValidationEffect(
                        combined,
                        initialValue = "AB",
                        validators = listOf(validator)
                    )
                    if (initializeFirst) {
                        ValidationEffect(first, initialValue = "A", validators = emptyList())
                    }
                    if (initializeSecond) {
                        ValidationEffect(second, initialValue = "B", validators = emptyList())
                    }
                    SideEffect { formScope = this }
                }
            }
            waitForIdle()

            runOnIdle {
                with(formScope) { assertNull(combined.error) }
                initializeFirst = true
            }
            waitForIdle()

            runOnIdle {
                with(formScope) { assertNull(combined.error) }
                initializeSecond = true
            }
            waitForIdle()

            runOnIdle {
                with(formScope) {
                    assertNull(combined.error)
                    first.value = "X"
                }
            }
            waitForIdle()

            runOnIdle {
                with(formScope) {
                    assertEquals(MISMATCH_ERROR, combined.error)
                    first.value = "A"
                }
            }
            waitForIdle()

            runOnIdle {
                with(formScope) {
                    assertNull(combined.error)
                    second.value = "Y"
                }
            }
            waitForIdle()

            runOnIdle {
                with(formScope) { assertEquals(MISMATCH_ERROR, combined.error) }
            }
        }

    @Test
    fun reset_applies_the_current_initial_value_without_marking_the_field_dirty() =
        runCompositionTest {
            val field = Field.Text("name")
            val state = FormState()
            var initialValue by mutableStateOf("first")
            lateinit var formScope: FormScope

            setContent {
                Form(state = state) {
                    ValidationEffect(field, initialValue = initialValue, validators = emptyList())
                    SideEffect { formScope = this }
                }
            }
            waitForIdle()

            runOnIdle {
                with(formScope) {
                    assertEquals("first", field.value)
                    assertFalse(field.isDirty)
                    field.value = "edited"
                    assertTrue(field.isDirty)
                }
                initialValue = "current"
            }
            waitForIdle()

            runOnIdle {
                with(formScope) {
                    assertEquals("edited", field.value)
                    reset()
                }
            }
            waitForIdle()

            runOnIdle {
                with(formScope) {
                    assertEquals("current", field.value)
                    assertFalse(field.isDirty)
                }
            }
        }

    @Test
    fun reset_revalidates_an_invalid_field_with_the_same_initial_value() = runCompositionTest {
        val field = Field.Text("name")
        lateinit var formScope: FormScope

        setContent {
            Form {
                ValidationEffect(
                    field,
                    initialValue = "",
                    validators = listOf(RequiredValidator(VALIDATION_ERROR))
                )
                SideEffect { formScope = this }
            }
        }
        waitForIdle()

        runOnIdle {
            with(formScope) {
                assertEquals(VALIDATION_ERROR, field.error)
                reset()
                assertNull(field.error)
            }
        }
        waitForIdle()

        runOnIdle {
            with(formScope) {
                assertEquals("", field.value)
                assertEquals(VALIDATION_ERROR, field.error)
            }
        }
    }

    @Test
    fun a_value_change_replaces_an_external_error_with_validation_result() = runCompositionTest {
        val field = Field.Text("email")
        lateinit var formScope: FormScope

        setContent {
            Form {
                ValidationEffect(
                    field,
                    initialValue = "",
                    validators = listOf(RequiredValidator(VALIDATION_ERROR))
                )
                SideEffect { formScope = this }
            }
        }
        waitForIdle()

        runOnIdle {
            with(formScope) {
                field.error = EXTERNAL_ERROR
                field.value = "valid"
            }
        }
        waitForIdle()

        runOnIdle {
            with(formScope) {
                assertNull(field.error)
            }
        }
    }

    @Test
    fun removing_and_readding_validation_preserves_value_and_dirty_state_but_not_hidden_error() =
        runCompositionTest {
            val field = Field.Text("conditional")
            var showField by mutableStateOf(true)
            lateinit var formScope: FormScope

            setContent {
                Form {
                    if (showField) {
                        ValidationEffect(
                            field,
                            initialValue = "",
                            validators = listOf(RequiredValidator(VALIDATION_ERROR))
                        )
                    }
                    SideEffect { formScope = this }
                }
            }
            waitForIdle()

            runOnIdle {
                with(formScope) {
                    field.isDirty = true
                    assertTrue(field in fields)
                    assertEquals(VALIDATION_ERROR, field.error)
                    assertTrue(isValid)
                }
                showField = false
            }
            waitForIdle()

            runOnIdle {
                with(formScope) {
                    assertTrue(field in fields)
                    assertEquals("", field.value)
                    assertTrue(field.isDirty)
                    assertNull(field.error)
                    assertTrue(isValid)
                }
                showField = true
            }
            waitForIdle()

            runOnIdle {
                with(formScope) {
                    assertTrue(field in fields)
                    assertEquals("", field.value)
                    assertTrue(field.isDirty)
                    assertEquals(VALIDATION_ERROR, field.error)
                    assertTrue(isValid)
                }
            }
        }

    @Test
    fun validator_receives_a_read_only_scope_instead_of_the_form_scope() = runCompositionTest {
        val field = Field.Text("scope")
        var receiverWasFormScope: Boolean? = null
        val validator = object : Validator<String> {
            override val errorMessage = "invalid"

            override fun ValidationScope.validate(value: String): Boolean {
                receiverWasFormScope = (this as Any) is FormScope
                return true
            }
        }

        setContent {
            Form {
                ValidationEffect(field, initialValue = "value", validators = listOf(validator))
            }
        }
        waitForIdle()

        runOnIdle { assertEquals(false, receiverWasFormScope) }
    }

    @Test
    fun same_callback_edit_is_validated_before_submit() = runCompositionTest {
        val field = Field.Text("immediate")
        lateinit var scope: FormScope
        var submitted = 0
        var invalid = 0
        setContent {
            Form {
                ValidationEffect(field, "valid", listOf(RequiredValidator(VALIDATION_ERROR)))
                SideEffect { scope = this }
            }
        }
        waitForIdle()
        runOnIdle {
            with(scope) {
                field.value = ""
                submit(onInvalid = { invalid++ }) { submitted++ }
                assertEquals(VALIDATION_ERROR, field.error)
                assertEquals(0, submitted)
                assertEquals(1, invalid)
            }
        }
    }

    @Test
    fun same_callback_correction_submits_and_queued_effect_preserves_new_rejection() = runCompositionTest {
        val field = Field.Text("email")
        lateinit var scope: FormScope
        var submitted = 0
        setContent {
            Form {
                ValidationEffect(field, "", listOf(RequiredValidator(VALIDATION_ERROR)))
                SideEffect { scope = this }
            }
        }
        waitForIdle()
        runOnIdle {
            with(scope) {
                field.value = "corrected"
                submit {
                    submitted++
                    field.error = EXTERNAL_ERROR
                }
                assertEquals(1, submitted)
            }
        }
        waitForIdle()
        runOnIdle { with(scope) { assertEquals(EXTERNAL_ERROR, field.error) } }
    }

    @Test
    fun unchanged_external_error_blocks_but_changed_input_replaces_it() = runCompositionTest {
        val field = Field.Text("email")
        lateinit var scope: FormScope
        var submitted = 0
        var invalid = 0
        setContent {
            Form {
                ValidationEffect(field, "valid", listOf(RequiredValidator(VALIDATION_ERROR)))
                SideEffect { scope = this }
            }
        }
        waitForIdle()
        runOnIdle {
            with(scope) {
                field.error = EXTERNAL_ERROR
                submit(onInvalid = { invalid++ }) { submitted++ }
                assertEquals(EXTERNAL_ERROR, field.error)
                assertEquals(1, invalid)
                assertEquals(0, submitted)
                field.value = "changed"
                submit(onInvalid = { invalid++ }) { submitted++ }
                assertNull(field.error)
                assertEquals(1, invalid)
                assertEquals(1, submitted)
            }
        }
    }

    @Test
    fun same_callback_dependency_edits_use_one_current_snapshot() = runCompositionTest {
        val first = Field.Text("first")
        val second = Field.Text("second")
        lateinit var scope: FormScope
        var submitted = 0
        setContent {
            Form {
                ValidationEffect(first, "old", listOf(FieldEqualsValidator(MISMATCH_ERROR, second)))
                ValidationEffect(second, "old", listOf(FieldEqualsValidator(MISMATCH_ERROR, first)))
                SideEffect { scope = this }
            }
        }
        waitForIdle()
        runOnIdle {
            with(scope) {
                first.value = "new"
                second.value = "new"
                submit { submitted++ }
                assertNull(first.error)
                assertNull(second.error)
                assertEquals(1, submitted)
                first.value = "different"
                submit(onInvalid = { submitted-- }) { error("Must be invalid") }
                assertEquals(MISMATCH_ERROR, first.error)
                assertEquals(MISMATCH_ERROR, second.error)
                assertEquals(0, submitted)
            }
        }
    }

    @Test
    fun thrown_validator_does_not_partially_commit_pending_errors_or_submit_flags() = runCompositionTest {
        val first = Field.Text("first")
        val second = Field.Text("second")
        lateinit var scope: FormScope
        var callbacks = 0
        val throwing = object : Validator<String> {
            override val errorMessage = "unused"
            override fun ValidationScope.validate(value: String): Boolean {
                if (value == "throw") error("Validator failed")
                return true
            }
        }
        setContent {
            Form {
                ValidationEffect(first, "valid", listOf(RequiredValidator(VALIDATION_ERROR)))
                ValidationEffect(second, "valid", listOf(throwing))
                SideEffect { scope = this }
            }
        }
        waitForIdle()
        runOnIdle {
            with(scope) {
                first.error = EXTERNAL_ERROR
                first.value = ""
                second.value = "throw"
                first.isDirty = false
                second.isDirty = false
                kotlin.test.assertFailsWith<IllegalStateException> {
                    submit(onInvalid = { callbacks++ }) {
                        callbacks++
                    }
                }
                assertEquals(0, callbacks)
                assertEquals(EXTERNAL_ERROR, first.error)
                assertNull(second.error)
                assertFalse(first.isDirty)
                assertFalse(second.isDirty)
                assertFalse(shouldShowErrors)
                // Restore before the queued reactive effect runs.
                second.value = "valid"
            }
        }
    }

    @Test
    fun submit_after_reset_before_recomposition_skips_uninitialized_fields() = runCompositionTest {
        val field = Field.Text("required")
        lateinit var scope: FormScope
        var initial by mutableStateOf("old")
        var submitted = 0
        setContent {
            Form {
                ValidationEffect(field, initial, listOf(RequiredValidator(VALIDATION_ERROR)))
                SideEffect { scope = this }
            }
        }
        waitForIdle()
        runOnIdle {
            with(scope) {
                initial = "current"
                reset()
                submit { submitted++ }
                assertEquals(1, submitted)
                assertTrue(fields.isEmpty())
                assertFalse(field.isDirty)
            }
        }
        waitForIdle()
        runOnIdle {
            with(scope) {
                assertEquals("current", field.value)
                assertFalse(field.isDirty)
            }
        }
    }

    @Test
    fun removed_effect_is_not_flushed_and_replaced_state_owns_its_validation() = runCompositionTest {
        val field = Field.Text("conditional")
        var visible by mutableStateOf(true)
        var state by mutableStateOf(FormState())
        lateinit var scope: FormScope
        setContent {
            Form(state) {
                if (visible) ValidationEffect(field, "", listOf(RequiredValidator(VALIDATION_ERROR)))
                SideEffect { scope = this }
            }
        }
        waitForIdle()
        val oldScope = scope
        runOnIdle {
            with(scope) { field.isDirty = true }
            visible = false
        }
        waitForIdle()
        runOnIdle {
            with(scope) {
                submit { assertEquals("", field.value) }
                assertNull(field.error)
                assertTrue(field.isDirty)
            }
            visible = true
            state = FormState()
        }
        waitForIdle()
        runOnIdle {
            with(oldScope) { submit { assertNull(field.error) } }
            with(scope) {
                submit(onInvalid = {}) { error("New state must be invalid") }
                assertEquals(VALIDATION_ERROR, field.error)
            }
        }
    }

    @Test
    fun missing_dependency_is_skipped_during_submit_and_success_may_reset() = runCompositionTest {
        val field = Field.Text("confirmation")
        val absent = Field.Text("absent")
        lateinit var scope: FormScope
        setContent {
            Form {
                ValidationEffect(field, "old", listOf(FieldEqualsValidator(MISMATCH_ERROR, absent)))
                SideEffect { scope = this }
            }
        }
        waitForIdle()
        runOnIdle {
            with(scope) {
                field.value = "changed"
                submit {
                    assertNull(field.error)
                    reset()
                }
                assertTrue(fields.isEmpty())
                assertFalse(shouldShowErrors)
            }
        }
    }

    @Test
    fun disposing_a_duplicate_declaration_does_not_remove_the_other_registration() = runCompositionTest {
        val field = Field.Text("shared")
        var showFirst by mutableStateOf(true)
        lateinit var scope: FormScope
        setContent {
            Form {
                if (showFirst) {
                    androidx.compose.runtime.key("first") {
                        ValidationEffect(field, "valid", emptyList())
                    }
                }
                androidx.compose.runtime.key("second") {
                    ValidationEffect(field, "valid", listOf(RequiredValidator(VALIDATION_ERROR)))
                }
                SideEffect { scope = this }
            }
        }
        waitForIdle()
        runOnIdle {
            with(scope) { field.error = EXTERNAL_ERROR }
            showFirst = false
        }
        waitForIdle()
        runOnIdle {
            with(scope) {
                assertEquals(EXTERNAL_ERROR, field.error)
                field.value = ""
                submit(onInvalid = {}) { error("Remaining registration must validate") }
                assertEquals(VALIDATION_ERROR, field.error)
            }
        }
    }

    @Test
    fun submit_before_recomposition_uses_registered_validators_then_replacement_revalidates() = runCompositionTest {
        val field = Field.Text("rules")
        var validators by mutableStateOf<List<Validator<String>>>(emptyList())
        lateinit var scope: FormScope
        var submitted = 0
        setContent {
            Form {
                ValidationEffect(field, "", validators)
                SideEffect { scope = this }
            }
        }
        waitForIdle()
        runOnIdle {
            validators = listOf(RequiredValidator(VALIDATION_ERROR))
            with(scope) {
                submit { submitted++ }
                assertEquals(1, submitted)
            }
        }
        waitForIdle()
        runOnIdle {
            with(scope) {
                submit(onInvalid = {}) { error("Replaced validators must block") }
                assertEquals(VALIDATION_ERROR, field.error)
            }
        }
    }

    @Test
    fun pending_results_are_computed_before_any_error_is_applied() = runCompositionTest {
        val first = Field.Text("first")
        val second = Field.Text("second")
        lateinit var scope: FormScope
        var observedError: String? = null
        val observesFirst = object : Validator<String> {
            override val errorMessage = "unused"
            override fun ValidationScope.validate(value: String): Boolean {
                observedError = with(scope) { first.error }
                return true
            }
        }
        setContent {
            Form {
                ValidationEffect(first, "valid", listOf(RequiredValidator(VALIDATION_ERROR)))
                ValidationEffect(second, "valid", listOf(observesFirst))
                SideEffect { scope = this }
            }
        }
        waitForIdle()
        runOnIdle {
            with(scope) {
                first.error = EXTERNAL_ERROR
                first.value = ""
                second.value = "changed"
                submit(onInvalid = {}) { error("Required field must block") }
                assertEquals(EXTERNAL_ERROR, observedError)
                assertEquals(VALIDATION_ERROR, first.error)
            }
        }
    }

    private fun runCompositionTest(
        block: suspend CompositionFixture.() -> Unit
    ): TestResult = runTest {
        val frameClock = BroadcastFrameClock()
        val effectDispatcher = StandardTestDispatcher(testScheduler)
        val recomposer = Recomposer(effectDispatcher + frameClock)
        val composition = Composition(UnitApplier(), recomposer)
        val recomposerJob = backgroundScope.launch(
            UnconfinedTestDispatcher(testScheduler) + frameClock
        ) {
            recomposer.runRecomposeAndApplyChanges()
        }
        val fixture = CompositionFixture(
            scope = this,
            composition = composition,
            frameClock = frameClock,
            recomposerJob = recomposerJob
        )

        try {
            fixture.block()
        } finally {
            composition.dispose()
            recomposer.cancel()
            recomposerJob.cancel()
        }
    }

    private class CompositionFixture(
        private val scope: TestScope,
        private val composition: Composition,
        private val frameClock: BroadcastFrameClock,
        @Suppress("unused") private val recomposerJob: Job
    ) {
        fun setContent(content: @Composable () -> Unit) {
            composition.setContent(content)
        }

        fun runOnIdle(block: () -> Unit) {
            settle()
            block()
        }

        fun waitForIdle() {
            settle()
        }

        private fun settle() {
            repeat(3) { frame ->
                Snapshot.sendApplyNotifications()
                frameClock.sendFrame((scope.testScheduler.currentTime + frame) * 1_000_000)
                scope.runCurrent()
            }
        }
    }

    private class UnitApplier : AbstractApplier<Unit>(Unit) {
        override fun insertBottomUp(index: Int, instance: Unit) = Unit

        override fun insertTopDown(index: Int, instance: Unit) = Unit

        override fun move(from: Int, to: Int, count: Int) = Unit

        override fun onClear() = Unit

        override fun remove(index: Int, count: Int) = Unit
    }

    private companion object {
        const val EXTERNAL_ERROR = "Email is already registered"
        const val VALIDATION_ERROR = "Email is required"
        const val MISMATCH_ERROR = "Values do not match"
    }
}
