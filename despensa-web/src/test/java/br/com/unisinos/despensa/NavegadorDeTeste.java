package br.com.unisinos.despensa;

import java.io.IOException;
import java.net.CookieManager;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

final class NavegadorDeTeste {

    private static final Pattern TOKEN_CSRF = Pattern.compile("name=\"_csrf\" value=\"([^\"]+)\"");
    private static final String ACEITA_HTML = "text/html,application/xhtml+xml,*/*;q=0.8";

    private final int porta;
    private final HttpClient cliente = HttpClient.newBuilder()
            .cookieHandler(new CookieManager())
            .followRedirects(HttpClient.Redirect.NEVER)
            .build();

    NavegadorDeTeste(int porta) {
        this.porta = porta;
    }

    HttpResponse<String> abrir(String caminho) throws IOException, InterruptedException {
        return cliente.send(HttpRequest.newBuilder(endereco(caminho)).header("Accept", ACEITA_HTML).GET().build(),
                HttpResponse.BodyHandlers.ofString());
    }

    HttpResponse<byte[]> baixar(String caminho) throws IOException, InterruptedException {
        return cliente.send(HttpRequest.newBuilder(endereco(caminho)).GET().build(),
                HttpResponse.BodyHandlers.ofByteArray());
    }

    HttpResponse<String> enviarFormulario(String caminho, String formulario) throws IOException, InterruptedException {
        return cliente.send(HttpRequest.newBuilder(endereco(caminho))
                        .header("Accept", ACEITA_HTML)
                        .header("Content-Type", "application/x-www-form-urlencoded")
                        .POST(HttpRequest.BodyPublishers.ofString(formulario))
                        .build(),
                HttpResponse.BodyHandlers.ofString());
    }

    String entrar(String email, String senha) throws IOException, InterruptedException {
        Matcher token = TOKEN_CSRF.matcher(abrir("/login").body());
        if (!token.find()) {
            throw new IllegalStateException("Página de login sem token CSRF");
        }
        HttpResponse<String> resposta = enviarFormulario("/login",
                "email=" + codificar(email) + "&senha=" + codificar(senha) + "&_csrf=" + codificar(token.group(1)));
        if (resposta.statusCode() != 302) {
            throw new IllegalStateException("Login devolveu HTTP " + resposta.statusCode());
        }
        return resposta.headers().firstValue("Location").orElseThrow().replace("http://localhost:" + porta, "");
    }

    private URI endereco(String caminho) {
        return URI.create("http://localhost:" + porta + caminho);
    }

    private static String codificar(String valor) {
        return URLEncoder.encode(valor, StandardCharsets.UTF_8);
    }
}
