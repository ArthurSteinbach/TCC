package br.com.unisinos.despensa.web;

import br.com.unisinos.despensa.exception.ValidacaoException;
import br.com.unisinos.despensa.seguranca.UsuarioLogado;
import br.com.unisinos.despensa.service.LoteService;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.time.Clock;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

@Controller
public class DespensaController {

    static final String MENSAGEM_ALTERACAO_CONCORRENTE = "Este item foi alterado em outra tela, atualize a página";

    private static final Map<String, String> DESTINOS_APOS_BAIXA = Map.of(
            "despensa", "redirect:/",
            "alertas", "redirect:/alertas");

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

    @PostMapping("/lotes/{produtoId}/{loteId}/consumir")
    public String consumir(@AuthenticationPrincipal UsuarioLogado usuario, @PathVariable String produtoId,
                           @PathVariable String loteId, @RequestParam(defaultValue = "despensa") String origem,
                           RedirectAttributes redirecionamento) {
        return darBaixa(redirecionamento, origem, "Item marcado como consumido.",
                () -> loteService.consumir(usuario.getId(), produtoId, loteId));
    }

    @PostMapping("/lotes/{produtoId}/{loteId}/descartar")
    public String descartar(@AuthenticationPrincipal UsuarioLogado usuario, @PathVariable String produtoId,
                            @PathVariable String loteId, @RequestParam(defaultValue = "despensa") String origem,
                            RedirectAttributes redirecionamento) {
        return darBaixa(redirecionamento, origem, "Item marcado como descartado.",
                () -> loteService.descartar(usuario.getId(), produtoId, loteId));
    }

    private String darBaixa(RedirectAttributes redirecionamento, String origem, String mensagemDeSucesso,
                            Runnable baixa) {
        try {
            baixa.run();
            redirecionamento.addFlashAttribute("sucesso", mensagemDeSucesso);
        } catch (ValidacaoException e) {
            redirecionamento.addFlashAttribute("erro", e.getMessage());
        } catch (OptimisticLockingFailureException e) {
            redirecionamento.addFlashAttribute("erro", MENSAGEM_ALTERACAO_CONCORRENTE);
        }
        return DESTINOS_APOS_BAIXA.getOrDefault(origem, "redirect:/");
    }
}
