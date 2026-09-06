package br.com.unisinos.ui;

import br.com.unisinos.model.Lote;

import javax.swing.table.AbstractTableModel;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;

/**
 * Adapta uma lista de Lote para ser exibida numa JTable. Cada linha
 * representa um lote ativo; a ordem de exibição é a mesma devolvida
 * pelo Service (já ordenada por FEFO).
 */
public class LoteTableModel extends AbstractTableModel {

    private static final String[] COLUNAS = {"Produto", "Quantidade", "Validade", "Dias p/ vencer", "Valor (R$)"};
    private static final DateTimeFormatter FORMATO_DATA = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    private List<Lote> lotes = new ArrayList<>();

    public void setLotes(List<Lote> lotes) {
        this.lotes = lotes;
        fireTableDataChanged();
    }

    public Lote getLoteNaLinha(int linha) {
        return lotes.get(linha);
    }

    /** Mostra "3" em vez de "3.0" quando a quantidade é um número inteiro. */
    private String formatarQuantidade(double quantidade) {
        if (quantidade == Math.floor(quantidade)) {
            return String.valueOf((long) quantidade);
        }
        return String.valueOf(quantidade);
    }

    @Override
    public int getRowCount() {
        return lotes.size();
    }

    @Override
    public int getColumnCount() {
        return COLUNAS.length;
    }

    @Override
    public String getColumnName(int coluna) {
        return COLUNAS[coluna];
    }

    @Override
    public Object getValueAt(int linha, int coluna) {
        Lote lote = lotes.get(linha);
        return switch (coluna) {
            case 0 -> lote.getNomeProduto();
            case 1 -> formatarQuantidade(lote.getQuantidade());
            case 2 -> lote.getDataValidade().format(FORMATO_DATA);
            case 3 -> ChronoUnit.DAYS.between(LocalDate.now(), lote.getDataValidade());
            case 4 -> String.format("%.2f", lote.getValorTotal());
            default -> "";
        };
    }
}
