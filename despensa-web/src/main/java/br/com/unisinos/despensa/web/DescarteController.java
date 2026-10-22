package br.com.unisinos.despensa.web;

import br.com.unisinos.despensa.exception.ValidacaoException;
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
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
@RequestMapping("/lotes/{produtoId}/{loteId}/descartar")
public class DescarteController {

    private final ProdutoService produtoService;
    private final LoteService loteService;

    public DescarteController(ProdutoService produtoService, LoteService loteService) {
        this.produtoService = produtoService;
        this.loteService = loteService;
    }

    @GetMapping
    public String formulario(@AuthenticationPrincipal UsuarioLogado usuario, @PathVariable String produtoId,
                             @PathVariable String loteId, @RequestParam(required = false) String origem,
                             Model model, RedirectAttributes redirecionamento) {
        try {
            Produto produto = produtoService.buscarPorId(usuario.getId(), produtoId);
            Lote lote = loteAtivo(produto, loteId);
            return pagina(model, produto, lote, Formatos.numeroParaCampo(lote.getQuantidade()), origem);
        } catch (ValidacaoException e) {
            redirecionamento.addFlashAttribute("erro", e.getMessage());
            return Destinos.redirecionar(origem);
        }
    }

    @PostMapping
    public String descartar(@AuthenticationPrincipal UsuarioLogado usuario, @PathVariable String produtoId,
                            @PathVariable String loteId, @RequestParam(required = false) String quantidade,
                            @RequestParam(required = false) String origem,
                            Model model, RedirectAttributes redirecionamento) {
        Produto produto;
        Lote lote;
        try {
            produto = produtoService.buscarPorId(usuario.getId(), produtoId);
            lote = loteAtivo(produto, loteId);
        } catch (ValidacaoException e) {
            redirecionamento.addFlashAttribute("erro", e.getMessage());
            return Destinos.redirecionar(origem);
        }

        double total = lote.getQuantidade();
        try {
            int unidades = quantidade == null ? (int) total : LeituraDeCampos.quantidadeInteira(quantidade);
            loteService.descartar(usuario.getId(), produtoId, loteId, unidades);
            redirecionamento.addFlashAttribute("sucesso", "Descarte registrado: " + unidades + " de "
                    + Formatos.quantidade(total) + (total == 1 ? " unidade" : " unidades")
                    + " de \"" + produto.getNome() + "\".");
            return Destinos.redirecionar(origem);
        } catch (ValidacaoException e) {
            model.addAttribute("erro", e.getMessage());
            return pagina(model, produto, lote, quantidade, origem);
        } catch (OptimisticLockingFailureException e) {
            redirecionamento.addFlashAttribute("erro", DespensaController.MENSAGEM_ALTERACAO_CONCORRENTE);
            return Destinos.redirecionar(origem);
        }
    }

    private static Lote loteAtivo(Produto produto, String loteId) {
        return produto.buscarLote(loteId)
                .filter(Lote::isAtivo)
                .orElseThrow(() -> new ValidacaoException("Lote não encontrado ou já baixado."));
    }

    private static String pagina(Model model, Produto produto, Lote lote, String quantidade, String origem) {
        String total = Formatos.quantidade(lote.getQuantidade());
        model.addAttribute("menu", Destinos.origemValida(origem));
        model.addAttribute("produto", produto);
        model.addAttribute("lote", lote);
        model.addAttribute("quantidade", quantidade);
        model.addAttribute("maximo", Formatos.numeroParaCampo(lote.getQuantidade()));
        model.addAttribute("origem", Destinos.origemValida(origem));
        model.addAttribute("voltarPara", Destinos.caminho(origem));
        model.addAttribute("modeloConfirmacao", "Descartar {quantidade} de " + total
                + (lote.getQuantidade() == 1 ? " unidade" : " unidades") + " de " + produto.getNome()
                + "? Isso entra no total de perdas.");
        return "descartar";
    }
}
