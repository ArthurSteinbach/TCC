package br.com.unisinos.ui;

import br.com.unisinos.model.Lote;
import br.com.unisinos.service.LoteService;

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
 * Relatório de perdas financeiras: lista todos os lotes descartados por
 * vencimento, individualmente, com valor e data de descarte — atendendo
 * ao objetivo específico "d" do TCC I (comparar as perdas financeiras
 * através da geração de relatórios sobre o desperdício).
 */
public class DialogRelatorioPerdas extends JDialog {

    public DialogRelatorioPerdas(JFrame parent, LoteService loteService) {
        super(parent, "Relatório de Perdas Financeiras", true);
        setSize(650, 450);
        setLocationRelativeTo(parent);
        setLayout(new BorderLayout(10, 10));

        JLabel titulo = new JLabel("Itens descartados por vencimento", SwingConstants.LEFT);
        titulo.setFont(titulo.getFont().deriveFont(Font.BOLD, 15f));
        titulo.setBorder(BorderFactory.createEmptyBorder(10, 10, 0, 10));
        add(titulo, BorderLayout.NORTH);

        RelatorioPerdasTableModel modelo = new RelatorioPerdasTableModel();
        JTable tabela = new JTable(modelo);
        tabela.setRowHeight(24);
        add(new JScrollPane(tabela), BorderLayout.CENTER);

        JLabel labelTotal = new JLabel();
        labelTotal.setFont(labelTotal.getFont().deriveFont(Font.BOLD));
        labelTotal.setBorder(BorderFactory.createEmptyBorder(8, 10, 10, 10));

        JButton btnFechar = new JButton("Fechar");
        btnFechar.addActionListener(e -> dispose());
        JPanel painelBotao = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        painelBotao.add(btnFechar);

        JPanel painelInferior = new JPanel(new BorderLayout());
        painelInferior.add(labelTotal, BorderLayout.CENTER);
        painelInferior.add(painelBotao, BorderLayout.SOUTH);
        add(painelInferior, BorderLayout.SOUTH);

        try {
            List<Lote> descartados = loteService.listarDescartados();
            modelo.setLotes(descartados);
            double total = loteService.calcularPerdaFinanceiraTotal();
            labelTotal.setText(String.format(
                    "Total perdido: R$ %.2f  (%d item(ns) descartado(s))", total, descartados.size()));
        } catch (SQLException e) {
            JOptionPane.showMessageDialog(this, "Erro ao carregar relatório: " + e.getMessage(),
                    "Erro", JOptionPane.ERROR_MESSAGE);
        }
    }
}
