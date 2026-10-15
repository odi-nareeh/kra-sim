package com.krasim.compliance.security;

import com.krasim.compliance.service.AuditAction;
import com.krasim.compliance.service.AuditService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.security.authentication.event.AbstractAuthenticationFailureEvent;
import org.springframework.security.authentication.event.AuthenticationSuccessEvent;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.authentication.WebAuthenticationDetails;
import org.springframework.stereotype.Component;

@Component
public class AuthEventListener {

    private static final Logger log = LoggerFactory.getLogger(AuthEventListener.class);

    private final LoginAttemptService attempts;
    private final AuditService audit;

    public AuthEventListener(LoginAttemptService attempts, AuditService audit) {
        this.attempts = attempts;
        this.audit = audit;
    }

    @EventListener
    public void onSuccess(AuthenticationSuccessEvent event) {
        Authentication auth = event.getAuthentication();
        attempts.recordSuccess(auth.getName());
        safeAudit(auth.getName(), AuditAction.LOGIN_SUCCESS, null, ipOf(auth));
    }

    @EventListener
    public void onFailure(AbstractAuthenticationFailureEvent event) {
        Authentication auth = event.getAuthentication();
        String ip = ipOf(auth);
        attempts.recordFailure(auth.getName(), ip);
        // Only the exception type is logged, never the attempted password
        safeAudit(auth.getName(), AuditAction.LOGIN_FAILURE,
                event.getException().getClass().getSimpleName(), ip);
    }

    /**
     * A broken audit table must not stop every officer from signing in, so failures
     * are logged and swallowed.
     */
    private void safeAudit(String actor, AuditAction action, String details, String ip) {
        try {
            audit.record(actor, action, null, details, ip);
        } catch (RuntimeException ex) {
            log.error("Could not write audit event {}", action, ex);
        }
    }

    private static String ipOf(Authentication auth) {
        return auth.getDetails() instanceof WebAuthenticationDetails details
                ? details.getRemoteAddress()
                : null;
    }
}