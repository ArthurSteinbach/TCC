package br.com.unisinos.ui;

import br.com.unisinos.model.Lote;

import javax.swing.table.AbstractTableModel;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;

/**
 * Adapta a lista de lotes descartados para exibição na tabela do
 * relatório de perdas financeiras (objetivo específico "d" do TCC I).
 */
public class RelatorioPerdasTableModel extends AbstractTableModel {

    private static final String[] COLUNAS = {"Produto", "Quantidade", "Valor (R$)", "Descartado em"};
    private static final DateTimeFormatter FORMATO_ENTRADA = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final DateTimeFormatter FORMATO_SAIDA = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");

    private List<Lote> lotes = new ArrayList<>();

    public void setLotes(List<Lote> lotes) {
        this.lotes = lotes;
        fireTableDataChanged();
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
            case 2 -> String.format("%.2f", lote.getValorTotal());
            case 3 -> formatarDataBaixa(lote.getDataBaixa());
            default -> "";
        };
    }

    private String formatarQuantidade(double quantidade) {
        if (quantidade == Math.floor(quantidade)) {
            return String.valueOf((long) quantidade);
        }
        return String.valueOf(quantidade);
    }

    private String formatarDataBaixa(String dataBaixa) {
        if (dataBaixa == null || dataBaixa.isBlank()) {
            return "-";
        }
        try {
            LocalDateTime dt = LocalDateTime.parse(dataBaixa, FORMATO_ENTRADA);
            return dt.format(FORMATO_SAIDA);
        } catch (DateTimeParseException e) {
            return dataBaixa; // mostra como veio, caso o formato armazenado seja diferente
        }
    }
}
