package br.com.unisinos.despensa.web;

import br.com.unisinos.despensa.exception.ValidacaoException;
import br.com.unisinos.despensa.service.UsuarioService;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

@Controller
public class UsuarioController {

    private final UsuarioService usuarioService;

    public UsuarioController(UsuarioService usuarioService) {
        this.usuarioService = usuarioService;
    }

    @GetMapping("/login")
    public String login(Authentication autenticacao) {
        return estaLogado(autenticacao) ? "redirect:/" : "login";
    }

    @GetMapping("/usuarios/novo")
    public String formularioDeCadastro(Authentication autenticacao) {
        return estaLogado(autenticacao) ? "redirect:/" : "cadastro-usuario";
    }

    @PostMapping("/usuarios/novo")
    public String cadastrar(@RequestParam(defaultValue = "") String nome,
                            @RequestParam(defaultValue = "") String email,
                            @RequestParam(defaultValue = "") String senha,
                            @RequestParam(defaultValue = "") String confirmacaoSenha,
                            Model model) {
        try {
            usuarioService.cadastrar(nome, email, senha, confirmacaoSenha);
            return "redirect:/login?cadastrado";
        } catch (ValidacaoException e) {
            model.addAttribute("erro", e.getMessage());
            model.addAttribute("nome", nome);
            model.addAttribute("email", email);
            return "cadastro-usuario";
        }
    }

    private static boolean estaLogado(Authentication autenticacao) {
        return autenticacao != null && autenticacao.isAuthenticated()
                && !(autenticacao instanceof AnonymousAuthenticationToken);
    }
}
