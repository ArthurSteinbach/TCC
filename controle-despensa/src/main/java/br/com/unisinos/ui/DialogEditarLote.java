package br.com.unisinos.ui;

import br.com.unisinos.exception.ValidacaoException;
import br.com.unisinos.model.Lote;
import br.com.unisinos.service.LoteService;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JDialog;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JTextField;
import java.awt.BorderLayout;
import java.awt.FlowLayout;
import java.awt.GridLayout;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;

/** Formulário para editar quantidade, preço e validade de um lote já existente. */
public class DialogEditarLote extends JDialog {

    private static final DateTimeFormatter FORMATO_DATA = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    private final JTextField campoQuantidade;
    private final JTextField campoPreco;
    private final JTextField campoValidade;
    private final Lote lote;

    public DialogEditarLote(JFrame parent, Lote lote, LoteService loteService, Runnable aoSalvarComSucesso) {
        super(parent, "Editar Lote", true);
        this.lote = lote;
        setSize(400, 280);
        setLocationRelativeTo(parent);
        setLayout(new BorderLayout(10, 10));

        campoQuantidade = new JTextField(formatarNumero(lote.getQuantidade()), 6);
        campoPreco = new JTextField(formatarNumero(lote.getPrecoUnitario()), 6);
        campoValidade = new JTextField(lote.getDataValidade().format(FORMATO_DATA), 10);

        JPanel form = new JPanel(new GridLayout(4, 2, 8, 8));
        form.setBorder(BorderFactory.createEmptyBorder(15, 15, 15, 15));
        form.add(new JLabel("Produto:"));
        form.add(new JLabel(lote.getNomeProduto()));
        form.add(new JLabel("Quantidade:"));
        form.add(campoQuantidade);
        form.add(new JLabel("Preço unitário (R$):"));
        form.add(campoPreco);
        form.add(new JLabel("Validade (DD/MM/AAAA):"));
        form.add(campoValidade);
        add(form, BorderLayout.CENTER);

        JPanel painelBotoes = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        JButton btnSalvar = new JButton("Salvar");
        JButton btnCancelar = new JButton("Cancelar");
        painelBotoes.add(btnCancelar);
        painelBotoes.add(btnSalvar);
        add(painelBotoes, BorderLayout.SOUTH);

        btnCancelar.addActionListener(e -> dispose());
        btnSalvar.addActionListener(e -> salvar(loteService, aoSalvarComSucesso));
    }

    private String formatarNumero(double valor) {
        if (valor == Math.floor(valor)) {
            return String.valueOf((long) valor);
        }
        return String.valueOf(valor);
    }

    private void salvar(LoteService loteService, Runnable aoSalvarComSucesso) {
        double quantidade;
        double preco;
        LocalDate validade;

        try {
            quantidade = Double.parseDouble(campoQuantidade.getText().replace(",", "."));
        } catch (NumberFormatException e) {
            JOptionPane.showMessageDialog(this, "Quantidade inválida.", "Erro de formato", JOptionPane.WARNING_MESSAGE);
            return;
        }

        try {
            preco = Double.parseDouble(campoPreco.getText().replace(",", "."));
        } catch (NumberFormatException e) {
            JOptionPane.showMessageDialog(this, "Preço inválido.", "Erro de formato", JOptionPane.WARNING_MESSAGE);
            return;
        }

        try {
            validade = LocalDate.parse(campoValidade.getText().trim(), FORMATO_DATA);
        } catch (DateTimeParseException e) {
            JOptionPane.showMessageDialog(this, "Data inválida. Use o formato DD/MM/AAAA (ex: 31/12/2026).",
                    "Erro de formato", JOptionPane.WARNING_MESSAGE);
            return;
        }

        try {
            loteService.editar(lote.getId(), quantidade, preco, validade);
            JOptionPane.showMessageDialog(this, "Lote atualizado com sucesso!");
            aoSalvarComSucesso.run();
            dispose();
        } catch (ValidacaoException e) {
            JOptionPane.showMessageDialog(this, e.getMessage(), "Não foi possível salvar", JOptionPane.WARNING_MESSAGE);
        } catch (SQLException e) {
            JOptionPane.showMessageDialog(this, "Erro ao salvar no banco: " + e.getMessage(), "Erro", JOptionPane.ERROR_MESSAGE);
        }
    }
}
