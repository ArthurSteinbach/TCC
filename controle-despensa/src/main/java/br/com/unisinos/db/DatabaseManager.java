package br.com.unisinos.db;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;

/**
 * Responsável pela conexão com o banco SQLite local e pela inicialização
 * do schema (tabelas produtos e lotes).
 *
 * getConnection() devolve uma conexão nova a cada chamada (padrão comum
 * para SQLite em apps desktop de usuário único); quem chama é
 * responsável por fechar, idealmente via try-with-resources — é assim
 * que todos os DAOs já usam.
 *
 * inicializar() deve ser chamado uma única vez, no início da aplicação
 * (ou no início de cada classe de teste), para criar as tabelas.
 */
public final class DatabaseManager {

    private static final String DB_URL_PREFIX = "jdbc:sqlite:";

    private DatabaseManager() {
        // classe utilitária, não deve ser instanciada
    }

    /**
     * Caminho do arquivo do banco. Pode ser sobrescrito via propriedade
     * de sistema "despensa.db.path" — é assim que os testes automatizados
     * rodam num banco temporário, sem misturar dados com o despensa.db
     * real usado pela aplicação.
     */
    private static String obterCaminhoBanco() {
        return System.getProperty("despensa.db.path", "despensa.db");
    }

    public static Connection getConnection() throws SQLException {
        Connection connection = DriverManager.getConnection(DB_URL_PREFIX + obterCaminhoBanco());
        connection.createStatement().execute("PRAGMA foreign_keys = ON;");
        return connection;
    }

    /**
     * Lê o schema.sql (em src/main/resources) e roda os
     * CREATE TABLE IF NOT EXISTS. Seguro de chamar mais de uma vez.
     */
    public static void inicializar() {
        try (Connection conn = getConnection();
             InputStream is = DatabaseManager.class.getClassLoader().getResourceAsStream("schema.sql")) {

            if (is == null) {
                throw new IllegalStateException(
                        "Arquivo schema.sql não encontrado em src/main/resources.");
            }

            String sql = lerConteudo(is);

            try (Statement stmt = conn.createStatement()) {
                for (String comando : sql.split(";")) {
                    String trimmed = comando.trim();
                    if (!trimmed.isEmpty()) {
                        stmt.execute(trimmed);
                    }
                }
            }

        } catch (IOException | SQLException e) {
            throw new RuntimeException("Falha ao inicializar o banco de dados.", e);
        }
    }

    private static String lerConteudo(InputStream is) throws IOException {
        StringBuilder sb = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(is, StandardCharsets.UTF_8))) {
            String linha;
            while ((linha = reader.readLine()) != null) {
                sb.append(linha).append("\n");
            }
        }
        return sb.toString();
    }
}
