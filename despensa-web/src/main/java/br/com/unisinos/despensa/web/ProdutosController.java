package br.com.unisinos.despensa.web;

import br.com.unisinos.despensa.exception.ValidacaoException;
import br.com.unisinos.despensa.model.Lote;
import br.com.unisinos.despensa.model.Produto;
import br.com.unisinos.despensa.seguranca.UsuarioLogado;
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
@RequestMapping("/produtos")
public class ProdutosController {

    private final ProdutoService produtoService;

    public ProdutosController(ProdutoService produtoService) {
        this.produtoService = produtoService;
    }

    @GetMapping
    public String lista(@AuthenticationPrincipal UsuarioLogado usuario, Model model) {
        model.addAttribute("menu", "produtos");
        model.addAttribute("produtos", produtoService.listar(usuario.getId()).stream()
                .map(ItemProduto::de)
                .toList());
        return "produtos";
    }

    @GetMapping("/{produtoId}/editar")
    public String formulario(@AuthenticationPrincipal UsuarioLogado usuario, @PathVariable String produtoId,
                             Model model, RedirectAttributes redirecionamento) {
        try {
            Produto produto = produtoService.buscarPorId(usuario.getId(), produtoId);
            return pagina(model, produto, produto.getNome(), produto.getCategoria());
        } catch (ValidacaoException e) {
            redirecionamento.addFlashAttribute("erro", e.getMessage());
            return "redirect:/produtos";
        }
    }

    @PostMapping("/{produtoId}/editar")
    public String salvar(@AuthenticationPrincipal UsuarioLogado usuario, @PathVariable String produtoId,
                         @RequestParam(defaultValue = "") String nome,
                         @RequestParam(defaultValue = "") String categoria,
                         Model model, RedirectAttributes redirecionamento) {
        Produto produto;
        try {
            produto = produtoService.buscarPorId(usuario.getId(), produtoId);
        } catch (ValidacaoException e) {
            redirecionamento.addFlashAttribute("erro", e.getMessage());
            return "redirect:/produtos";
        }

        try {
            Produto editado = produtoService.editar(usuario.getId(), produtoId, nome, categoria);
            redirecionamento.addFlashAttribute("sucesso", "Produto \"" + editado.getNome() + "\" atualizado.");
            return "redirect:/produtos";
        } catch (ValidacaoException e) {
            model.addAttribute("erro", e.getMessage());
        } catch (OptimisticLockingFailureException e) {
            model.addAttribute("erro", DespensaController.MENSAGEM_ALTERACAO_CONCORRENTE);
        }
        return pagina(model, produto, nome, categoria);
    }

    @PostMapping("/{produtoId}/excluir")
    public String excluir(@AuthenticationPrincipal UsuarioLogado usuario, @PathVariable String produtoId,
                          RedirectAttributes redirecionamento) {
        try {
            String nome = produtoService.buscarPorId(usuario.getId(), produtoId).getNome();
            produtoService.excluir(usuario.getId(), produtoId);
            redirecionamento.addFlashAttribute("sucesso", "Produto \"" + nome + "\" excluído.");
        } catch (ValidacaoException e) {
            redirecionamento.addFlashAttribute("erro", e.getMessage());
        } catch (OptimisticLockingFailureException e) {
            redirecionamento.addFlashAttribute("erro", DespensaController.MENSAGEM_ALTERACAO_CONCORRENTE);
        }
        return "redirect:/produtos";
    }

    private static String pagina(Model model, Produto produto, String nome, String categoria) {
        model.addAttribute("menu", "produtos");
        model.addAttribute("produto", produto);
        model.addAttribute("nome", nome);
        model.addAttribute("categoria", categoria == null ? "" : categoria);
        return "editar-produto";
    }

    public record ItemProduto(String id, String codigoBarras, String nome, String categoria, long lotesAtivos,
                              int lotesRegistrados) {

        static ItemProduto de(Produto produto) {
            return new ItemProduto(produto.getId(), produto.getCodigoBarras(), produto.getNome(),
                    produto.getCategoria() == null ? "" : produto.getCategoria(),
                    produto.getLotes().stream().filter(Lote::isAtivo).count(), produto.getLotes().size());
        }
    }
}
