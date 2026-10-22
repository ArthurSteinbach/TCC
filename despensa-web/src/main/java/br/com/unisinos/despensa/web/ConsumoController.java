package br.com.unisinos.despensa.web;

import br.com.unisinos.despensa.exception.ValidacaoException;
import br.com.unisinos.despensa.model.ItemConsumo;
import br.com.unisinos.despensa.model.Lote;
import br.com.unisinos.despensa.model.Produto;
import br.com.unisinos.despensa.seguranca.UsuarioLogado;
import br.com.unisinos.despensa.service.LoteService;
import br.com.unisinos.despensa.service.ProdutoService;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.time.Clock;
import java.time.LocalDate;
import java.util.List;

@Controller
@RequestMapping("/consumir")
public class ConsumoController {

    private final ProdutoService produtoService;
    private final LoteService loteService;
    private final Clock relogio;

    public ConsumoController(ProdutoService produtoService, LoteService loteService, Clock relogio) {
        this.produtoService = produtoService;
        this.loteService = loteService;
        this.relogio = relogio;
    }

    @GetMapping
    public String pagina(@AuthenticationPrincipal UsuarioLogado usuario,
                         @RequestParam(required = false) String produtoId,
                         @RequestParam(defaultValue = "1") String quantidade,
                         @RequestParam(required = false) String origem, Model model) {
        if (produtoId != null) {
            try {
                int unidades = LeituraDeCampos.quantidadeInteira(quantidade);
                Produto produto = produtoService.buscarPorId(usuario.getId(), produtoEscolhido(produtoId));
                List<ItemConsumo> plano = loteService.planejarConsumoFEFO(usuario.getId(), produto.getId(), unidades);
                double disponivel = OpcaoConsumo.de(produto, LocalDate.now(relogio)).disponivel();
                model.addAttribute("perguntaConfirmacao", "Consumir " + unidades + " de "
                        + Formatos.quantidade(disponivel) + (disponivel == 1 ? " unidade" : " unidades")
                        + " de " + produto.getNome() + "?");
                model.addAttribute("previa", plano.stream().map(LinhaPrevia::de).toList());
                model.addAttribute("quantidadePrevia", unidades);
            } catch (ValidacaoException e) {
                model.addAttribute("erro", e.getMessage());
            }
        }
        return montarPagina(usuario, produtoId, quantidade, origem, model);
    }

    @PostMapping
    public String confirmar(@AuthenticationPrincipal UsuarioLogado usuario,
                            @RequestParam(defaultValue = "") String produtoId,
                            @RequestParam(defaultValue = "") String quantidade,
                            @RequestParam(required = false) String origem,
                            Model model, RedirectAttributes redirecionamento) {
        try {
            int unidades = LeituraDeCampos.quantidadeInteira(quantidade);
            Produto produto = produtoService.buscarPorId(usuario.getId(), produtoEscolhido(produtoId));
            loteService.consumirPorFEFO(usuario.getId(), produto.getId(), unidades);
            redirecionamento.addFlashAttribute("sucesso", "Consumo registrado: " + unidades
                    + (unidades == 1 ? " unidade" : " unidades") + " de \"" + produto.getNome() + "\".");
            return Destinos.redirecionar(origem);
        } catch (ValidacaoException e) {
            model.addAttribute("erro", e.getMessage());
        } catch (OptimisticLockingFailureException e) {
            model.addAttribute("erro", DespensaController.MENSAGEM_ALTERACAO_CONCORRENTE);
        }
        return montarPagina(usuario, produtoId, quantidade, origem, model);
    }

    private String montarPagina(UsuarioLogado usuario, String produtoId, String quantidade, String origem,
                                Model model) {
        LocalDate hoje = LocalDate.now(relogio);
        List<OpcaoConsumo> opcoes = produtoService.listarComLotesAtivos(usuario.getId()).stream()
                .map(p -> OpcaoConsumo.de(p, hoje))
                .toList();
        String selecionado = produtoId == null ? "" : produtoId;

        model.addAttribute("menu", "consumir");
        model.addAttribute("opcoes", opcoes);
        model.addAttribute("produtoSelecionado", selecionado);
        model.addAttribute("maximo", opcoes.stream()
                .filter(o -> o.produtoId().equals(selecionado) && o.disponivel() > 0)
                .map(o -> Formatos.quantidade(o.disponivel()))
                .findFirst().orElse(null));
        model.addAttribute("quantidade", quantidade);
        model.addAttribute("origem", origem == null ? "" : Destinos.origemValida(origem));
        model.addAttribute("voltarPara", origem == null ? "/consumir" : Destinos.caminho(origem));
        return "consumir";
    }

    private static String produtoEscolhido(String produtoId) {
        if (produtoId == null || produtoId.isBlank()) {
            throw new ValidacaoException("Escolha o produto.");
        }
        return produtoId;
    }

    public record OpcaoConsumo(String produtoId, String nome, String codigoBarras, double disponivel) {

        static OpcaoConsumo de(Produto produto, LocalDate hoje) {
            double disponivel = produto.getLotes().stream()
                    .filter(Lote::isAtivo)
                    .filter(l -> !l.isVencidoEm(hoje))
                    .mapToDouble(Lote::getQuantidade)
                    .sum();
            return new OpcaoConsumo(produto.getId(), produto.getNome(), produto.getCodigoBarras(), disponivel);
        }

        public String disponivelParaCampo() {
            return disponivel > 0 ? Formatos.numeroParaCampo(disponivel) : "";
        }

        public String rotulo() {
            if (disponivel <= 0) {
                return nome + " — só itens vencidos";
            }
            return nome + " — " + Formatos.quantidade(disponivel) + (disponivel == 1 ? " disponível" : " disponíveis");
        }
    }

    public record LinhaPrevia(LocalDate dataValidade, String quantidadeNoLote, String quantidadeConsumida,
                              String quantidadeRestante, boolean loteInteiro) {

        static LinhaPrevia de(ItemConsumo item) {
            return new LinhaPrevia(item.dataValidade(), Formatos.quantidade(item.quantidadeNoLote()),
                    Formatos.quantidade(item.quantidadeConsumida()), Formatos.quantidade(item.quantidadeRestante()),
                    item.consomeLoteInteiro());
        }
    }
}
