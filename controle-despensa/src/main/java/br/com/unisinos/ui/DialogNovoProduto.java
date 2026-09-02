package br.com.unisinos.ui;

import br.com.unisinos.exception.ValidacaoException;
import br.com.unisinos.service.ProdutoService;

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

/** Formulário simples para cadastrar um novo produto no catálogo. */
public class DialogNovoProduto extends JDialog {

    private final JTextField campoCodigoBarras = new JTextField(20);
    private final JTextField campoNome = new JTextField(20);
    private final JTextField campoCategoria = new JTextField(20);
    private final JTextField campoUnidade = new JTextField("UN", 5);

    public DialogNovoProduto(JFrame parent, ProdutoService produtoService) {
        super(parent, "Novo Produto", true);
        setSize(380, 260);
        setLocationRelativeTo(parent);
        setLayout(new BorderLayout(10, 10));

        JPanel form = new JPanel(new GridLayout(4, 2, 8, 8));
        form.setBorder(BorderFactory.createEmptyBorder(15, 15, 15, 15));
        form.add(new JLabel("Código de barras:"));
        form.add(campoCodigoBarras);
        form.add(new JLabel("Nome do produto:"));
        form.add(campoNome);
        form.add(new JLabel("Categoria:"));
        form.add(campoCategoria);
        form.add(new JLabel("Unidade de medida:"));
        form.add(campoUnidade);
        add(form, BorderLayout.CENTER);

        JPanel painelBotoes = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        JButton btnSalvar = new JButton("Salvar");
        JButton btnCancelar = new JButton("Cancelar");
        painelBotoes.add(btnCancelar);
        painelBotoes.add(btnSalvar);
        add(painelBotoes, BorderLayout.SOUTH);

        btnCancelar.addActionListener(e -> dispose());
        btnSalvar.addActionListener(e -> salvar(produtoService));
    }

    private void salvar(ProdutoService produtoService) {
        try {
            produtoService.cadastrar(
                    campoCodigoBarras.getText(),
                    campoNome.getText(),
                    campoCategoria.getText(),
                    campoUnidade.getText()
            );
            JOptionPane.showMessageDialog(this, "Produto cadastrado com sucesso!");
            dispose();
        } catch (ValidacaoException e) {
            JOptionPane.showMessageDialog(this, e.getMessage(), "Não foi possível cadastrar", JOptionPane.WARNING_MESSAGE);
        } catch (SQLException e) {
            JOptionPane.showMessageDialog(this, "Erro ao salvar no banco: " + e.getMessage(), "Erro", JOptionPane.ERROR_MESSAGE);
        }
    }
}
