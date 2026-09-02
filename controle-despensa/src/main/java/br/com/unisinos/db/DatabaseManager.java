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
 * Responsável pela conexão com o banco SQLite local (despensa.db).
 *
 * Cada chamada de {@link #getConnection()} devolve uma conexão nova, que o
 * chamador deve fechar (idealmente via try-with-resources). A criação do
 * schema (tabelas produtos e lotes) é feita uma única vez, chamando
 * {@link #inicializar()} no arranque da aplicação.
 */
public final class DatabaseManager {

    private static final String DB_URL = "jdbc:sqlite:despensa.db";

    private DatabaseManager() {
        // classe utilitária, não deve ser instanciada
    }

    /**
     * Abre e devolve uma nova conexão com o banco, já com as foreign keys
     * habilitadas. O chamador é responsável por fechá-la.
     */
    public static Connection getConnection() throws SQLException {
        Connection conn = DriverManager.getConnection(DB_URL);
        try (Statement stmt = conn.createStatement()) {
            stmt.execute("PRAGMA foreign_keys = ON;");
        }
        return conn;
    }

    /**
     * Executa o schema.sql (localizado em src/main/resources) para criar as
     * tabelas caso ainda não existam. Usa CREATE TABLE IF NOT EXISTS, então é
     * seguro chamar em toda inicialização, mas deve ser chamado apenas uma
     * vez, no início do App.main().
     */
    public static void inicializar() {
        try (InputStream is = DatabaseManager.class.getClassLoader()
                .getResourceAsStream("schema.sql")) {

            if (is == null) {
                throw new IllegalStateException(
                        "Arquivo schema.sql não encontrado em src/main/resources.");
            }

            String sql = lerConteudo(is);

            try (Connection conn = getConnection();
                 Statement stmt = conn.createStatement()) {
                // schema.sql pode ter múltiplos comandos separados por ';'
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
