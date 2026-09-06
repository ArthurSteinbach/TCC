package br.com.unisinos.ui;

import br.com.unisinos.model.Lote;
import br.com.unisinos.service.LoteService;
import br.com.unisinos.service.ProdutoService;

import javax.swing.BorderFactory;
import javax.swing.JButton;
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
import java.util.List;

/**
 * Tela principal do sistema: lista os lotes ativos ordenados por FEFO
 * (validade mais próxima primeiro), destaca visualmente os que estão
 * vencendo, e permite cadastrar produtos/lotes ou dar baixa nos itens.
 */
public class TelaPrincipal extends JFrame {

    private final ProdutoService produtoService = new ProdutoService();
    private final LoteService loteService = new LoteService();

    private final LoteTableModel tableModel = new LoteTableModel();
    private final JTable tabela = new JTable(tableModel);
    private final JLabel labelPerdaTotal = new JLabel();

    public TelaPrincipal() {
        super("Controle de Despensa \u2014 FEFO");
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setSize(860, 560);
        setLocationRelativeTo(null);

        montarLayout();
        carregarDados();

        addWindowListener(new java.awt.event.WindowAdapter() {
            @Override
            public void windowOpened(java.awt.event.WindowEvent e) {
                verificarAlertaInicial();
            }
        });
    }

    private void montarLayout() {
        setLayout(new BorderLayout(10, 10));

        JLabel titulo = new JLabel("Itens na despensa (ordenados por validade)", SwingConstants.LEFT);
        titulo.setFont(titulo.getFont().deriveFont(Font.BOLD, 16f));
        titulo.setBorder(BorderFactory.createEmptyBorder(10, 10, 0, 10));
        add(titulo, BorderLayout.NORTH);

        tabela.setDefaultRenderer(Object.class, new AlertaRowRenderer());
        tabela.setRowHeight(24);
        tabela.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        add(new JScrollPane(tabela), BorderLayout.CENTER);

        JPanel painelInferior = new JPanel(new BorderLayout());

        JPanel painelBotoesLinha1 = new JPanel(new FlowLayout(FlowLayout.LEFT));
        JPanel painelBotoesLinha2 = new JPanel(new FlowLayout(FlowLayout.LEFT));

        JButton btnNovoProduto = new JButton("Novo Produto");
        JButton btnNovoLote = new JButton("Novo Lote");
        JButton btnEditar = new JButton("Editar");
        JButton btnAtualizar = new JButton("Atualizar");

        JButton btnConsumir = new JButton("Marcar como Consumido");
        JButton btnDescartar = new JButton("Marcar como Descartado (vencido)");
        JButton btnAlertas = new JButton("Alertas");
        JButton btnRelatorio = new JButton("Relatório de Perdas");

        btnNovoProduto.addActionListener(e -> abrirDialogNovoProduto());
        btnNovoLote.addActionListener(e -> abrirDialogNovoLote());
        btnEditar.addActionListener(e -> editarSelecionado());
        btnAtualizar.addActionListener(e -> carregarDados());
        btnConsumir.addActionListener(e -> darBaixaSelecionado(true));
        btnDescartar.addActionListener(e -> darBaixaSelecionado(false));
        btnAlertas.addActionListener(e -> abrirAlertas());
        btnRelatorio.addActionListener(e -> abrirRelatorioPerdas());

        painelBotoesLinha1.add(btnNovoProduto);
        painelBotoesLinha1.add(btnNovoLote);
        painelBotoesLinha1.add(btnEditar);
        painelBotoesLinha1.add(btnAtualizar);

        painelBotoesLinha2.add(btnConsumir);
        painelBotoesLinha2.add(btnDescartar);
        painelBotoesLinha2.add(btnAlertas);
        painelBotoesLinha2.add(btnRelatorio);

        JPanel painelBotoes = new JPanel();
        painelBotoes.setLayout(new javax.swing.BoxLayout(painelBotoes, javax.swing.BoxLayout.Y_AXIS));
        painelBotoes.add(painelBotoesLinha1);
        painelBotoes.add(painelBotoesLinha2);

        labelPerdaTotal.setBorder(BorderFactory.createEmptyBorder(5, 10, 10, 10));
        labelPerdaTotal.setFont(labelPerdaTotal.getFont().deriveFont(Font.BOLD));

        painelInferior.add(painelBotoes, BorderLayout.NORTH);
        painelInferior.add(labelPerdaTotal, BorderLayout.SOUTH);
        add(painelInferior, BorderLayout.SOUTH);
    }

    /** Recarrega a tabela (FEFO) e o total de perdas financeiras a partir do banco. */
    private void carregarDados() {
        try {
            List<Lote> ativos = loteService.listarAtivosFEFO();
            tableModel.setLotes(ativos);

            double perda = loteService.calcularPerdaFinanceiraTotal();
            labelPerdaTotal.setText(String.format("Perda financeira acumulada (itens descartados): R$ %.2f", perda));
        } catch (SQLException e) {
            mostrarErro("Erro ao carregar dados do banco: " + e.getMessage());
        }
    }

    private void editarSelecionado() {
        int linha = tabela.getSelectedRow();
        if (linha == -1) {
            JOptionPane.showMessageDialog(this, "Selecione um item na lista primeiro.",
                    "Nenhum item selecionado", JOptionPane.WARNING_MESSAGE);
            return;
        }

        Lote lote = tableModel.getLoteNaLinha(linha);
        DialogEditarLote dialog = new DialogEditarLote(this, lote, loteService, this::carregarDados);
        dialog.setVisible(true);
    }

    private void darBaixaSelecionado(boolean consumido) {
        int linha = tabela.getSelectedRow();
        if (linha == -1) {
            JOptionPane.showMessageDialog(this, "Selecione um item na lista primeiro.",
                    "Nenhum item selecionado", JOptionPane.WARNING_MESSAGE);
            return;
        }

        Lote lote = tableModel.getLoteNaLinha(linha);
        String acao = consumido ? "consumido" : "descartado (vencido)";

        int confirmacao = JOptionPane.showConfirmDialog(this,
                "Marcar \"" + lote.getNomeProduto() + "\" como " + acao + "?",
                "Confirmar", JOptionPane.YES_NO_OPTION);

        if (confirmacao != JOptionPane.YES_OPTION) {
            return;
        }

        try {
            if (consumido) {
                loteService.consumir(lote.getId());
            } else {
                loteService.descartar(lote.getId());
            }
            carregarDados();
        } catch (SQLException e) {
            mostrarErro("Erro ao dar baixa no item: " + e.getMessage());
        }
    }

    private void abrirDialogNovoProduto() {
        DialogNovoProduto dialog = new DialogNovoProduto(this, produtoService);
        dialog.setVisible(true);
    }

    private void abrirDialogNovoLote() {
        try {
            if (produtoService.listarTodos().isEmpty()) {
                JOptionPane.showMessageDialog(this,
                        "Cadastre pelo menos um produto antes de criar um lote.",
                        "Nenhum produto cadastrado", JOptionPane.INFORMATION_MESSAGE);
                return;
            }
        } catch (SQLException e) {
            mostrarErro("Erro ao consultar produtos: " + e.getMessage());
            return;
        }

        DialogNovoLote dialog = new DialogNovoLote(this, produtoService, loteService, this::carregarDados);
        dialog.setVisible(true);
    }

    private void abrirAlertas() {
        DialogAlertas dialog = new DialogAlertas(this, loteService);
        dialog.setVisible(true);
    }

    private void abrirRelatorioPerdas() {
        DialogRelatorioPerdas dialog = new DialogRelatorioPerdas(this, loteService);
        dialog.setVisible(true);
    }

    /** Mostra um aviso ao abrir o sistema, se houver itens vencendo em breve. */
    private void verificarAlertaInicial() {
        try {
            int quantidade = loteService.listarAlertaVencimento().size();
            if (quantidade > 0) {
                JOptionPane.showMessageDialog(this,
                        "Você tem " + quantidade + " item(ns) vencendo em breve ou já vencidos.\n"
                                + "Clique em \"Alertas\" para ver a lista completa.",
                        "Alerta de Vencimento", JOptionPane.WARNING_MESSAGE);
            }
        } catch (SQLException e) {
            // Não interrompe a abertura do sistema por causa disso.
        }
    }

    private void mostrarErro(String mensagem) {
        JOptionPane.showMessageDialog(this, mensagem, "Erro", JOptionPane.ERROR_MESSAGE);
    }
}
