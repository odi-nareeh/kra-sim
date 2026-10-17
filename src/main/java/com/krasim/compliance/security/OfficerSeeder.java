package com.krasim.compliance.security;

import com.krasim.compliance.entity.Officer;
import com.krasim.compliance.entity.Officer.Role;
import com.krasim.compliance.repository.OfficerRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

/** Creates the initial officer and auditor accounts if they do not exist yet. */
@Component
public class OfficerSeeder implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(OfficerSeeder.class);

    private final OfficerRepository officerRepository;
    private final PasswordEncoder passwordEncoder;
    private final String officerUsername;
    private final String officerPassword;
    private final String auditorUsername;
    private final String auditorPassword;

    public OfficerSeeder(OfficerRepository officerRepository,
                         PasswordEncoder passwordEncoder,
                         @Value("${kra.officer.username}") String officerUsername,
                         @Value("${kra.officer.password}") String officerPassword,
                         @Value("${kra.auditor.username}") String auditorUsername,
                         @Value("${kra.auditor.password}") String auditorPassword) {
        this.officerRepository = officerRepository;
        this.passwordEncoder = passwordEncoder;
        this.officerUsername = officerUsername;
        this.officerPassword = officerPassword;
        this.auditorUsername = auditorUsername;
        this.auditorPassword = auditorPassword;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (officerUsername.equalsIgnoreCase(auditorUsername)) {
            throw new IllegalStateException("The officer and auditor usernames must be different.");
        }
        seed(officerUsername, officerPassword, Role.OFFICER);
        seed(auditorUsername, auditorPassword, Role.AUDITOR);
    }

    private void seed(String username, String password, Role role) {
        if (officerRepository.existsByUsername(username)) {
            return;
        }
        officerRepository.save(new Officer(username, passwordEncoder.encode(password), role));
        log.info("Created {} account '{}'", role, username); // never log the password
    }
}