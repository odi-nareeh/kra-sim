package com.krasim.compliance.security;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Tracks failed sign-ins per username and per IP address, in memory.
 * After too many failures within the window, that username or IP is blocked
 * until the window passes.
 */
@Service
public class LoginAttemptService {

    private static final int PURGE_THRESHOLD = 10_000;

    private static final class Entry {
        private final Instant firstFailure;
        private int failures;
        private volatile Instant blockedUntil;

        Entry(Instant firstFailure) {
            this.firstFailure = firstFailure;
        }

        boolean blocked(Instant now) {
            return blockedUntil != null && now.isBefore(blockedUntil);
        }

        /**
         * An entry is stale once its block ended, or its counting window passed without
         * a block.
         */
        boolean expired(Instant now, Duration window) {
            return blockedUntil != null
                    ? !now.isBefore(blockedUntil)
                    : now.isAfter(firstFailure.plus(window));
        }
    }

    private final int maxUserFailures;
    private final int maxIpFailures;
    private final Duration window;
    private final Clock clock;

    private final Map<String, Entry> users = new ConcurrentHashMap<>();
    private final Map<String, Entry> ips = new ConcurrentHashMap<>();

    @Autowired
    public LoginAttemptService(@Value("${kra.security.max-user-failures:5}") int maxUserFailures,
            @Value("${kra.security.max-ip-failures:20}") int maxIpFailures,
            @Value("${kra.security.lockout-minutes:15}") long lockoutMinutes) {
        this(maxUserFailures, maxIpFailures, Duration.ofMinutes(lockoutMinutes), Clock.systemUTC());
    }

    /** Used by tests, which need a controllable clock. */
    LoginAttemptService(int maxUserFailures, int maxIpFailures, Duration window, Clock clock) {
        this.maxUserFailures = maxUserFailures;
        this.maxIpFailures = maxIpFailures;
        this.window = window;
        this.clock = clock;
    }

    public boolean isBlocked(String username, String ip) {
        Instant now = clock.instant();
        Entry byUser = users.get(userKey(username));
        if (byUser != null && byUser.blocked(now)) {
            return true;
        }
        Entry byIp = ip == null ? null : ips.get(ip);
        return byIp != null && byIp.blocked(now);
    }

    public void recordFailure(String username, String ip) {
        register(users, userKey(username), maxUserFailures);
        if (ip != null) {
            register(ips, ip, maxIpFailures);
        }
    }

    /** Clears the username's counter only. The IP counter is deliberately kept. */
    public void recordSuccess(String username) {
        users.remove(userKey(username));
    }

    // ---------- helpers ----------

    private void register(Map<String, Entry> map, String key, int maxFailures) {
        Instant now = clock.instant();
        if (map.size() > PURGE_THRESHOLD) {
            map.values().removeIf(entry -> entry.expired(now, window)); // keeps memory bounded under attack
        }
        map.compute(key, (k, existing) -> {
            Entry entry = (existing == null || existing.expired(now, window)) ? new Entry(now) : existing;
            entry.failures++;
            if (entry.failures >= maxFailures) {
                entry.blockedUntil = now.plus(window);
            }
            return entry;
        });
    }

    private static String userKey(String username) {
        return username == null ? "" : username.trim().toLowerCase(Locale.ROOT);
    }
}