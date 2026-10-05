package br.com.unisinos.ui;

import br.com.unisinos.exception.ValidacaoException;
import br.com.unisinos.model.Produto;
import br.com.unisinos.service.LoteService;
import br.com.unisinos.service.ProdutoService;

import javax.swing.BorderFactory;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JDialog;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextField;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import java.awt.BorderLayout;
import java.awt.FlowLayout;
import java.awt.GridLayout;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Formulário para cadastrar um ou mais lotes de um produto existente.
 *
 * Por padrão, assume que todos os itens da quantidade informada têm a
 * mesma validade (caso mais comum). Se o usuário desmarcar "todos têm a
 * mesma validade" (ex: 2 leites comprados com datas de vencimento
 * diferentes), o formulário passa a pedir uma validade por item — ao
 * salvar, os itens são agrupados por data e um Lote é criado para cada
 * validade distinta, preservando a regra de que um lote sempre tem uma
 * única data de validade (essencial para o FEFO funcionar corretamente).
 */
public class DialogNovoLote extends JDialog {

    private static final DateTimeFormatter FORMATO_DATA = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    private static final int LIMITE_CAMPOS_INDIVIDUAIS = 50;

    private final JComboBox<Produto> comboProduto = new JComboBox<>();
    private final JTextField campoQuantidade = new JTextField("1", 4);
    private final JTextField campoPreco = new JTextField(6);
    private final JCheckBox checkMesmaValidade = new JCheckBox("Todos os itens têm a mesma validade", true);
    private final JTextField campoValidadeUnica = new JTextField(10);
    private final JPanel painelValidades = new JPanel();
    private final List<JTextField> camposValidadeIndividuais = new ArrayList<>();

    public DialogNovoLote(JFrame parent, ProdutoService produtoService, LoteService loteService,
                           Runnable aoSalvarComSucesso) {
        super(parent, "Novo Lote", true);
        setSize(460, 520);
        setLocationRelativeTo(parent);
        setLayout(new BorderLayout(10, 10));

        carregarProdutos(produtoService);

        JPanel formTopo = new JPanel(new GridLayout(3, 2, 8, 8));
        formTopo.setBorder(BorderFactory.createEmptyBorder(15, 15, 5, 15));
        formTopo.add(new JLabel("Produto:"));
        formTopo.add(comboProduto);
        formTopo.add(new JLabel("Quantidade:"));
        formTopo.add(campoQuantidade);
        formTopo.add(new JLabel("Preço unitário (R$):"));
        formTopo.add(campoPreco);

        JPanel painelCheckbox = new JPanel(new FlowLayout(FlowLayout.LEFT));
        painelCheckbox.setBorder(BorderFactory.createEmptyBorder(0, 10, 0, 10));
        painelCheckbox.add(checkMesmaValidade);

        painelValidades.setLayout(new BoxLayout(painelValidades, BoxLayout.Y_AXIS));
        JScrollPane scrollValidades = new JScrollPane(painelValidades);
        scrollValidades.setBorder(BorderFactory.createTitledBorder("Validade"));

        JPanel painelPrincipal = new JPanel();
        painelPrincipal.setLayout(new BoxLayout(painelPrincipal, BoxLayout.Y_AXIS));
        painelPrincipal.add(formTopo);
        painelPrincipal.add(painelCheckbox);
        painelPrincipal.add(scrollValidades);
        add(painelPrincipal, BorderLayout.CENTER);

        JPanel painelBotoes = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        JButton btnSalvar = new JButton("Salvar");
        JButton btnCancelar = new JButton("Cancelar");
        painelBotoes.add(btnCancelar);
        painelBotoes.add(btnSalvar);
        add(painelBotoes, BorderLayout.SOUTH);

        btnCancelar.addActionListener(e -> dispose());
        btnSalvar.addActionListener(e -> salvar(loteService, aoSalvarComSucesso));

        checkMesmaValidade.addActionListener(e -> reconstruirCamposValidade());
        campoQuantidade.getDocument().addDocumentListener(new DocumentListener() {
            @Override
            public void insertUpdate(DocumentEvent e) {
                reconstruirCamposValidade();
            }

            @Override
            public void removeUpdate(DocumentEvent e) {
                reconstruirCamposValidade();
            }

            @Override
            public void changedUpdate(DocumentEvent e) {
                reconstruirCamposValidade();
            }
        });

        reconstruirCamposValidade();
    }

    private void carregarProdutos(ProdutoService produtoService) {
        try {
            for (Produto p : produtoService.listarTodos()) {
                comboProduto.addItem(p);
            }
        } catch (SQLException e) {
            JOptionPane.showMessageDialog(this, "Erro ao carregar produtos: " + e.getMessage(),
                    "Erro", JOptionPane.ERROR_MESSAGE);
        }
    }

    /**
     * Reconstrói a área de validade: um único campo (se "mesma validade"
     * estiver marcado ou a quantidade for 1), ou um campo por item.
     */
    private void reconstruirCamposValidade() {
        painelValidades.removeAll();
        camposValidadeIndividuais.clear();

        int quantidade = lerQuantidadeParaGeracaoDeCampos();

        if (checkMesmaValidade.isSelected() || quantidade <= 1) {
            JPanel linha = new JPanel(new FlowLayout(FlowLayout.LEFT));
            linha.add(new JLabel("Validade (DD/MM/AAAA):"));
            linha.add(campoValidadeUnica);
            painelValidades.add(linha);
        } else {
            for (int i = 1; i <= quantidade; i++) {
                JTextField campo = new JTextField(10);
                camposValidadeIndividuais.add(campo);
                JPanel linha = new JPanel(new FlowLayout(FlowLayout.LEFT));
                linha.add(new JLabel("Item " + i + " — Validade (DD/MM/AAAA):"));
                linha.add(campo);
                painelValidades.add(linha);
            }
        }

        painelValidades.revalidate();
        painelValidades.repaint();
    }

    /** Lê a quantidade digitada como inteiro, com limite de segurança para não gerar campos demais. */
    private int lerQuantidadeParaGeracaoDeCampos() {
        try {
            int q = Integer.parseInt(campoQuantidade.getText().trim());
            return Math.max(1, Math.min(q, LIMITE_CAMPOS_INDIVIDUAIS));
        } catch (NumberFormatException e) {
            return 1;
        }
    }

    private void salvar(LoteService loteService, Runnable aoSalvarComSucesso) {
        Produto produtoSelecionado = (Produto) comboProduto.getSelectedItem();
        if (produtoSelecionado == null) {
            JOptionPane.showMessageDialog(this, "Selecione um produto.", "Campo obrigatório", JOptionPane.WARNING_MESSAGE);
            return;
        }

        double preco;
        try {
            preco = Double.parseDouble(campoPreco.getText().replace(",", "."));
        } catch (NumberFormatException e) {
            JOptionPane.showMessageDialog(this, "Preço inválido.", "Erro de formato", JOptionPane.WARNING_MESSAGE);
            return;
        }

        try {
            if (checkMesmaValidade.isSelected() || lerQuantidadeParaGeracaoDeCampos() <= 1) {
                salvarComValidadeUnica(loteService, produtoSelecionado, preco);
            } else {
                salvarComValidadesIndividuais(loteService, produtoSelecionado, preco);
            }
            aoSalvarComSucesso.run();
            dispose();
        } catch (ValidacaoException e) {
            JOptionPane.showMessageDialog(this, e.getMessage(), "Não foi possível cadastrar", JOptionPane.WARNING_MESSAGE);
        } catch (SQLException e) {
            JOptionPane.showMessageDialog(this, "Erro ao salvar no banco: " + e.getMessage(), "Erro", JOptionPane.ERROR_MESSAGE);
        }
    }

    private void salvarComValidadeUnica(LoteService loteService, Produto produto, double preco) throws SQLException {
        double quantidade;
        try {
            quantidade = Double.parseDouble(campoQuantidade.getText().trim().replace(",", "."));
        } catch (NumberFormatException e) {
            throw new ValidacaoException("Quantidade inválida.");
        }

        LocalDate validade;
        try {
            validade = LocalDate.parse(campoValidadeUnica.getText().trim(), FORMATO_DATA);
        } catch (DateTimeParseException e) {
            throw new ValidacaoException("Data inválida. Use o formato DD/MM/AAAA (ex: 31/12/2026).");
        }

        loteService.cadastrar(produto.getId(), quantidade, preco, validade);
        JOptionPane.showMessageDialog(this, "Lote cadastrado com sucesso!");
    }

    private void salvarComValidadesIndividuais(LoteService loteService, Produto produto, double preco) throws SQLException {
        List<LocalDate> validadesDosItens = new ArrayList<>();

        for (int i = 0; i < camposValidadeIndividuais.size(); i++) {
            String texto = camposValidadeIndividuais.get(i).getText().trim();
            if (texto.isEmpty()) {
                throw new ValidacaoException("Preencha a validade de todos os itens (item " + (i + 1) + " está vazio).");
            }

            LocalDate data;
            try {
                data = LocalDate.parse(texto, FORMATO_DATA);
            } catch (DateTimeParseException e) {
                throw new ValidacaoException("Data inválida no item " + (i + 1)
                        + ". Use o formato DD/MM/AAAA (ex: 31/12/2026).");
            }

            validadesDosItens.add(data);
        }

        Map<LocalDate, Integer> contagemPorData = LoteService.agruparPorValidade(validadesDosItens);
        loteService.cadastrarPorValidade(produto.getId(), contagemPorData, preco);

        JOptionPane.showMessageDialog(this,
                contagemPorData.size() + " lote(s) cadastrado(s) com sucesso (agrupados por validade)!");
    }
}
