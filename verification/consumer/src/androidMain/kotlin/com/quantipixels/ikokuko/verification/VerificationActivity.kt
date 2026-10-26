package com.quantipixels.ikokuko.verification

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.ImeAction
import com.quantipixels.ikokuko.*

val Name = Field.Text("name")
val Selected = Field.List<String>("selected")

class VerificationActivity : ComponentActivity() {
    lateinit var scope: FormScope
    var outcome by mutableStateOf("idle")
        private set

    fun submitCurrent() = with(scope) {
        submit(onInvalid = { outcome = "invalid" }) {
            outcome = "submitted:${Name.value}:${Selected.value.joinToString()}"
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            val state = rememberSaveableFormState()
            Form(state) {
                SideEffect { scope = this }
                ValidationEffect(Name, "", listOf(RequiredValidator("Required"), MaxLengthValidator("Too long", 16)))
                ValidationEffect(Selected, emptyList(), listOf(SelectionRangeValidator("Select one", min = 1)))
                Column {
                    TextField(
                        value = Name.value,
                        onValueChange = { Name.value = it },
                        modifier = Modifier.testTag("name"),
                        minLines = 2,
                        maxLines = 3,
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                        keyboardActions = KeyboardActions(onDone = { submitCurrent() }),
                    )
                    Button(onClick = { Selected.value = listOf("second-id") }, Modifier.testTag("select")) {
                        Text("Select second")
                    }
                    Text(Selected.value.joinToString(), Modifier.testTag("selection"))
                    Button(onClick = ::submitCurrent, Modifier.testTag("submit")) { Text("Submit") }
                    Button(onClick = {
                        Name.value = ""
                        submitCurrent()
                    }, Modifier.testTag("fresh-invalid")) { Text("Edit invalid and submit") }
                    Button(onClick = {
                        Name.value = "fresh"
                        submitCurrent()
                    }, Modifier.testTag("fresh-valid")) { Text("Edit valid and submit") }
                    Text(outcome, Modifier.testTag("outcome"))
                }
            }
        }
    }
}
