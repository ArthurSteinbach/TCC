package br.com.unisinos.despensa.email;

public class FalhaNoEnvioException extends Exception {

    public FalhaNoEnvioException(String mensagem) {
        super(mensagem);
    }

    public FalhaNoEnvioException(String mensagem, Throwable causa) {
        super(mensagem, causa);
    }
}
