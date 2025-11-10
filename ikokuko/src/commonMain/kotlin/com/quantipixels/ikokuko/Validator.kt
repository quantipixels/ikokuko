package com.quantipixels.ikokuko

/** Matches standard email formats: local@domain */
private val EmailRegex =
    Regex("[a-zA-Z0-9+._%\\-]{1,256}@[a-zA-Z0-9][a-zA-Z0-9\\-]{0,64}(\\.[a-zA-Z0-9][a-zA-Z0-9\\-]{0,25})+")

/** Matches E.164-style phone numbers (e.g., +14155552671, +2348012345678) */
private val PhoneNumberRegex = Regex("^\\+[1-9]\\d{0,2}[1-9]\\d{3,11}$")

/**
 * Contract for validating values of type [T].
 */
interface Validator<T> {
    /** The error message returned when [validate] fails. */
    val errorMessage: String

    fun validate(value: T): Boolean
}

/** Ensures a string is not blank. */
class RequiredValidator(override val errorMessage: String) : Validator<String> {
    override fun validate(value: String) = value.isNotBlank()
}

/**
 * Base class for numeric validators that operate on [String] input.
 *
 * This handles the common logic of parsing the input, blank-value handling,
 * and delegating the actual numeric comparison to [predicate].
 *
 * Typical implementations include validators for minimum, maximum, or range checks.
 *
 * @param T The numeric type to validate (e.g., [Int], [Float], [Double]).
 *
 * @see Validator
 */
abstract class NumericValidator<T> : Validator<String>
        where T : Number, T : Comparable<T> {

    /** Whether a blank input should be considered valid. */
    abstract val allowEmpty: Boolean

    /** Converts the raw string into the target numeric type, or `null` if parsing fails. */
    abstract val transform: (value: String) -> T?

    override fun validate(value: String): Boolean =
        if (value.isBlank()) {
            allowEmpty
        } else {
            transform(value)?.let(::predicate) ?: false
        }

    /** Performs the actual numeric comparison or check for the parsed value. */
    protected abstract fun predicate(value: T): Boolean
}

/** Validates that a string represents an integer greater than or equal to [min]. */
class MinValidator<T>(
    override val errorMessage: String,
    override val allowEmpty: Boolean = false,
    private val min: T,
    override val transform: (String) -> T?
) : NumericValidator<T>() where T : Number, T : Comparable<T> {
    override fun predicate(value: T) = value >= min
}

/** Validates that a string represents an integer less than or equal to [max]. */
class MaxValidator<T>(
    override val errorMessage: String,
    override val allowEmpty: Boolean = false,
    private val max: T,
    override val transform: (String) -> T?
) : NumericValidator<T>() where T : Number, T : Comparable<T> {
    override fun predicate(value: T) = value <= max
}

/**
 * Validates that a numeric string is within [min]..[max].
 *
 * @throws IllegalArgumentException if [min] is greater than [max] or either is negative.
 * */
class RangeValidator<T>(
    override val errorMessage: String,
    override val allowEmpty: Boolean = false,
    private val min: T,
    private val max: T,
    override val transform: (String) -> T?
) : NumericValidator<T>() where T : Number, T : Comparable<T> {
    init {
        require(min <= max) { "min ($min) must not be greater than max ($max)" }
    }

    override fun predicate(value: T) = value in min..max
}

/** Validates that a string’s length is exactly [length] characters. */
class LengthValidator(
    override val errorMessage: String,
    private val length: Int
) : Validator<String> {
    override fun validate(value: String) = value.length == length
}

/** Validates that a string’s length is at least [length] characters. */
class MinLengthValidator(
    override val errorMessage: String,
    private val length: Int
) : Validator<String> {
    override fun validate(value: String) = value.length >= length
}

/** Validates that a string’s length does not exceed [length] characters. */
class MaxLengthValidator(
    override val errorMessage: String,
    private val length: Int
) : Validator<String> {
    override fun validate(value: String) = value.length <= length
}

/** Validates that the entire string matches the given [pattern]. */
open class MatchPatternValidator(
    override val errorMessage: String,
    private val pattern: Regex
) : Validator<String> {
    override fun validate(value: String) = pattern.matches(value)
}

/** Validates that the string contains a match for the given [pattern]. */
open class ContainsPatternValidator(
    override val errorMessage: String,
    private val pattern: Regex
) : Validator<String> {
    override fun validate(value: String) = pattern.containsMatchIn(value)
}

/** Validates that a string matches a standard email pattern. */
class EmailValidator(errorMessage: String) : MatchPatternValidator(errorMessage, EmailRegex)

/** Validates that a string matches an international phone number pattern (E.164). */
class PhoneNumberValidator(errorMessage: String) :
    MatchPatternValidator(errorMessage, PhoneNumberRegex)

/**
 * Validates that a value is equal to a given [expected] value.
 *
 * Useful for confirming matching inputs (e.g., password confirmation).
 */
class EqualsValidator<T>(
    override val errorMessage: String,
    private val expected: () -> T
) : Validator<T> {
    override fun validate(value: T) = value == expected()
}

/** Validates that a value is not equal to a given [unwanted] value. */
class NotEqualsValidator<T>(
    override val errorMessage: String,
    private val unwanted: T
) : Validator<T> {
    override fun validate(value: T) = value != unwanted
}

/** Validates that a selection is not empty. */
class NonEmptySelectionValidator<T>(
    override val errorMessage: String
) : Validator<List<T>> {
    override fun validate(value: List<T>) = value.isNotEmpty()
}

/** Validates that a selection contains at least [min] items. */
class MinSelectionValidator<T>(
    override val errorMessage: String,
    private val min: Int
) : Validator<List<T>> {
    override fun validate(value: List<T>) = value.size >= min
}

/** Validates that a selection contains at most [max] items. */
class MaxSelectionValidator<T>(
    override val errorMessage: String,
    private val max: Int
) : Validator<List<T>> {
    override fun validate(value: List<T>) = value.size <= max
}

/** Validates that a selection contains exactly [size] items. */
class ExactSelectionValidator<T>(
    override val errorMessage: String,
    private val size: Int
) : Validator<List<T>> {
    override fun validate(value: List<T>) = value.size == size
}

/**
 * Validates that a selection contains between [min] and [max] items (inclusive).
 *
 * Useful for cases where a user must choose within a range, such as "Select 2–5 options".
 *
 * @throws IllegalArgumentException if [min] is greater than [max] or either is negative.
 */
class SelectionRangeValidator<T>(
    override val errorMessage: String,
    private val min: Int,
    private val max: Int
) : Validator<List<T>> {
    init {
        require(min >= 0) { "min must not be negative (was $min)" }
        require(min <= max) { "min ($min) must not be greater than max ($max)" }
    }

    override fun validate(value: List<T>) = value.size in min..max
}