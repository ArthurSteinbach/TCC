package br.com.unisinos.despensa.web;

import br.com.unisinos.despensa.exception.ValidacaoException;
import br.com.unisinos.despensa.model.Lote;
import br.com.unisinos.despensa.model.LoteDoProduto;
import br.com.unisinos.despensa.model.Produto;
import br.com.unisinos.despensa.model.StatusLote;
import br.com.unisinos.despensa.seguranca.UsuarioLogado;
import br.com.unisinos.despensa.service.LoteService;
import br.com.unisinos.despensa.service.ProdutoService;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.time.LocalDateTime;
import java.util.Map;

@Controller
@RequestMapping("/historico")
public class HistoricoController {

    private static final Map<String, StatusLote> FILTROS = Map.of(
            "consumidos", StatusLote.CONSUMIDO,
            "descartados", StatusLote.DESCARTADO);

    private final ProdutoService produtoService;
    private final LoteService loteService;

    public HistoricoController(ProdutoService produtoService, LoteService loteService) {
        this.produtoService = produtoService;
        this.loteService = loteService;
    }

    @GetMapping
    public String historico(@AuthenticationPrincipal UsuarioLogado usuario,
                            @RequestParam(defaultValue = "") String situacao, Model model) {
        String filtro = FILTROS.containsKey(situacao) ? situacao : "";
        model.addAttribute("menu", "historico");
        model.addAttribute("filtro", filtro);
        model.addAttribute("itens", loteService.listarHistorico(usuario.getId(), FILTROS.get(filtro)).stream()
                .map(ItemHistorico::de)
                .toList());
        return "historico";
    }

    @PostMapping("/{produtoId}/{loteId}/desfazer")
    public String desfazer(@AuthenticationPrincipal UsuarioLogado usuario, @PathVariable String produtoId,
                           @PathVariable String loteId, @RequestParam(defaultValue = "") String situacao,
                           RedirectAttributes redirecionamento) {
        try {
            Produto produto = produtoService.buscarPorId(usuario.getId(), produtoId);
            Lote baixado = produto.buscarLote(loteId)
                    .orElseThrow(() -> new ValidacaoException("Item não encontrado no histórico."));
            String quantidade = Formatos.quantidade(baixado.getQuantidade());
            loteService.desfazerBaixa(usuario.getId(), produtoId, loteId);
            redirecionamento.addFlashAttribute("sucesso", "Desfeito: " + quantidade
                    + (baixado.getQuantidade() == 1 ? " unidade" : " unidades") + " de \"" + produto.getNome()
                    + "\" voltou para a despensa.");
        } catch (ValidacaoException e) {
            redirecionamento.addFlashAttribute("erro", e.getMessage());
        } catch (OptimisticLockingFailureException e) {
            redirecionamento.addFlashAttribute("erro", DespensaController.MENSAGEM_ALTERACAO_CONCORRENTE);
        }
        return FILTROS.containsKey(situacao) ? "redirect:/historico?situacao=" + situacao : "redirect:/historico";
    }

    public record ItemHistorico(String produtoId, String loteId, String nomeProduto, double quantidade,
                                double valorTotal, StatusLote status, LocalDateTime dataBaixa) {

        static ItemHistorico de(LoteDoProduto loteDoProduto) {
            Lote lote = loteDoProduto.lote();
            return new ItemHistorico(loteDoProduto.produtoId(), lote.getId(), loteDoProduto.nomeProduto(),
                    lote.getQuantidade(), lote.getValorTotal(), lote.getStatus(), lote.getDataBaixa());
        }

        public String situacao() {
            return status == StatusLote.DESCARTADO ? "Descartado" : "Consumido";
        }

        public boolean descartado() {
            return status == StatusLote.DESCARTADO;
        }

        public String quantidadeFormatada() {
            return Formatos.quantidade(quantidade);
        }

        public String confirmacaoDesfazer() {
            String unidades = quantidadeFormatada() + (quantidade == 1 ? " unidade" : " unidades");
            return "Desfazer: devolver " + unidades + " de " + nomeProduto + " para a despensa?"
                    + (descartado() ? " O valor sai do total de perdas." : "");
        }
    }
}
