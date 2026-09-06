package br.com.unisinos.ui;

import br.com.unisinos.model.Lote;
import br.com.unisinos.service.LoteService;
import br.com.unisinos.util.Constantes;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JDialog;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.SwingConstants;
import java.awt.BorderLayout;
import java.awt.FlowLayout;
import java.awt.Font;
import java.sql.SQLException;
import java.util.List;

/**
 * Tela de alertas: mostra apenas os lotes vencendo dentro da janela
 * configurada (Constantes.DIAS_ALERTA_VENCIMENTO) ou já vencidos —
 * atendendo ao objetivo específico "c" do TCC I (identificar os
 * alimentos próximos do prazo de validade para organizar o consumo).
 */
public class DialogAlertas extends JDialog {

    public DialogAlertas(JFrame parent, LoteService loteService) {
        super(parent, "Alertas de Vencimento", true);
        setSize(720, 450);
        setLocationRelativeTo(parent);
        setLayout(new BorderLayout(10, 10));

        JLabel titulo = new JLabel(
                "Itens vencendo em até " + Constantes.DIAS_ALERTA_VENCIMENTO + " dias (ou já vencidos)",
                SwingConstants.LEFT);
        titulo.setFont(titulo.getFont().deriveFont(Font.BOLD, 15f));
        titulo.setBorder(BorderFactory.createEmptyBorder(10, 10, 0, 10));
        add(titulo, BorderLayout.NORTH);

        LoteTableModel modelo = new LoteTableModel();
        JTable tabela = new JTable(modelo);
        tabela.setDefaultRenderer(Object.class, new AlertaRowRenderer());
        tabela.setRowHeight(24);
        add(new JScrollPane(tabela), BorderLayout.CENTER);

        JButton btnFechar = new JButton("Fechar");
        btnFechar.addActionListener(e -> dispose());
        JPanel painelBotao = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        painelBotao.add(btnFechar);
        add(painelBotao, BorderLayout.SOUTH);

        try {
            List<Lote> vencendo = loteService.listarAlertaVencimento();
            modelo.setLotes(vencendo);
            if (vencendo.isEmpty()) {
                JOptionPane.showMessageDialog(this,
                        "Nenhum item vencendo nos próximos " + Constantes.DIAS_ALERTA_VENCIMENTO + " dias. Tudo em dia!",
                        "Sem alertas", JOptionPane.INFORMATION_MESSAGE);
            }
        } catch (SQLException e) {
            JOptionPane.showMessageDialog(this, "Erro ao carregar alertas: " + e.getMessage(),
                    "Erro", JOptionPane.ERROR_MESSAGE);
        }
    }
}
