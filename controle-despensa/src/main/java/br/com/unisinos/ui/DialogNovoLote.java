package br.com.unisinos.ui;

import br.com.unisinos.exception.ValidacaoException;
import br.com.unisinos.model.Produto;
import br.com.unisinos.service.LoteService;
import br.com.unisinos.service.ProdutoService;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JComboBox;
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
import java.util.List;

/** Formulário para cadastrar um novo lote de um produto já existente no catálogo. */
public class DialogNovoLote extends JDialog {

    private final JComboBox<Produto> comboProduto = new JComboBox<>();
    private final JTextField campoQuantidade = new JTextField(6);
    private final JTextField campoPreco = new JTextField(6);
    private final JTextField campoValidade = new JTextField(10);

    public DialogNovoLote(JFrame parent, ProdutoService produtoService, LoteService loteService,
                           Runnable aoSalvarComSucesso) {
        super(parent, "Novo Lote", true);
        setSize(400, 280);
        setLocationRelativeTo(parent);
        setLayout(new BorderLayout(10, 10));

        carregarProdutos(produtoService);

        JPanel form = new JPanel(new GridLayout(4, 2, 8, 8));
        form.setBorder(BorderFactory.createEmptyBorder(15, 15, 15, 15));
        form.add(new JLabel("Produto:"));
        form.add(comboProduto);
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

    private void carregarProdutos(ProdutoService produtoService) {
        try {
            List<Produto> produtos = produtoService.listarTodos();
            for (Produto p : produtos) {
                comboProduto.addItem(p);
            }
        } catch (SQLException e) {
            JOptionPane.showMessageDialog(this, "Erro ao carregar produtos: " + e.getMessage(),
                    "Erro", JOptionPane.ERROR_MESSAGE);
        }
    }

    private void salvar(LoteService loteService, Runnable aoSalvarComSucesso) {
        Produto produtoSelecionado = (Produto) comboProduto.getSelectedItem();
        if (produtoSelecionado == null) {
            JOptionPane.showMessageDialog(this, "Selecione um produto.", "Campo obrigatório", JOptionPane.WARNING_MESSAGE);
            return;
        }

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
            DateTimeFormatter formatoBrasileiro = DateTimeFormatter.ofPattern("dd/MM/yyyy");
            validade = LocalDate.parse(campoValidade.getText().trim(), formatoBrasileiro);
        } catch (DateTimeParseException e) {
            JOptionPane.showMessageDialog(this, "Data inválida. Use o formato DD/MM/AAAA (ex: 31/12/2026).",
                    "Erro de formato", JOptionPane.WARNING_MESSAGE);
            return;
        }

        try {
            loteService.cadastrar(produtoSelecionado.getId(), quantidade, preco, validade);
            JOptionPane.showMessageDialog(this, "Lote cadastrado com sucesso!");
            aoSalvarComSucesso.run();
            dispose();
        } catch (ValidacaoException e) {
            JOptionPane.showMessageDialog(this, e.getMessage(), "Não foi possível cadastrar", JOptionPane.WARNING_MESSAGE);
        } catch (SQLException e) {
            JOptionPane.showMessageDialog(this, "Erro ao salvar no banco: " + e.getMessage(), "Erro", JOptionPane.ERROR_MESSAGE);
        }
    }
}
