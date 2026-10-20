package br.com.unisinos.despensa.web;

import java.math.BigDecimal;
import java.util.Locale;

final class Formatos {

    private Formatos() {
    }

    static String quantidade(double quantidade) {
        return quantidade == Math.rint(quantidade)
                ? String.valueOf((long) quantidade)
                : String.valueOf(quantidade).replace('.', ',');
    }

    static String numeroParaCampo(double valor) {
        return BigDecimal.valueOf(valor).stripTrailingZeros().toPlainString();
    }

    static String precoParaCampo(double valor) {
        return String.format(Locale.ROOT, "%.2f", valor);
    }
}
