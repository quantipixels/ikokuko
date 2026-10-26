package com.quantipixels.ikokuko

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.remember

/**
 * Provides typed field data and form actions inside a [Form] composition.
 */
@Suppress("UNCHECKED_CAST")
class FormScope internal constructor(
    internal val state: FormState
) {
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
     * Flushes pending validation against one value snapshot, then marks initialized fields dirty
     * and enables error display. Calls exactly one of [onSubmit] or [onInvalid].
     *
     * Unchanged validation inputs retain manual errors. Value and dependency edits in the same
     * callback are included; validators use the currently registered list until recomposition.
     * A validator exception propagates before errors, dirty flags or callbacks are changed here.
     * Callbacks run after validation commits and may reset or resubmit; recursion is caller-owned.
     */
    fun submit(onInvalid: () -> Unit = {}, onSubmit: FormScope.() -> Unit) {
        val snapshot = state.values.toMap()
        val pending = state.registrations.values.mapNotNull { registration ->
            registration.pending(snapshot, state.resetKey)
        }
        pending.forEach { it.apply(state) }

        fields.forEach { it.isDirty = true }
        state.shouldShowErrors = true

        if (state.isValid) onSubmit() else onInvalid()
    }

    /** Clears values, errors and visibility; mounted fields reinitialize on recomposition. */
    fun reset() = state.reset()
}

/**
 * Initializes [field] once and validates it when its value, validators, or declared
 * dependency values change, or after reset. Use one ValidationEffect for each field in a form.
 * Removing it clears its error and keeps its value and dirty flag. Registration updates commit
 * with composition; submit before recomposition uses the previous validator list.
 */
@Composable
fun <T : Any> FormScope.ValidationEffect(
    field: Field<T>,
    initialValue: T,
    validators: List<Validator<T>>
) {
    if (!field.isInitialized) state.values[field] = initialValue

    val registration = remember(state, field) { ValidationRegistration(field) }
    val value = field.value
    val dependencyValues = dependencyValues(validators, state.values)

    DisposableEffect(registration) {
        onDispose {
            if (state.registrations[field.name] === registration) {
                state.registrations.remove(field.name)
                field.error = null
            }
        }
    }

    SideEffect {
        registration.validators = validators.toList()
        state.registrations[field.name] = registration
    }

    LaunchedEffect(registration, value, validators, dependencyValues, state.resetKey) {
        // Submit may already have evaluated these inputs and then received a server rejection.
        if (state.registrations[field.name] === registration) {
            registration.pending(state.values.toMap(), state.resetKey)?.apply(state)
        }
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
