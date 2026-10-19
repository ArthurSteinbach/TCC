package br.com.unisinos.despensa.web;

import br.com.unisinos.despensa.model.Lote;
import br.com.unisinos.despensa.model.LoteDoProduto;
import br.com.unisinos.despensa.util.Constantes;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;

public record ItemDespensa(String produtoId, String loteId, String nomeProduto, double quantidade,
                           LocalDate dataValidade, long diasParaVencer, double valorTotal) {

    public static ItemDespensa de(LoteDoProduto loteDoProduto, LocalDate hoje) {
        Lote lote = loteDoProduto.lote();
        return new ItemDespensa(loteDoProduto.produtoId(), lote.getId(), loteDoProduto.nomeProduto(),
                lote.getQuantidade(), lote.getDataValidade(), ChronoUnit.DAYS.between(hoje, lote.getDataValidade()),
                lote.getValorTotal());
    }

    public boolean vencido() {
        return diasParaVencer < 0;
    }

    public boolean emAlerta() {
        return diasParaVencer <= Constantes.DIAS_ALERTA_VENCIMENTO;
    }

    public String situacao() {
        if (vencido()) {
            return "item-vencido";
        }
        return emAlerta() ? "item-vencendo" : "item-normal";
    }

    public String prazo() {
        if (diasParaVencer < 0) {
            long dias = -diasParaVencer;
            return "Vencido há " + dias + (dias == 1 ? " dia" : " dias");
        }
        if (diasParaVencer == 0) {
            return "Vence hoje";
        }
        return diasParaVencer + (diasParaVencer == 1 ? " dia" : " dias");
    }

    public String quantidadeFormatada() {
        return quantidade == Math.rint(quantidade)
                ? String.valueOf((long) quantidade)
                : String.valueOf(quantidade).replace('.', ',');
    }
}
