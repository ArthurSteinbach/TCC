package br.com.unisinos.ui;

import br.com.unisinos.model.Lote;
import br.com.unisinos.util.Constantes;

import javax.swing.JTable;
import javax.swing.table.DefaultTableCellRenderer;
import java.awt.Color;
import java.awt.Component;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;

/**
 * Colore as linhas da tabela de lotes conforme a proximidade do vencimento:
 * vermelho claro para itens já vencidos, amarelo claro para os que estão
 * dentro da janela de alerta (Constantes.DIAS_ALERTA_VENCIMENTO), branco
 * para os demais.
 */
public class AlertaRowRenderer extends DefaultTableCellRenderer {

    private static final Color VENCIDO = new Color(255, 205, 205);
    private static final Color ALERTA = new Color(255, 244, 180);

    @Override
    public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected,
                                                     boolean hasFocus, int row, int column) {
        Component c = super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column);

        LoteTableModel modelo = (LoteTableModel) table.getModel();
        Lote lote = modelo.getLoteNaLinha(row);
        long dias = ChronoUnit.DAYS.between(LocalDate.now(), lote.getDataValidade());

        if (isSelected) {
            c.setBackground(table.getSelectionBackground());
        } else if (dias < 0) {
            c.setBackground(VENCIDO);
        } else if (dias <= Constantes.DIAS_ALERTA_VENCIMENTO) {
            c.setBackground(ALERTA);
        } else {
            c.setBackground(Color.WHITE);
        }
        return c;
    }
}
