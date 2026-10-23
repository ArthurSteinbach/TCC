package br.com.unisinos.despensa.web;

import br.com.unisinos.despensa.email.FalhaNoEnvioException;
import br.com.unisinos.despensa.exception.ValidacaoException;
import br.com.unisinos.despensa.seguranca.UsuarioLogado;
import br.com.unisinos.despensa.service.NotificacaoService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.Optional;

@Controller
@RequestMapping("/notificacoes")
public class NotificacoesController {

    private static final Logger LOG = LoggerFactory.getLogger(NotificacoesController.class);
    private static final String FALHA_NO_ENVIO =
            "Não foi possível enviar o e-mail agora. Tente de novo em alguns minutos.";

    private final NotificacaoService notificacaoService;

    public NotificacoesController(NotificacaoService notificacaoService) {
        this.notificacaoService = notificacaoService;
    }

    @GetMapping
    public String pagina(@AuthenticationPrincipal UsuarioLogado usuario, Model model) {
        model.addAttribute("menu", "notificacoes");
        model.addAttribute("preferencias", notificacaoService.preferencias(usuario.getId()));
        return "notificacoes";
    }

    @PostMapping("/avisos")
    public String definirAvisos(@AuthenticationPrincipal UsuarioLogado usuario,
                                @RequestParam(defaultValue = "false") boolean ativos,
                                RedirectAttributes redirecionamento) {
        return executar(redirecionamento, () -> {
            notificacaoService.definirAvisos(usuario.getId(), ativos);
            return ativos ? "Avisos por e-mail ligados." : "Avisos por e-mail desligados.";
        });
    }

    @PostMapping("/email")
    public String trocarEmail(@AuthenticationPrincipal UsuarioLogado usuario,
                              @RequestParam(defaultValue = "") String email,
                              RedirectAttributes redirecionamento) {
        return executar(redirecionamento, () -> notificacaoService.solicitarTrocaDeEmail(usuario.getId(), email)
                ? "Enviamos um link de confirmação para " + email.trim().toLowerCase()
                + ". Até você confirmar, os avisos continuam indo para o e-mail atual."
                : "Os avisos voltaram a ir para o e-mail da sua conta.");
    }

    @PostMapping("/email/conta")
    public String usarEmailDaConta(@AuthenticationPrincipal UsuarioLogado usuario,
                                   RedirectAttributes redirecionamento) {
        return executar(redirecionamento, () -> {
            notificacaoService.usarEmailDaConta(usuario.getId());
            return "Os avisos voltaram a ir para o e-mail da sua conta.";
        });
    }

    @PostMapping("/teste")
    public String enviarTeste(@AuthenticationPrincipal UsuarioLogado usuario, RedirectAttributes redirecionamento) {
        return executar(redirecionamento,
                () -> "E-mail de teste enviado para " + notificacaoService.enviarTeste(usuario.getId()) + ".");
    }

    @GetMapping("/confirmar")
    public String confirmar(@RequestParam(defaultValue = "") String token, Model model) {
        try {
            String email = notificacaoService.confirmarEmail(token);
            return paginaPublica(model, "E-mail confirmado",
                    "Pronto! Os avisos de vencimento agora vão para " + email + ".", true);
        } catch (ValidacaoException e) {
            return paginaPublica(model, "Não foi possível confirmar", e.getMessage(), false);
        }
    }

    @GetMapping("/descadastrar")
    public String confirmarDescadastro(@RequestParam(defaultValue = "") String token, Model model) {
        Optional<String> email = notificacaoService.emailDoDescadastro(token);
        if (email.isEmpty()) {
            return paginaPublica(model, "Link inválido", "Este link de descadastro é inválido.", false);
        }
        model.addAttribute("token", token);
        model.addAttribute("emailMascarado", mascarar(email.get()));
        return "descadastrar";
    }

    @PostMapping("/descadastrar")
    public String descadastrar(@RequestParam(defaultValue = "") String token, Model model) {
        try {
            notificacaoService.descadastrar(token);
            return paginaPublica(model, "Avisos desligados",
                    "Você não vai mais receber avisos de vencimento por e-mail. Para voltar a receber, entre na sua "
                            + "conta e ligue os avisos na tela Notificações.", true);
        } catch (ValidacaoException e) {
            return paginaPublica(model, "Link inválido", e.getMessage(), false);
        }
    }

    private String executar(RedirectAttributes redirecionamento, Acao acao) {
        try {
            redirecionamento.addFlashAttribute("sucesso", acao.executar());
        } catch (ValidacaoException e) {
            redirecionamento.addFlashAttribute("erro", e.getMessage());
        } catch (FalhaNoEnvioException e) {
            LOG.warn("Falha ao enviar e-mail pela tela de notificações: {}", e.getMessage());
            redirecionamento.addFlashAttribute("erro", FALHA_NO_ENVIO);
        }
        return "redirect:/notificacoes";
    }

    private static String paginaPublica(Model model, String titulo, String mensagem, boolean sucesso) {
        model.addAttribute("titulo", titulo);
        model.addAttribute("mensagem", mensagem);
        model.addAttribute("sucesso", sucesso);
        return "notificacao-resultado";
    }

    static String mascarar(String email) {
        int arroba = email.indexOf('@');
        if (arroba <= 1) {
            return email;
        }
        return email.charAt(0) + "***" + email.substring(arroba);
    }

    @FunctionalInterface
    private interface Acao {
        String executar() throws FalhaNoEnvioException;
    }
}
