package com.company.forgeops.feedback;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import com.company.forgeops.feedback.domain.FeedbackIdentifier;
import org.junit.jupiter.api.Test;

class FeedbackIdentifierTest {

    @Test
    void parsesPrefixedIdentifier() {
        var p = FeedbackIdentifier.parse("ADB-FB-1001");
        assertEquals("ADB", p.prefix());
        assertEquals(1001L, p.displayNo());
    }

    @Test
    void parsesLegacyIdentifier() {
        var p = FeedbackIdentifier.parse("FB-1002");
        assertNull(p.prefix());
        assertEquals(1002L, p.displayNo());
    }

    @Test
    void lowerCasePrefixNormalized() {
        assertEquals("ADB", FeedbackIdentifier.parse("adb-FB-7").prefix());
    }

    @Test
    void rejectsGarbage() {
        assertNull(FeedbackIdentifier.parse("AKSO-2"));
        assertNull(FeedbackIdentifier.parse("MR !1"));
        assertNull(FeedbackIdentifier.parse(null));
    }
}
