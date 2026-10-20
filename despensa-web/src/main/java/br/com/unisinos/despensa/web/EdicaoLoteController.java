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
@RequestMapping("/lotes/{produtoId}/{loteId}/editar")
public class EdicaoLoteController {

    private final ProdutoService produtoService;
    private final LoteService loteService;

    public EdicaoLoteController(ProdutoService produtoService, LoteService loteService) {
        this.produtoService = produtoService;
        this.loteService = loteService;
    }

    @GetMapping
    public String formulario(@AuthenticationPrincipal UsuarioLogado usuario, @PathVariable String produtoId,
                             @PathVariable String loteId, Model model, RedirectAttributes redirecionamento) {
        try {
            Produto produto = produtoService.buscarPorId(usuario.getId(), produtoId);
            Lote lote = produto.buscarLote(loteId)
                    .filter(Lote::isAtivo)
                    .orElseThrow(() -> new ValidacaoException("Lote não encontrado ou já baixado."));
            return pagina(model, produto, loteId, Formatos.numeroParaCampo(lote.getQuantidade()),
                    Formatos.precoParaCampo(lote.getPrecoUnitario()), lote.getDataValidade().toString());
        } catch (ValidacaoException e) {
            redirecionamento.addFlashAttribute("erro", e.getMessage());
            return "redirect:/";
        }
    }

    @PostMapping
    public String salvar(@AuthenticationPrincipal UsuarioLogado usuario, @PathVariable String produtoId,
                         @PathVariable String loteId,
                         @RequestParam(defaultValue = "") String quantidade,
                         @RequestParam(defaultValue = "") String precoUnitario,
                         @RequestParam(defaultValue = "") String validade,
                         Model model, RedirectAttributes redirecionamento) {
        Produto produto;
        try {
            produto = produtoService.buscarPorId(usuario.getId(), produtoId);
        } catch (ValidacaoException e) {
            redirecionamento.addFlashAttribute("erro", e.getMessage());
            return "redirect:/";
        }

        try {
            loteService.editar(usuario.getId(), produtoId, loteId, LeituraDeCampos.quantidadeInteira(quantidade),
                    LeituraDeCampos.preco(precoUnitario), LeituraDeCampos.data(validade, "Informe a data de validade."));
            redirecionamento.addFlashAttribute("sucesso", "Lote de \"" + produto.getNome() + "\" atualizado.");
            return "redirect:/";
        } catch (ValidacaoException e) {
            model.addAttribute("erro", e.getMessage());
        } catch (OptimisticLockingFailureException e) {
            model.addAttribute("erro", DespensaController.MENSAGEM_ALTERACAO_CONCORRENTE);
        }
        return pagina(model, produto, loteId, quantidade, precoUnitario, validade);
    }

    private static String pagina(Model model, Produto produto, String loteId, String quantidade, String preco,
                                 String validade) {
        model.addAttribute("menu", "despensa");
        model.addAttribute("produto", produto);
        model.addAttribute("loteId", loteId);
        model.addAttribute("quantidade", quantidade);
        model.addAttribute("precoUnitario", preco);
        model.addAttribute("validade", validade);
        return "editar-lote";
    }
}
