package com.krasim.compliance.controller;

import com.krasim.compliance.dto.DashboardView;
import com.krasim.compliance.exception.DuplicateTaxpayerException;
import com.krasim.compliance.service.TaxpayerService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.Optional;
import java.util.regex.Pattern;

@Controller
public class PageController {

    // KRA PIN format: starts with A or P, 9 digits, ends with a letter (e.g.
    // A012345678Z)
    private static final Pattern PIN_PATTERN = Pattern.compile("^[AP]\\d{9}[A-Z]$");

    private final TaxpayerService taxpayerService;

    public PageController(TaxpayerService taxpayerService) {
        this.taxpayerService = taxpayerService;
    }

    @GetMapping("/")
    public String home() {
        return "redirect:/dashboard";
    }

    // ---------- Compliance dashboard ----------

    @GetMapping("/dashboard")
    public String dashboard(@RequestParam(required = false) String pin, Model model) {
        model.addAttribute("activePage", "dashboard");

        if (pin == null || pin.isBlank()) {
            model.addAttribute("searched", false);
            return "dashboard";
        }

        String cleanPin = pin.trim().toUpperCase();
        model.addAttribute("searched", true);
        model.addAttribute("pin", cleanPin);

        Optional<DashboardView> view = taxpayerService.findDashboard(cleanPin);
        if (view.isPresent()) {
            DashboardView data = view.get();
            model.addAttribute("taxpayer", data.taxpayer());
            model.addAttribute("ledger", data.ledger());
            model.addAttribute("invoiceCount", data.invoiceCount());
            model.addAttribute("recentInvoices", data.recentInvoices());
        }
        return "dashboard";
    }

    // ---------- Register taxpayer ----------

    @GetMapping("/taxpayers/register")
    public String registerForm(Model model) {
        model.addAttribute("activePage", "register");
        return "register-taxpayer";
    }

    @PostMapping("/taxpayers/register")
    public String registerSubmit(@RequestParam String pin,
            @RequestParam String companyName,
            @RequestParam String email,
            RedirectAttributes redirect,
            Model model) {
        String cleanPin = pin.trim().toUpperCase();

        String error = null;
        if (!PIN_PATTERN.matcher(cleanPin).matches()) {
            error = "Invalid PIN. A KRA PIN has 11 characters: A or P, then 9 digits, then a letter. Example: A112233445Z.";
        } else if (companyName.isBlank()) {
            error = "Company name is required.";
        } else {
            try {
                taxpayerService.register(cleanPin, companyName.trim(), email.trim());
            } catch (DuplicateTaxpayerException ex) {
                error = ex.getMessage();
            }
        }

        if (error != null) {
            model.addAttribute("activePage", "register");
            model.addAttribute("error", error);
            model.addAttribute("pin", cleanPin);
            model.addAttribute("companyName", companyName);
            model.addAttribute("email", email);
            return "register-taxpayer";
        }

        redirect.addFlashAttribute("success", "Taxpayer " + cleanPin + " registered successfully.");
        return "redirect:/dashboard?pin=" + cleanPin;
    }

    // ---------- eTIMS POS simulator ----------

    @GetMapping("/invoices/new")
    public String invoiceForm(Model model) {
        model.addAttribute("activePage", "invoice");
        return "submit-invoice";
    }
}