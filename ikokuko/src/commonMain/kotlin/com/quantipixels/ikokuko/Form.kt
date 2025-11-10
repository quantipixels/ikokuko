package com.quantipixels.ikokuko

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
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
    /**
     * Holds the current values for all registered [Field]s in the form.
     *
     * Each entry maps a [Field] to its latest assigned value.
     * Although fields are used as map keys, their equality is determined
     * by their [Field.name] — meaning multiple field instances that share
     * the same name will point to the same stored value.
     *
     * Warning: If two fields with the same name are defined with different types,
     * a type cast error will occur when retrieving the value through [FormScope.value].
     *
     * @throws ClassCastException if multiple fields share the same [Field.name] but declare
     * different types, causing a type mismatch on value retrieval via [FormScope.value].
     */
    internal val values = mutableStateMapOf<Field<*>, Any>()

    /**
     * Tracks all [Field]s that have been modified since form initialization or reset.
     *
     * A field is added to this list the first time its value changes from its default.
     * This allows the form to distinguish between fields the user has interacted with
     * ("dirty" fields) and those that are still untouched.
     *
     * Validation or UI layers can use this to show errors or feedback only after
     * a field has been modified, improving UX by preventing premature validation.
     *
     * The list is cleared whenever [FormState.reset] is called.
     *
     * @see Field
     * @see FormScope.value
     * @see FormState.reset
     */
    internal val dirtyFields = mutableStateListOf<Field<*>>()

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

    /**
     * Indicates whether any [Field] in the form has been modified
     * since initialization or the last [reset].
     */
    val isDirty: Boolean
        get() = dirtyFields.isNotEmpty()

    /**
     * Indicates whether the form is currently in a valid (non-error) state.
     *
     * Validation errors are ignored while [shouldShowErrors] is `false`,
     * so this property represents perceived validity according to the
     * current error visibility policy rather than strict validation state.
     */
    val isValid: Boolean
        get() = !shouldShowErrors || !isDirty || errors.isEmpty()

    /**
     * Clears all field values and hides validation errors.
     *
     * After reset, all [ValidationEffect] composables in the form will automatically
     * reinitialize their associated [Field]s to their provided default values and mark them
     * as pristine on the next recomposition.
     *
     * This fully restores the form to its initial state.
     */
    fun reset() {
        dirtyFields.clear()
        values.clear()
        shouldShowErrors = false
    }
}