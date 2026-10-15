package com.krasim.compliance.security;

import com.krasim.compliance.service.AuditAction;
import com.krasim.compliance.service.AuditService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.web.util.matcher.AntPathRequestMatcher;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * Runs before Spring checks the password. If the username or IP is blocked, the
 * request is
 * turned away immediately, so even a correct password cannot be used during a
 * lockout.
 *
 * Deliberately NOT a @Component: SecurityConfig attaches it to the web chain
 * only.
 */
public class LoginThrottleFilter extends OncePerRequestFilter {

    private static final AntPathRequestMatcher LOGIN_POST = new AntPathRequestMatcher("/login", "POST");

    private final LoginAttemptService attempts;
    private final AuditService audit;

    public LoginThrottleFilter(LoginAttemptService attempts, AuditService audit) {
        this.attempts = attempts;
        this.audit = audit;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !LOGIN_POST.matches(request);
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
            HttpServletResponse response,
            FilterChain chain) throws ServletException, IOException {
        String username = request.getParameter("username");
        String ip = request.getRemoteAddr();

        if (attempts.isBlocked(username, ip)) {
            audit.record(username, AuditAction.LOGIN_BLOCKED, null,
                    "Sign-in rejected: too many failed attempts", ip);
            response.sendRedirect(request.getContextPath() + "/login?locked");
            return;
        }

        chain.doFilter(request, response);
    }
}