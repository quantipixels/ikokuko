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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.quantipixels.ikokuko.ContainsPatternValidator
import com.quantipixels.ikokuko.EmailValidator
import com.quantipixels.ikokuko.EqualsValidator
import com.quantipixels.ikokuko.ExactSelectionValidator
import com.quantipixels.ikokuko.Field
import com.quantipixels.ikokuko.Form
import com.quantipixels.ikokuko.MinLengthValidator
import com.quantipixels.ikokuko.PhoneNumberValidator
import com.quantipixels.ikokuko.RequiredValidator

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
private val CapacityField = Field.List<Capacity>("capacity")
private val ProjectsField = Field.List<Project>("projects")
private val TermsField = Field.Boolean("terms")

private val SymbolRegex = Regex("[^A-Za-z0-9 ]")
private val DigitRegex = Regex("\\d")
private val UppercaseRegex = Regex("[A-Z]")
private val LowercaseRegex = Regex("[a-z]")

enum class Capacity {
    Personal, Professional
}

enum class Project {
    Commercial, OpenSource, Personal
}

@Composable
fun SignUpForm(
    onSubmit: (SignUpData) -> Unit,
    modifier: Modifier = Modifier.Companion
) {
    Form(
        onSubmit = {
            onSubmit(
                SignUpData(
                    PhoneNumberField.value,
                    EmailField.value,
                    PasswordField.value,
                    ConfirmPasswordField.value,
                    CapacityField.value.first(),
                    ProjectsField.value,
                    TermsField.value
                )
            )
            reset()
        }
    ) {
        Column(modifier = modifier) {
            TextInput(
                field = PhoneNumberField,
                label = "Phone number",
                placeholder = "+353 85 616 4829",
                validators = listOf(
                    RequiredValidator("phone number is required"),
                    PhoneNumberValidator("must be a valid phone number")
                )
            )
            TextInput(
                field = EmailField,
                label = "Email address",
                placeholder = "sample@ikokuko.dev",
                validators = listOf(
                    RequiredValidator("email is required"),
                    EmailValidator("must be a valid email address")
                )
            )
            TextInput(
                field = PasswordField,
                label = "Password",
                validators = listOf(
                    RequiredValidator("password is required"),
                    MinLengthValidator("must be at least 8 characters", 8),
                    ContainsPatternValidator("must contain an uppercase character", UppercaseRegex),
                    ContainsPatternValidator("must contain a lowercase character", LowercaseRegex),
                    ContainsPatternValidator("must contain a digit", DigitRegex),
                    ContainsPatternValidator("must contain a symbol", SymbolRegex)
                )
            )
            TextInput(
                field = ConfirmPasswordField,
                label = "Confirm password",
                validators = listOf(
                    RequiredValidator("password confirmation is required"),
                    EqualsValidator("passwords must match") { PasswordField.value }
                )
            )
            RadioGroup(
                field = CapacityField,
                label = "In what capacity do you intend to use this library?",
                items = Capacity.entries,
                validators = listOf(ExactSelectionValidator("capacity is required", 1))
            )
            CheckGroup(
                field = ProjectsField,
                label = "What type of projects do you intend to use this library for?",
                items = Project.entries,
                validators = listOf(ExactSelectionValidator("you must select 2 options", 2))
            )
            CheckBox(
                field = TermsField,
                label = "I agree to the Terms & Conditions",
                validators = listOf(
                    EqualsValidator("you must agree with the terms & conditions") { true }
                )
            )
            Spacer(Modifier.Companion.height(8.dp))
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