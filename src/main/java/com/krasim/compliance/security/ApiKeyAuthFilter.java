package com.krasim.compliance.security;

import com.krasim.compliance.service.DeviceService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

/**
 * Authenticates POS devices from the X-API-Key header. A valid key for an
 * ACTIVE device
 * becomes the authenticated principal (PosDevicePrincipal) with
 * ROLE_POS_DEVICE.
 * Anything else stays unauthenticated and the security chain answers 401.
 *
 * Deliberately NOT a @Component: Spring Boot would register it as a global
 * servlet filter.
 * SecurityConfig creates it and attaches it to the /api chain only.
 */
public class ApiKeyAuthFilter extends OncePerRequestFilter {

    public static final String HEADER = "X-API-Key";

    private final DeviceService deviceService;

    public ApiKeyAuthFilter(DeviceService deviceService) {
        this.deviceService = deviceService;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
            HttpServletResponse response,
            FilterChain chain) throws ServletException, IOException {
        String provided = request.getHeader(HEADER);

        if (provided != null) {
            deviceService.authenticate(provided.trim()).ifPresent(device -> {
                var authentication = new UsernamePasswordAuthenticationToken(
                        device, null, List.of(new SimpleGrantedAuthority("ROLE_POS_DEVICE")));
                SecurityContext context = SecurityContextHolder.createEmptyContext();
                context.setAuthentication(authentication);
                SecurityContextHolder.setContext(context);
            });
        }

        chain.doFilter(request, response);
    }
}