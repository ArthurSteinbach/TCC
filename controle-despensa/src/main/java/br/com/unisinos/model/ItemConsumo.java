package br.com.unisinos.model;

public record ItemConsumo(Lote lote, double quantidadeConsumida) {

    public double quantidadeRestante() {
        return lote.getQuantidade() - quantidadeConsumida;
    }

    public boolean consomeLoteInteiro() {
        return quantidadeRestante() <= 0;
    }
}
