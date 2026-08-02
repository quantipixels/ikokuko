package com.quantipixels.ikokuko

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
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
 * @param shouldShowErrors Whether stored errors should affect form validity and be visible initially.
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
     * they share one map entry. A type cast error can occur when retrieving the value through
     * [FormScope.value]. [ValidationEffect] does not detect duplicate names.
     *
     * @throws ClassCastException if multiple fields share the same [Field.name] but declare
     * different types, causing a type mismatch on value retrieval via [FormScope.value].
     */
    internal val values = mutableStateMapOf<Field<*>, Any>()

    /**
     * Tracks all [Field]s that have been modified since form initialization or reset.
     *
     * A field is added to this list the first time its initialized value changes.
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

    /** Changes when reset must restart validation for otherwise equal inputs. */
    internal var resetKey by mutableStateOf(0)
        private set

    /**
     * Controls whether stored errors affect form validity and can be shown for dirty fields.
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
     * Indicates whether stored errors currently block the form.
     *
     * Returns `true` while error reporting is disabled. When error reporting is enabled,
     * returns `true` only when the form has no stored validation or external errors.
     */
    val isValid: Boolean
        get() = !shouldShowErrors || errors.isEmpty()

    /**
     * Clears all field values, errors, dirty state, and error visibility.
     *
     * After reset, all [ValidationEffect] composables in the form will automatically
     * reinitialize their associated [Field]s to their provided initial values and mark them
     * as pristine on the next recomposition.
     *
     * This fully restores the form to its initial state.
     */
    fun reset() {
        dirtyFields.clear()
        values.clear()
        errors.clear()
        shouldShowErrors = false
        resetKey++
    }

    companion object {
        /** Saves form values supported by the current platform save registry. */
        val Saver: Saver<FormState, Any> = formStateSaver(DefaultFormValuesSaver)

        /** Creates a form-state saver that transforms the complete field-name-to-value map. */
        fun <Saveable : Any> saver(
            valuesSaver: Saver<Map<String, Any>, Saveable>
        ): Saver<FormState, Any> = formStateSaver(valuesSaver)
    }
}

private val DefaultFormValuesSaver: Saver<Map<String, Any>, Any> = listSaver(
    save = { values ->
        buildList {
            add(values.size)
            values.forEach { (name, value) ->
                add(name)
                add(value)
            }
        }
    },
    restore = { saved ->
        val size = saved.first() as Int
        buildMap {
            repeat(size) { index ->
                val offset = 1 + index * 2
                put(saved[offset] as String, saved[offset + 1])
            }
        }
    }
)

private fun <Saveable : Any> formStateSaver(
    valuesSaver: Saver<Map<String, Any>, Saveable>
): Saver<FormState, Any> = listSaver(
    save = { state ->
        val savedValues = with(valuesSaver) {
            save(state.values.entries.associate { (field, value) -> field.name to value })
        } ?: return@listSaver emptyList()

        buildList {
            add(savedValues)
            add(state.shouldShowErrors)
            add(state.dirtyFields.size)
            state.dirtyFields.forEach { add(it.name) }
            add(state.errors.size)
            state.errors.forEach { (name, error) ->
                add(name)
                add(error)
            }
        }
    },
    restore = { saved ->
        var index = 0
        @Suppress("UNCHECKED_CAST")
        val restoredValues = valuesSaver.restore(saved[index++] as Saveable)
            ?: return@listSaver null
        val state = FormState(shouldShowErrors = saved[index++] as Boolean)

        restoredValues.forEach { (name, value) ->
            state.values[Field<Any>(name)] = value
        }

        val dirtyCount = saved[index++] as Int
        repeat(dirtyCount) {
            state.dirtyFields.add(Field<Any>(saved[index++] as String))
        }

        val errorCount = saved[index++] as Int
        repeat(errorCount) {
            state.errors[saved[index++] as String] = saved[index++] as String
        }
        state
    }
)

/**
 * Remembers a [FormState] through compatible platform state restoration.
 *
 * Every field value must be supported by the platform save registry. For custom field values,
 * pass a saver created with [FormState.saver] or replace [saver] completely.
 */
@Composable
fun rememberSaveableFormState(
    shouldShowErrors: Boolean = false,
    saver: Saver<FormState, out Any> = FormState.Saver
): FormState = rememberSaveable(saver = saver) {
    FormState(shouldShowErrors)
}
