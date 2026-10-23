package br.com.unisinos.despensa.email;

import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;
import java.util.Map;

public class BrevoEnviadorDeEmail implements EnviadorDeEmail {

    static final URI ENDERECO_API = URI.create("https://api.brevo.com/v3/smtp/email");
    private static final JsonMapper JSON = JsonMapper.builder().build();
    private static final int LIMITE_DO_CORPO_NO_ERRO = 300;

    private final HttpClient cliente;
    private final String chaveApi;
    private final String remetente;
    private final String nomeRemetente;

    public BrevoEnviadorDeEmail(HttpClient cliente, String chaveApi, String remetente, String nomeRemetente) {
        this.cliente = cliente;
        this.chaveApi = chaveApi;
        this.remetente = remetente;
        this.nomeRemetente = nomeRemetente;
    }

    @Override
    public boolean ativo() {
        return true;
    }

    @Override
    public void enviar(MensagemEmail mensagem) throws FalhaNoEnvioException {
        Map<String, Object> corpo = Map.of(
                "sender", Map.of("name", nomeRemetente, "email", remetente),
                "to", List.of(Map.of("email", mensagem.destinatario())),
                "subject", mensagem.assunto(),
                "htmlContent", mensagem.html(),
                "textContent", mensagem.texto());

        HttpRequest requisicao = HttpRequest.newBuilder(ENDERECO_API)
                .timeout(Duration.ofSeconds(15))
                .header("api-key", chaveApi)
                .header("accept", "application/json")
                .header("content-type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(JSON.writeValueAsString(corpo)))
                .build();

        HttpResponse<String> resposta;
        try {
            resposta = cliente.send(requisicao, HttpResponse.BodyHandlers.ofString());
        } catch (IOException e) {
            throw new FalhaNoEnvioException("Falha de comunicação com a Brevo: " + e.getClass().getSimpleName(), e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new FalhaNoEnvioException("Envio interrompido.", e);
        }

        if (resposta.statusCode() / 100 != 2) {
            String detalhe = resposta.body() == null ? "" : resposta.body();
            if (detalhe.length() > LIMITE_DO_CORPO_NO_ERRO) {
                detalhe = detalhe.substring(0, LIMITE_DO_CORPO_NO_ERRO);
            }
            throw new FalhaNoEnvioException("A Brevo recusou o envio: HTTP " + resposta.statusCode() + " " + detalhe);
        }
    }
}
