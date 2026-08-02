package com.quantipixels.ikokuko

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class ValidatorTest {

    private val scope = object : ValidationScope {
        override val <T : Any> Field<T>.value: T
            get() = error("This validator must not read a field")
    }

    @Test
    fun requiredValidator_validates_non_blank_text() {
        val validator = RequiredValidator("Cannot be blank")

        assertTrue(validator.test("abc"))
        assertFalse(validator.test("   "))
    }

    @Test
    fun integer_validators_check_bounds_and_invalid_text() {
        val minimum = MinValidator("too small", min = 5)
        val maximum = MaxValidator("too large", max = 10)
        val range = RangeValidator("out of range", min = 1, max = 5)

        assertTrue(minimum.test("5"))
        assertTrue(minimum.test("6"))
        assertFalse(minimum.test("4"))
        assertFalse(minimum.test("5.5"))
        assertFalse(minimum.test("999999999999999999999"))

        assertTrue(maximum.test("10"))
        assertTrue(maximum.test("5"))
        assertFalse(maximum.test("11"))

        assertTrue(range.test("1"))
        assertTrue(range.test("3"))
        assertTrue(range.test("5"))
        assertFalse(range.test("0"))
        assertFalse(range.test("6"))
    }

    @Test
    fun integer_validators_allow_blank_only_when_configured() {
        assertTrue(MinValidator("too small", min = 5, allowEmpty = true).test(""))
        assertFalse(MinValidator("too small", min = 5).test(""))
        assertTrue(MaxValidator("too large", max = 5, allowEmpty = true).test("   "))
        assertTrue(RangeValidator("invalid", min = 1, max = 5, allowEmpty = true).test(""))
    }

    @Test
    fun rangeValidator_rejects_reversed_bounds() {
        assertFailsWith<IllegalArgumentException> {
            RangeValidator("Invalid", min = 5, max = 3)
        }
    }

    @Test
    fun length_validators_check_text_length() {
        assertTrue(LengthValidator("must be 4 chars", 4).test("test"))
        assertFalse(LengthValidator("must be 4 chars", 4).test("testing"))
        assertTrue(MinLengthValidator("too short", 3).test("abcd"))
        assertFalse(MinLengthValidator("too short", 3).test("a"))
        assertTrue(MaxLengthValidator("too long", 5).test("abc"))
        assertFalse(MaxLengthValidator("too long", 5).test("abcdef"))
    }

    @Test
    fun pattern_validators_use_string_patterns() {
        val match = MatchPatternValidator("invalid", "^abc.*")
        val contains = ContainsPatternValidator("invalid", "\\d")

        assertTrue(match.test("abcdef"))
        assertFalse(match.test("zzz"))
        assertTrue(contains.test("ab1c2"))
        assertFalse(contains.test("abc"))
    }

    @Test
    fun checkedValidator_requires_true() {
        val validator = CheckedValidator("must be checked")

        assertTrue(validator.test(true))
        assertFalse(validator.test(false))
    }

    @Test
    fun membership_validators_check_allowed_and_disallowed_values() {
        val allowed = InValidator("Value not allowed", listOf("A", "B", "C"))
        val disallowed = NotInValidator("Value forbidden", listOf("X", "Y", "Z"))

        assertTrue(allowed.test("A"))
        assertFalse(allowed.test("X"))
        assertTrue(disallowed.test("A"))
        assertFalse(disallowed.test("X"))
    }

    @Test
    fun selectionRangeValidator_supports_bounded_and_unbounded_ranges() {
        val nonempty = SelectionRangeValidator("required", min = 1)
        val maximum = SelectionRangeValidator("too many", max = 2)
        val exact = SelectionRangeValidator("exactly two", min = 2, max = 2)

        assertFalse(nonempty.test(emptyList<String>()))
        assertTrue(nonempty.test(listOf("A")))
        assertTrue(nonempty.test(List(1_000) { "$it" }))

        assertTrue(maximum.test(emptyList<String>()))
        assertTrue(maximum.test(listOf("A", "B")))
        assertFalse(maximum.test(listOf("A", "B", "C")))

        assertFalse(exact.test(listOf("A")))
        assertTrue(exact.test(listOf("A", "B")))
        assertFalse(exact.test(listOf("A", "B", "C")))
    }

    @Test
    fun selectionRangeValidator_rejects_invalid_bounds() {
        assertFailsWith<IllegalArgumentException> {
            SelectionRangeValidator("Invalid", min = -1)
        }
        assertFailsWith<IllegalArgumentException> {
            SelectionRangeValidator("Invalid", min = 5, max = 3)
        }
    }

    @Test
    fun selectionInValidator_checks_every_item_and_empty_policy() {
        val allowed = listOf("A", "B", "C")

        assertTrue(SelectionInValidator("invalid", allowed).test(listOf("A", "B")))
        assertFalse(SelectionInValidator("invalid", allowed).test(listOf("A", "Z")))
        assertTrue(
            SelectionInValidator("invalid", allowed, allowEmpty = true)
                .test(emptyList())
        )
        assertFalse(
            SelectionInValidator("invalid", allowed, allowEmpty = false)
                .test(emptyList())
        )
    }

    @Test
    fun first_party_validator_instances_have_structural_equality() {
        val field = Field.Text("other")
        val first = listOf(
            RequiredValidator("required"),
            MinValidator("minimum", min = 1),
            MaxValidator("maximum", max = 2),
            RangeValidator("range", min = 1, max = 2),
            LengthValidator("length", length = 2),
            MinLengthValidator("minimum length", length = 1),
            MaxLengthValidator("maximum length", length = 2),
            MatchPatternValidator("match", "\\d"),
            ContainsPatternValidator("contains", "\\d"),
            CheckedValidator("checked"),
            FieldEqualsValidator("equal", field),
            InValidator("in", listOf("A")),
            NotInValidator("not in", listOf("A")),
            SelectionRangeValidator("selection range", min = 1),
            SelectionInValidator("selection in", listOf("A"))
        )
        val second = first.map { validator ->
            when (validator) {
                is RequiredValidator -> RequiredValidator("required")
                is MinValidator -> MinValidator("minimum", min = 1)
                is MaxValidator -> MaxValidator("maximum", max = 2)
                is RangeValidator -> RangeValidator("range", min = 1, max = 2)
                is LengthValidator -> LengthValidator("length", length = 2)
                is MinLengthValidator -> MinLengthValidator("minimum length", length = 1)
                is MaxLengthValidator -> MaxLengthValidator("maximum length", length = 2)
                is MatchPatternValidator -> MatchPatternValidator("match", "\\d")
                is ContainsPatternValidator -> ContainsPatternValidator("contains", "\\d")
                is CheckedValidator -> CheckedValidator("checked")
                is FieldEqualsValidator<*> -> FieldEqualsValidator("equal", field)
                is InValidator<*> -> InValidator("in", listOf("A"))
                is NotInValidator<*> -> NotInValidator("not in", listOf("A"))
                is SelectionRangeValidator -> SelectionRangeValidator("selection range", min = 1)
                is SelectionInValidator<*> ->
                    SelectionInValidator("selection in", listOf("A"))
                else -> error("Missing equality fixture for $validator")
            }
        }

        assertEquals(first, second)
    }

    @Test
    fun collection_validators_snapshot_mutable_rules_for_behavior_and_equality() {
        val values = mutableListOf("A")
        val allowedBeforeMutation = InValidator("invalid", values)
        val disallowedBeforeMutation = NotInValidator("invalid", values)
        val selectionBeforeMutation = SelectionInValidator("invalid", values)

        values += "B"

        val allowedAfterMutation = InValidator("invalid", values)
        val disallowedAfterMutation = NotInValidator("invalid", values)
        val selectionAfterMutation = SelectionInValidator("invalid", values)

        assertFalse(allowedBeforeMutation.test("B"))
        assertTrue(allowedAfterMutation.test("B"))
        assertTrue(disallowedBeforeMutation.test("B"))
        assertFalse(disallowedAfterMutation.test("B"))
        assertFalse(selectionBeforeMutation.test(listOf("B")))
        assertTrue(selectionAfterMutation.test(listOf("B")))
        assertNotEquals(allowedBeforeMutation, allowedAfterMutation)
        assertNotEquals(disallowedBeforeMutation, disallowedAfterMutation)
        assertNotEquals(selectionBeforeMutation, selectionAfterMutation)
    }

    @Test
    fun validator_is_contravariant() {
        val anyValidator = object : Validator<Any> {
            override val errorMessage = "invalid"

            override fun ValidationScope.validate(value: Any) = value.toString().isNotEmpty()
        }
        val stringValidator: Validator<String> = anyValidator

        assertTrue(stringValidator.test("value"))
    }

    private fun <T> Validator<T>.test(value: T): Boolean = with(this) {
        scope.validate(value)
    }
}
