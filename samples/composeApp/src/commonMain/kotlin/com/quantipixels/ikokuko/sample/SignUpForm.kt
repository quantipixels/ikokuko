package com.quantipixels.ikokuko.sample

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.quantipixels.ikokuko.CheckedValidator
import com.quantipixels.ikokuko.ContainsPatternValidator
import com.quantipixels.ikokuko.Field
import com.quantipixels.ikokuko.FieldEqualsValidator
import com.quantipixels.ikokuko.Form
import com.quantipixels.ikokuko.MatchPatternValidator
import com.quantipixels.ikokuko.MinLengthValidator
import com.quantipixels.ikokuko.RequiredValidator
import com.quantipixels.ikokuko.SelectionRangeValidator

data class SignUpData(
    val phoneNumber: String,
    val email: String,
    val password: String,
    val confirmation: String,
    val capacity: Capacity,
    val projects: List<Project>,
    val terms: Boolean
)

private val PhoneNumberField = Field.Text("phone_number")
private val EmailField = Field.Text("email")
private val PasswordField = Field.Text("password")
private val ConfirmPasswordField = Field.Text("confirm_password")
private val CapacityField = Field.Text("capacity")
private val ProjectsField = Field.List<Project>("projects")
private val TermsField = Field.Boolean("terms")

private const val SymbolPattern = "[^A-Za-z0-9 ]"
private const val DigitPattern = "\\d"
private const val UppercasePattern = "[A-Z]"
private const val LowercasePattern = "[a-z]"
private const val EmailPattern = "[^@\\s]+@[^@\\s]+\\.[^@\\s]+"
private const val PhoneNumberPattern = "\\+[1-9]\\d{1,14}"

enum class Capacity {
    Personal, Professional
}

enum class Project {
    Commercial, OpenSource, Personal
}

@Composable
fun SignUpForm(
    onSubmit: (SignUpData) -> Unit,
    modifier: Modifier = Modifier
) {
    Form(
        onSubmit = {
            onSubmit(
                SignUpData(
                    PhoneNumberField.value,
                    EmailField.value,
                    PasswordField.value,
                    ConfirmPasswordField.value,
                    enumValueOf<Capacity>(CapacityField.value),
                    ProjectsField.value,
                    TermsField.value
                )
            )
            reset()
        }
    ) {
        var passwordHidden by remember { mutableStateOf(true) }
        var confirmationHidden by remember { mutableStateOf(true) }

        Column(modifier = modifier) {
            TextInput(
                field = PhoneNumberField,
                label = "Phone number",
                placeholder = "+353 85 616 4829",
                validators = listOf(
                    RequiredValidator("phone number is required"),
                    MatchPatternValidator("must be a valid phone number", PhoneNumberPattern)
                )
            )
            TextInput(
                field = EmailField,
                label = "Email address",
                placeholder = "sample@ikokuko.dev",
                validators = listOf(
                    RequiredValidator("email is required"),
                    MatchPatternValidator("must be a valid email address", EmailPattern)
                )
            )
            TextInput(
                field = PasswordField,
                label = "Password",
                isPassword = passwordHidden,
                validators = listOf(
                    RequiredValidator("password is required"),
                    MinLengthValidator("must be at least 8 characters", 8),
                    ContainsPatternValidator("must contain an uppercase character", UppercasePattern),
                    ContainsPatternValidator("must contain a lowercase character", LowercasePattern),
                    ContainsPatternValidator("must contain a digit", DigitPattern),
                    ContainsPatternValidator("must contain a symbol", SymbolPattern)
                ),
                trailingIcon = {
                    PasswordVisibilityToggle(
                        isHidden = passwordHidden,
                        onClick = { passwordHidden = !passwordHidden }
                    )
                }
            )
            TextInput(
                field = ConfirmPasswordField,
                label = "Confirm password",
                isPassword = confirmationHidden,
                validators = listOf(
                    RequiredValidator("password confirmation is required"),
                    FieldEqualsValidator("passwords must match", PasswordField)
                ),
                trailingIcon = {
                    PasswordVisibilityToggle(
                        isHidden = confirmationHidden,
                        onClick = { confirmationHidden = !confirmationHidden }
                    )
                }
            )
            RadioGroup(
                field = CapacityField,
                label = "In what capacity do you intend to use this library?",
                items = Capacity.entries,
                validators = listOf(RequiredValidator("capacity is required"))
            )
            CheckGroup(
                field = ProjectsField,
                label = "What type of projects do you intend to use this library for?",
                items = Project.entries,
                validators = listOf(
                    SelectionRangeValidator(
                        errorMessage = "you must select 2 options",
                        min = 2,
                        max = 2
                    )
                )
            )
            CheckBox(
                field = TermsField,
                label = "I agree to the Terms & Conditions",
                validators = listOf(
                    CheckedValidator("you must agree with the terms & conditions")
                )
            )
            Spacer(Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Button(
                    onClick = ::submit,
                    enabled = isValid,
                    modifier = Modifier.weight(1f)
                ) {
                    Text("Sign Up!")
                }
                FilledTonalButton(
                    onClick = ::reset,
                    modifier = Modifier.weight(1f)
                ) {
                    Text("Reset")
                }
            }
            Spacer(Modifier.height(16.dp))
        }

    }
}
