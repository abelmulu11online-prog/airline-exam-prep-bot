package com.airlineprep.bot.admin;

import java.security.Principal;
import jakarta.validation.Valid;
import com.airlineprep.bot.settings.*;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;

@Controller
@RequestMapping("/admin/settings")
public class SettingsController {
    private final SettingsService settings;
    public SettingsController(SettingsService settings) { this.settings=settings; }
    @GetMapping
    public String show(Model model) {
        model.addAttribute("form",SettingsForm.from(settings.current()));
        return "admin/settings";
    }
    @PostMapping
    public String save(@Valid @ModelAttribute("form") SettingsForm form, BindingResult result, Principal admin) {
        if (result.hasErrors()) return "admin/settings";
        settings.update(form,admin.getName());
        return "redirect:/admin/settings?saved";
    }
}
