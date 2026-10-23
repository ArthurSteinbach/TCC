package br.com.unisinos.despensa.email;

public interface EnviadorDeEmail {

    boolean ativo();

    void enviar(MensagemEmail mensagem) throws FalhaNoEnvioException;
}
