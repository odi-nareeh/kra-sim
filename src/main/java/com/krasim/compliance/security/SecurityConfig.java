package com.krasim.compliance.security;

import com.krasim.compliance.service.AuditAction;
import com.krasim.compliance.service.AuditService;
import com.krasim.compliance.service.DeviceService;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    /**
     * Chain 1: the POS API. Stateless, per-device API key, no login page, no cookies.
     * CSRF is disabled because there is no browser session to attack; the key is the credential.
     */
    @Bean
    @Order(1)
    public SecurityFilterChain apiChain(HttpSecurity http, DeviceService deviceService) throws Exception {
        http
                .securityMatcher("/api/**")
                .csrf(csrf -> csrf.disable())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth.anyRequest().hasRole("POS_DEVICE"))
                .addFilterBefore(new ApiKeyAuthFilter(deviceService), UsernamePasswordAuthenticationFilter.class)
                .exceptionHandling(ex -> ex.authenticationEntryPoint((request, response, e) -> {
                    response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                    response.setContentType("application/json");
                    response.getWriter().write("{\"error\":\"Missing or invalid X-API-Key header.\"}");
                }));
        return http.build();
    }

    /** Chain 2: the web pages. Form login with throttling and auditing, role-based access, CSRF on. */
    @Bean
    @Order(2)
    public SecurityFilterChain webChain(HttpSecurity http,
                                        LoginAttemptService attempts,
                                        AuditService audit) throws Exception {
        http
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/login", "/error", "/css/**", "/js/**", "/images/**").permitAll()
                        .requestMatchers("/").authenticated()                   // redirects each role to its home
                        .requestMatchers("/audit", "/audit/**").hasRole("AUDITOR")
                        .anyRequest().hasRole("OFFICER"))
                .addFilterBefore(new LoginThrottleFilter(attempts, audit), UsernamePasswordAuthenticationFilter.class)
                .formLogin(form -> form
                        .loginPage("/login")
                        .successHandler(homeByRole())
                        .failureUrl("/login?error")
                        .permitAll())
                .logout(logout -> logout
                        .addLogoutHandler((request, response, authentication) -> {
                            if (authentication != null) {
                                audit.record(authentication.getName(), AuditAction.LOGOUT,
                                        null, null, request.getRemoteAddr());
                            }
                        })
                        .logoutSuccessUrl("/login?logout")
                        .permitAll());
        return http.build();
    }

    /** Auditors land on the audit log, officers on the dashboard. */
    private static AuthenticationSuccessHandler homeByRole() {
        return (request, response, authentication) -> {
            boolean auditor = authentication.getAuthorities().stream()
                    .anyMatch(a -> "ROLE_AUDITOR".equals(a.getAuthority()));
            response.sendRedirect(request.getContextPath() + (auditor ? "/audit" : "/dashboard"));
        };
    }
}