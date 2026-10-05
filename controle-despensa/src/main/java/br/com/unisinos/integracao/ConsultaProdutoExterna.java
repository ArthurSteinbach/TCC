package br.com.unisinos.integracao;

import org.json.JSONObject;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Optional;

/**
 * Consulta a base pública Open Food Facts (openfoodfacts.org) para obter
 * nome e categoria de um produto a partir do seu código de barras EAN-13.
 *
 * Importante: essa consulta NUNCA retorna data de validade — bases de
 * produto só armazenam informações fixas do item genérico (nome, marca,
 * categoria), nunca a validade de um lote específico, que é decidida na
 * fábrica no momento da embalagem e não faz parte do código de barras.
 * A validade continua sendo sempre informada manualmente pelo usuário,
 * como já previsto na Seção 4.3.1 do TCC I.
 */
public class ConsultaProdutoExterna {

    private static final String URL_BASE = "https://world.openfoodfacts.org/api/v2/product/";

    private final HttpClient client;

    public ConsultaProdutoExterna() {
        this(HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(5))
                .build());
    }

    public ConsultaProdutoExterna(HttpClient client) {
        this.client = client;
    }

    /**
     * Busca o produto pelo código de barras. Retorna vazio se não for
     * encontrado na base. Se a consulta falhar (sem internet, timeout,
     * base fora do ar, resposta ilegível), lança exceção — em ambos os
     * casos o fluxo normal é o usuário preencher os dados manualmente.
     */
    public Optional<InfoProdutoExterno> buscarPorCodigoBarras(String codigoBarras)
            throws IOException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(URL_BASE + codigoBarras + ".json"))
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

        JSONObject json = new JSONObject(response.body());

        if (json.optInt("status", 0) != 1) {
            return Optional.empty(); // produto não encontrado na base
        }

        JSONObject produto = json.getJSONObject("product");
        String nome = produto.optString("product_name", "").trim();
        String categorias = produto.optString("categories", "").trim();
        String categoria = categorias.isEmpty() ? "" : categorias.split(",")[0].trim();

        if (nome.isEmpty()) {
            return Optional.empty(); // veio resposta, mas sem nome utilizável
        }

        return Optional.of(new InfoProdutoExterno(nome, categoria));
    }
}
