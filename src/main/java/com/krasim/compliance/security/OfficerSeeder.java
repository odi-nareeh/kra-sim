package com.krasim.compliance.security;

import com.krasim.compliance.entity.Officer;
import com.krasim.compliance.repository.OfficerRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

/** Creates the initial compliance officer if it does not exist yet. */
@Component
public class OfficerSeeder implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(OfficerSeeder.class);

    private final OfficerRepository officerRepository;
    private final PasswordEncoder passwordEncoder;
    private final String username;
    private final String password;

    public OfficerSeeder(OfficerRepository officerRepository,
            PasswordEncoder passwordEncoder,
            @Value("${kra.officer.username}") String username,
            @Value("${kra.officer.password}") String password) {
        this.officerRepository = officerRepository;
        this.passwordEncoder = passwordEncoder;
        this.username = username;
        this.password = password;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (officerRepository.existsByUsername(username)) {
            return;
        }
        officerRepository.save(new Officer(username, passwordEncoder.encode(password)));
        log.info("Created compliance officer '{}'", username); // never log the password
    }
}