package br.com.unisinos.despensa.model;

import java.time.LocalDate;

public record ItemConsumo(String loteId, LocalDate dataValidade, double precoUnitario,
                          double quantidadeNoLote, double quantidadeConsumida) {

    public double quantidadeRestante() {
        return quantidadeNoLote - quantidadeConsumida;
    }

    public boolean consomeLoteInteiro() {
        return quantidadeRestante() <= 0;
    }
}
