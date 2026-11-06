package br.com.unisinos.despensa;

import java.io.IOException;
import java.net.CookieManager;
import java.net.HttpCookie;
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
    private final CookieManager cookies = new CookieManager();
    private final HttpClient cliente = HttpClient.newBuilder()
            .cookieHandler(cookies)
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
        HttpResponse<String> resposta = enviarLogin(email, senha, tokenDoLogin());
        if (resposta.statusCode() != 302) {
            throw new IllegalStateException("Login devolveu HTTP " + resposta.statusCode());
        }
        return destino(resposta);
    }

    String tokenDoLogin() throws IOException, InterruptedException {
        Matcher token = TOKEN_CSRF.matcher(abrir("/login").body());
        if (!token.find()) {
            throw new IllegalStateException("Página de login sem token CSRF");
        }
        return token.group(1);
    }

    HttpResponse<String> enviarLogin(String email, String senha, String token) throws IOException, InterruptedException {
        return enviarFormulario("/login",
                "email=" + codificar(email) + "&senha=" + codificar(senha) + "&_csrf=" + codificar(token));
    }

    void trocarSessaoPorUmaQueNaoExisteMais() {
        cookies.getCookieStore().removeAll();
        HttpCookie sessao = new HttpCookie("JSESSIONID", "SESSAO-DE-ANTES-DO-SERVIDOR-REINICIAR");
        sessao.setPath("/");
        sessao.setVersion(0);
        cookies.getCookieStore().add(endereco("/"), sessao);
    }

    String destino(HttpResponse<?> resposta) {
        return resposta.headers().firstValue("Location").orElseThrow().replace("http://localhost:" + porta, "");
    }

    private URI endereco(String caminho) {
        return URI.create("http://localhost:" + porta + caminho);
    }

    private static String codificar(String valor) {
        return URLEncoder.encode(valor, StandardCharsets.UTF_8);
    }
}
