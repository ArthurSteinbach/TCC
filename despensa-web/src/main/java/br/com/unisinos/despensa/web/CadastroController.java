package br.com.unisinos.despensa.web;

import br.com.unisinos.despensa.exception.ValidacaoException;
import br.com.unisinos.despensa.model.Produto;
import br.com.unisinos.despensa.model.RegistroCompra;
import br.com.unisinos.despensa.model.ResultadoConsultaCodigo;
import br.com.unisinos.despensa.seguranca.UsuarioLogado;
import br.com.unisinos.despensa.service.CadastroCompraService;
import br.com.unisinos.despensa.service.ProdutoService;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.time.Clock;
import java.time.LocalDate;
import java.util.Map;

@Controller
@RequestMapping("/cadastrar")
public class CadastroController {

    private final CadastroCompraService cadastroCompraService;
    private final ProdutoService produtoService;
    private final Clock relogio;

    public CadastroController(CadastroCompraService cadastroCompraService, ProdutoService produtoService,
                              Clock relogio) {
        this.cadastroCompraService = cadastroCompraService;
        this.produtoService = produtoService;
        this.relogio = relogio;
    }

    @GetMapping
    public String formulario(@AuthenticationPrincipal UsuarioLogado usuario, Model model) {
        return pagina(usuario, new CadastroForm(), model);
    }

    @GetMapping("/consulta")
    @ResponseBody
    public ResultadoConsultaCodigo consultar(@AuthenticationPrincipal UsuarioLogado usuario,
                                             @RequestParam(defaultValue = "") String codigo) {
        return cadastroCompraService.consultarCodigo(usuario.getId(), codigo);
    }

    @PostMapping
    public String salvar(@AuthenticationPrincipal UsuarioLogado usuario, @ModelAttribute("form") CadastroForm form,
                         Model model, RedirectAttributes redirecionamento) {
        try {
            if (form.getCodigoBarras().isBlank()) {
                throw new ValidacaoException("Informe o código de barras.");
            }
            form.quantidadeInformada();
            double preco = form.precoInformado();
            Map<LocalDate, Integer> quantidadePorValidade = form.quantidadePorValidade();

            RegistroCompra registro = cadastroCompraService.registrarCompra(usuario.getId(), form.getCodigoBarras(),
                    form.getNome(), form.getCategoria(), quantidadePorValidade, preco);
            redirecionamento.addFlashAttribute("sucesso", mensagemDeSucesso(registro));
            return "redirect:/";
        } catch (ValidacaoException e) {
            model.addAttribute("erro", e.getMessage());
        } catch (OptimisticLockingFailureException e) {
            model.addAttribute("erro", DespensaController.MENSAGEM_ALTERACAO_CONCORRENTE);
        }
        return pagina(usuario, form, model);
    }

    private String pagina(UsuarioLogado usuario, CadastroForm form, Model model) {
        Produto existente = form.getCodigoBarras().isBlank()
                ? null
                : produtoService.buscarPorCodigoBarras(usuario.getId(), form.getCodigoBarras()).orElse(null);

        model.addAttribute("menu", "cadastrar");
        model.addAttribute("form", form);
        model.addAttribute("hoje", LocalDate.now(relogio));
        model.addAttribute("codigoInformado", !form.getCodigoBarras().isBlank());
        model.addAttribute("produtoExistente", existente);
        model.addAttribute("limiteUnidades", CadastroForm.LIMITE_UNIDADES_COM_VALIDADES_DIFERENTES);
        return "cadastrar";
    }

    private static String mensagemDeSucesso(RegistroCompra registro) {
        String nome = "\"" + registro.produto().getNome() + "\"";
        int lotes = registro.lotesCadastrados();
        if (registro.produtoNovo()) {
            return "Produto " + nome + " cadastrado com "
                    + (lotes == 1 ? "1 lote." : lotes + " lotes (agrupados por validade).");
        }
        return lotes == 1
                ? "Lote de " + nome + " cadastrado com sucesso."
                : lotes + " lotes de " + nome + " cadastrados (agrupados por validade).";
    }
}
