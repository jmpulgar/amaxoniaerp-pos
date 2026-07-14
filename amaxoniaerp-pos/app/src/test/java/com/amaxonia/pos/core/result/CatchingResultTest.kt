package com.amaxonia.pos.core.result

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class CatchingResultTest {
    @Test
    fun `converts exceptions to failure without replacing their identity`() =
        runTest {
            val exception = IllegalStateException("failure")

            val result = catchingResult<Unit> { throw exception }

            assertSame(exception, result.exceptionOrNull())
        }

    @Test
    fun `preserves an existing result and never swallows fatal errors`() =
        runTest {
            val expected = Result.success("value")
            val fatal = AssertionError("fatal")

            assertEquals(expected.getOrNull(), catchingResult { expected }.getOrNull())
            val thrown = runCatching { catchingResult<Unit> { throw fatal } }.exceptionOrNull()
            assertTrue(thrown === fatal)
        }
}
