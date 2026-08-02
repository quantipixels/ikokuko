package com.quantipixels.ikokuko

import androidx.compose.runtime.Immutable
import kotlin.jvm.JvmInline

/**
 * Logical identifier for a form field of type [T].
 *
 * The [name] must be unique within a form.
 * Two fields with the same name are considered the same field for storage and validation,
 * even when their declared generic types differ. Generic types do not affect value-class equality.
 */
@JvmInline
@Immutable
value class Field<T : Any>(val name: String) {
    @Suppress("FunctionName")
    companion object {
        fun Text(name: String) = Field<String>(name)
        fun Boolean(name: String) = Field<Boolean>(name)
        fun Range(name: String) = Field<ClosedFloatingPointRange<Float>>(name)
        fun Float(name: String) = Field<Float>(name)
        fun <T : Any> List(name: String) = Field<List<T>>(name)
    }
}
