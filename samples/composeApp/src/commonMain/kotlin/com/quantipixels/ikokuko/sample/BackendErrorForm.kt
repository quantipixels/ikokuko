package com.quantipixels.ikokuko.sample

import androidx.compose.foundation.layout.Column
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.quantipixels.ikokuko.Field
import com.quantipixels.ikokuko.Form
import com.quantipixels.ikokuko.MaxLengthValidator
import com.quantipixels.ikokuko.RequiredValidator

private val ContactEmail = Field.Text("email")
private val Notes = Field.Text("notes")

/** A local response control demonstrates the policy without a network dependency. */
@Composable
fun BackendErrorExample() {
    var sequence by remember { mutableStateOf(0L) }
    var request by remember { mutableStateOf<SubmittedRequest?>(null) }
    var consumedRequestId by remember { mutableStateOf<Long?>(null) }
    var response by remember { mutableStateOf<FieldRejection?>(null) }
    Column {
        Text("Backend rejection and multiline input")
        BackendErrorForm(
            request = request,
            response = response,
            consumedRequestId = consumedRequestId,
            onConsumed = { consumed ->
                if (request?.id == consumed.requestId) consumedRequestId = consumed.requestId
                if (response == consumed) response = null
            },
            onSubmit = { values -> request = SubmittedRequest(++sequence, values) }
        )
        Button(enabled = request != null, onClick = {
            response = FieldRejection(request!!.id, mapOf("email" to "This email is already registered"))
        }) { Text("Reject the last submitted email") }
    }
}

/** The caller retains request identity and removes acknowledged responses across screen entries. */
@Composable
fun BackendErrorForm(
    request: SubmittedRequest?,
    response: FieldRejection?,
    consumedRequestId: Long?,
    onConsumed: (FieldRejection) -> Unit,
    onSubmit: (Map<String, Any>) -> Unit
) {
    var fallback by remember { mutableStateOf<String?>(null) }
    Form {
        TextInput(ContactEmail, label = "Contact email",
            validators = listOf(RequiredValidator("Email is required")))
        TextInput(Notes, label = "Notes", minLines = 3, maxLines = 6,
            counterText = ::notesCounter,
            validators = listOf(MaxLengthValidator("Use at most $NotesLimit UTF-16 units", NotesLimit)))

        LaunchedEffect(response) {
            response?.let {
                val decision = rejectionDecision(request, it, submittedValues(),
                    fields.map { field -> field.name }.toSet(),
                    consumed = consumedRequestId == it.requestId)
                applyRejection(decision)
                fallback = decision.fallback
                onConsumed(it)
            }
        }
        fallback?.let { Text(it) }
        Button(onClick = {
            submit {
                fallback = null
                onSubmit(submittedValues())
            }
        }) { Text("Submit contact details") }
    }
}
