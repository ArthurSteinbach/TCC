package br.com.unisinos.dao;

import br.com.unisinos.db.DatabaseManager;
import br.com.unisinos.model.ItemConsumo;
import br.com.unisinos.model.Lote;
import br.com.unisinos.model.StatusLote;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * Acesso ao banco de dados para a tabela lotes. Concentra as consultas que
 * implementam a regra de negócio FEFO (First to Expire, First Out) e as
 * operações de baixa (consumido / descartado).
 */
public class LoteDAO {

    /** Insere um novo lote, sempre com status inicial ATIVO. */
    public Lote inserir(Lote lote) throws SQLException {
        String sql = "INSERT INTO lotes (produto_id, quantidade, preco_unitario, data_validade, status, data_cadastro) "
                + "VALUES (?, ?, ?, ?, 'ATIVO', datetime('now', 'localtime'))";

        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {

            stmt.setInt(1, lote.getProdutoId());
            stmt.setDouble(2, lote.getQuantidade());
            stmt.setDouble(3, lote.getPrecoUnitario());
            stmt.setString(4, lote.getDataValidade().toString()); // formato ISO yyyy-MM-dd
            stmt.executeUpdate();

            try (ResultSet rs = stmt.getGeneratedKeys()) {
                if (rs.next()) {
                    lote.setId(rs.getInt(1));
                }
            }
        }
        lote.setStatus(StatusLote.ATIVO);
        return lote;
    }

    /** Atualiza quantidade, preço e validade de um lote já existente. */
    public void atualizar(Lote lote) throws SQLException {
        String sql = "UPDATE lotes SET quantidade = ?, preco_unitario = ?, data_validade = ? WHERE id = ?";

        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setDouble(1, lote.getQuantidade());
            stmt.setDouble(2, lote.getPrecoUnitario());
            stmt.setString(3, lote.getDataValidade().toString());
            stmt.setInt(4, lote.getId());
            stmt.executeUpdate();
        }
    }

    /**
     * Retorna todos os lotes ATIVOS ordenados pela regra FEFO: data de
     * validade mais próxima primeiro. É esta lista que deve ser exibida
     * em destaque na tela principal do sistema.
     */
    public List<Lote> listarAtivosFEFO() throws SQLException {
        String sql = "SELECT l.*, p.nome AS nome_produto, p.codigo_barras AS codigo_barras_produto, "
                + "p.unidade_medida AS unidade_medida_produto "
                + "FROM lotes l "
                + "JOIN produtos p ON p.id = l.produto_id "
                + "WHERE l.status = 'ATIVO' "
                + "ORDER BY l.data_validade ASC";
        return executarConsultaLotes(sql, null);
    }

    /**
     * Retorna os lotes ativos cuja validade cai dentro dos próximos `dias`
     * (incluindo os já vencidos). Usado para o alerta preventivo — chame
     * com Constantes.DIAS_ALERTA_VENCIMENTO.
     */
    public List<Lote> listarVencendoEm(int dias) throws SQLException {
        String sql = "SELECT l.*, p.nome AS nome_produto, p.codigo_barras AS codigo_barras_produto, "
                + "p.unidade_medida AS unidade_medida_produto "
                + "FROM lotes l "
                + "JOIN produtos p ON p.id = l.produto_id "
                + "WHERE l.status = 'ATIVO' AND date(l.data_validade) <= date(?) "
                + "ORDER BY l.data_validade ASC";
        String limite = LocalDate.now().plusDays(dias).toString();
        return executarConsultaLotes(sql, limite);
    }

    public List<Lote> listarAtivosPorProdutoFEFO(int produtoId) throws SQLException {
        return listarPorProduto(produtoId, "AND l.status = 'ATIVO' ");
    }

    public List<Lote> listarPorProduto(int produtoId) throws SQLException {
        return listarPorProduto(produtoId, "");
    }

    private List<Lote> listarPorProduto(int produtoId, String filtroStatus) throws SQLException {
        String sql = "SELECT l.*, p.nome AS nome_produto, p.codigo_barras AS codigo_barras_produto, "
                + "p.unidade_medida AS unidade_medida_produto "
                + "FROM lotes l "
                + "JOIN produtos p ON p.id = l.produto_id "
                + "WHERE l.produto_id = ? " + filtroStatus
                + "ORDER BY l.data_validade ASC, l.id ASC";
        List<Lote> lotes = new ArrayList<>();

        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setInt(1, produtoId);
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    lotes.add(mapear(rs));
                }
            }
        }
        return lotes;
    }

    public void registrarConsumo(List<ItemConsumo> itens) throws SQLException {
        String sqlConsumoTotal = "UPDATE lotes SET status = 'CONSUMIDO', data_baixa = datetime('now', 'localtime') "
                + "WHERE id = ? AND status = 'ATIVO' AND quantidade = ?";
        String sqlRegistroParcial = "INSERT INTO lotes "
                + "(produto_id, quantidade, preco_unitario, data_validade, status, data_cadastro, data_baixa) "
                + "SELECT produto_id, ?, preco_unitario, data_validade, 'CONSUMIDO', data_cadastro, "
                + "datetime('now', 'localtime') FROM lotes WHERE id = ?";
        String sqlReducao = "UPDATE lotes SET quantidade = ? "
                + "WHERE id = ? AND status = 'ATIVO' AND quantidade = ?";

        try (Connection conn = DatabaseManager.getConnection()) {
            conn.setAutoCommit(false);
            try (PreparedStatement consumoTotal = conn.prepareStatement(sqlConsumoTotal);
                 PreparedStatement registroParcial = conn.prepareStatement(sqlRegistroParcial);
                 PreparedStatement reducao = conn.prepareStatement(sqlReducao)) {

                for (ItemConsumo item : itens) {
                    Lote lote = item.lote();
                    int alterados;
                    if (item.consomeLoteInteiro()) {
                        consumoTotal.setInt(1, lote.getId());
                        consumoTotal.setDouble(2, lote.getQuantidade());
                        alterados = consumoTotal.executeUpdate();
                    } else {
                        registroParcial.setDouble(1, item.quantidadeConsumida());
                        registroParcial.setInt(2, lote.getId());
                        registroParcial.executeUpdate();

                        reducao.setDouble(1, item.quantidadeRestante());
                        reducao.setInt(2, lote.getId());
                        reducao.setDouble(3, lote.getQuantidade());
                        alterados = reducao.executeUpdate();
                    }
                    if (alterados != 1) {
                        throw new SQLException("O lote " + lote.getId() + " foi alterado durante o consumo.");
                    }
                }
                conn.commit();
            } catch (SQLException | RuntimeException e) {
                conn.rollback();
                throw e;
            }
        }
    }

    private List<Lote> executarConsultaLotes(String sql, String parametroData) throws SQLException {
        List<Lote> lotes = new ArrayList<>();

        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            if (parametroData != null) {
                stmt.setString(1, parametroData);
            }
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    lotes.add(mapear(rs));
                }
            }
        }
        return lotes;
    }

    /**
     * Retorna todos os lotes descartados por vencimento, do mais recente
     * para o mais antigo — base do relatório de perdas financeiras.
     */
    public List<Lote> listarDescartados() throws SQLException {
        String sql = "SELECT l.*, p.nome AS nome_produto, p.codigo_barras AS codigo_barras_produto, "
                + "p.unidade_medida AS unidade_medida_produto "
                + "FROM lotes l "
                + "JOIN produtos p ON p.id = l.produto_id "
                + "WHERE l.status = 'DESCARTADO' "
                + "ORDER BY l.data_baixa DESC";
        return executarConsultaLotes(sql, null);
    }

    /** Dá baixa manual: o usuário consumiu o item normalmente. */
    public void marcarComoConsumido(int loteId) throws SQLException {
        atualizarStatus(loteId, StatusLote.CONSUMIDO);
    }

    /** Dá baixa manual: o item venceu e foi descartado (gera perda financeira). */
    public void marcarComoDescartado(int loteId) throws SQLException {
        atualizarStatus(loteId, StatusLote.DESCARTADO);
    }

    private void atualizarStatus(int loteId, StatusLote novoStatus) throws SQLException {
        String sql = "UPDATE lotes SET status = ?, data_baixa = datetime('now', 'localtime') WHERE id = ?";

        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setString(1, novoStatus.name());
            stmt.setInt(2, loteId);
            stmt.executeUpdate();
        }
    }

    /**
     * Soma o valor financeiro (quantidade x preço unitário) de todos os
     * lotes marcados como DESCARTADO — a base do relatório de perdas.
     */
    public double calcularPerdaFinanceiraTotal() throws SQLException {
        String sql = "SELECT COALESCE(SUM(quantidade * preco_unitario), 0) AS perda "
                + "FROM lotes WHERE status = 'DESCARTADO'";

        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql);
             ResultSet rs = stmt.executeQuery()) {

            if (rs.next()) {
                return rs.getDouble("perda");
            }
        }
        return 0.0;
    }

    private Lote mapear(ResultSet rs) throws SQLException {
        Lote l = new Lote();
        l.setId(rs.getInt("id"));
        l.setProdutoId(rs.getInt("produto_id"));
        l.setQuantidade(rs.getDouble("quantidade"));
        l.setPrecoUnitario(rs.getDouble("preco_unitario"));
        l.setDataValidade(LocalDate.parse(rs.getString("data_validade")));
        l.setDataCadastro(rs.getString("data_cadastro"));
        l.setStatus(StatusLote.valueOf(rs.getString("status")));
        l.setDataBaixa(rs.getString("data_baixa"));
        l.setNomeProduto(rs.getString("nome_produto"));
        l.setCodigoBarrasProduto(rs.getString("codigo_barras_produto"));
        l.setUnidadeMedidaProduto(rs.getString("unidade_medida_produto"));
        return l;
    }
}
