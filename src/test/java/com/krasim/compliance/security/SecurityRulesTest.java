package com.krasim.compliance.security;

import com.krasim.compliance.controller.AuditController;
import com.krasim.compliance.controller.AuthController;
import com.krasim.compliance.controller.DeviceController;
import com.krasim.compliance.controller.InvoiceController;
import com.krasim.compliance.controller.PageController;
import com.krasim.compliance.dto.InvoiceResponse;
import com.krasim.compliance.dto.PosDevicePrincipal;
import com.krasim.compliance.exception.DeviceNotActiveException;
import com.krasim.compliance.exception.TaxpayerNotFoundException;
import com.krasim.compliance.service.AuditAction;
import com.krasim.compliance.service.AuditService;
import com.krasim.compliance.service.DeviceService;
import com.krasim.compliance.service.InvoiceService;
import com.krasim.compliance.service.TaxpayerService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Optional;

import static org.hamcrest.Matchers.endsWith;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.never;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrlPattern;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = {
                PageController.class, InvoiceController.class, AuthController.class,
                DeviceController.class, AuditController.class })
@Import({ SecurityConfig.class, LoginAttemptService.class })
class SecurityRulesTest {

        private static final String API = "/api/v1/invoices/submit";
        private static final String PIN = "A112233445Z";
        private static final String VALID_KEY = "kra_a1b2c3d4_" + "0".repeat(64);
        private static final String BODY = "{\"taxpayerPin\":\"" + PIN + "\",\"amountBeforeTax\":10000}";

        private static final PosDevicePrincipal DEVICE = new PosDevicePrincipal(1, "SCU000001", "Test POS");

        private static final InvoiceResponse CREATED = new InvoiceResponse(
                        7, "SCU000001/000001", PIN, "SCU000001",
                        new BigDecimal("10000.00"), new BigDecimal("1600.00"), new BigDecimal("11600.00"),
                        LocalDateTime.of(2026, 10, 15, 8, 0));

        @Autowired
        private MockMvc mockMvc;
        @Autowired
        private LoginAttemptService attempts;

        @MockBean
        private InvoiceService invoiceService;
        @MockBean
        private TaxpayerService taxpayerService;
        @MockBean
        private DeviceService deviceService;
        @MockBean
        private AuditService auditService;

        @BeforeEach
        void knownDevice() {
                // Only VALID_KEY authenticates. Any other key gets Mockito's default:
                // Optional.empty().
                when(deviceService.authenticate(VALID_KEY)).thenReturn(Optional.of(DEVICE));
        }

        // ================= Redirect path: web pages =================

        @ParameterizedTest
        @ValueSource(strings = {"/dashboard", "/taxpayers/register", "/invoices/new", "/devices"})
        @WithMockUser(roles = "OFFICER")
        void officersCanOpenProtectedPages(String url) throws Exception {
                mockMvc.perform(get(url)).andExpect(status().isOk());
        }

        @Test
        void loginPageIsPublic() throws Exception {
                mockMvc.perform(get("/login")).andExpect(status().isOk());
        }

        @Test
        @WithMockUser(roles = "OFFICER")
        void logoutRedirectsToLoginPage() throws Exception {
                mockMvc.perform(post("/logout").with(csrf()))
                                .andExpect(status().is3xxRedirection())
                                .andExpect(header().string("Location", endsWith("/login?logout")));
        }

        @Test
        @WithMockUser(roles = "OFFICER")
        void formPostWithoutCsrfTokenIsForbidden() throws Exception {
                mockMvc.perform(post("/taxpayers/register")
                                .param("pin", PIN).param("companyName", "X").param("email", "x@x.co.ke"))
                                .andExpect(status().isForbidden());
        }

        // ================= Login throttling =================

        @Test
        void signInIsRejectedWithoutCheckingThePasswordOnceLockedOut() throws Exception {
                for (int i = 0; i < 5; i++) {
                        attempts.recordFailure("mallory", "127.0.0.1"); // MockMvc requests come from 127.0.0.1
                }

                mockMvc.perform(post("/login").with(csrf())
                                .param("username", "mallory").param("password", "even-the-right-one"))
                                .andExpect(status().is3xxRedirection())
                                .andExpect(redirectedUrl("/login?locked"));

                verify(auditService).record(eq("mallory"), eq(AuditAction.LOGIN_BLOCKED),
                                isNull(), anyString(), anyString());
        }
            // ================= Role separation =================

    @Test
    @WithMockUser(roles = "OFFICER")
    void officersCannotReadTheAuditLog() throws Exception {
        mockMvc.perform(get("/audit")).andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = "AUDITOR")
    void auditorsCanReadTheAuditLog() throws Exception {
        mockMvc.perform(get("/audit")).andExpect(status().isOk());
    }

    @ParameterizedTest
    @ValueSource(strings = {"/dashboard", "/taxpayers/register", "/invoices/new", "/devices"})
    @WithMockUser(roles = "AUDITOR")
    void auditorsCannotOpenManagementPages(String url) throws Exception {
        mockMvc.perform(get(url)).andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = "AUDITOR")
    void auditorsCannotManageDevices() throws Exception {
        mockMvc.perform(post("/devices").with(csrf()).param("deviceName", "Rogue till"))
                .andExpect(status().isForbidden());
        mockMvc.perform(post("/devices/1/revoke").with(csrf()))
                .andExpect(status().isForbidden());

        verify(deviceService, never()).register(anyString());
        verify(deviceService, never()).revoke(any());
    }

        // ================= 401 path: API =================

        @Test
        void apiWithoutKeyIsUnauthorized() throws Exception {
                mockMvc.perform(post(API).contentType(MediaType.APPLICATION_JSON).content(BODY))
                                .andExpect(status().isUnauthorized())
                                .andExpect(jsonPath("$.error").value("Missing or invalid X-API-Key header."));

                verifyNoInteractions(invoiceService);
        }

        @Test
        void apiWithUnknownOrRevokedKeyIsUnauthorized() throws Exception {
                mockMvc.perform(post(API)
                                .header("X-API-Key", "kra_deadbeef_" + "f".repeat(64))
                                .contentType(MediaType.APPLICATION_JSON).content(BODY))
                                .andExpect(status().isUnauthorized());

                verifyNoInteractions(invoiceService);
        }

        @Test
        void deviceRevokedDuringTheRequestIsUnauthorized() throws Exception {
                when(invoiceService.submitInvoice(eq(PIN), any(BigDecimal.class), eq(1), anyString()))
                                .thenThrow(new DeviceNotActiveException());

                mockMvc.perform(post(API).header("X-API-Key", VALID_KEY)
                                .contentType(MediaType.APPLICATION_JSON).content(BODY))
                                .andExpect(status().isUnauthorized());
        }

        // ================= 201 path: API =================

        @Test
        void validDeviceKeyCreatesTheInvoice() throws Exception {
                when(invoiceService.submitInvoice(eq(PIN), any(BigDecimal.class), eq(1), anyString()))
                                .thenReturn(CREATED);

                mockMvc.perform(post(API).header("X-API-Key", VALID_KEY)
                                .contentType(MediaType.APPLICATION_JSON).content(BODY))
                                .andExpect(status().isCreated())
                                .andExpect(jsonPath("$.vatAmount").value(1600.00))
                                .andExpect(jsonPath("$.controlUnitInvoiceNo").value("SCU000001/000001"))
                                .andExpect(jsonPath("$.deviceSerial").value("SCU000001"));
        }

        // ================= Errors behind a valid key =================

        @Test
        void negativeAmountIsRejectedWith400() throws Exception {
                mockMvc.perform(post(API).header("X-API-Key", VALID_KEY)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"taxpayerPin\":\"" + PIN + "\",\"amountBeforeTax\":-5}"))
                                .andExpect(status().isBadRequest())
                                .andExpect(jsonPath("$.error").value("amountBeforeTax must be greater than zero."));

                verifyNoInteractions(invoiceService);
        }

        @Test
        void unknownTaxpayerReturns404() throws Exception {
                when(invoiceService.submitInvoice(eq("A999999999Z"), any(BigDecimal.class), eq(1), anyString()))
                                .thenThrow(new TaxpayerNotFoundException("A999999999Z"));

                mockMvc.perform(post(API).header("X-API-Key", VALID_KEY)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"taxpayerPin\":\"A999999999Z\",\"amountBeforeTax\":500}"))
                                .andExpect(status().isNotFound());
        }
}