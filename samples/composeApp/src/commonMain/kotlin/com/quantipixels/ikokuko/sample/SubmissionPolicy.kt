package com.quantipixels.ikokuko.sample

import com.quantipixels.ikokuko.Field
import com.quantipixels.ikokuko.FormScope

/** Keep this request with its response; a newer submission replaces it. */
data class SubmittedRequest(val id: Long, val values: Map<String, Any>)

data class FieldRejection(val requestId: Long, val errors: Map<String, String>)

internal data class RejectionDecision(
    val fieldErrors: Map<String, String> = emptyMap(),
    val fallback: String? = null
)

/** Stale responses are consumed too, so returning to the screen cannot apply them later. */
internal fun rejectionDecision(
    request: SubmittedRequest?,
    response: FieldRejection,
    currentValues: Map<String, Any>,
    knownFields: Set<String>,
    consumed: Boolean
): RejectionDecision {
    if (consumed || request == null || request.id != response.requestId ||
        request.values != currentValues
    ) return RejectionDecision()

    return RejectionDecision(
        fieldErrors = response.errors.filterKeys { it in knownFields },
        fallback = if (response.errors.keys.any { it !in knownFields }) {
            "The request was rejected. Review your details and try again."
        } else null
    )
}

internal fun FormScope.submittedValues(): Map<String, Any> =
    fields.associate { it.name to it.value }

internal fun FormScope.applyRejection(decision: RejectionDecision) {
    val initialized = fields.associateBy(Field<*>::name)
    decision.fieldErrors.forEach { (name, message) ->
        initialized[name]?.let { field ->
            field.error = message
            field.isDirty = true
        }
    }
    if (decision.fieldErrors.isNotEmpty()) isErrorReportingEnabled = true
}

internal const val NotesLimit = 240
internal fun notesCounter(value: String): String = "${value.length} / $NotesLimit UTF-16 units"
