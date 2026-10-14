package br.com.unisinos.ui;

import br.com.unisinos.exception.ValidacaoException;
import br.com.unisinos.model.Produto;
import br.com.unisinos.service.ProdutoService;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JDialog;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.ListSelectionModel;
import javax.swing.SwingConstants;
import java.awt.BorderLayout;
import java.awt.FlowLayout;
import java.awt.Font;
import java.sql.SQLException;

public class DialogProdutos extends JDialog {

    private final ProdutoService produtoService;
    private final Runnable aoAlterarProdutos;

    private final ProdutoTableModel tableModel = new ProdutoTableModel();
    private final JTable tabela = new JTable(tableModel);

    public DialogProdutos(JFrame parent, ProdutoService produtoService, Runnable aoAlterarProdutos) {
        super(parent, "Produtos", true);
        this.produtoService = produtoService;
        this.aoAlterarProdutos = aoAlterarProdutos;
        setSize(650, 450);
        setLocationRelativeTo(parent);
        setLayout(new BorderLayout(10, 10));

        JLabel titulo = new JLabel("Produtos cadastrados", SwingConstants.LEFT);
        titulo.setFont(titulo.getFont().deriveFont(Font.BOLD, 15f));
        titulo.setBorder(BorderFactory.createEmptyBorder(10, 10, 0, 10));
        add(titulo, BorderLayout.NORTH);

        tabela.setRowHeight(24);
        tabela.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        add(new JScrollPane(tabela), BorderLayout.CENTER);

        JPanel painelBotoes = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        JButton btnEditar = new JButton("Editar");
        JButton btnExcluir = new JButton("Excluir");
        JButton btnFechar = new JButton("Fechar");
        painelBotoes.add(btnEditar);
        painelBotoes.add(btnExcluir);
        painelBotoes.add(btnFechar);
        add(painelBotoes, BorderLayout.SOUTH);

        btnEditar.addActionListener(e -> editarSelecionado());
        btnExcluir.addActionListener(e -> excluirSelecionado());
        btnFechar.addActionListener(e -> dispose());

        carregarProdutos();
    }

    private void carregarProdutos() {
        try {
            tableModel.setProdutos(produtoService.listarTodos());
        } catch (SQLException e) {
            mostrarErro("Erro ao carregar produtos: " + e.getMessage());
        }
    }

    private Produto produtoSelecionado() {
        int linha = tabela.getSelectedRow();
        if (linha == -1) {
            JOptionPane.showMessageDialog(this, "Selecione um produto na lista primeiro.",
                    "Nenhum produto selecionado", JOptionPane.WARNING_MESSAGE);
            return null;
        }
        return tableModel.getProdutoNaLinha(linha);
    }

    private void editarSelecionado() {
        Produto produto = produtoSelecionado();
        if (produto == null) {
            return;
        }

        DialogEditarProduto dialog = new DialogEditarProduto(this, produto, produtoService, () -> {
            carregarProdutos();
            aoAlterarProdutos.run();
        });
        dialog.setVisible(true);
    }

    private void excluirSelecionado() {
        Produto produto = produtoSelecionado();
        if (produto == null) {
            return;
        }

        int confirmacao = JOptionPane.showConfirmDialog(this,
                "Excluir o produto \"" + produto.getNome() + "\" (" + produto.getCodigoBarras() + ")?",
                "Confirmar exclusão", JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE);
        if (confirmacao != JOptionPane.YES_OPTION) {
            return;
        }

        try {
            produtoService.excluir(produto.getId());
            carregarProdutos();
            aoAlterarProdutos.run();
        } catch (ValidacaoException e) {
            JOptionPane.showMessageDialog(this, e.getMessage(), "Não foi possível excluir",
                    JOptionPane.WARNING_MESSAGE);
        } catch (SQLException e) {
            mostrarErro("Erro ao excluir o produto: " + e.getMessage());
        }
    }

    private void mostrarErro(String mensagem) {
        JOptionPane.showMessageDialog(this, mensagem, "Erro", JOptionPane.ERROR_MESSAGE);
    }
}
