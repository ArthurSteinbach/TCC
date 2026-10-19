package br.com.unisinos.despensa.integracao;

import org.springframework.stereotype.Component;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Optional;

@Component
public class ConsultaProdutoExterna {

    private static final String URL_BASE = "https://world.openfoodfacts.org/api/v2/product/";
    private static final JsonMapper JSON = JsonMapper.builder().build();

    private final HttpClient client;

    public ConsultaProdutoExterna(HttpClient client) {
        this.client = client;
    }

    public Optional<InfoProdutoExterno> buscarPorCodigoBarras(String codigoBarras)
            throws IOException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(URL_BASE + URLEncoder.encode(codigoBarras.trim(), StandardCharsets.UTF_8) + ".json"))
                .timeout(Duration.ofSeconds(5))
                .GET()
                .build();

        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

        if (response.statusCode() == 404) {
            return Optional.empty();
        }
        if (response.statusCode() != 200) {
            throw new IOException("Resposta inesperada da Open Food Facts: HTTP " + response.statusCode());
        }

        JsonNode json;
        try {
            json = JSON.readTree(response.body());
        } catch (JacksonException e) {
            throw new IOException("Resposta ilegível da Open Food Facts.", e);
        }

        JsonNode status = json.get("status");
        if (status == null || !status.isNumber() || status.intValue() != 1) {
            return Optional.empty();
        }

        JsonNode produto = json.get("product");
        if (produto == null || !produto.isObject()) {
            return Optional.empty();
        }

        String nome = texto(produto, "product_name");
        String categorias = texto(produto, "categories");
        String categoria = categorias.isEmpty() ? "" : categorias.split(",")[0].trim();

        if (nome.isEmpty()) {
            return Optional.empty();
        }

        return Optional.of(new InfoProdutoExterno(nome, categoria));
    }

    private static String texto(JsonNode no, String campo) {
        JsonNode valor = no.get(campo);
        return valor != null && valor.isString() ? valor.stringValue().trim() : "";
    }
}
