package br.com.unisinos.despensa.web;

import br.com.unisinos.despensa.status.StatusBancoService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class InicioController {

    private final StatusBancoService statusBancoService;

    public InicioController(StatusBancoService statusBancoService) {
        this.statusBancoService = statusBancoService;
    }

    @GetMapping("/")
    public String inicio(Model model) {
        model.addAttribute("status", statusBancoService.verificar());
        return "inicio";
    }
}
