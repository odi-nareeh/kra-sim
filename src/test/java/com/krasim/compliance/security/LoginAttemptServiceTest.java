package com.krasim.compliance.security;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LoginAttemptServiceTest {

    private static final String IP = "197.232.10.5";

    /**
     * A clock the test can move forward, so lockout expiry is tested without
     * waiting.
     */
    private static class MutableClock extends Clock {
        private Instant now;

        MutableClock(Instant start) {
            this.now = start;
        }

        void advance(Duration amount) {
            now = now.plus(amount);
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return now;
        }
    }

    private MutableClock clock;
    private LoginAttemptService service;

    @BeforeEach
    void setUp() {
        clock = new MutableClock(Instant.parse("2026-10-15T08:00:00Z"));
        // 3 failures per username, 5 per IP, 15-minute window
        service = new LoginAttemptService(3, 5, Duration.ofMinutes(15), clock);
    }

    @Test
    void notBlockedBeforeTheLimit() {
        service.recordFailure("officer", IP);
        service.recordFailure("officer", IP);

        assertFalse(service.isBlocked("officer", IP));
    }

    @Test
    void blockedAtTheLimit() {
        for (int i = 0; i < 3; i++)
            service.recordFailure("officer", IP);

        assertTrue(service.isBlocked("officer", IP));
    }

    @Test
    void blockEndsAfterTheWindow() {
        for (int i = 0; i < 3; i++)
            service.recordFailure("officer", IP);

        clock.advance(Duration.ofMinutes(16));

        assertFalse(service.isBlocked("officer", IP));
    }

    @Test
    void successResetsTheUsernameCounter() {
        service.recordFailure("officer", IP);
        service.recordFailure("officer", IP);
        service.recordSuccess("officer");
        service.recordFailure("officer", IP);
        service.recordFailure("officer", IP);

        assertFalse(service.isBlocked("officer", IP)); // 2 failures since the reset, limit is 3
    }

    @Test
    void usernameMatchingIgnoresCase() {
        service.recordFailure("Officer", "1.1.1.1");
        service.recordFailure("OFFICER", "1.1.1.2");
        service.recordFailure("officer", "1.1.1.3");

        assertTrue(service.isBlocked("officer", "9.9.9.9"));
    }

    @Test
    void ipLimitBlocksAcrossDifferentUsernames() {
        for (int i = 0; i < 5; i++)
            service.recordFailure("user" + i, IP);

        assertTrue(service.isBlocked("someoneElse", IP));
    }

    @Test
    void oneBlockedUserDoesNotBlockAnother() {
        for (int i = 0; i < 3; i++)
            service.recordFailure("mallory", IP);

        assertTrue(service.isBlocked("mallory", IP));
        assertFalse(service.isBlocked("officer", "8.8.8.8"));
    }

    @Test
    void failuresOutsideTheWindowDoNotAccumulate() {
        service.recordFailure("officer", IP);
        service.recordFailure("officer", IP);

        clock.advance(Duration.ofMinutes(16));

        service.recordFailure("officer", IP);
        service.recordFailure("officer", IP);

        assertFalse(service.isBlocked("officer", IP)); // the counter restarted after the window
    }
}