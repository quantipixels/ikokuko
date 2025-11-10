package com.quantipixels.ikokuko

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue

/**
 * A composable container that manages a [FormState] and exposes a [FormScope]
 * for building forms declaratively.
 *
 * Use [ValidationEffect] inside the form to attach validators to fields.
 * The provided [FormScope] gives access to helper methods like [FormScope.submit]
 * and [FormScope.reset].
 *
 * @param state The [FormState] instance backing this form.
 * @param onSubmit Called when [FormScope.submit] is triggered and the form is valid.
 * @param content The form body, scoped to [FormScope].
 */
@Composable
fun Form(
    state: FormState = remember { FormState() },
    onSubmit: FormScope.() -> Unit,
    content: @Composable FormScope.() -> Unit
) {
    val currentOnSubmit = rememberUpdatedState(onSubmit)
    val scope = remember(state) {
        FormScope(state) { currentOnSubmit.value(this) }
    }
    scope.content()
}

/**
 * Holds field values, validation errors, and visibility flags for a form.
 *
 * Acts as the single source of truth for all form data and validation state.
 * Can be remembered in a composable or hoisted to persist across navigation.
 *
 * @param shouldShowErrors Whether validation errors should be visible initially.
 */
@Stable
class FormState(shouldShowErrors: Boolean = false) {

    internal val values = mutableStateMapOf<Field<*>, Any>()

    /**
     * Validation errors keyed by field name.
     * Using the field name (rather than the instance) makes errors stable
     * even if field objects are recreated.
     */
    internal val errors = mutableStateMapOf<String, String>()

    /**
     * Controls when validation errors become visible.
     * Validation still runs reactively regardless of this flag.
     */
    var shouldShowErrors by mutableStateOf(shouldShowErrors)

    /** True if the entire form is valid or errors are hidden. */
    val isValid: Boolean
        get() = !shouldShowErrors || errors.isEmpty()

    /**
     * Clears all field values and hides validation errors.
     *
     * After reset, all [ValidationEffect] composables in the form will automatically
     * reinitialize their associated [Field]s to their provided default values on the next
     * recomposition.
     *
     * This effectively restores the form to its initial state.
     */
    fun reset() {
        values.clear()
        shouldShowErrors = false
    }
}