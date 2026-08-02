package com.quantipixels.ikokuko

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect

/**
 * Provides typed field data and form actions inside a [Form] composition.
 */
@Suppress("UNCHECKED_CAST")
class FormScope internal constructor(
    internal val state: FormState,
    private val onSubmit: FormScope.() -> Unit
) {
    internal val validationScope: ValidationScope = StateValidationScope(this)

    /** All initialized fields in this form. */
    val fields: Set<Field<*>>
        get() = state.values.keys

    /** Returns `true` when this field has an initialized value. */
    val Field<*>.isInitialized: Boolean
        get() = this in state.values

    /**
     * The current value of this field.
     *
     * A changed value marks an initialized field as dirty. Initial assignment by
     * [ValidationEffect] bypasses this setter and keeps the field pristine.
     */
    var <T : Any> Field<T>.value: T
        get() = state.values[this] as? T
            ?: error(
                "Field '$name' accessed before initialization. " +
                    "Call ValidationEffect(...) for this field first."
            )
        set(value) {
            val wasInitialized = isInitialized
            val previousValue = state.values[this]
            if (!wasInitialized || previousValue != value) {
                state.values[this] = value
                if (wasInitialized) isDirty = true
            }
        }

    /** Controls whether this field is marked as dirty. */
    var Field<*>.isDirty: Boolean
        get() = this in state.dirtyFields
        set(value) {
            if (value) {
                if (!isDirty) state.dirtyFields.add(this)
            } else {
                state.dirtyFields.remove(this)
            }
        }

    /** The current stored error. Error visibility does not change this value. */
    var Field<*>.error: String?
        get() = state.errors[name]
        set(value) {
            if (value == null) state.errors.remove(name) else state.errors[name] = value
        }

    /** Returns `true` when this field has no stored error. */
    val Field<*>.isValid: Boolean
        get() = name !in state.errors

    /** Returns `true` when the UI must display this field's stored error. */
    val Field<*>.shouldDisplayError: Boolean
        get() = isDirty && state.shouldShowErrors && !isValid

    /** Returns `true` when stored errors do not currently block the form. */
    val isValid: Boolean
        get() = state.isValid

    /** Controls whether stored errors affect form validity and can be shown for dirty fields. */
    var shouldShowErrors: Boolean
        get() = state.shouldShowErrors
        set(value) {
            state.shouldShowErrors = value
        }

    /**
     * Marks all initialized fields as dirty and enables error display.
     * Calls the form submit callback only when the form is valid.
     */
    fun submit(onInvalid: () -> Unit = {}) {
        fields.forEach { it.isDirty = true }
        state.shouldShowErrors = true
        if (state.isValid) onSubmit() else onInvalid()
    }

    /** Clears all state in this form. */
    fun reset() = state.reset()
}

private class StateValidationScope(
    private val scope: FormScope
) : ValidationScope {
    override val <T : Any> Field<T>.value: T
        get() = with(scope) { value }
}

/**
 * Initializes [field] once and validates it when its value, validators, or declared
 * dependency values change. Use one ValidationEffect for each field in a form.
 */
@Composable
fun <T : Any> FormScope.ValidationEffect(
    field: Field<T>,
    initialValue: T,
    validators: List<Validator<T>>
) {
    if (!field.isInitialized) state.values[field] = initialValue

    DisposableEffect(field) {
        onDispose { field.error = null }
    }

    val value = field.value
    val dependencyValues = validators
        .flatMap(Validator<*>::dependencies)
        .distinct()
        .mapNotNull(state.values::get)

    LaunchedEffect(value, validators, dependencyValues, state.resetKey) {
        field.error = validators.firstOrNull { validator ->
            validator.dependencies.all { it.isInitialized } &&
                !with(validator) { validationScope.validate(value) }
        }?.errorMessage
    }
}

/** Initializes and validates [field] before composing [content]. */
@Composable
fun <T : Any> FormScope.FormField(
    field: Field<T>,
    initialValue: T,
    validators: List<Validator<T>> = emptyList(),
    content: @Composable () -> Unit
) {
    ValidationEffect(field, initialValue, validators)
    content()
}
