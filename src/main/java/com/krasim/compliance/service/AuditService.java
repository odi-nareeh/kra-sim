package com.krasim.compliance.service;

import com.krasim.compliance.entity.AuditEvent;
import com.krasim.compliance.repository.AuditEventRepository;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.util.List;

@Service
public class AuditService {

    private static final int MAX_ACTOR = 60;
    private static final int MAX_TARGET = 100;
    private static final int MAX_DETAILS = 300;

    private final AuditEventRepository repository;

    public AuditService(AuditEventRepository repository) {
        this.repository = repository;
    }

    /**
     * Records an event for the signed-in officer and the current request's IP.
     * Joins the caller's transaction, so if the business action rolls back, so does
     * its audit row.
     */
    @Transactional
    public void record(AuditAction action, String target, String details) {
        record(currentActor(), action, target, details, currentIp());
    }

    /**
     * Records an event with an explicit actor and IP (used for logins, where nobody
     * is signed in yet).
     */
    @Transactional
    public void record(String actor, AuditAction action, String target, String details, String sourceIp) {
        String cleanActor = clean(actor, MAX_ACTOR);
        repository.save(new AuditEvent(
                cleanActor == null ? "unknown" : cleanActor,
                action.name(),
                clean(target, MAX_TARGET),
                clean(details, MAX_DETAILS),
                clean(sourceIp, 45)));
    }

    @Transactional(readOnly = true)
    public List<AuditEvent> recent(AuditAction filter) {
        return filter == null
                ? repository.findTop100ByOrderByOccurredAtDescIdDesc()
                : repository.findTop100ByActionOrderByOccurredAtDescIdDesc(filter.name());
    }

    // ---------- helpers ----------

    private static String currentActor() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated() || auth instanceof AnonymousAuthenticationToken) {
            return "system";
        }
        return auth.getName();
    }

    private static String currentIp() {
        if (RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attributes) {
            return attributes.getRequest().getRemoteAddr();
        }
        return null;
    }

    /**
     * Replaces control characters (log forging) and truncates to the column size.
     */
    private static String clean(String value, int maxLength) {
        if (value == null) {
            return null;
        }
        String cleaned = value.replaceAll("\\p{Cntrl}", " ").trim();
        if (cleaned.isEmpty()) {
            return null;
        }
        return cleaned.length() > maxLength ? cleaned.substring(0, maxLength) : cleaned;
    }
}