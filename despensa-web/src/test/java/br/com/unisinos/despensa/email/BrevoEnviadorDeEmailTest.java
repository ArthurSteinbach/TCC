package br.com.unisinos.despensa.email;

import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLParameters;
import javax.net.ssl.SSLSession;
import java.io.ByteArrayOutputStream;
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
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.Flow;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BrevoEnviadorDeEmailTest {

    private static final String CHAVE = "chave-falsa-de-teste";
    private static final MensagemEmail MENSAGEM =
            new MensagemEmail("ana@exemplo.com", "Assunto", "<p>Olá</p>", "Olá");

    @Test
    void envio_deveChamarAApiDaBrevoComAChaveNoCabecalhoEOCorpoCompleto() throws Exception {
        ClienteFalso cliente = new ClienteFalso(201, "{\"messageId\":\"<1@brevo>\"}", null);

        new BrevoEnviadorDeEmail(cliente, CHAVE, "avisos@despensa.teste", "Controle de Despensa").enviar(MENSAGEM);

        HttpRequest requisicao = cliente.requisicao;
        assertEquals(URI.create("https://api.brevo.com/v3/smtp/email"), requisicao.uri());
        assertEquals("POST", requisicao.method());
        assertEquals(CHAVE, requisicao.headers().firstValue("api-key").orElseThrow());
        assertEquals("application/json", requisicao.headers().firstValue("content-type").orElseThrow());
        JsonNode corpo = JsonMapper.builder().build().readTree(corpoDe(requisicao));
        assertEquals("avisos@despensa.teste", corpo.get("sender").get("email").stringValue());
        assertEquals("Controle de Despensa", corpo.get("sender").get("name").stringValue());
        assertEquals("ana@exemplo.com", corpo.get("to").get(0).get("email").stringValue());
        assertEquals("Assunto", corpo.get("subject").stringValue());
        assertEquals("<p>Olá</p>", corpo.get("htmlContent").stringValue());
        assertEquals("Olá", corpo.get("textContent").stringValue());
    }

    @Test
    void respostaDeErroDaBrevo_deveVirarFalhaSemExporAChave() {
        ClienteFalso cliente = new ClienteFalso(401, "{\"message\":\"Key not found\"}", null);
        BrevoEnviadorDeEmail enviador = new BrevoEnviadorDeEmail(cliente, CHAVE, "avisos@despensa.teste", "Despensa");

        FalhaNoEnvioException erro = assertThrows(FalhaNoEnvioException.class, () -> enviador.enviar(MENSAGEM));

        assertTrue(erro.getMessage().contains("HTTP 401"));
        assertFalse(erro.getMessage().contains(CHAVE));
    }

    @Test
    void semConexao_deveVirarFalhaNoEnvio() {
        ClienteFalso cliente = new ClienteFalso(0, null, new ConnectException("sem internet"));
        BrevoEnviadorDeEmail enviador = new BrevoEnviadorDeEmail(cliente, CHAVE, "avisos@despensa.teste", "Despensa");

        assertThrows(FalhaNoEnvioException.class, () -> enviador.enviar(MENSAGEM));
    }

    @Test
    void envioDesligado_naoDeveEstarAtivoNemEnviar() {
        EnvioDeEmailDesligado desligado = new EnvioDeEmailDesligado();

        assertFalse(desligado.ativo());
        assertThrows(FalhaNoEnvioException.class, () -> desligado.enviar(MENSAGEM));
    }

    private static String corpoDe(HttpRequest requisicao) {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        requisicao.bodyPublisher().orElseThrow().subscribe(new Flow.Subscriber<>() {
            @Override
            public void onSubscribe(Flow.Subscription assinatura) {
                assinatura.request(Long.MAX_VALUE);
            }

            @Override
            public void onNext(ByteBuffer parte) {
                byte[] dados = new byte[parte.remaining()];
                parte.get(dados);
                bytes.writeBytes(dados);
            }

            @Override
            public void onError(Throwable erro) {
                throw new IllegalStateException(erro);
            }

            @Override
            public void onComplete() {
            }
        });
        return bytes.toString(StandardCharsets.UTF_8);
    }

    private static final class ClienteFalso extends HttpClient {

        private final int status;
        private final String corpo;
        private final IOException falha;
        private HttpRequest requisicao;

        private ClienteFalso(int status, String corpo, IOException falha) {
            this.status = status;
            this.corpo = corpo;
            this.falha = falha;
        }

        @Override
        @SuppressWarnings("unchecked")
        public <T> HttpResponse<T> send(HttpRequest request, HttpResponse.BodyHandler<T> handler) throws IOException {
            requisicao = request;
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
