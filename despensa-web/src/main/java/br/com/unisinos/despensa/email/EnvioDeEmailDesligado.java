package br.com.unisinos.despensa.email;

public class EnvioDeEmailDesligado implements EnviadorDeEmail {

    @Override
    public boolean ativo() {
        return false;
    }

    @Override
    public void enviar(MensagemEmail mensagem) throws FalhaNoEnvioException {
        throw new FalhaNoEnvioException("O envio de e-mails está desligado.");
    }
}
