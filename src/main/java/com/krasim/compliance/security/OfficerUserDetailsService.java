package com.krasim.compliance.security;

import com.krasim.compliance.repository.OfficerRepository;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

@Service
public class OfficerUserDetailsService implements UserDetailsService {

    private final OfficerRepository officerRepository;

    public OfficerUserDetailsService(OfficerRepository officerRepository) {
        this.officerRepository = officerRepository;
    }

    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        return officerRepository.findByUsername(username)
                .map(officer -> User.withUsername(officer.getUsername())
                        .password(officer.getPasswordHash())
                        .roles(officer.getRole().name())
                        .disabled(!officer.isEnabled())
                        .build())
                .orElseThrow(() -> new UsernameNotFoundException("Officer not found"));
    }
}