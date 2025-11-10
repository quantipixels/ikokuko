package com.quantipixels.ikokuko

import androidx.compose.runtime.Immutable
import kotlin.jvm.JvmInline

/**
 * Logical identifier for a form field of type [T].
 *
 * The [name] must be unique within a form.
 * Two fields with the same name are considered the same field for storage/validation
 */
@JvmInline
@Immutable
value class Field<T>(val name: String) {
    operator fun component1() = name

    @Suppress("FunctionName")
    companion object {
        fun Text(name: String) = Field<String>(name)
        fun Boolean(name: String) = Field<Boolean>(name)
        fun Range(name: String) = Field<ClosedFloatingPointRange<Float>>(name)
        fun Int(name: String) = Field<Int>(name)
        fun Float(name: String) = Field<Float>(name)
        fun Long(name: String) = Field<Long>(name)
        fun Double(name: String) = Field<Double>(name)
        fun <T> List(name: String) = Field<List<T>>(name)
    }
}