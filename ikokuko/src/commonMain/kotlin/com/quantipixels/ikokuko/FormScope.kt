package com.quantipixels.ikokuko

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect

/**
 * Provides access to all form-related data and actions within a [Form] composition.
 *
 * A [FormScope] exposes extension properties on [Field] for reading and writing values,
 * checking validation results, and interacting with the form lifecycle through [submit]
 * and [reset].
 *
 * It’s created automatically by [Form] and passed to both the form body and onSubmit.
 */
@Suppress("UNCHECKED_CAST")
class FormScope internal constructor(
    private val state: FormState,
    private val onSubmit: FormScope.() -> Unit
) {
    /** True if the [Field] has been initialized and holds a value. */
    val Field<*>.isInitialized: Boolean
        get() = this in state.values.keys

    /**
     * The current value of this [Field].
     *
     * Fields are initialized with their default value through [ValidationEffect].
     * Reading this property before initialization throws an error.
     */
    var <T : Any> Field<T>.value: T
        get() = state.values[this] as? T
            ?: error("Field '$name' accessed before initialization. Call ValidationEffect(...) for this field first.")
        set(value) {
            state.values[this] = value
        }

    /**
     * The current validation error for this [Field], or `null` if none.
     * Errors are visible only when [shouldShowErrors] is `true`.
     */
    var Field<*>.error: String?
        get() = if (state.shouldShowErrors) state.errors[name] else null
        set(value) {
            if (value != null) {
                state.errors[name] = value
            } else {
                state.errors.remove(name)
            }
        }

    /** True if the field is valid or errors are hidden. */
    val Field<*>.isValid: Boolean
        get() = !state.shouldShowErrors || error == null

    /** @see [FormState.isValid] */
    val isValid: Boolean
        get() = state.isValid

    /** @see [FormState.shouldShowErrors] */
    var shouldShowErrors: Boolean
        get() = state.shouldShowErrors
        set(value) {
            state.shouldShowErrors = value
        }

    /**
     * Shows errors and triggers form submission.
     *
     * If the form is valid, [onSubmit] is executed.
     * Otherwise, [onInvalid] is invoked (if provided).
     */
    fun submit(onInvalid: (() -> Unit)? = null) {
        state.shouldShowErrors = true
        if (state.isValid) onSubmit() else onInvalid?.invoke()
    }

    /** @see [FormState.reset] */
    fun reset() = state.reset()
}

/**
 * Attaches validation logic to a [field] within a [FormScope].
 *
 * This composable reacts to changes in the field’s [FormScope.value] property or in [validators],
 * running each validator and updating the field’s [FormScope.error] accordingly.
 *
 * Validation runs continuously, but errors are only visible when
 * [FormState.shouldShowErrors] is `true`.
 *
 * The field is initialized with [default] if it has no current value.
 *
 * @param field The [Field] to validate.
 * @param default The initial value applied if the field is not yet initialized.
 * @param validators The list of [Validator]s used to validate the field’s value.
 *
 * @see Field
 * @see Validator
 * @see FormState.shouldShowErrors
 */
@Composable
fun <T : Any> FormScope.ValidationEffect(
    field: Field<T>,
    default: T,
    validators: List<Validator<T>>
) {
    if (!field.isInitialized) {
        /**
         * Initialize (or reinitialize after reset).
         * Must set default before launching validation; if not initialized,
         * reading field.value in LaunchedEffect would throw.
         */
        field.value = default
    }
    LaunchedEffect(field.value, validators) {
        field.error = validators.firstOrNull { !it.validate(field.value) }?.errorMessage
    }
}