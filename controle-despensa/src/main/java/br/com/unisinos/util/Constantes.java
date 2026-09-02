package br.com.unisinos.util;

/**
 * Constantes de configuração da aplicação.
 */
public final class Constantes {

    private Constantes() {
        // classe utilitária, não deve ser instanciada
    }

    /**
     * Quantidade de dias antes do vencimento em que um lote passa a
     * aparecer no alerta preventivo de "vencendo em breve".
     */
    public static final int DIAS_ALERTA_VENCIMENTO = 7;
}
