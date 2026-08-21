package com.moonforce.ohmyainote.hwr

import com.google.android.gms.tasks.Task
import com.google.mlkit.common.model.RemoteModelManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import org.mockito.Mockito

/**
 * JVM-runnable slice of [DigitalInkModelStore].
 *
 * The success/failure paths use `com.google.android.gms.tasks.Tasks.forResult/forException`,
 * which internally call `android.text.TextUtils` (a stubbed framework class) and therefore only
 * run under Robolectric/on-device. The timeout path is exercised here by handing the store a
 * never-completing [Task] mock, which avoids `Tasks` entirely and still verifies the
 * `withTimeout` + cancellation + `IllegalStateException` wrapping contract.
 */
class DigitalInkModelStoreTest {
    private val manager: RemoteModelManager = Mockito.mock(RemoteModelManager::class.java)

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun downloadTimeoutWrapsInIllegalState() {
        @Suppress("UNCHECKED_CAST")
        val neverCompleting: Task<Void> = Mockito.mock(Task::class.java) as Task<Void>
        val store = DigitalInkModelStore(manager, timeoutMs = 50)
        Mockito.`when`(manager.download(Mockito.any(), Mockito.any()))
            .thenReturn(neverCompleting)
        try {
            runBlocking { store.download() }
            fail("expected IllegalStateException from timeout")
        } catch (expected: IllegalStateException) {
            assertTrue("message was: ${expected.message}", expected.message!!.contains("手写识别模型下载失败"))
        }
    }
}
