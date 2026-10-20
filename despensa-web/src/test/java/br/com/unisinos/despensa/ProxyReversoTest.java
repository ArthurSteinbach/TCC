package br.com.unisinos.despensa;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "spring.mongodb.uri=mongodb://localhost:27017",
        "despensa.indices.criar-ao-iniciar=false"
})
class ProxyReversoTest {

    private final HttpClient cliente = HttpClient.newBuilder()
            .followRedirects(HttpClient.Redirect.NEVER)
            .build();

    @LocalServerPort
    private int porta;

    @Test
    void atrasDoProxyComHttps_cookieDeSessaoDeveSerSeguro() throws Exception {
        HttpResponse<String> resposta = enviar("/cadastrar", true);

        String cookie = cookieDeSessao(resposta).orElseThrow();
        assertTrue(cookie.contains("; Secure"), cookie);
        assertTrue(cookie.contains("; HttpOnly"), cookie);
        assertTrue(cookie.contains("SameSite=Lax"), cookie);
    }

    @Test
    void atrasDoProxyComHttps_redirecionamentoParaOLoginDeveContinuarEmHttps() throws Exception {
        HttpResponse<String> resposta = enviar("/cadastrar", true);

        assertEquals(302, resposta.statusCode());
        String destino = resposta.headers().firstValue("Location").orElseThrow();
        assertTrue(destino.equals("/login") || destino.equals("https://despensa.onrender.com/login"), destino);
    }

    @Test
    void atrasDoProxyComHttps_deveEnviarOCabecalhoHsts() throws Exception {
        HttpResponse<String> resposta = enviar("/login", true);

        assertTrue(resposta.headers().firstValue("Strict-Transport-Security").isPresent());
    }

    @Test
    void semProxyEmHttpLocal_cookieDeSessaoNaoDeveExigirHttps() throws Exception {
        HttpResponse<String> resposta = enviar("/cadastrar", false);

        String cookie = cookieDeSessao(resposta).orElseThrow();
        assertFalse(cookie.contains("Secure"), cookie);
        assertEquals("/login", resposta.headers().firstValue("Location").orElseThrow()
                .replace("http://localhost:" + porta, ""));
    }

    @Test
    void endpointDeSaude_deveResponderOkSemLoginSemDadosESemCriarSessao() throws Exception {
        HttpResponse<String> resposta = enviar("/saude", true);

        assertEquals(200, resposta.statusCode());
        assertEquals("OK", resposta.body());
        assertTrue(resposta.headers().firstValue("Content-Type").orElseThrow().startsWith("text/plain"));
        assertTrue(resposta.headers().firstValue("Cache-Control").orElseThrow().contains("no-store"));
        assertTrue(cookieDeSessao(resposta).isEmpty());
    }

    private HttpResponse<String> enviar(String caminho, boolean viaProxyHttps) throws IOException, InterruptedException {
        HttpRequest.Builder requisicao = HttpRequest.newBuilder(URI.create("http://localhost:" + porta + caminho)).GET();
        if (viaProxyHttps) {
            requisicao.header("X-Forwarded-Proto", "https")
                    .header("X-Forwarded-Host", "despensa.onrender.com")
                    .header("X-Forwarded-Port", "443")
                    .header("X-Forwarded-For", "203.0.113.10");
        }
        return cliente.send(requisicao.build(), HttpResponse.BodyHandlers.ofString());
    }

    private static Optional<String> cookieDeSessao(HttpResponse<String> resposta) {
        List<String> cookies = resposta.headers().allValues("Set-Cookie");
        return cookies.stream().filter(c -> c.startsWith("JSESSIONID=")).findFirst();
    }
}
