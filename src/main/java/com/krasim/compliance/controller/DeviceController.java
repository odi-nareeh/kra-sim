package com.krasim.compliance.controller;

import com.krasim.compliance.service.DeviceService;
import com.krasim.compliance.service.DeviceService.IssuedKey;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
@RequestMapping("/devices")
public class DeviceController {

    private final DeviceService deviceService;

    public DeviceController(DeviceService deviceService) {
        this.deviceService = deviceService;
    }

    @GetMapping
    public String list(Model model) {
        model.addAttribute("activePage", "devices");
        model.addAttribute("devices", deviceService.findAll());
        return "devices";
    }

    @PostMapping
    public String register(@RequestParam String deviceName, RedirectAttributes redirect) {
        String name = deviceName.trim();
        if (name.isEmpty() || name.length() > 100) {
            redirect.addFlashAttribute("error", "Enter a device name of up to 100 characters.");
            return "redirect:/devices";
        }
        reveal(redirect, deviceService.register(name), "registered");
        return "redirect:/devices";
    }

    @PostMapping("/{id}/rotate")
    public String rotate(@PathVariable Integer id, RedirectAttributes redirect) {
        try {
            reveal(redirect, deviceService.rotate(id), "rotated");
        } catch (IllegalArgumentException | IllegalStateException ex) {
            redirect.addFlashAttribute("error", ex.getMessage());
        }
        return "redirect:/devices";
    }

    @PostMapping("/{id}/revoke")
    public String revoke(@PathVariable Integer id, RedirectAttributes redirect) {
        try {
            deviceService.revoke(id);
            redirect.addFlashAttribute("success", "Device revoked. Its key no longer works.");
        } catch (IllegalArgumentException ex) {
            redirect.addFlashAttribute("error", ex.getMessage());
        }
        return "redirect:/devices";
    }

    /**
     * Passes the one-time key to the next page view only. It is never logged or
     * stored.
     */
    private void reveal(RedirectAttributes redirect, IssuedKey issued, String action) {
        redirect.addFlashAttribute("newKey", issued.rawKey());
        redirect.addFlashAttribute("newKeySerial", issued.device().getSerial());
        redirect.addFlashAttribute("success", "Device " + issued.device().getSerial() + " " + action + ".");
    }
}