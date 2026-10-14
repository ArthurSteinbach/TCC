package br.com.unisinos.dao;

import br.com.unisinos.db.DatabaseManager;
import br.com.unisinos.model.Produto;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Acesso ao banco de dados para a tabela produtos (o catálogo).
 */
public class ProdutoDAO {

    /** Insere um novo produto no catálogo e preenche o id gerado. */
    public Produto inserir(Produto produto) throws SQLException {
        String sql = "INSERT INTO produtos (codigo_barras, nome, categoria, unidade_medida, data_cadastro) "
                + "VALUES (?, ?, ?, ?, datetime('now', 'localtime'))";

        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {

            stmt.setString(1, produto.getCodigoBarras());
            stmt.setString(2, produto.getNome());
            stmt.setString(3, produto.getCategoria());
            stmt.setString(4, produto.getUnidadeMedida() != null ? produto.getUnidadeMedida() : "UN");
            stmt.executeUpdate();

            try (ResultSet rs = stmt.getGeneratedKeys()) {
                if (rs.next()) {
                    produto.setId(rs.getInt(1));
                }
            }
        }
        return produto;
    }

    /** Busca um produto pelo código de barras (usado ao ler via câmera). */
    public Optional<Produto> buscarPorCodigoBarras(String codigoBarras) throws SQLException {
        String sql = "SELECT * FROM produtos WHERE codigo_barras = ?";

        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setString(1, codigoBarras);
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return Optional.of(mapear(rs));
                }
            }
        }
        return Optional.empty();
    }

    /** Busca um produto pelo id. */
    public Optional<Produto> buscarPorId(int id) throws SQLException {
        String sql = "SELECT * FROM produtos WHERE id = ?";

        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setInt(1, id);
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return Optional.of(mapear(rs));
                }
            }
        }
        return Optional.empty();
    }

    /** Lista todos os produtos cadastrados, em ordem alfabética. */
    public List<Produto> listarTodos() throws SQLException {
        List<Produto> produtos = new ArrayList<>();
        String sql = "SELECT * FROM produtos ORDER BY nome";

        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql);
             ResultSet rs = stmt.executeQuery()) {

            while (rs.next()) {
                produtos.add(mapear(rs));
            }
        }
        return produtos;
    }

    public void atualizar(Produto produto) throws SQLException {
        String sql = "UPDATE produtos SET nome = ?, categoria = ? WHERE id = ?";

        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setString(1, produto.getNome());
            stmt.setString(2, produto.getCategoria());
            stmt.setInt(3, produto.getId());
            stmt.executeUpdate();
        }
    }

    public void excluir(int id) throws SQLException {
        String sql = "DELETE FROM produtos WHERE id = ?";

        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setInt(1, id);
            stmt.executeUpdate();
        }
    }

    public int contarLotes(int produtoId) throws SQLException {
        String sql = "SELECT COUNT(*) FROM lotes WHERE produto_id = ?";

        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setInt(1, produtoId);
            try (ResultSet rs = stmt.executeQuery()) {
                return rs.next() ? rs.getInt(1) : 0;
            }
        }
    }

    public List<Produto> listarComLotesAtivos() throws SQLException {
        List<Produto> produtos = new ArrayList<>();
        String sql = "SELECT * FROM produtos p "
                + "WHERE EXISTS (SELECT 1 FROM lotes l WHERE l.produto_id = p.id AND l.status = 'ATIVO') "
                + "ORDER BY p.nome";

        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql);
             ResultSet rs = stmt.executeQuery()) {

            while (rs.next()) {
                produtos.add(mapear(rs));
            }
        }
        return produtos;
    }

    private Produto mapear(ResultSet rs) throws SQLException {
        Produto p = new Produto();
        p.setId(rs.getInt("id"));
        p.setCodigoBarras(rs.getString("codigo_barras"));
        p.setNome(rs.getString("nome"));
        p.setCategoria(rs.getString("categoria"));
        p.setUnidadeMedida(rs.getString("unidade_medida"));
        p.setDataCadastro(rs.getString("data_cadastro"));
        return p;
    }
}
