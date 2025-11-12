package com.quantipixels.ikokuko

import kotlin.test.Test
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ValidatorTest {

    @Test
    fun requiredValidator_passes_on_non_blank() {
        val validator = RequiredValidator("Cannot be blank")
        assertTrue(validator.validate("abc"))
    }

    @Test
    fun requiredValidator_fails_on_blank() {
        val validator = RequiredValidator("Cannot be blank")
        assertFalse(validator.validate("   "))
    }

    @Test
    fun minValidator_allows_equal_or_greater() {
        val validator = MinValidator("too small", min = 5, transform = String::toIntOrNull)
        assertTrue(validator.validate("5"))
        assertTrue(validator.validate("6"))
        assertFalse(validator.validate("4"))
    }

    @Test
    fun maxValidator_allows_equal_or_less() {
        val validator = MaxValidator("too large", max = 10.5, transform = String::toDoubleOrNull)
        assertTrue(validator.validate("10.5"))
        assertTrue(validator.validate("5.6"))
        assertFalse(validator.validate("11.34"))
    }

    @Test
    fun rangeValidator_checks_bounds() {
        val validator = RangeValidator(
            "out of range",
            min = 1f,
            max = 5f,
            transform = String::toFloatOrNull
        )
        assertTrue(validator.validate("1"))
        assertTrue(validator.validate("3"))
        assertFalse(validator.validate("0"))
        assertFalse(validator.validate("6"))
    }

    @Test
    fun rangeValidator_throws_when_min_greater_than_max() {
        assertFailsWith<IllegalArgumentException> {
            RangeValidator("Invalid", min = 5, max = 3, transform = String::toIntOrNull)
        }
    }

    @Test
    fun numericValidators_allow_empty_if_configured() {
        val validator = MinValidator(
            "too small",
            min = 5,
            allowEmpty = true,
            transform = String::toIntOrNull
        )
        assertTrue(validator.validate(""))
    }

    @Test
    fun numericValidators_fail_on_invalid_number() {
        val validator = MinValidator("too small", min = 5, transform = String::toIntOrNull)
        assertFalse(validator.validate("abc"))
    }

    @Test
    fun lengthValidator_passes_when_length_matches() {
        val validator = LengthValidator("must be 4 chars", 4)
        assertTrue(validator.validate("test"))
        assertFalse(validator.validate("testing"))
    }

    @Test
    fun minLengthValidator_passes_on_longer() {
        val validator = MinLengthValidator("too short", 3)
        assertTrue(validator.validate("abcd"))
        assertFalse(validator.validate("a"))
    }

    @Test
    fun maxLengthValidator_passes_on_shorter() {
        val validator = MaxLengthValidator("too long", 5)
        assertTrue(validator.validate("abc"))
        assertFalse(validator.validate("abcdef"))
    }

    @Test
    fun matchPatternValidator_matches_regex() {
        val validator = MatchPatternValidator("invalid", Regex("^abc.*"))
        assertTrue(validator.validate("abcdef"))
        assertFalse(validator.validate("zzz"))
    }

    @Test
    fun containsPatternValidator_contains_regex() {
        val validator = ContainsPatternValidator("invalid", Regex("\\d"))
        assertTrue(validator.validate("ab1c2"))
        assertFalse(validator.validate("abc"))
    }

    @Test
    fun emailValidator_validates_common_emails() {
        val validator = EmailValidator("invalid email")
        assertTrue(validator.validate("eosobande@ikokuko.dev"))
        assertTrue(validator.validate("sample.ikokuko+test@domain.co.uk"))
        assertFalse(validator.validate("invalid@"))
    }

    @Test
    fun phoneValidator_validates_e164_format() {
        val validator = PhoneNumberValidator("invalid phone")
        assertTrue(validator.validate("+14155552671"))
        assertTrue(validator.validate("+2348012345678"))
        assertFalse(validator.validate("08012345678"))
        assertFalse(validator.validate("+0123456789")) // 0 not allowed after '+'
    }

    @Test
    fun equalsValidator_compares_values() {
        val validator = EqualsValidator("not equal", expected = { "abc" })
        assertTrue(validator.validate("abc"))
        assertFalse(validator.validate("def"))
    }

    @Test
    fun notEqualsValidator_compares_values() {
        val validator = NotEqualsValidator("must differ", unwanted = { "abc" })
        assertTrue(validator.validate("def"))
        assertFalse(validator.validate("abc"))
    }

    @Test
    fun nonEmptySelectionValidator_passes_when_not_empty() {
        val validator = NonEmptySelectionValidator<String>("Selection required")
        assertTrue(validator.validate(listOf("A")), "Expected non-empty list to be valid")
    }

    @Test
    fun nonEmptySelectionValidator_fails_when_empty() {
        val validator = NonEmptySelectionValidator<String>("Selection required")
        assertFalse(validator.validate(emptyList()), "Expected empty list to be invalid")
    }

    @Test
    fun minSelectionValidator_passes_when_above_min() {
        val validator = MinSelectionValidator<String>("At least 2 required", min = 2)
        assertTrue(
            validator.validate(listOf("A", "B", "C")),
            "Expected list with >=2 items to be valid"
        )
    }

    @Test
    fun minSelectionValidator_fails_when_below_min() {
        val validator = MinSelectionValidator<String>("At least 2 required", min = 2)
        assertFalse(validator.validate(listOf("A")), "Expected list with <2 items to be invalid")
    }

    @Test
    fun maxSelectionValidator_passes_when_below_or_equal_max() {
        val validator = MaxSelectionValidator<String>("At most 3 allowed", max = 3)
        assertTrue(validator.validate(listOf("A", "B")), "Expected list with <=3 items to be valid")
        assertTrue(
            validator.validate(listOf("A", "B", "C")),
            "Expected list with ==3 items to be valid"
        )
    }

    @Test
    fun maxSelectionValidator_fails_when_above_max() {
        val validator = MaxSelectionValidator<String>("At most 3 allowed", max = 3)
        assertFalse(
            validator.validate(listOf("A", "B", "C", "D")),
            "Expected list with >3 items to be invalid"
        )
    }

    @Test
    fun exactSelectionValidator_passes_when_size_matches() {
        val validator = ExactSelectionValidator<String>("Exactly 2 required", size = 2)
        assertTrue(
            validator.validate(listOf("A", "B")),
            "Expected list with exactly 2 items to be valid"
        )
    }

    @Test
    fun exactSelectionValidator_fails_when_size_differs() {
        val validator = ExactSelectionValidator<String>("Exactly 2 required", size = 2)
        assertFalse(validator.validate(listOf("A")), "Expected list with 1 item to be invalid")
        assertFalse(
            validator.validate(listOf("A", "B", "C")),
            "Expected list with 3 items to be invalid"
        )
    }

    @Test
    fun selectionRangeValidator_passes_within_bounds() {
        val validator = SelectionRangeValidator<String>("Select between 2 and 4", min = 2, max = 4)
        assertTrue(validator.validate(listOf("A", "B")), "Expected 2 selections to be valid")
        assertTrue(validator.validate(listOf("A", "B", "C")), "Expected 3 selections to be valid")
        assertTrue(
            validator.validate(listOf("A", "B", "C", "D")),
            "Expected 4 selections to be valid"
        )
    }

    @Test
    fun selectionRangeValidator_fails_outside_bounds() {
        val validator = SelectionRangeValidator<String>("Select between 2 and 4", min = 2, max = 4)
        assertFalse(validator.validate(listOf("A")), "Expected <2 selections to be invalid")
        assertFalse(
            validator.validate(listOf("A", "B", "C", "D", "E")),
            "Expected >4 selections to be invalid"
        )
    }

    @Test
    fun selectionRangeValidator_throws_when_min_greater_than_max() {
        assertFailsWith<IllegalArgumentException> {
            SelectionRangeValidator<String>("Invalid", min = 5, max = 3)
        }
    }

}