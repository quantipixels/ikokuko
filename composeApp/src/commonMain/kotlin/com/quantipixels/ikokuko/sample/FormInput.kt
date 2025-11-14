package com.quantipixels.ikokuko.sample

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.quantipixels.ikokuko.Field
import com.quantipixels.ikokuko.FormField
import com.quantipixels.ikokuko.FormScope
import com.quantipixels.ikokuko.Validator

@Composable
fun PasswordVisibilityToggle(
    isHidden: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Icon(
        imageVector = if (isHidden){
            Icons.Outlined.VisibilityOff
        } else {
            Icons.Outlined.Visibility
        },
        contentDescription = null,
        modifier = modifier.clickable(onClick = onClick)
    )
}

@Composable
fun FormScope.TextInput(
    field: Field<String>,
    modifier: Modifier = Modifier,
    initialValue: String = "",
    label: String = "",
    placeholder: String = "",
    isPassword: Boolean = false,
    validators: List<Validator<String>> = emptyList(),
    trailingIcon: @Composable (() -> Unit)? = null,
) {
    FormField(field, initialValue, validators) {
        Column(modifier = modifier) {
            OutlinedTextField(
                value = field.value,
                isError = !field.isValid,
                label = { Text(label) },
                placeholder = {
                    Text(
                        placeholder,
                        color = MaterialTheme.colorScheme.secondary.copy(alpha = .7f)
                    )
                },
                supportingText = field.error?.let { { Text(it) } },
                onValueChange = { field.value = it },
                singleLine = true,
                visualTransformation = if (isPassword) {
                    PasswordVisualTransformation()
                } else {
                    VisualTransformation.None
                },
                modifier = Modifier.fillMaxWidth(),
                trailingIcon = trailingIcon
            )
            Spacer(Modifier.Companion.height(12.dp))
        }
    }
}

@Composable
fun FormScope.CheckBox(
    field: Field<Boolean>,
    label: String,
    checked: Boolean = false,
    validators: List<Validator<Boolean>> = emptyList(),
    modifier: Modifier = Modifier,
    verticalAlignment: Alignment.Vertical = Alignment.CenterVertically
) {
    FormField(field, checked, validators) {
        Column(modifier = modifier) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = verticalAlignment,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Checkbox(
                    checked = field.value,
                    onCheckedChange = { field.value = it },
                    colors = CheckboxDefaults.colors(
                        uncheckedColor = if (!field.isValid) {
                            MaterialTheme.colorScheme.error
                        } else {
                            Color.Unspecified
                        },
                    ),
                    modifier = Modifier
                        .scale(.8f)
                        .clip(MaterialTheme.shapes.extraSmall)
                )
                Text(text = label)
            }
            field.error?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(start = 16.dp)
                )
            }

            Spacer(Modifier.height(12.dp))
        }
    }
}

@Composable
fun <T> FormScope.RadioGroup(
    field: Field<String>,
    label: String,
    items: List<T>,
    modifier: Modifier = Modifier,
    transform: (T) -> String = { it.toString() },
    itemLabel: @Composable (T) -> Unit = {
        Text(text = "$it", modifier = Modifier.padding(end = 16.dp))
    },
    initialValue: String = "",
    validators: List<Validator<String>> = emptyList()
) {
    FormField(field, initialValue, validators) {
        val onItemClick: (T) -> Unit = { item ->
            val stringValue = transform(item)
            if (stringValue != field.value) {
                field.value = stringValue
            }
        }

        Column(modifier = modifier) {
            Text(text = label)
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items.forEach { item ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .clip(MaterialTheme.shapes.medium)
                            .clickable { onItemClick(item) }
                    ) {
                        RadioButton(
                            selected = transform(item) == field.value,
                            onClick = { onItemClick(item) },
                            colors = RadioButtonDefaults.colors(
                                unselectedColor = if (field.isValid) {
                                    Color.Unspecified
                                } else {
                                    MaterialTheme.colorScheme.error
                                }
                            )
                        )
                        itemLabel(item)
                    }
                }
            }
            field.error?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(start = 16.dp)
                )
            }
            Spacer(Modifier.height(12.dp))
        }
    }


}

@Composable
fun <T> FormScope.CheckGroup(
    field: Field<List<T>>,
    label: String,
    items: List<T>,
    modifier: Modifier = Modifier,
    limit: Int = 2,
    itemLabel: @Composable (T) -> Unit = {
        Text(
            text = "$it",
            modifier = Modifier.padding(end = 16.dp)
        )
    },
    initialValue: List<T> = emptyList(),
    validators: List<Validator<List<T>>> = emptyList()
) {
    FormField(field, initialValue, validators) {
        val onItemClick: (T) -> Unit = { item ->
            if (item in field.value) {
                field.value -= item
            } else if (field.value.size < limit) {
                field.value += item
            }
        }

        Column(modifier = modifier) {
            Text(text = label)
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items.forEach { item ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .clip(MaterialTheme.shapes.medium)
                            .clickable { onItemClick(item) }
                    ) {
                        Checkbox(
                            checked = item in field.value,
                            onCheckedChange = { onItemClick(item) },
                            colors = CheckboxDefaults.colors(
                                uncheckedColor = if (!field.isValid) {
                                    MaterialTheme.colorScheme.error
                                } else {
                                    Color.Unspecified
                                }
                            ),
                            modifier = Modifier
                                .scale(.8f)
                                .clip(MaterialTheme.shapes.extraSmall)
                        )
                        itemLabel(item)
                    }
                }
            }
            field.error?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(start = 16.dp)
                )
            }
            Spacer(Modifier.height(12.dp))
        }
    }
}