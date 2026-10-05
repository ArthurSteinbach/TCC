package br.com.unisinos.ui;

import br.com.unisinos.exception.ValidacaoException;
import br.com.unisinos.integracao.ConsultaProdutoExterna;
import br.com.unisinos.integracao.InfoProdutoExterno;
import br.com.unisinos.service.ProdutoService;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JDialog;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JTextField;
import javax.swing.SwingWorker;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.event.FocusAdapter;
import java.awt.event.FocusEvent;
import java.sql.SQLException;
import java.util.Optional;

/**
 * Formulário para cadastrar um novo produto no catálogo. Ao informar um
 * código de barras válido (via câmera ou digitado), tenta buscar nome e
 * categoria automaticamente numa base pública (Open Food Facts); se não
 * encontrar, o usuário preenche manualmente. A validade NUNCA vem desse
 * tipo de busca — ela é sempre informada na tela de cadastro de lote.
 *
 * Não há mais escolha de unidade de medida: o sistema sempre conta
 * pacotes/frascos (não peso ou volume), já que cada produto tem tamanho
 * fixo definido pelo seu próprio código de barras — geralmente já visível
 * no próprio nome (ex: "Orégano Kitano Pacote 3g").
 */
public class DialogNovoProduto extends JDialog {

    private final JTextField campoCodigoBarras = new JTextField(18);
    private final JTextField campoNome = new JTextField(22);
    private final JTextField campoCategoria = new JTextField(22);
    private final JLabel labelStatusBusca = new JLabel(" ");

    public DialogNovoProduto(JFrame parent, ProdutoService produtoService) {
        super(parent, "Novo Produto", true);
        setSize(540, 300);
        setLocationRelativeTo(parent);
        setLayout(new BorderLayout(10, 10));

        JPanel painelCodigoBarras = new JPanel(new BorderLayout(5, 0));
        painelCodigoBarras.add(campoCodigoBarras, BorderLayout.CENTER);
        JButton btnLerCamera = new JButton("Ler via câmera");
        btnLerCamera.addActionListener(e -> abrirLeitorCamera());
        painelCodigoBarras.add(btnLerCamera, BorderLayout.EAST);

        campoCodigoBarras.addFocusListener(new FocusAdapter() {
            @Override
            public void focusLost(FocusEvent e) {
                buscarInfoProduto();
            }
        });

        JPanel form = new JPanel(new GridBagLayout());
        form.setBorder(BorderFactory.createEmptyBorder(15, 15, 10, 15));
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(4, 4, 4, 4);
        gbc.anchor = GridBagConstraints.WEST;

        gbc.gridx = 0;
        gbc.gridy = 0;
        gbc.weightx = 0;
        form.add(new JLabel("Código de barras:"), gbc);
        gbc.gridx = 1;
        gbc.weightx = 1;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        form.add(painelCodigoBarras, gbc);

        gbc.gridx = 0;
        gbc.gridy = 1;
        gbc.weightx = 0;
        gbc.fill = GridBagConstraints.NONE;
        form.add(new JLabel("Nome do produto:"), gbc);
        gbc.gridx = 1;
        gbc.weightx = 1;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        form.add(campoNome, gbc);

        gbc.gridx = 0;
        gbc.gridy = 2;
        gbc.weightx = 0;
        gbc.fill = GridBagConstraints.NONE;
        form.add(new JLabel("Categoria:"), gbc);
        gbc.gridx = 1;
        gbc.weightx = 1;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        form.add(campoCategoria, gbc);

        labelStatusBusca.setFont(labelStatusBusca.getFont().deriveFont(Font.ITALIC, 11f));
        labelStatusBusca.setForeground(Color.GRAY);
        labelStatusBusca.setBorder(BorderFactory.createEmptyBorder(0, 15, 0, 15));

        JLabel dicaQuantidade = new JLabel(
                "<html>Dica: a <b>quantidade</b> do lote (na próxima tela) representa quantos "
                        + "pacotes/frascos você tem — não o peso ou volume de cada um "
                        + "(ex: 1 pacote de 200 g é quantidade = 1, não 200).</html>");
        dicaQuantidade.setFont(dicaQuantidade.getFont().deriveFont(Font.ITALIC, 11f));
        dicaQuantidade.setForeground(Color.GRAY);
        dicaQuantidade.setBorder(BorderFactory.createEmptyBorder(0, 15, 10, 15));

        JPanel painelCentro = new JPanel(new BorderLayout());
        painelCentro.add(form, BorderLayout.NORTH);
        painelCentro.add(labelStatusBusca, BorderLayout.CENTER);
        painelCentro.add(dicaQuantidade, BorderLayout.SOUTH);
        add(painelCentro, BorderLayout.CENTER);

        JPanel painelBotoes = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        JButton btnSalvar = new JButton("Salvar");
        JButton btnCancelar = new JButton("Cancelar");
        painelBotoes.add(btnCancelar);
        painelBotoes.add(btnSalvar);
        add(painelBotoes, BorderLayout.SOUTH);

        btnCancelar.addActionListener(e -> dispose());
        btnSalvar.addActionListener(e -> salvar(produtoService));
    }

    private void abrirLeitorCamera() {
        DialogLeitorCodigoBarras leitor = new DialogLeitorCodigoBarras(
                javax.swing.SwingUtilities.getWindowAncestor(this),
                codigoLido -> {
                    campoCodigoBarras.setText(codigoLido);
                    campoCodigoBarras.setCaretPosition(0);
                    buscarInfoProduto();
                }
        );
        leitor.setVisible(true);
    }

    /**
     * Consulta a base pública em segundo plano (sem travar a interface) e
     * preenche nome/categoria automaticamente se encontrar. Só dispara
     * para códigos com o tamanho exato de um EAN-13 (13 dígitos).
     */
    private void buscarInfoProduto() {
        String codigo = campoCodigoBarras.getText().trim();
        if (codigo.length() != 13) {
            return;
        }

        labelStatusBusca.setText("Buscando informações do produto...");

        SwingWorker<Optional<InfoProdutoExterno>, Void> worker = new SwingWorker<>() {
            @Override
            protected Optional<InfoProdutoExterno> doInBackground() throws Exception {
                return new ConsultaProdutoExterna().buscarPorCodigoBarras(codigo);
            }

            @Override
            protected void done() {
                try {
                    Optional<InfoProdutoExterno> resultado = get();
                    if (resultado.isPresent()) {
                        InfoProdutoExterno info = resultado.get();
                        campoNome.setText(info.nome());
                        if (!info.categoria().isEmpty()) {
                            campoCategoria.setText(info.categoria());
                        }
                        labelStatusBusca.setText("Produto encontrado na base pública — confira os dados antes de salvar.");
                    } else {
                        labelStatusBusca.setText("Produto não encontrado na base pública — preencha manualmente.");
                    }
                } catch (Exception e) {
                    labelStatusBusca.setText("Não foi possível consultar a base pública agora — preencha manualmente.");
                }
            }
        };
        worker.execute();
    }

    private void salvar(ProdutoService produtoService) {
        try {
            produtoService.cadastrar(
                    campoCodigoBarras.getText(),
                    campoNome.getText(),
                    campoCategoria.getText()
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
