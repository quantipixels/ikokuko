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
    /** All [Field]s currently registered in this form. */
    val fields: Set<Field<*>>
        get() = state.values.keys

    /** True if the [Field] has been initialized and holds a value. */
    val Field<*>.isInitialized: Boolean
        get() = this in fields

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
     * Indicates whether this [Field] has been modified since it was initialized or last reset.
     *
     * A field becomes dirty when its value changes from the default provided in
     * [ValidationEffect], or when [markAsDirty] is called manually.
     *
     * This property is typically used to control when validation errors or visual feedback
     * should be displayed for a particular field.
     */
    val <T> Field<T>.isDirty: Boolean
        get() = this in state.dirtyFields

    /**
     * The current validation error message for this [Field], or `null` if none.
     *
     * Prevents premature error display before user interaction or explicit error visibility.
     */
    var Field<*>.error: String?
        get() = if (isDirty && state.shouldShowErrors) state.errors[name] else null
        set(value) {
            if (value != null) {
                state.errors[name] = value
            } else {
                state.errors.remove(name)
            }
        }

    /** Indicates whether this [Field] is currently valid. */
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
     * Marks this [Field] as dirty, indicating that its value has been modified
     * or interacted with by the user.
     *
     * Normally, fields are automatically marked as dirty when their [FormScope.value]
     * is changed, but this can be invoked manually for custom interaction flows
     * or programmatic updates.
     */
    fun Field<*>.markAsDirty() {
        if (!isDirty) state.dirtyFields.add(this)
    }

    /**
     * Shows errors and triggers form submission.
     *
     * If the form is valid, [onSubmit] is executed.
     * Otherwise, [onInvalid] is invoked (if provided).
     */
    fun submit(onInvalid: (() -> Unit)? = null) {
        fields.forEach { it.markAsDirty() }
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
 * @param default The default value applied when the field is first initialized
 * or reinitialized after a form reset.
 * @param validators The list of [Validator]s used to validate the field’s value.
 */
@Composable
fun <T : Any> FormScope.ValidationEffect(
    field: Field<T>,
    default: T,
    validators: List<Validator<T>>
) {
    // initialize (or reinitialize after reset).
    // Must set default before launching validation; if not initialized,
    // reading field.value in LaunchedEffect would throw.
    if (!field.isInitialized) field.value = default

    // Skip validation until the field value diverges from its default.
    if (field.value != default && !field.isDirty) {
        LaunchedEffect(Unit) { field.markAsDirty() }
    }

    // Always validate; visibility of the error is controlled by Field.error itself.
    LaunchedEffect(field.value, validators) {
        field.error = validators.firstOrNull { !it.validate(field.value) }?.errorMessage
    }
}

/**
 * A convenience composable that automatically attaches [ValidationEffect]
 * to the given [field] before rendering its [content].
 *
 * This ensures that validation, dirty-state tracking, and default value
 * initialization are always applied without requiring the consumer to call
 * [ValidationEffect] manually.
 *
 * Typical usage wraps an input element that binds to field.value,
 * displays field.error, and updates the value as the user types.
 *
 * Example:
 * ```
 * FormField(EmailField, "", listOf(EmailValidator("Invalid email"))) {
 *     OutlinedTextField(
 *         value = EmailField.value,
 *         onValueChange = { EmailField.value = it },
 *         isError = !EmailField.isValid,
 *         supportingText = EmailField.error?.let { { Text(it) } },
 *         label = { Text("Email") }
 *     )
 * }
 * ```
 *
 * @param field The [Field] managed by this input.
 * @param default The default value applied when the field is first initialized
 * or reinitialized after a form reset.
 * @param validators A list of [Validator]s used to validate the field's value.
 * @param content The composable content that displays and interacts with the field.
 */
@Composable
fun <T : Any> FormScope.FormField(
    field: Field<T>,
    default: T,
    validators: List<Validator<T>> = emptyList(),
    content: @Composable () -> Unit
) {
    ValidationEffect(field, default, validators)
    content()
}