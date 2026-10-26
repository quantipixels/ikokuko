package com.quantipixels.ikokuko

private class SnapshotValidationScope(
    private val values: Map<Field<*>, Any>
) : ValidationScope {
    @Suppress("UNCHECKED_CAST")
    override val <T : Any> Field<T>.value: T
        get() = values[this] as? T ?: error("Field '$name' accessed before initialization")
}

internal data class ValidationKey(
    val value: Any,
    val validators: List<Validator<*>>,
    val dependencyValues: List<Any>,
    val resetKey: Int
)

// Both effect restarts and completed-validation keys must observe the same dependencies.
internal fun dependencyValues(
    validators: List<Validator<*>>,
    values: Map<Field<*>, Any>
): List<Any> = validators
    .flatMap { it.dependencies }
    .distinct()
    .mapNotNull(values::get)

internal class ValidationRegistration<T : Any>(val field: Field<T>) {
    var validators: List<Validator<T>> = emptyList()
    var evaluatedKey: ValidationKey? = null

    fun key(values: Map<Field<*>, Any>, resetKey: Int): ValidationKey? {
        val value = values[field] ?: return null

        return ValidationKey(
            value = value,
            validators = validators,
            dependencyValues = dependencyValues(validators, values),
            resetKey = resetKey
        )
    }

    @Suppress("UNCHECKED_CAST")
    fun pending(values: Map<Field<*>, Any>, resetKey: Int): PendingValidation? {
        val key = key(values, resetKey) ?: return null
        if (key == evaluatedKey) return null

        val scope = SnapshotValidationScope(values)
        val firstFailure = validators.firstOrNull { validator ->
            val dependenciesAvailable = validator.dependencies.all { it in values }
            if (!dependenciesAvailable) return@firstOrNull false

            val valid = with(validator) { scope.validate(key.value as T) }
            !valid
        }

        return PendingValidation(this, key, firstFailure?.errorMessage)
    }
}

internal class PendingValidation(
    private val registration: ValidationRegistration<*>,
    private val key: ValidationKey,
    private val error: String?
) {
    fun apply(state: FormState) {
        if (error == null) {
            state.errors.remove(registration.field.name)
        } else {
            state.errors[registration.field.name] = error
        }

        registration.evaluatedKey = key
    }
}
