package br.com.unisinos.despensa.email;

import java.time.LocalDate;

public record ItemAviso(String produtoId, String loteId, String nomeProduto, double quantidade,
                        LocalDate dataValidade, long diasParaVencer) {

    public boolean vencido() {
        return diasParaVencer < 0;
    }

    public String prazo() {
        if (diasParaVencer < 0) {
            long dias = -diasParaVencer;
            return "Venceu há " + dias + (dias == 1 ? " dia" : " dias");
        }
        if (diasParaVencer == 0) {
            return "Vence hoje";
        }
        if (diasParaVencer == 1) {
            return "Vence amanhã";
        }
        return "Vence em " + diasParaVencer + " dias";
    }

    public String quantidadeFormatada() {
        return quantidade == Math.rint(quantidade)
                ? String.valueOf((long) quantidade)
                : String.valueOf(quantidade).replace('.', ',');
    }
}
