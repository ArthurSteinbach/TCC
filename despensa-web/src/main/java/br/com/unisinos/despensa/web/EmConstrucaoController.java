package br.com.unisinos.despensa.web;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

import java.util.Map;

@Controller
public class EmConstrucaoController {

    private static final Map<String, String> PAGINAS = Map.of(
            "consumir", "Consumir",
            "alertas", "Alertas",
            "relatorio", "Relatório",
            "produtos", "Produtos");

    @GetMapping("/{pagina:consumir|alertas|relatorio|produtos}")
    public String emConstrucao(@PathVariable String pagina, Model model) {
        model.addAttribute("menu", pagina);
        model.addAttribute("titulo", PAGINAS.get(pagina));
        return "em-construcao";
    }
}
