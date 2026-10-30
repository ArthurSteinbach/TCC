package br.com.unisinos.despensa.web;

import br.com.unisinos.despensa.model.Usuario;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class NotificacoesWebTest extends TesteWebBase {

    private static final Pattern TOKEN_CONFIRMACAO = Pattern.compile("confirmar\\?token=([A-Za-z0-9_-]+)");

    @Test
    void pagina_deveMostrarAvisosLigadosEOEmailDaConta() throws Exception {
        mockMvc.perform(get("/notificacoes").with(user(ANA)))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Situação:")))
                .andExpect(content().string(containsString("Enquanto houver itens vencidos ou vencendo em até 7 dias, "
                        + "enviamos um e-mail por dia com a lista.")))
                .andExpect(content().string(containsString("<strong>ligados</strong>")))
                .andExpect(content().string(containsString("ana@exemplo.com")))
                .andExpect(content().string(containsString("(e-mail da sua conta)")))
                .andExpect(content().string(containsString("Enviar e-mail de teste")))
                .andExpect(content().string(containsString(">Notificações<")));
    }

    @Test
    void desligarEReligarOsAvisos_deveFicarGuardado() throws Exception {
        mockMvc.perform(post("/notificacoes/avisos").with(user(ANA)).with(csrf()).param("ativos", "false"))
                .andExpect(redirectedUrl("/notificacoes"))
                .andExpect(flash().attribute("sucesso", "Avisos por e-mail desligados."));
        assertFalse(ana().recebeAvisos());
        mockMvc.perform(get("/notificacoes").with(user(ANA)))
                .andExpect(content().string(containsString("<strong>desligados</strong>")));

        mockMvc.perform(post("/notificacoes/avisos").with(user(ANA)).with(csrf()).param("ativos", "true"));
        assertTrue(ana().recebeAvisos());
    }

    @Test
    void acoesDaTela_semTokenCsrf_devemSerRecusadas() throws Exception {
        mockMvc.perform(post("/notificacoes/avisos").with(user(ANA)).param("ativos", "false"))
                .andExpect(status().isForbidden());
        mockMvc.perform(post("/notificacoes/teste").with(user(ANA)))
                .andExpect(status().isForbidden());

        assertTrue(ana().recebeAvisos());
        assertTrue(emails().enviadas().isEmpty());
    }

    @Test
    void outroEmail_deveFicarPendenteAteAConfirmacaoPeloLink() throws Exception {
        mockMvc.perform(post("/notificacoes/email").with(user(ANA)).with(csrf()).param("email", "novo@exemplo.com"))
                .andExpect(redirectedUrl("/notificacoes"))
                .andExpect(flash().attribute("sucesso", containsString("Enviamos um link de confirmação")));

        assertEquals("novo@exemplo.com", emails().ultima().destinatario());
        mockMvc.perform(get("/notificacoes").with(user(ANA)))
                .andExpect(content().string(containsString("Aguardando confirmação de <strong>novo@exemplo.com</strong>")));
        assertEquals("ana@exemplo.com", ana().emailDeDestinoDosAvisos());

        mockMvc.perform(get("/notificacoes/confirmar").param("token", tokenDaConfirmacao()))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("E-mail confirmado")))
                .andExpect(content().string(containsString("novo@exemplo.com")));

        assertEquals("novo@exemplo.com", ana().emailDeDestinoDosAvisos());
    }

    @Test
    void linkDeConfirmacaoExpirado_deveAvisarEManterOEmailAnterior() throws Exception {
        mockMvc.perform(post("/notificacoes/email").with(user(ANA)).with(csrf()).param("email", "novo@exemplo.com"));
        Usuario ana = ana();
        ana.setConfirmacaoExpiraEm(LocalDateTime.of(2026, 10, 18, 22, 0));
        usuarios().save(ana);

        mockMvc.perform(get("/notificacoes/confirmar").param("token", tokenDaConfirmacao()))
                .andExpect(content().string(containsString("Este link expirou")));

        assertEquals("ana@exemplo.com", ana().emailDeDestinoDosAvisos());
    }

    @Test
    void linkDeConfirmacaoInvalido_deveAvisar() throws Exception {
        mockMvc.perform(get("/notificacoes/confirmar").param("token", "inventado"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Não foi possível confirmar")));
    }

    @Test
    void emailDeTeste_deveSerEnviadoComLimiteDeUmPorMinuto() throws Exception {
        mockMvc.perform(post("/notificacoes/teste").with(user(ANA)).with(csrf()))
                .andExpect(flash().attribute("sucesso", "E-mail de teste enviado para ana@exemplo.com."));
        mockMvc.perform(post("/notificacoes/teste").with(user(ANA)).with(csrf()))
                .andExpect(flash().attribute("erro", "Aguarde um minuto antes de enviar outro e-mail."));

        assertEquals(1, emails().enviadas().size());
        assertEquals("Despensa: e-mail de teste", emails().ultima().assunto());
    }

    @Test
    void falhaAoEnviarOTeste_deveMostrarMensagemAmigavel() throws Exception {
        emails().falharEnvios(true);

        mockMvc.perform(post("/notificacoes/teste").with(user(ANA)).with(csrf()))
                .andExpect(flash().attribute("erro", "Não foi possível enviar o e-mail agora. Tente de novo em alguns minutos."));
    }

    @Test
    void envioNaoConfigurado_deveAvisarNaTelaEDesativarOTeste() throws Exception {
        emails().desligar();

        mockMvc.perform(get("/notificacoes").with(user(ANA)))
                .andExpect(content().string(containsString("ainda não está configurado no servidor")))
                .andExpect(content().string(containsString("disabled")));
    }

    @Test
    void descadastroPeloLink_deveFuncionarSemLoginComUmClique() throws Exception {
        mockMvc.perform(post("/notificacoes/teste").with(user(ANA)).with(csrf()));
        String token = ana().getTokenDescadastro();

        mockMvc.perform(get("/notificacoes/descadastrar").param("token", token))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("a***@exemplo.com")))
                .andExpect(content().string(containsString("Sim, desligar os avisos")));
        assertTrue(ana().recebeAvisos());

        mockMvc.perform(post("/notificacoes/descadastrar").with(csrf()).param("token", token))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Avisos desligados")));

        assertFalse(ana().recebeAvisos());
    }

    @Test
    void descadastroComTokenInvalido_naoDeveDesligarNada() throws Exception {
        mockMvc.perform(get("/notificacoes/descadastrar").param("token", "inventado"))
                .andExpect(content().string(containsString("Link inválido")))
                .andExpect(content().string(not(containsString("Sim, desligar"))));
        mockMvc.perform(post("/notificacoes/descadastrar").with(csrf()).param("token", "inventado"))
                .andExpect(content().string(containsString("Link inválido")));

        assertTrue(ana().recebeAvisos());
        assertTrue(usuarios().findById(BRUNO.getId()).orElseThrow().recebeAvisos());
    }

    @Test
    void cadaUsuario_deveVerEAlterarSoAsPropriasPreferencias() throws Exception {
        mockMvc.perform(post("/notificacoes/avisos").with(user(BRUNO)).with(csrf()).param("ativos", "false"));
        mockMvc.perform(post("/notificacoes/email").with(user(BRUNO)).with(csrf()).param("email", "b2@exemplo.com"));

        assertTrue(ana().recebeAvisos());
        assertEquals(null, ana().getEmailAvisosPendente());
        mockMvc.perform(get("/notificacoes").with(user(ANA)))
                .andExpect(content().string(not(containsString("bruno@exemplo.com"))))
                .andExpect(content().string(not(containsString("b2@exemplo.com"))));
    }

    private Usuario ana() {
        return usuarios().findById(ANA.getId()).orElseThrow();
    }

    private String tokenDaConfirmacao() {
        Matcher token = TOKEN_CONFIRMACAO.matcher(emails().ultima().texto());
        assertTrue(token.find(), "E-mail sem link de confirmação");
        return token.group(1);
    }
}
