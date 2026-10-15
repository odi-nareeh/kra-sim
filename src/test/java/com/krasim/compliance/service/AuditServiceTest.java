package com.krasim.compliance.service;

import com.krasim.compliance.entity.AuditEvent;
import com.krasim.compliance.repository.AuditEventRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class AuditServiceTest {

    @Mock
    private AuditEventRepository repository;
    @InjectMocks
    private AuditService service;

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    private AuditEvent savedEvent() {
        ArgumentCaptor<AuditEvent> captor = ArgumentCaptor.forClass(AuditEvent.class);
        verify(repository).save(captor.capture());
        return captor.getValue();
    }

    @Test
    void storesAllFields() {
        service.record("officer", AuditAction.LOGIN_SUCCESS, "SCU000001", "details", "197.1.1.1");

        AuditEvent event = savedEvent();
        assertEquals("officer", event.getActor());
        assertEquals("LOGIN_SUCCESS", event.getAction());
        assertEquals("SCU000001", event.getTarget());
        assertEquals("details", event.getDetails());
        assertEquals("197.1.1.1", event.getSourceIp());
    }

    @Test
    void controlCharactersAreRemovedSoLogLinesCannotBeForged() {
        service.record("evil\nLOGIN_SUCCESS", AuditAction.LOGIN_FAILURE, null, null, null);

        assertEquals("evil LOGIN_SUCCESS", savedEvent().getActor());
    }

    @Test
    void overlongValuesAreTruncatedToTheColumnSize() {
        service.record("officer", AuditAction.LOGIN_FAILURE, null, "x".repeat(500), null);

        assertEquals(300, savedEvent().getDetails().length());
    }

    @Test
    void blankActorBecomesUnknown() {
        service.record("   ", AuditAction.LOGIN_FAILURE, null, null, null);

        assertEquals("unknown", savedEvent().getActor());
        assertNull(savedEvent().getTarget());
    }

    @Test
    void actorComesFromTheSignedInOfficer() {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken("officer", null, List.of()));

        service.record(AuditAction.DEVICE_REVOKED, "SCU000001", null);

        assertEquals("officer", savedEvent().getActor());
    }

    @Test
    void actorIsSystemWhenNobodyIsSignedIn() {
        service.record(AuditAction.DEVICE_REVOKED, "SCU000001", null);

        assertEquals("system", savedEvent().getActor());
    }
}