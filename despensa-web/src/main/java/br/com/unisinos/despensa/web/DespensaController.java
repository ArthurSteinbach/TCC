package br.com.unisinos.despensa.web;

import br.com.unisinos.despensa.seguranca.UsuarioLogado;
import br.com.unisinos.despensa.service.LoteService;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

import java.time.Clock;
import java.time.LocalDate;
import java.util.List;

@Controller
public class DespensaController {

    static final String MENSAGEM_ALTERACAO_CONCORRENTE = "Este item foi alterado em outra tela, atualize a página";

    private final LoteService loteService;
    private final Clock relogio;

    public DespensaController(LoteService loteService, Clock relogio) {
        this.loteService = loteService;
        this.relogio = relogio;
    }

    @GetMapping("/")
    public String despensa(@AuthenticationPrincipal UsuarioLogado usuario, Model model) {
        LocalDate hoje = LocalDate.now(relogio);
        List<ItemDespensa> itens = loteService.listarAtivosFEFO(usuario.getId()).stream()
                .map(l -> ItemDespensa.de(l, hoje))
                .toList();

        model.addAttribute("menu", "despensa");
        model.addAttribute("itens", itens);
        model.addAttribute("quantidadeEmAlerta", itens.stream().filter(ItemDespensa::emAlerta).count());
        model.addAttribute("perdaTotal", loteService.calcularPerdaFinanceiraTotal(usuario.getId()));
        return "despensa";
    }

    @GetMapping("/alertas")
    public String alertas(@AuthenticationPrincipal UsuarioLogado usuario, Model model) {
        LocalDate hoje = LocalDate.now(relogio);
        model.addAttribute("menu", "alertas");
        model.addAttribute("itens", loteService.listarAlertaVencimento(usuario.getId()).stream()
                .map(l -> ItemDespensa.de(l, hoje))
                .toList());
        return "alertas";
    }
}
