package com.quantipixels.ikokuko.verification

import androidx.compose.runtime.AbstractApplier
import androidx.compose.runtime.Composition
import androidx.compose.runtime.Recomposer
import androidx.compose.runtime.SideEffect
import com.quantipixels.ikokuko.*
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.*

class PublishedApiTest {
    @OptIn(ExperimentalCoroutinesApi::class)
    @Test fun publishedSubmitValidatesAnEditInTheSameEvent() = runTest {
        val recomposer = Recomposer(coroutineContext)
        val composition = Composition(NoNodes(), recomposer)
        val name = Field.Text("name")
        var form: FormScope? = null
        var accepted = 0
        var rejected = 0
        try {
            composition.setContent {
                Form {
                    ValidationEffect(name, "ready", listOf(RequiredValidator("Required")))
                    SideEffect { form = this }
                }
            }
            runCurrent()
            with(checkNotNull(form)) {
                name.value = ""
                submit(onInvalid = { rejected++ }) { accepted++ }
                assertEquals(1, rejected)
                assertEquals(0, accepted)
                name.value = "fresh"
                submit(onInvalid = { rejected++ }) { accepted++ }
                assertEquals(1, rejected)
                assertEquals(1, accepted)
            }
        } finally {
            composition.dispose()
            recomposer.cancel()
        }
    }

    @Test fun publishedValidatorsUseUtf16Bounds() {
        val scope = object : ValidationScope {
            override val <T : Any> Field<T>.value: T get() = error("No dependencies")
        }
        with(MaxLengthValidator("Too long", 4)) {
            assertTrue(with(scope) { validate("🙂🙂") })
            assertFalse(with(scope) { validate("🙂🙂a") })
        }
    }
}

// Form and ValidationEffect emit no UI nodes. Unexpected UI fails this runtime-only consumer.
private class NoNodes : AbstractApplier<Unit>(Unit) {
    override fun insertTopDown(index: Int, instance: Unit) = error("Unexpected UI node")
    override fun insertBottomUp(index: Int, instance: Unit) = error("Unexpected UI node")
    override fun remove(index: Int, count: Int) = error("Unexpected UI node")
    override fun move(from: Int, to: Int, count: Int) = error("Unexpected UI node")
    override fun onClear() = Unit
}
