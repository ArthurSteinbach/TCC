package br.com.unisinos.despensa.model;

import java.time.LocalDateTime;

public record PreferenciasNotificacao(boolean avisosAtivos, String emailConta, String emailDestino,
                                      String emailPendente, LocalDateTime pendenteExpiraEm,
                                      boolean envioConfigurado) {

    public boolean usaEmailDaConta() {
        return emailDestino.equals(emailConta);
    }

    public boolean temPendente() {
        return emailPendente != null;
    }
}
