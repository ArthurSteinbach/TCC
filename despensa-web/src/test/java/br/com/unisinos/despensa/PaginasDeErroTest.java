package br.com.unisinos.despensa;

import br.com.unisinos.despensa.model.Usuario;
import br.com.unisinos.despensa.repository.UsuarioRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.net.http.HttpResponse;
import java.util.Arrays;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "spring.mongodb.uri=mongodb://localhost:27017",
        "despensa.indices.criar-ao-iniciar=false"
})
@Import(PaginasDeErroTest.ControladorQueFalha.class)
class PaginasDeErroTest {

    private static final String EMAIL = "ana@exemplo.com";
    private static final String SENHA = "senha-segura-123";
    private static final byte[] ASSINATURA_PNG = {(byte) 0x89, 'P', 'N', 'G', '\r', '\n', 0x1a, '\n'};

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
    void favicon_deveExistirSemLogin() throws Exception {
        HttpResponse<byte[]> ico = navegador.baixar("/favicon.ico");
        assertEquals(200, ico.statusCode());
        assertArrayEquals(new byte[]{0, 0, 1, 0}, Arrays.copyOf(ico.body(), 4));

        HttpResponse<byte[]> svg = navegador.baixar("/favicon.svg");
        assertEquals(200, svg.statusCode());
        assertTrue(tipo(svg).startsWith("image/svg+xml"));
    }

    @Test
    void iconesDoIphone_devemExistirSemLoginComoPng() throws Exception {
        for (String caminho : new String[]{"/apple-touch-icon.png", "/apple-touch-icon-precomposed.png"}) {
            HttpResponse<byte[]> icone = navegador.baixar(caminho);
            assertEquals(200, icone.statusCode(), caminho);
            assertEquals("image/png", tipo(icone));
            assertArrayEquals(ASSINATURA_PNG, Arrays.copyOf(icone.body(), 8), caminho);
        }
    }

    @Test
    void layout_deveApontarParaOsIcones() throws Exception {
        String html = navegador.abrir("/login").body();

        assertTrue(html.contains("href=\"/favicon.ico\""));
        assertTrue(html.contains("href=\"/favicon.svg\""));
        assertTrue(html.contains("rel=\"apple-touch-icon\" href=\"/apple-touch-icon.png\""));
    }

    @Test
    void arquivoInexistenteSemLogin_deveMostrarPagina404EmPortuguesNoLayout() throws Exception {
        HttpResponse<String> resposta = navegador.abrir("/css/nao-existe.css");

        assertEquals(404, resposta.statusCode());
        assertTrue(tipo(resposta).startsWith("text/html"));
        assertTrue(resposta.body().contains("Página não encontrada"));
        assertTrue(resposta.body().contains("bootstrap@5.3.8"));
        assertFalse(resposta.body().contains("navbar"));
        assertPaginaSemDetalhesTecnicos(resposta.body());
    }

    @Test
    void paginaInexistenteComLogin_deveMostrarPagina404ComOMenu() throws Exception {
        navegador.entrar(EMAIL, SENHA);

        HttpResponse<String> resposta = navegador.abrir("/pagina-que-nao-existe");

        assertEquals(404, resposta.statusCode());
        assertTrue(resposta.body().contains("Página não encontrada"));
        assertTrue(resposta.body().contains(">Sair<"));
        assertPaginaSemDetalhesTecnicos(resposta.body());
    }

    @Test
    void erroInesperado_deveMostrarPagina500EmPortuguesSemDetalhesTecnicos() throws Exception {
        navegador.entrar(EMAIL, SENHA);

        HttpResponse<String> resposta = navegador.abrir("/teste/falha");

        assertEquals(500, resposta.statusCode());
        assertTrue(tipo(resposta).startsWith("text/html"));
        assertTrue(resposta.body().contains("Algo deu errado"));
        assertFalse(resposta.body().contains("detalhe interno"));
        assertFalse(resposta.body().contains("IllegalStateException"));
        assertPaginaSemDetalhesTecnicos(resposta.body());
    }

    @Test
    void formularioComTokenVencido_deveMostrarPagina403Amigavel() throws Exception {
        navegador.entrar(EMAIL, SENHA);

        HttpResponse<String> resposta = navegador.enviarFormulario("/cadastrar", "codigoBarras=123&_csrf=vencido");

        assertEquals(403, resposta.statusCode());
        assertTrue(resposta.body().contains("Sua sessão pode ter expirado"));
        assertPaginaSemDetalhesTecnicos(resposta.body());
    }

    @Test
    void paginaDeErroAbertaDireto_naoDeveMostrarOCodigo999NemJson() throws Exception {
        navegador.entrar(EMAIL, SENHA);

        HttpResponse<String> resposta = navegador.abrir("/error");

        assertTrue(tipo(resposta).startsWith("text/html"));
        assertFalse(resposta.body().contains("999"));
        assertPaginaSemDetalhesTecnicos(resposta.body());
    }

    private static void assertPaginaSemDetalhesTecnicos(String html) {
        assertFalse(html.contains("timestamp"), html);
        assertFalse(html.contains("Whitelabel"), html);
        assertFalse(html.contains("trace"), html);
        assertFalse(html.trim().startsWith("{"), html);
    }

    private static String tipo(HttpResponse<?> resposta) {
        return resposta.headers().firstValue("Content-Type").orElse("");
    }

    @RestController
    static class ControladorQueFalha {

        @GetMapping("/teste/falha")
        String falhar() {
            throw new IllegalStateException("detalhe interno que não pode aparecer");
        }
    }
}
