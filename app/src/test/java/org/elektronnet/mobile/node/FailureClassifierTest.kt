package org.elektronnet.mobile.node

import org.junit.Assert.assertEquals
import org.junit.Test

class FailureClassifierTest {
    @Test
    fun classifiesKnownMessages() {
        assertEquals(NodeFailure.DiskFull, FailureClassifier.classify(listOf("Error: Disk space is too low!")))
        assertEquals(NodeFailure.CorruptedDatabase, FailureClassifier.classify(listOf("x", "Error opening block database")))
        assertEquals(NodeFailure.AlreadyRunning, FailureClassifier.classify(listOf("Cannot obtain a lock on directory /d. Elektron is probably already running.")))
    }

    @Test
    fun unknownByDefault() {
        assertEquals(NodeFailure.Unknown, FailureClassifier.classify(listOf("something else")))
        assertEquals(NodeFailure.Unknown, FailureClassifier.classify(emptyList()))
    }
}
