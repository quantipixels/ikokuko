![Ikokuko Banner](https://github.com/user-attachments/assets/99fbdbf5-1d3f-4780-89bd-f7569ac0fa51)

# ìkọkúkọ

[![Maven Central](https://img.shields.io/maven-central/v/com.quantipixels/ikokuko.svg?label=Maven%20Central)](https://central.sonatype.com/artifact/com.quantipixels/ikokuko)
[![License](https://img.shields.io/github/license/quantipixels/ikokuko)](https://github.com/quantipixels/ikokuko/blob/main/LICENSE)
![Kotlin](https://img.shields.io/badge/Kotlin-2.2.20-blue?logo=kotlin)
![Android](https://img.shields.io/badge/Android-✔-green?logo=android&logoColor=white)
![iOS](https://img.shields.io/badge/iOS-✔-lightgrey?logo=apple&logoColor=white)

> **Reactive, type-safe form validation for Compose Multiplatform (Android & iOS)**  
> Build declarative, cross-platform forms that validate themselves as users type.

---

## Features

- **Lightweight** – no reflection or annotation processors.  
- **Compose-first** – integrates naturally with Compose Multiplatform UIs.  
- **Reactive validation** – runs automatically when field values change.  
- **Type-safe fields** – `Field<T>` enforces consistent types.  
- **Composable DSL** – define forms and validators declaratively.  
- **Multiplatform** – supports Android and iOS.
- **Built-in validators** – text, numeric, pattern, equality, selection.  
- **Extendable** – implement your own `Validator<T>` easily.

---

## Getting Started

### 1. Add dependency

```kotlin
repositories {
    mavenCentral()
}

dependencies {
    implementation("com.quantipixels:ikokuko:0.2.0")
}
```

---

### 2. [FormState](/ikokuko/src/commonMain/kotlin/com/quantipixels/ikokuko/Form.kt)
`FormState` manages all field values, validation errors, and visibility flags for a form.
It’s the single source of truth for the form’s current state.

`FormState.isValid` follows the form's error-reporting state. It remains `true` while
`shouldShowErrors` is `false`. After error reporting is enabled, it is `false` while any field has
a stored error. Individual `field.isValid` values remain strict regardless of error visibility.

> Optionally pass `shouldShowErrors` when creating the state to control its initial error visibility behavior.

```kotlin
// Default: dirty-field errors are hidden until submit or a manual toggle
val formState = remember { FormState() }

// Dirty-field errors can be displayed immediately
val formState = remember { FormState(shouldShowErrors = true) }
```

Use `rememberSaveableFormState()` when field values must survive supported platform state restoration:

```kotlin
val formState = rememberSaveableFormState()
```

The default saver supports values accepted by the platform save registry. `Form` keeps its default
state in memory because `Field<T>` can contain any non-null type. For custom field values, pass a
complete `Saver<FormState, out Any>`. You can also use `FormState.saver(valuesSaver)` to provide a
saver for the complete `Map<String, Any>` of field names and values while Ikokuko saves the remaining
form state.

#### shouldShowErrors
Controls whether stored errors can be displayed for dirty fields. A pristine field does not display
its error until it becomes dirty. `submit()` marks all initialized fields as dirty.

|Value|Behaviour|Typical Use Case|
|---|---|---|
|`false` (default)|Validation runs continuously, but dirty-field errors remain hidden and `FormState.isValid` remains `true`.|Use when errors must first affect the UI after `submit()` or a manual toggle.|
|`true`|Stored errors affect `FormState.isValid` and are visible for dirty fields. Pristine-field errors remain hidden.|Use when validation must affect the UI as fields change.|

You can toggle this flag at any time from either the FormState or inside the FormScope.
```kotlin
// From FormState
formState.shouldShowErrors = true // Permit dirty-field errors
formState.shouldShowErrors = false // Hide errors again

// From FormScope
Form(onSubmit = {}) {
    // ...
    shouldShowErrors = true // Permit dirty-field errors
    shouldShowErrors = false // Hide errors again
}
```

#### Resetting the form
The form can be reset from either the FormState or inside the FormScope.
```kotlin
// From FormState
formState.reset()

// From FormScope
Form(onSubmit = {}) {
    // ...
    Button(onClick = ::reset) { Text("Reset Form") }
}
```

---

### 3. Defining [Field](/ikokuko/src/commonMain/kotlin/com/quantipixels/ikokuko/Field.kt)s
You can define a `Field` using either typed **constructors** or **generic syntax**, depending on your use case and desired type safety.

- Typed constructors (recommended for readability) — ìkọkúkọ provides convenience factory functions for the most common field types:
```kotlin
val EmailField = Field.Text("email")
val RememberMeField = Field.Boolean("remember_me")
val RangeField = Field.Range("price_range") // ClosedFloatingPointRange<Float>
```

- Generic field syntax (for custom or advanced cases) — You can also define a `Field` directly with its type parameter:
```kotlin
val NameField = Field<String>("name")
val CustomField = Field<MyCustomData>("custom")
```

You can define `Field` objects as 
- top-level (or global), 
- local, or
- composable-scoped values — they’re lightweight and can be freely recreated.

```kotlin
// top-level (or global)
val EmailField = Field.Text("email")

@Composable
fun DemoForm() {
    // local — recreated on every recomposition (fine for stateless forms)
    val emailField = Field.Text("email")

    // composable-scoped — stable across recompositions
    val emailField = remember { Field.Text("email") }
}
```

#### How fields work
- `Field` instances are identified by their name, not by object identity.
- `Field<T>` requires a non-null `T`. Use an explicit value such as an empty string or list to represent no input.
- You can safely recreate them on each composition — their state in the form will persist as long as the name stays the same.
- `Field` objects are cheap to construct; there’s no need to remember them unless you prefer stable references.

#### Name-based behavior
|Case|Behaviour|
|---|---|
|Same name, same type|Fields share the same value in the FormState. Updating one updates them all.|
|Same name, different type|Fields share one map entry because generic types do not affect field equality. Reading the entry through the wrong type can cause a runtime cast error.|
|Different names|Fields maintain independent values and validation states.|

#### Recommended
> Each field name must be unique within a `FormScope`. `ValidationEffect` does not check for
> duplicate names because it cannot distinguish a recreated logical field from a second declaration.

---

### 4. Add Validation and Connect Fields to the FormState

You can connect fields to your FormState and enable validation in two ways:

Use one `ValidationEffect` or `FormField` for each field in a form.

- **Manual setup** — call [ValidationEffect](/ikokuko/src/commonMain/kotlin/com/quantipixels/ikokuko/FormScope.kt) directly to register and validate a field.
```kotlin
Form(onSubmit={ println("Email: ${EmailField.value}") }) {
    ValidationEffect(
        field = EmailField,
        initialValue = "",
        validators = listOf(
            RequiredValidator("Email required"),
            MatchPatternValidator("Invalid email", "[^@\\s]+@[^@\\s]+\\.[^@\\s]+")
        )
    )
    OutlinedTextField(
        value = EmailField.value,
        isError = EmailField.shouldDisplayError,
        label = { Text("Email") },
        supportingText = EmailField.error.takeIf { EmailField.shouldDisplayError }?.let {
            { Text(it, color = MaterialTheme.colorScheme.error) }
        },
        onValueChange = { EmailField.value = it }
    )
}
```
- **Convenience setup** — use FormField, which automatically registers the field and runs validation on value changes.
```kotlin
FormField(
    field = EmailField,
    initialValue = "",
    validators = listOf(
        RequiredValidator("Email required"),
        MatchPatternValidator("Invalid email", "[^@\\s]+@[^@\\s]+\\.[^@\\s]+")
    )
) {
    OutlinedTextField(
        value = EmailField.value,
        isError = EmailField.shouldDisplayError,
        label = { Text("Email") },
        supportingText = EmailField.error.takeIf { EmailField.shouldDisplayError }?.let {
            { Text(it, color = MaterialTheme.colorScheme.error) }
        },
        onValueChange = { EmailField.value = it }
    )
}
```

---

### 5. Overriding Errors Manually

Each `Field` exposes a raw `error` property that represents its current validation error message. It can be set or cleared manually at any time. Use `shouldDisplayError` to decide whether to render it.

```kotlin
var Field<*>.error: String?
```

Normally, this value is updated automatically by `ValidationEffect` whenever validators fail, but you can override it manually for advanced use cases such as:
- Server-side or asynchronous validation (e.g. username already taken).
- Custom inline validation not covered by existing Validator classes.
- Resetting or clearing errors programmatically.

#### Example: Manual error assignment
```kotlin
// Inside a FormScope

// Assign error message
if (EmailField.value.endsWith("@test.com")) {
    EmailField.error = "Test domains are not allowed"
}

// Clear the error message
EmailField.error = null
```

---

### 6. Creating Reusable Form Components

ìkọkúkọ’s [FormScope](/ikokuko/src/commonMain/kotlin/com/quantipixels/ikokuko/FormScope.kt) lets you build reusable composable form components that automatically handle value binding, validation, and error display.
This makes it easy to define input fields once and reuse them across different forms.

#### Example: `TextInput`

You can create a reusable text input field as an extension on `FormScope`:

```kotlin
@Composable
fun FormScope.TextInput(
    field: Field<String>,
    modifier: Modifier = Modifier,
    initialValue: String = "",
    label: String = "",
    placeholder: String = "",
    validators: List<Validator<String>> = emptyList()
) {
    FormField(field, initialValue, validators) {
        Column(modifier = modifier) {
            OutlinedTextField(
                value = field.value,
                isError = field.shouldDisplayError,
                label = { Text(label) },
                placeholder = {
                    Text(
                        placeholder, 
                        color = MaterialTheme.colorScheme.secondary.copy(alpha = 0.7f)
                    )
                },
                supportingText = field.error.takeIf { field.shouldDisplayError }?.let { { Text(it) } },
                onValueChange = { field.value = it },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(12.dp))
        }
    }
}
```
#### How it works
- `ValidationEffect` attaches validators and ensures the field’s value and errors stay reactive.
- `field.value` binds the text input to the form state.
- `field.error` provides the raw active error message.
- `field.isValid` reports strict validation state.
- `field.shouldDisplayError` drives error styling and message visibility.

All form logic is encapsulated inside the FormScope, so the field automatically integrates with `submit()`, `reset()`, and global validation visibility.

---

### 7. Build a form

Compose your complete form by combining your defined fields, inputs, and validators inside a Form.
The Form automatically manages field registration, validation, and submission through a shared FormState.
It also supports cross-field validation, allowing validators to depend on the values of other fields (e.g. password confirmation, date ranges, matching inputs).

> 💡 This example builds on the reusable `TextInput` component described in the previous section —
each input is already wired to its corresponding Field and validation logic.

```kotlin
val EmailField = Field.Text("email")
val PasswordField = Field.Text("password")
val ConfirmPasswordField = Field.Text("confirm_password")

@Composable
fun SignUpForm() {
    val formState = remember { FormState() }

    Form(state = formState, onSubmit = {
        println("Email: ${EmailField.value}")
        println("Password: ${PasswordField.value}")
        println("Password Confirmation: ${ConfirmPasswordField.value}")
    }) {
        Column {
            TextInput(
                field = EmailField,
                label = "Email",
                validators = listOf(
                    RequiredValidator("Email required"),
                    MatchPatternValidator("Invalid email", "[^@\\s]+@[^@\\s]+\\.[^@\\s]+")
                )
            )
            TextInput(
                field = PasswordField,
                label = "Password",
                validators = listOf(
                    RequiredValidator("Password required"), 
                    MinLengthValidator("At least 8 characters", 8)
                )
            )

            // Cross-field validation
            // FieldEqualsValidator declares PasswordField as a dependency.
            TextInput(
                field = ConfirmPasswordField,
                label = "Password Confirmation",
                validators = listOf(
                    RequiredValidator("password confirmation is required"),
                    FieldEqualsValidator("passwords must match", PasswordField)
                )
            )
            Button(onClick = ::submit, enabled = isValid) {
                Text("Sign In")
            }
        }
    }
}
```

#### Notes
- Uses the `TextInput` reusable component defined in the previous section.
- `FormState` tracks and validates all registered fields automatically.
- The `onSubmit` callback executes only when all validations pass.
- Cross-field validators declare the fields they read. A dependency value change revalidates the target field.
- The form `isValid` property remains `true` until error reporting is enabled. After the first
  `submit()`, stored errors can disable UI elements such as this button.
- `submit()` uses the latest completed reactive validation cycle. Do not assign a field value and call `submit()` synchronously in the same callback.
---

### 8. Built-in [Validators](/ikokuko/src/commonMain/kotlin/com/quantipixels/ikokuko/Validator.kt)

#### Text
| Validator | Description |
|------------|--------------|
| `RequiredValidator` | Must not be blank |
| `MinLengthValidator` | Minimum characters |
| `MaxLengthValidator` | Maximum characters |
| `LengthValidator` | Exact length |

#### Numeric
| Validator | Description |
|------------|--------------|
| `MinValidator` | ≥ min |
| `MaxValidator` | ≤ max |
| `RangeValidator` | Between min and max |

#### Pattern
| Validator | Description |
|------------|--------------|
| `MatchPatternValidator` | Entire string matches a pattern string |
| `ContainsPatternValidator` | Pattern string occurs anywhere |

#### Equality and membership
| Validator | Description |
|------------|-------------|
| `FieldEqualsValidator` | Must equal another field value |
| `InValidator` | Value must be in the allowed set |
| `NotInValidator` | Value must not be in the disallowed set |

#### Selection / Lists
| Validator | Description |
|------------|-------------|
| `SelectionRangeValidator` | Minimum and optional maximum item count |
| `SelectionInValidator` | Ensures all selected values are within the allowed options |

#### Boolean
| Validator | Description |
|------------|-------------|
| `CheckedValidator` | Must be `true` |

#### Custom Validators

Implement the `Validator<T>` interface:

```kotlin
data class StartsWithValidator(
    override val errorMessage: String,
    private val prefix: String
) : Validator<String> {
    override fun ValidationScope.validate(value: String) = value.startsWith(prefix)
}
```
Use it normally:

```kotlin
ValidationEffect(
    field = UsernameField,
    initialValue = "",
    validators = listOf(StartsWithValidator("Must start with @", "@"))
)
```

`ValidationEffect` uses the validator list as an effect key. Built-in validators are data
classes so an equivalent inline validator remains equal across recompositions. This prevents
validation from restarting when no validation rule changed.

Custom validators used inline should also have structural equality. A data class is the
simplest option. Avoid lambda-backed validators. A new lambda normally has a new identity on
each recomposition, even when it has the same behavior. This can restart validation and replace
an external error without a field or dependency value change.

If a custom validator must contain a lambda, remember the validator instance:

```kotlin
val validator = remember {
    CustomValidator("Invalid value") { value -> checkValue(value) }
}
```

Declare every field read during validation in `dependencies`.

---

## Migrating from 0.1.0

- Change nullable field types to non-null types. `Field<T>` now requires `T : Any`.
- Remove field destructuring and replace the removed `Field.Int`, `Field.Long`, and `Field.Double`
  factories with the generic `Field<T>(name)` constructor when required.
- Give each field a unique name within its `FormScope`. Same-name fields share one stored value.
- Rename the `default` argument of `ValidationEffect` and `FormField` to `initialValue`.
- Replace `field.markAsDirty()` with `field.isDirty = true`. The `isDirty` property is now writable.
- Remove explicit `null` arguments from `submit(onInvalid)`. Its fallback is now a non-null no-op
  callback.
- Use `field.shouldDisplayError` for rendering. `field.error` and `field.isValid` expose strict
  stored field state. `FormState.isValid` ignores stored errors until error reporting is enabled.
- Update custom validators to implement `fun ValidationScope.validate(value: T)`. Declare each
  field read by validation in `dependencies`.
- Use stable structural equality for inline custom validators. Prefer data classes, and remember
  validators that contain lambdas.
- Replace numeric transform lambdas with the string-backed integer `MinValidator`, `MaxValidator`,
  and `RangeValidator` constructors. These validators parse values with `toIntOrNull()`.
- Pass pattern strings instead of `Regex` objects to `MatchPatternValidator` and
  `ContainsPatternValidator`.
- Replace `EmailValidator` and `PhoneNumberValidator` with `MatchPatternValidator` and an
  application-owned pattern.
- Replace `EqualsValidator` with `FieldEqualsValidator` for field comparison. Implement fixed-value
  equality, inequality, and other uncommon rules as custom validators.
- Replace `NonEmptySelectionValidator`, `MinSelectionValidator`, `MaxSelectionValidator`, and
  `ExactSelectionValidator` with `SelectionRangeValidator`. Its `max` argument can be `null` for an
  unbounded maximum.
- Use `CheckedValidator` for required Boolean fields. Use `InValidator`, `NotInValidator`, and
  `SelectionInValidator` for membership rules.
- Account for reset behavior. `reset()` now clears values, errors, dirty state, and error visibility,
  then revalidates initialized effects.

---

## Demo - [Sample App](/samples/composeApp)

See **Ikokuko** — the reactive, type-safe form validation library for Compose Multiplatform (Android & iOS) — in action:

https://github.com/user-attachments/assets/d83ed2e5-5cc0-4034-9cb3-98d355177db5

> This short video showcases real-time validation and error handling using Ikokuko in a Compose Multiplatform sample app.

---

## License

    Copyright 2025 Quanti Pixels

    Licensed under the Apache License, Version 2.0 (the "License");
    you may not use this file except in compliance with the License.
    You may obtain a copy of the License at

    http://www.apache.org/licenses/LICENSE-2.0

    Unless required by applicable law or agreed to in writing, software
    distributed under the License is distributed on an "AS IS" BASIS,
    WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
    See the License for the specific language governing permissions and
    limitations under the License.
