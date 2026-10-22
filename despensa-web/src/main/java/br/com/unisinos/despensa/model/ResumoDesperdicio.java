package br.com.unisinos.despensa.model;

import java.util.OptionalDouble;

public record ResumoDesperdicio(double totalConsumido, double totalDescartado) {

    public boolean temDados() {
        return totalConsumido + totalDescartado > 0;
    }

    public OptionalDouble percentualDesperdicio() {
        if (!temDados()) {
            return OptionalDouble.empty();
        }
        return OptionalDouble.of(totalDescartado / (totalConsumido + totalDescartado) * 100);
    }
}
