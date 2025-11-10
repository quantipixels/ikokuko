# 🧩 ìkọkúkọ

> **Reactive, type-safe form and validation library for Compose Multiplatform**  
> Build declarative, cross-platform forms that validate themselves as users type.

---

## ✨ Features

- **Lightweight** – no reflection or annotation processors.  
- **Compose-first** – integrates naturally with Compose Multiplatform UIs.  
- **Reactive validation** – runs automatically when field values change.  
- **Type-safe fields** – `Field<T>` enforces consistent types.  
- **Composable DSL** – define forms and validators declaratively.  
- **Platform-agnostic** – works on Android, Desktop, iOS, JS, and Wasm.  
- **Built-in validators** – text, numeric, pattern, equality, selection.  
- **Extendable** – implement your own `Validator<T>` easily.

---

## 🚀 Getting Started

### 1️⃣ Add dependency

```kotlin
repositories {
    mavenCentral()
}

dependencies {
    implementation("com.quantipixels:ikokuko:1.0.0")
}
```

---

### 2️⃣ Define fields
You can define `[Field](/ikokuko/src/commonMain/kotlin/com/quantipixels/ikokuko/Field.kt)` objects as top-level, local, or composable-scoped values — they’re lightweight and can be freely recreated.

```kotlin
val EmailField = Field.Text("email")
val PasswordField = Field.Text("password")
val TermsField = Field.Boolean("terms")
```
#### How fields work
- Field instances are identified by their name, not by object identity.
- You can safely recreate them on each composition — their state in the form will persist as long as the name stays the same.
- Field objects are cheap to construct; there’s no need to remember them unless you prefer stable references.

#### Name-based behavior
|Case|Behaviour|
|---|---|
|Same name, same type|Fields share the same value in the FormState. Updating one updates them all.|
|Same name, different type|Causes a crash when FormScope tries to cast the stored value back to the wrong type.|
|Different names|Fields maintain independent values and validation states.|

#### Recommended
> Always ensure that all form fields have unique names within a single `FormScope`.

---

### 3️⃣ [FormState](/ikokuko/src/commonMain/kotlin/com/quantipixels/ikokuko/Form.kt)
`[FormState](/ikokuko/src/commonMain/kotlin/com/quantipixels/ikokuko/Form)` manages all field values, validation errors, and visibility flags for a form.
It’s the single source of truth for the form’s current state.

You usually create it with `remember { FormState() }`, optionally passing `shouldShowErrors` to control **initial error visibility behavior**:

```kotlin
// Default: errors hidden until submit or manual toggle
val formState = remember { FormState() }

// Errors become visible after submit or as fields change (dirty)
val formState = remember { FormState(shouldShowErrors = true) }
```

#### shouldShowErrors
Controls when validation errors are globally visible.
|Value|Behaviour|Typical Use Case|
|---|---|---|
|`false` (default)|Validation runs continuously, but errors are hidden until submit() or manual toggle.|Most common — errors appear only after first submit.|
|`true`|Errors become visible once a field value changes (becomes dirty) or after submit.|Used when you want validation messages to show immediately upon interaction.|

You can toggle this flag at any time from either the FormState or inside the FormScope.
```kotlin
// From FormState
formState.shouldShowErrors = true // Show all validation errors
formState.shouldShowErrors = false // Hide errors again

// From FormScope
Form(onSubmit = {}) {
    // ...
    shouldShowErrors = true // Show all validation errors
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

### 4️⃣ Build a form

```kotlin
val EmailField = Field.Text("email")
val PasswordField = Field.Text("password")

@Composable
fun SignInForm() {
    val formState = remember { FormState() }

    Form(state = formState, onSubmit = {
        println("Email: ${EmailField.value}")
        println("Password: ${PasswordField.value}")
    }) {
        ValidationEffect(
            field = EmailField,
            default = "",
            validators = listOf(RequiredValidator("Email required"), EmailValidator("Invalid email"))
        )
        ValidationEffect(
            field = PasswordField,
            default = "",
            validators = listOf(RequiredValidator("Password required"), MinLengthValidator("At least 8 characters", 8))
        )

        Column {
            OutlinedTextField(
                value = EmailField.value,
                isError = !EmailField.isValid,
                label = { Text("Email") },
                supportingText = EmailField.error?.let { { Text(it, color = MaterialTheme.colorScheme.error) } },
                onValueChange = { EmailField.value = it }
            )
            OutlinedTextField(
                value = PasswordField.value,
                isError = !PasswordField.isValid,
                label = { Text("Password") },
                supportingText = PasswordField.error?.let { { Text(it, color = MaterialTheme.colorScheme.error) } },
                onValueChange = { PasswordField.value = it }
            )
            Button(onClick = ::submit, enabled = isValid) {
                Text("Sign In")
            }
        }
    }
}
```

---

### 5️⃣ Overriding Errors Manually

Each `Field` exposes an `error` property that represents its current validation error message, and it can be set or cleared manually at any time.

```kotlin
var Field<*>.error: String?
```
Normally, this value is updated automatically by ValidationEffect whenever validators fail, but you can override it manually for advanced use cases such as:
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

### 6️⃣ Creating Reusable Form Components

Ikokuko’s `[FormScope](/ikokuko/src/commonMain/kotlin/com/quantipixels/ikokuko/FormScope.kt)` lets you build reusable composable form components that automatically handle value binding, validation, and error display.
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
    ValidationEffect(field, initialValue, validators)

    Column(modifier = modifier) {
        OutlinedTextField(
            value = field.value,
            isError = !field.isValid,
            label = { Text(label) },
            placeholder = {
                Text(placeholder,color = MaterialTheme.colorScheme.secondary.copy(alpha = 0.7f))
            },
            supportingText = field.error?.let { { Text(it) } },
            onValueChange = { field.value = it },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(12.dp))
    }
}
```
How it works
- ValidationEffect attaches validators and ensures the field’s value and errors stay reactive.
- field.value binds the text input to the form state.
- field.error provides the active error message when visible.
- field.isValid drives the error styling (isError = !field.isValid).

All form logic is encapsulated inside the FormScope, so the field automatically integrates with submit(), reset(), and global validation visibility.

---

### 7️⃣ Built-in [Validators](/ikokuko/src/commonMain/kotlin/com/quantipixels/ikokuko/Validator.kt)

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
| `MatchPatternValidator` | Entire string matches regex |
| `ContainsPatternValidator` | Regex occurs anywhere |
| `EmailValidator` | Standard email format |
| `PhoneNumberValidator` | E.164 phone format |

#### Equality
| Validator | Description |
|------------|-------------|
| `EqualsValidator` | Must equal expected value |
| `NotEqualsValidator` | Must differ from unwanted value |

#### Selection / Lists
| Validator | Description |
|------------|-------------|
| `NonEmptySelectionValidator` | Selection not empty |
| `MinSelectionValidator` | At least N items |
| `MaxSelectionValidator` | At most N items |
| `ExactSelectionValidator` | Exactly N items |
| `SelectionRangeValidator` | Between min and max items |

#### Custom Validators

Implement the `Validator<T>` interface:

```kotlin
class StartsWithValidator(
    override val errorMessage: String,
    private val prefix: String
) : Validator<String> {
    override fun validate(value: String) = value.startsWith(prefix)
}
```
Use it normally:

```kotlin
ValidationEffect(
    field = UsernameField,
    default = "",
    validators = listOf(StartsWithValidator("Must start with @", "@"))
)
```

---

## 🪪 License

```
Apache License 2.0
Copyright © 2025 Olúwáṣeun Ṣóbándé
```
