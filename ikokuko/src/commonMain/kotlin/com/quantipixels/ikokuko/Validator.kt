package com.quantipixels.ikokuko

/** Provides read-only access to values used during validation. */
interface ValidationScope {
    /** The initialized value of this field. */
    val <T : Any> Field<T>.value: T
}

/**
 * Contract for validating values of type [T].
 *
 * [ValidationEffect] uses validator equality to decide when validation must restart.
 * Custom validators created inline should use structural equality, such as a data class.
 * Avoid lambda properties because recreated lambdas use identity equality.
 */
interface Validator<in T> {
    /** The error message stored when [validate] returns `false`. */
    val errorMessage: String

    /** Fields whose value changes must re-run this validator. */
    val dependencies: List<Field<*>>
        get() = emptyList()

    /** Returns `true` when [value] satisfies this validator. */
    fun ValidationScope.validate(value: T): Boolean
}

/** Ensures that a string is not blank. */
data class RequiredValidator(
    override val errorMessage: String
) : Validator<String> {
    override fun ValidationScope.validate(value: String) = value.isNotBlank()
}

/** Validates that an integer string is greater than or equal to [min]. */
data class MinValidator(
    override val errorMessage: String,
    private val min: Int,
    private val allowEmpty: Boolean = false
) : Validator<String> {
    override fun ValidationScope.validate(value: String): Boolean =
        if (value.isBlank()) allowEmpty else value.toIntOrNull()?.let { it >= min } ?: false
}

/** Validates that an integer string is less than or equal to [max]. */
data class MaxValidator(
    override val errorMessage: String,
    private val max: Int,
    private val allowEmpty: Boolean = false
) : Validator<String> {
    override fun ValidationScope.validate(value: String): Boolean =
        if (value.isBlank()) allowEmpty else value.toIntOrNull()?.let { it <= max } ?: false
}

/** Validates that an integer string is within [min] through [max], inclusive. */
data class RangeValidator(
    override val errorMessage: String,
    private val min: Int,
    private val max: Int,
    private val allowEmpty: Boolean = false
) : Validator<String> {
    init {
        require(min <= max) { "min ($min) must not be greater than max ($max)" }
    }

    override fun ValidationScope.validate(value: String): Boolean =
        if (value.isBlank()) allowEmpty else value.toIntOrNull()?.let { it in min..max } ?: false
}

/** Validates that a string has exactly [length] characters. */
data class LengthValidator(
    override val errorMessage: String,
    private val length: Int
) : Validator<String> {
    override fun ValidationScope.validate(value: String) = value.length == length
}

/** Validates that a string has at least [length] characters. */
data class MinLengthValidator(
    override val errorMessage: String,
    private val length: Int
) : Validator<String> {
    override fun ValidationScope.validate(value: String) = value.length >= length
}

/** Validates that a string has no more than [length] characters. */
data class MaxLengthValidator(
    override val errorMessage: String,
    private val length: Int
) : Validator<String> {
    override fun ValidationScope.validate(value: String) = value.length <= length
}

/** Validates that the complete string matches [pattern]. */
data class MatchPatternValidator(
    override val errorMessage: String,
    private val pattern: String
) : Validator<String> {
    private val regex = Regex(pattern)

    override fun ValidationScope.validate(value: String) = regex.matches(value)
}

/** Validates that a string contains a match for [pattern]. */
data class ContainsPatternValidator(
    override val errorMessage: String,
    private val pattern: String
) : Validator<String> {
    private val regex = Regex(pattern)

    override fun ValidationScope.validate(value: String) = regex.containsMatchIn(value)
}

/** Validates that a Boolean value is `true`. */
data class CheckedValidator(
    override val errorMessage: String
) : Validator<Boolean> {
    override fun ValidationScope.validate(value: Boolean) = value
}

/** Validates that a value equals the value of [field]. */
data class FieldEqualsValidator<T : Any>(
    override val errorMessage: String,
    private val field: Field<T>
) : Validator<T> {
    override val dependencies: List<Field<*>> = listOf(field)

    override fun ValidationScope.validate(value: T) = value == field.value
}

/** Validates that a value is in [allowed]. */
@ConsistentCopyVisibility
data class InValidator<T> private constructor(
    override val errorMessage: String,
    private val allowed: Set<T>
) : Validator<T> {
    constructor(errorMessage: String, allowed: Collection<T>) : this(
        errorMessage = errorMessage,
        allowed = allowed.toSet()
    )

    override fun ValidationScope.validate(value: T) = value in allowed
}

/** Validates that a value is not in [disallowed]. */
@ConsistentCopyVisibility
data class NotInValidator<T> private constructor(
    override val errorMessage: String,
    private val disallowed: Set<T>
) : Validator<T> {
    constructor(errorMessage: String, disallowed: Collection<T>) : this(
        errorMessage = errorMessage,
        disallowed = disallowed.toSet()
    )

    override fun ValidationScope.validate(value: T) = value !in disallowed
}

/** Validates the inclusive selection size from [min] through [max]. */
data class SelectionRangeValidator(
    override val errorMessage: String,
    private val min: Int = 0,
    private val max: Int? = null
) : Validator<List<*>> {
    init {
        require(min >= 0) { "min must not be negative (was $min)" }
        require(max == null || max >= min) {
            "min ($min) must not be greater than max ($max)"
        }
    }

    override fun ValidationScope.validate(value: List<*>) =
        value.size >= min && (max == null || value.size <= max)
}

/** Validates that all selected values are in [allowed]. */
@ConsistentCopyVisibility
data class SelectionInValidator<T> private constructor(
    override val errorMessage: String,
    private val allowed: Set<T>,
    private val allowEmpty: Boolean = false
) : Validator<List<T>> {
    constructor(
        errorMessage: String,
        allowed: Collection<T>,
        allowEmpty: Boolean = false
    ) : this(
        errorMessage = errorMessage,
        allowed = allowed.toSet(),
        allowEmpty = allowEmpty
    )

    override fun ValidationScope.validate(value: List<T>): Boolean =
        if (value.isEmpty()) allowEmpty else value.all { it in allowed }
}
