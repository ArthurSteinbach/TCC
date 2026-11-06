package br.com.unisinos.despensa;

import br.com.unisinos.despensa.model.Usuario;
import br.com.unisinos.despensa.repository.UsuarioRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.net.http.HttpResponse;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "spring.mongodb.uri=mongodb://localhost:27017",
        "despensa.indices.criar-ao-iniciar=false"
})
class RedirecionamentoAposLoginTest {

    private static final String EMAIL = "ana@exemplo.com";
    private static final String SENHA = "senha-segura-123";

    @LocalServerPort
    private int porta;

    @Autowired
    private PasswordEncoder codificador;

    @MockitoBean
    private UsuarioRepository usuarioRepository;

    private NavegadorDeTeste navegador;

    @BeforeEach
    void abrirNavegador() {
        navegador = new NavegadorDeTeste(porta);
        Usuario ana = new Usuario("Ana", EMAIL, codificador.encode(SENHA));
        ana.setId("usuario-ana");
        when(usuarioRepository.findByEmail(EMAIL)).thenReturn(Optional.of(ana));
    }

    @Test
    void navegadorPedindoOFaviconAntesDoLogin_loginDeveAbrirADespensa() throws Exception {
        assertEquals(200, navegador.abrir("/favicon.ico").statusCode());

        assertEquals("/", navegador.entrar(EMAIL, SENHA));
    }

    @Test
    void arquivoInexistenteAntesDoLogin_loginDeveAbrirADespensa() throws Exception {
        assertEquals(404, navegador.abrir("/css/nao-existe.css").statusCode());

        assertEquals("/", navegador.entrar(EMAIL, SENHA));
    }

    @Test
    void navegadorPedindoIconeDoCelularAntesDoLogin_loginDeveAbrirADespensa() throws Exception {
        navegador.abrir("/apple-touch-icon.png");
        navegador.abrir("/apple-touch-icon-precomposed.png");
        navegador.abrir("/apple-touch-icon-120x120.png");

        assertEquals("/", navegador.entrar(EMAIL, SENHA));
    }

    @Test
    void consultaDeCodigoComSessaoExpirada_loginNaoDeveAbrirOJson() throws Exception {
        navegador.abrir("/cadastrar/consulta?codigo=7891000100103");

        assertEquals("/", navegador.entrar(EMAIL, SENHA));
    }

    @Test
    void paginaDeErroAbertaDireto_naoDeveSerODestinoDoLogin() throws Exception {
        navegador.abrir("/error");

        assertEquals("/", navegador.entrar(EMAIL, SENHA));
    }

    @Test
    void paginaProtegidaAbertaAntesDoLogin_loginDeveVoltarParaEla() throws Exception {
        assertEquals(302, navegador.abrir("/cadastrar").statusCode());

        assertTrue(navegador.entrar(EMAIL, SENHA).startsWith("/cadastrar"));
    }

    @Test
    void loginSemTerTentadoAbrirNada_deveAbrirADespensa() throws Exception {
        assertEquals("/", navegador.entrar(EMAIL, SENHA));
    }

    @Test
    void loginEnviadoDuasVezesComOMesmoToken_segundoEnvioDeveAbrirADespensa() throws Exception {
        String token = navegador.tokenDoLogin();
        HttpResponse<String> primeiro = navegador.enviarLogin(EMAIL, SENHA, token);
        assertEquals(302, primeiro.statusCode());
        assertEquals("/", navegador.destino(primeiro));

        HttpResponse<String> segundo = navegador.enviarLogin(EMAIL, SENHA, token);

        assertEquals(302, segundo.statusCode());
        assertEquals("/", navegador.destino(segundo));
        assertTrue(navegador.abrir("/pagina-que-nao-existe").body().contains(">Sair<"));
    }

    @Test
    void loginComTokenDeUmaSessaoQueNaoExisteMais_deveVoltarAoLoginAvisandoQueASessaoExpirou() throws Exception {
        String token = navegador.tokenDoLogin();
        navegador.trocarSessaoPorUmaQueNaoExisteMais();

        HttpResponse<String> resposta = navegador.enviarLogin(EMAIL, SENHA, token);

        assertEquals(302, resposta.statusCode());
        assertEquals("/login?expirou", navegador.destino(resposta));
        HttpResponse<String> login = navegador.abrir("/login?expirou");
        assertEquals(200, login.statusCode());
        assertTrue(login.body().contains("Sua sessão expirou. Entre de novo."));
        assertEquals("/", navegador.entrar(EMAIL, SENHA));
    }
}
