package br.com.unisinos.ui;

import br.com.unisinos.exception.ValidacaoException;
import br.com.unisinos.model.ItemConsumo;
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
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.sql.SQLException;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Optional;

public class DialogConsumirProduto extends JDialog {

    private static final DateTimeFormatter FORMATO_DATA = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    private final ProdutoService produtoService;
    private final LoteService loteService;
    private final Runnable aoConsumirComSucesso;

    private final JComboBox<Produto> comboProduto = new JComboBox<>();
    private final JTextField campoQuantidade = new JTextField("1", 6);

    public DialogConsumirProduto(JFrame parent, ProdutoService produtoService, LoteService loteService,
                                 Runnable aoConsumirComSucesso) {
        super(parent, "Consumir Produto", true);
        this.produtoService = produtoService;
        this.loteService = loteService;
        this.aoConsumirComSucesso = aoConsumirComSucesso;
        setSize(520, 200);
        setLocationRelativeTo(parent);
        setLayout(new BorderLayout(10, 10));

        carregarProdutos();
        montarLayout();
    }

    private void montarLayout() {
        JPanel form = new JPanel(new GridBagLayout());
        form.setBorder(BorderFactory.createEmptyBorder(15, 15, 5, 15));
        GridBagConstraints c = new GridBagConstraints();
        c.insets = new Insets(4, 4, 4, 4);
        c.anchor = GridBagConstraints.WEST;

        c.gridx = 0;
        c.gridy = 0;
        form.add(new JLabel("Produto:"), c);
        c.gridx = 1;
        c.weightx = 1;
        c.fill = GridBagConstraints.HORIZONTAL;
        form.add(comboProduto, c);
        c.gridx = 2;
        c.weightx = 0;
        c.fill = GridBagConstraints.NONE;
        JButton btnCamera = new JButton("Ler código de barras");
        form.add(btnCamera, c);

        c.gridx = 0;
        c.gridy = 1;
        form.add(new JLabel("Quantidade:"), c);
        c.gridx = 1;
        form.add(campoQuantidade, c);

        add(form, BorderLayout.CENTER);

        JPanel painelBotoes = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        JButton btnConsumir = new JButton("Consumir");
        JButton btnCancelar = new JButton("Cancelar");
        painelBotoes.add(btnCancelar);
        painelBotoes.add(btnConsumir);
        add(painelBotoes, BorderLayout.SOUTH);

        btnCamera.addActionListener(e -> abrirLeitorCamera());
        btnCancelar.addActionListener(e -> dispose());
        btnConsumir.addActionListener(e -> consumir());
        getRootPane().setDefaultButton(btnConsumir);
    }

    private void carregarProdutos() {
        try {
            for (Produto p : produtoService.listarComLotesAtivos()) {
                comboProduto.addItem(p);
            }
        } catch (SQLException e) {
            mostrarErro("Erro ao carregar produtos: " + e.getMessage());
        }
    }

    private void abrirLeitorCamera() {
        DialogLeitorCodigoBarras leitor = new DialogLeitorCodigoBarras(this, this::selecionarPorCodigo);
        leitor.setVisible(true);
    }

    private void selecionarPorCodigo(String codigo) {
        for (int i = 0; i < comboProduto.getItemCount(); i++) {
            if (codigo.equals(comboProduto.getItemAt(i).getCodigoBarras())) {
                comboProduto.setSelectedIndex(i);
                return;
            }
        }

        try {
            Optional<Produto> produto = produtoService.buscarPorCodigoBarras(codigo);
            String mensagem = produto.isPresent()
                    ? "O produto \"" + produto.get().getNome() + "\" não tem lotes ativos na despensa."
                    : "Nenhum produto cadastrado com o código " + codigo + ".";
            JOptionPane.showMessageDialog(this, mensagem, "Produto indisponível", JOptionPane.WARNING_MESSAGE);
        } catch (SQLException e) {
            mostrarErro("Erro ao buscar o produto: " + e.getMessage());
        }
    }

    private void consumir() {
        Produto produto = (Produto) comboProduto.getSelectedItem();
        if (produto == null) {
            JOptionPane.showMessageDialog(this, "Selecione um produto.", "Campo obrigatório",
                    JOptionPane.WARNING_MESSAGE);
            return;
        }

        try {
            double quantidade = lerQuantidade();
            List<ItemConsumo> plano = loteService.planejarConsumoFEFO(produto.getId(), quantidade);

            int confirmacao = JOptionPane.showConfirmDialog(this, descreverPlano(produto, plano),
                    "Confirmar consumo", JOptionPane.YES_NO_OPTION);
            if (confirmacao != JOptionPane.YES_OPTION) {
                return;
            }

            loteService.consumirPorFEFO(produto.getId(), quantidade);
            aoConsumirComSucesso.run();
            dispose();
        } catch (ValidacaoException e) {
            JOptionPane.showMessageDialog(this, e.getMessage(), "Não foi possível consumir",
                    JOptionPane.WARNING_MESSAGE);
        } catch (SQLException e) {
            mostrarErro("Erro ao registrar o consumo: " + e.getMessage());
        }
    }

    private double lerQuantidade() {
        try {
            return Double.parseDouble(campoQuantidade.getText().trim().replace(",", "."));
        } catch (NumberFormatException e) {
            throw new ValidacaoException("Quantidade inválida.");
        }
    }

    private String descreverPlano(Produto produto, List<ItemConsumo> plano) {
        StringBuilder sb = new StringBuilder();
        sb.append("Lotes de \"").append(produto.getNome()).append("\" que serão usados (FEFO):\n\n");
        for (ItemConsumo item : plano) {
            sb.append("• Validade ").append(item.lote().getDataValidade().format(FORMATO_DATA))
                    .append(": consumir ").append(formatar(item.quantidadeConsumida()))
                    .append(" de ").append(formatar(item.lote().getQuantidade()));
            if (item.consomeLoteInteiro()) {
                sb.append(" (lote inteiro)");
            } else {
                sb.append(" (restam ").append(formatar(item.quantidadeRestante())).append(")");
            }
            sb.append("\n");
        }
        sb.append("\nConfirmar o consumo?");
        return sb.toString();
    }

    private static String formatar(double quantidade) {
        return quantidade == Math.rint(quantidade)
                ? String.valueOf((long) quantidade)
                : String.valueOf(quantidade);
    }

    private void mostrarErro(String mensagem) {
        JOptionPane.showMessageDialog(this, mensagem, "Erro", JOptionPane.ERROR_MESSAGE);
    }
}
