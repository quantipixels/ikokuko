# Changelog

All notable changes to Ikokuko are recorded in this file.

## [Unreleased]

## [0.2.0] - 2026-08-02

- Add strict field validation state and `Field.shouldDisplayError`.
- Add dependency-aware cross-field validation with `FieldEqualsValidator`.
- Add `CheckedValidator` and `SelectionRangeValidator`.
- Add `rememberSaveableFormState()` and custom value-saver support.
- Change `Validator` to use a read-only `ValidationScope` and declared field dependencies.
- Make built-in validators data classes with structural equality.
- Put `errorMessage` first in every built-in validator constructor.
- Make numeric validators parse integer text without transform lambdas.
- Make pattern validators accept pattern strings instead of `Regex` objects.
- Make `Field<T>` non-nullable and make `Field.isDirty` writable.
- Make form validity follow error reporting while field validity remains strict.
- Reset values, errors, dirty state, and error visibility before revalidation.
- Limit published and documented support to Android and iOS.
- Remove fixed email and phone validators. Use `MatchPatternValidator` with an application pattern.
- Remove `EqualsValidator`, `NotEqualsValidator`, and validator lambda APIs.
- Remove specialized selection-size validators. Use `SelectionRangeValidator`.
- Remove `Field.component1()` and the `Int`, `Long`, and `Double` field factories.
- Verify Android, Apple Silicon, and Intel iOS targets before publication.

## [0.1.0] - 2025-11-13

- Add the initial typed `Field`, `FormState`, `FormScope`, and `Form` APIs.
- Add reactive validation through `ValidationEffect` and `FormField`.
- Add text, numeric, pattern, equality, membership, and selection validators.
- Add dirty state, error visibility, submit, and reset behavior.
- Add the Compose Multiplatform sample application.
- Publish the library to Maven Central.

[Unreleased]: https://github.com/quantipixels/ikokuko/compare/release-0.2.0...HEAD
[0.2.0]: https://github.com/quantipixels/ikokuko/releases/tag/release-0.2.0
[0.1.0]: https://github.com/quantipixels/ikokuko/releases/tag/release-0.1.0
