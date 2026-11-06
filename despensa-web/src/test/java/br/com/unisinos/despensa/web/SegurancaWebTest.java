package br.com.unisinos.despensa.web;

import br.com.unisinos.despensa.model.Usuario;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.password.PasswordEncoder;


import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestBuilders.formLogin;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.security.test.web.servlet.response.SecurityMockMvcResultMatchers.authenticated;
import static org.springframework.security.test.web.servlet.response.SecurityMockMvcResultMatchers.unauthenticated;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class SegurancaWebTest extends TesteWebBase {

    private static final String SENHA = "senha-segura-123";

    @Autowired
    private PasswordEncoder codificador;

    @ParameterizedTest
    @ValueSource(strings = {"/", "/cadastrar", "/consumir", "/alertas", "/relatorio", "/historico", "/produtos",
            "/notificacoes"})
    void paginaProtegida_semLogin_deveRedirecionarParaOLogin(String pagina) throws Exception {
        mockMvc.perform(get(pagina))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/login"));
    }

    @Test
    void consultaDeCodigo_semLogin_deveRedirecionarParaOLoginSemConsultarNada() throws Exception {
        mockMvc.perform(get("/cadastrar/consulta").param("codigo", "7891000100103"))
                .andExpect(redirectedUrl("/login"));

        verifyNoInteractions(consultaProdutoExterna);
    }

    @Test
    void baixaDeLote_semLogin_deveRedirecionarParaOLogin() throws Exception {
        mockMvc.perform(post("/lotes/p1/l1/descartar").with(csrf()))
                .andExpect(redirectedUrl("/login"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"/login", "/usuarios/novo", "/saude", "/css/app.css", "/js/app.js", "/favicon.ico",
            "/favicon.svg", "/apple-touch-icon.png", "/apple-touch-icon-precomposed.png"})
    void loginCadastroEArquivosEstaticos_devemSerLiberados(String pagina) throws Exception {
        mockMvc.perform(get(pagina)).andExpect(status().isOk());
    }

    @Test
    void requisicaoPostSemTokenCsrf_deveSerRecusada() throws Exception {
        mockMvc.perform(post("/lotes/p1/l1/descartar").with(user(ANA)))
                .andExpect(status().isForbidden());
        mockMvc.perform(post("/usuarios/novo").param("nome", "Ana"))
                .andExpect(status().isForbidden());
    }

    @Test
    void loginComSenhaCorreta_deveAutenticarEAbrirADespensa() throws Exception {
        cadastrarAnaNoRepositorio();

        mockMvc.perform(formLogin("/login").userParameter("email").passwordParam("senha")
                        .user("ana@exemplo.com").password(SENHA))
                .andExpect(redirectedUrl("/"))
                .andExpect(authenticated().withUsername("ana@exemplo.com"));
    }

    @Test
    void loginComEmailEmMaiusculasEEspacos_deveAutenticar() throws Exception {
        cadastrarAnaNoRepositorio();

        mockMvc.perform(formLogin("/login").userParameter("email").passwordParam("senha")
                        .user("  ANA@Exemplo.com ").password(SENHA))
                .andExpect(authenticated().withUsername("ana@exemplo.com"));
    }

    @Test
    void loginComSenhaErrada_deveVoltarParaOLoginComErro() throws Exception {
        cadastrarAnaNoRepositorio();

        mockMvc.perform(formLogin("/login").userParameter("email").passwordParam("senha")
                        .user("ana@exemplo.com").password("senha-errada"))
                .andExpect(redirectedUrl("/login?erro"))
                .andExpect(unauthenticated());
    }

    @Test
    void loginComBancoForaDoAr_deveMostrarMensagemDeIndisponibilidade() throws Exception {
        usuarios().simularBancoForaDoAr(true);

        mockMvc.perform(formLogin("/login").userParameter("email").passwordParam("senha")
                        .user("ana@exemplo.com").password(SENHA))
                .andExpect(redirectedUrl("/login?indisponivel"))
                .andExpect(unauthenticated());
    }

    @Test
    void sair_deveEncerrarASessaoEVoltarParaOLogin() throws Exception {
        mockMvc.perform(post("/logout").with(user(ANA)).with(csrf()))
                .andExpect(redirectedUrl("/login?saiu"))
                .andExpect(unauthenticated());
    }

    @Test
    void paginaDeLogin_paraQuemJaEstaLogado_deveIrParaADespensa() throws Exception {
        mockMvc.perform(get("/login").with(user(ANA)))
                .andExpect(redirectedUrl("/"));
    }

    @Test
    void loginComTokenInvalido_deQuemJaEstaLogado_deveIrParaADespensa() throws Exception {
        mockMvc.perform(post("/login").param("email", "ana@exemplo.com").param("senha", SENHA)
                        .with(user(ANA)).with(csrf().useInvalidToken()))
                .andExpect(redirectedUrl("/"))
                .andExpect(authenticated().withUsername("ana@exemplo.com"));
    }

    @Test
    void loginComTokenInvalido_semEstarLogado_deveVoltarAoLoginSemAutenticar() throws Exception {
        cadastrarAnaNoRepositorio();

        mockMvc.perform(post("/login").param("email", "ana@exemplo.com").param("senha", SENHA)
                        .with(csrf().useInvalidToken()))
                .andExpect(redirectedUrl("/login?expirou"))
                .andExpect(unauthenticated());
    }

    @Test
    void loginSemToken_semEstarLogado_deveVoltarAoLoginSemAutenticar() throws Exception {
        cadastrarAnaNoRepositorio();

        mockMvc.perform(post("/login").param("email", "ana@exemplo.com").param("senha", SENHA))
                .andExpect(redirectedUrl("/login?expirou"))
                .andExpect(unauthenticated());
    }

    @ParameterizedTest
    @ValueSource(strings = {"/logout", "/usuarios/novo", "/cadastrar", "/consumir", "/notificacoes/avisos"})
    void tokenInvalidoForaDoLogin_deveContinuarRecusado(String caminho) throws Exception {
        mockMvc.perform(post(caminho).with(user(ANA)).with(csrf().useInvalidToken()))
                .andExpect(status().isForbidden());
    }

    @Test
    void paginaDeLogin_deveAvisarQueASessaoExpirouETravarOSegundoEnvio() throws Exception {
        mockMvc.perform(get("/login").param("expirou", ""))
                .andExpect(content().string(containsString("Sua sessão expirou. Entre de novo.")))
                .andExpect(content().string(containsString("data-envio-unico=\"Entrando...\"")));
        mockMvc.perform(get("/js/app.js"))
                .andExpect(content().string(containsString("data-envio-unico")));
    }

    private void cadastrarAnaNoRepositorio() {
        Usuario ana = usuarios().findById(ANA.getId()).orElseThrow();
        ana.setSenhaHash(codificador.encode(SENHA));
        usuarios().save(ana);
    }
}
