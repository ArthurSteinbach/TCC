package br.com.unisinos.despensa.integracao;

import org.junit.jupiter.api.Test;

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLParameters;
import javax.net.ssl.SSLSession;
import java.io.IOException;
import java.net.Authenticator;
import java.net.ConnectException;
import java.net.CookieHandler;
import java.net.ProxySelector;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpHeaders;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.time.Duration;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ConsultaProdutoExternaTest {

    private static final String CODIGO = "7891000100103";

    @Test
    void produtoEncontrado_deveRetornarNomeEPrimeiraCategoria() throws Exception {
        ClienteHttpFalso cliente = ClienteHttpFalso.respondendo(200,
                "{\"status\":1,\"product\":{\"product_name\":\" Leite Condensado \",\"categories\":\"Laticínios, Doces\"}}");

        Optional<InfoProdutoExterno> resultado = consultaCom(cliente).buscarPorCodigoBarras(CODIGO);

        assertTrue(resultado.isPresent());
        assertEquals("Leite Condensado", resultado.get().nome());
        assertEquals("Laticínios", resultado.get().categoria());
    }

    @Test
    void consulta_deveUsarOCodigoDeBarrasNaUrl() throws Exception {
        ClienteHttpFalso cliente = ClienteHttpFalso.respondendo(404, "{\"status\":0}");

        consultaCom(cliente).buscarPorCodigoBarras(CODIGO);

        assertEquals("https://world.openfoodfacts.org/api/v2/product/" + CODIGO + ".json",
                cliente.ultimaRequisicao.uri().toString());
    }

    @Test
    void http404_deveRetornarVazio() throws Exception {
        ClienteHttpFalso cliente = ClienteHttpFalso.respondendo(404,
                "{\"status\":0,\"status_verbose\":\"product not found\"}");

        assertTrue(consultaCom(cliente).buscarPorCodigoBarras(CODIGO).isEmpty());
    }

    @Test
    void http200ComStatusZero_deveRetornarVazio() throws Exception {
        ClienteHttpFalso cliente = ClienteHttpFalso.respondendo(200,
                "{\"status\":0,\"status_verbose\":\"no code or invalid code\"}");

        assertTrue(consultaCom(cliente).buscarPorCodigoBarras(CODIGO).isEmpty());
    }

    @Test
    void produtoSemNome_deveRetornarVazio() throws Exception {
        ClienteHttpFalso cliente = ClienteHttpFalso.respondendo(200,
                "{\"status\":1,\"product\":{\"product_name\":\"  \",\"categories\":\"Laticínios\"}}");

        assertTrue(consultaCom(cliente).buscarPorCodigoBarras(CODIGO).isEmpty());
    }

    @Test
    void semConexao_deveLancarIOException() throws Exception {
        ClienteHttpFalso cliente = ClienteHttpFalso.falhando(new ConnectException("sem internet"));

        assertThrows(IOException.class, () -> consultaCom(cliente).buscarPorCodigoBarras(CODIGO));
    }

    @Test
    void timeout_deveLancarHttpTimeoutException() throws Exception {
        ClienteHttpFalso cliente = ClienteHttpFalso.falhando(new HttpTimeoutException("request timed out"));

        assertThrows(HttpTimeoutException.class, () -> consultaCom(cliente).buscarPorCodigoBarras(CODIGO));
    }

    @Test
    void erroNoServidor_deveLancarIOException() throws Exception {
        ClienteHttpFalso cliente = ClienteHttpFalso.respondendo(503, "Service Unavailable");

        assertThrows(IOException.class, () -> consultaCom(cliente).buscarPorCodigoBarras(CODIGO));
    }

    @Test
    void respostaIlegivel_deveLancarIOException() throws Exception {
        ClienteHttpFalso cliente = ClienteHttpFalso.respondendo(200, "<html>manutenção</html>");

        assertThrows(IOException.class, () -> consultaCom(cliente).buscarPorCodigoBarras(CODIGO));
    }

    @Test
    void produtoSemCampoDeCategorias_deveRetornarCategoriaVazia() throws Exception {
        ClienteHttpFalso cliente = ClienteHttpFalso.respondendo(200,
                "{\"status\":1,\"product\":{\"product_name\":\"Café Torrado\"}}");

        Optional<InfoProdutoExterno> resultado = consultaCom(cliente).buscarPorCodigoBarras(CODIGO);

        assertTrue(resultado.isPresent());
        assertEquals("Café Torrado", resultado.get().nome());
        assertEquals("", resultado.get().categoria());
    }

    private static ConsultaProdutoExterna consultaCom(HttpClient cliente) {
        return new ConsultaProdutoExterna(cliente);
    }

    private static final class ClienteHttpFalso extends HttpClient {

        private final int status;
        private final String corpo;
        private final IOException falha;
        private HttpRequest ultimaRequisicao;

        private ClienteHttpFalso(int status, String corpo, IOException falha) {
            this.status = status;
            this.corpo = corpo;
            this.falha = falha;
        }

        static ClienteHttpFalso respondendo(int status, String corpo) {
            return new ClienteHttpFalso(status, corpo, null);
        }

        static ClienteHttpFalso falhando(IOException falha) {
            return new ClienteHttpFalso(0, null, falha);
        }

        @Override
        @SuppressWarnings("unchecked")
        public <T> HttpResponse<T> send(HttpRequest request, HttpResponse.BodyHandler<T> handler) throws IOException {
            ultimaRequisicao = request;
            if (falha != null) {
                throw falha;
            }
            return (HttpResponse<T>) new RespostaFalsa(request, status, corpo);
        }

        @Override
        public <T> CompletableFuture<HttpResponse<T>> sendAsync(HttpRequest request, HttpResponse.BodyHandler<T> handler) {
            throw new UnsupportedOperationException();
        }

        @Override
        public <T> CompletableFuture<HttpResponse<T>> sendAsync(HttpRequest request, HttpResponse.BodyHandler<T> handler,
                                                                HttpResponse.PushPromiseHandler<T> pushPromiseHandler) {
            throw new UnsupportedOperationException();
        }

        @Override
        public Optional<CookieHandler> cookieHandler() {
            return Optional.empty();
        }

        @Override
        public Optional<Duration> connectTimeout() {
            return Optional.empty();
        }

        @Override
        public Redirect followRedirects() {
            return Redirect.NEVER;
        }

        @Override
        public Optional<ProxySelector> proxy() {
            return Optional.empty();
        }

        @Override
        public SSLContext sslContext() {
            return null;
        }

        @Override
        public SSLParameters sslParameters() {
            return null;
        }

        @Override
        public Optional<Authenticator> authenticator() {
            return Optional.empty();
        }

        @Override
        public Version version() {
            return Version.HTTP_1_1;
        }

        @Override
        public Optional<Executor> executor() {
            return Optional.empty();
        }
    }

    private record RespostaFalsa(HttpRequest request, int statusCode, String body) implements HttpResponse<String> {

        @Override
        public Optional<HttpResponse<String>> previousResponse() {
            return Optional.empty();
        }

        @Override
        public HttpHeaders headers() {
            return HttpHeaders.of(Map.of(), (nome, valor) -> true);
        }

        @Override
        public Optional<SSLSession> sslSession() {
            return Optional.empty();
        }

        @Override
        public URI uri() {
            return request.uri();
        }

        @Override
        public HttpClient.Version version() {
            return HttpClient.Version.HTTP_1_1;
        }
    }
}
