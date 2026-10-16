package io.github.baitian6641.cloudy.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Test

class EndpointHostTest {
    @Test fun dnsAndInternationalNamesCanonicalizeWithoutResolving() {
        for ((input, expected) in listOf(
            "EXAMPLE.org." to "example.org",
            "bücher.example" to "xn--bcher-kva.example",
            "XN--BCHER-KVA.Example" to "xn--bcher-kva.example",
            "LOCALHOST" to "localhost",
            "not-enrolled.invalid" to "not-enrolled.invalid",
            "a-1.example" to "a-1.example",
        )) {
            assertEquals(expected, EndpointHost.canonicalize(input))
            assertEquals(expected, EndpointHost.canonicalize(expected))
        }
    }

    @Test fun numericAddressesExpandAndPreserveCaseSensitiveScope() {
        for ((input, expected) in listOf(
            "192.0.2.1" to "192.0.2.1",
            "192.0.2.1." to "192.0.2.1",
            "0.0.0.0" to "0.0.0.0",
            "255.255.255.255" to "255.255.255.255",
            "[2001:DB8::1]" to "2001:db8:0:0:0:0:0:1",
            "2001:0db8:0000:0000:0000:0000:0000:0001" to "2001:db8:0:0:0:0:0:1",
            "::ffff:192.0.2.1" to "0:0:0:0:0:ffff:c000:201",
            "::" to "0:0:0:0:0:0:0:0",
            "2001::" to "2001:0:0:0:0:0:0:0",
            "[FE80::1%Eth_0]" to "fe80:0:0:0:0:0:0:1%Eth_0",
            "fe80::1%eth_0" to "fe80:0:0:0:0:0:0:1%eth_0",
        )) {
            assertEquals(expected, EndpointHost.canonicalize(input))
            assertEquals(expected, EndpointHost.canonicalize(expected))
        }
    }

    @Test fun dnsLabelTotalNameAndScopeBudgetsRetainTheirExistingBoundaries() {
        val name = listOf("a".repeat(63), "b".repeat(63), "c".repeat(63), "d".repeat(61)).joinToString(".")
        assertEquals(253, name.length)
        assertEquals(name, EndpointHost.canonicalize("$name."))
        assertEquals("0:0:0:0:0:0:0:1%" + "a".repeat(64), EndpointHost.canonicalize("::1%" + "a".repeat(64)))
        for (input in listOf("a".repeat(64) + ".example", name + "d", "::1%" + "a".repeat(65), "a".repeat(1025))) {
            assertThrows(IllegalArgumentException::class.java) { EndpointHost.canonicalize(input) }
        }
    }

    @Test fun malformedOrAmbiguousAddressesAndHostSyntaxAreRejected() {
        for (input in listOf("", ".", "https://example.org", "u@example.org", "x/y", "x\\y", "x?y", "x#y",
                "x\ny", "x y", "x\u0000y", "x\u0085y", "a..b", "-a.example", "a-.example", "a_b.example",
                "01.2.3.4", "256.0.0.1", "127.1", "1.2.3.4.5", "1::2::3", "1:2:3",
                "1:2:3:4:5:6:7:8:9", "1:2:3:4:5:6:7:8::", "::ffff:01.2.3.4", "fe80::1%",
                "fe80::1%eth/0", "fe80::1%eth:0", "fe80::1%a%b", "example.org%eth0", "[::1]:22",
                "[::1", "::1]", "[example.org]", "[[]]", "12345::", "gggg::1")) {
            assertThrows(input, IllegalArgumentException::class.java) { EndpointHost.canonicalize(input) }
        }
    }

    @Test fun extractedFailuresKeepExistingFixedDiagnostics() {
        for ((input, message) in listOf(
            "u@example.org" to "Invalid logical host",
            "256.0.0.1" to "Invalid numeric host",
            "::1%" to "Invalid address scope",
        )) {
            val error = assertThrows(IllegalArgumentException::class.java) { EndpointHost.canonicalize(input) }
            assertEquals(message, error.message)
            assertNull(error.cause)
        }
    }
}
