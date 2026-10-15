package com.krasim.compliance.controller;

import com.krasim.compliance.service.AuditAction;
import com.krasim.compliance.service.AuditService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

@Controller
@RequestMapping("/audit")
public class AuditController {

    private final AuditService auditService;

    public AuditController(AuditService auditService) {
        this.auditService = auditService;
    }

    @GetMapping
    public String view(@RequestParam(required = false) String action, Model model) {
        AuditAction selected = AuditAction.parse(action); // unknown values are ignored, never trusted
        model.addAttribute("activePage", "audit");
        model.addAttribute("events", auditService.recent(selected));
        model.addAttribute("actions", AuditAction.values());
        model.addAttribute("selected", selected);
        return "audit";
    }
}