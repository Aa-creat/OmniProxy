package io.github.aacreat.omniproxy

import io.github.aacreat.omniproxy.core.data.CryptoHelper
import io.github.aacreat.omniproxy.core.jni.NativeProxy
import io.github.aacreat.omniproxy.core.model.ProxyConfig
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class OmniProxyUnitTest {

    @Test
    fun defaultProxyConfig_hasNoHardcodedWeakPassphrase() {
        val config = ProxyConfig()
        assertNotEquals("12345678", config.wifiDirectPassphrase)
        assertEquals("", config.wifiDirectPassphrase)
    }

    @Test
    fun generateRandomPassphrase_meetsLengthAndEntropyRequirements() {
        val p1 = CryptoHelper.generateRandomPassphrase()
        val p2 = CryptoHelper.generateRandomPassphrase()
        assertEquals(12, p1.length)
        assertEquals(12, p2.length)
        assertNotEquals(p1, p2)
        assertTrue(p1.all { it.isLetterOrDigit() })
    }

    @Test
    fun nativeProxy_returnsFalseWhenNativeLibraryNotLoaded() {
        if (!NativeProxy.isNativeReady()) {
            assertFalse(NativeProxy.startHttp("127.0.0.1", 8080, "", ""))
            assertFalse(NativeProxy.startSocks5("127.0.0.1", 1080, "", ""))
            assertFalse(NativeProxy.startPac("127.0.0.1", 8088, "127.0.0.1", 8080, 1080))
        }
    }
}
