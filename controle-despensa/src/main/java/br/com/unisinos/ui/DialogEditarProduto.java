package br.com.unisinos.ui;

import br.com.unisinos.exception.ValidacaoException;
import br.com.unisinos.model.Produto;
import br.com.unisinos.service.ProdutoService;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JTextField;
import java.awt.BorderLayout;
import java.awt.FlowLayout;
import java.awt.GridLayout;
import java.awt.Window;
import java.sql.SQLException;

public class DialogEditarProduto extends JDialog {

    private final JTextField campoNome;
    private final JTextField campoCategoria;
    private final Produto produto;

    public DialogEditarProduto(Window parent, Produto produto, ProdutoService produtoService,
                               Runnable aoSalvarComSucesso) {
        super(parent, "Editar Produto", ModalityType.APPLICATION_MODAL);
        this.produto = produto;
        setSize(420, 220);
        setLocationRelativeTo(parent);
        setLayout(new BorderLayout(10, 10));

        campoNome = new JTextField(produto.getNome(), 22);
        campoCategoria = new JTextField(produto.getCategoria() == null ? "" : produto.getCategoria(), 22);

        JPanel form = new JPanel(new GridLayout(3, 2, 8, 8));
        form.setBorder(BorderFactory.createEmptyBorder(15, 15, 15, 15));
        form.add(new JLabel("Código de barras:"));
        form.add(new JLabel(produto.getCodigoBarras()));
        form.add(new JLabel("Nome:"));
        form.add(campoNome);
        form.add(new JLabel("Categoria:"));
        form.add(campoCategoria);
        add(form, BorderLayout.CENTER);

        JPanel painelBotoes = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        JButton btnSalvar = new JButton("Salvar");
        JButton btnCancelar = new JButton("Cancelar");
        painelBotoes.add(btnCancelar);
        painelBotoes.add(btnSalvar);
        add(painelBotoes, BorderLayout.SOUTH);

        btnCancelar.addActionListener(e -> dispose());
        btnSalvar.addActionListener(e -> salvar(produtoService, aoSalvarComSucesso));
        getRootPane().setDefaultButton(btnSalvar);
    }

    private void salvar(ProdutoService produtoService, Runnable aoSalvarComSucesso) {
        try {
            produtoService.editar(produto.getId(), campoNome.getText(), campoCategoria.getText());
            JOptionPane.showMessageDialog(this, "Produto atualizado com sucesso!");
            aoSalvarComSucesso.run();
            dispose();
        } catch (ValidacaoException e) {
            JOptionPane.showMessageDialog(this, e.getMessage(), "Não foi possível salvar", JOptionPane.WARNING_MESSAGE);
        } catch (SQLException e) {
            JOptionPane.showMessageDialog(this, "Erro ao salvar no banco: " + e.getMessage(), "Erro", JOptionPane.ERROR_MESSAGE);
        }
    }
}
