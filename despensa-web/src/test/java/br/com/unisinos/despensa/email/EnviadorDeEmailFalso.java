package br.com.unisinos.despensa.email;

import java.util.ArrayList;
import java.util.List;

public class EnviadorDeEmailFalso implements EnviadorDeEmail {

    private final List<MensagemEmail> enviadas = new ArrayList<>();
    private boolean ativo = true;
    private boolean falhar;
    private Runnable antesDeEnviar;

    @Override
    public boolean ativo() {
        return ativo;
    }

    @Override
    public void enviar(MensagemEmail mensagem) throws FalhaNoEnvioException {
        if (antesDeEnviar != null) {
            Runnable acao = antesDeEnviar;
            antesDeEnviar = null;
            acao.run();
        }
        if (!ativo) {
            throw new FalhaNoEnvioException("O envio de e-mails está desligado.");
        }
        if (falhar) {
            throw new FalhaNoEnvioException("Falha simulada no envio");
        }
        enviadas.add(mensagem);
    }

    public List<MensagemEmail> enviadas() {
        return enviadas;
    }

    public List<MensagemEmail> enviadasPara(String destinatario) {
        return enviadas.stream().filter(m -> m.destinatario().equals(destinatario)).toList();
    }

    public MensagemEmail ultima() {
        return enviadas.get(enviadas.size() - 1);
    }

    public void desligar() {
        ativo = false;
    }

    public void falharEnvios(boolean falhar) {
        this.falhar = falhar;
    }

    public void executarAntesDoProximoEnvio(Runnable acao) {
        antesDeEnviar = acao;
    }

    public void limpar() {
        enviadas.clear();
        ativo = true;
        falhar = false;
        antesDeEnviar = null;
    }
}
