package br.com.unisinos;

import java.sql.Connection;
import java.sql.SQLException;

import br.com.unisinos.db.DatabaseManager;

public class App {
    public static void main(String[] args) {
        // Cria as tabelas (produtos e lotes) na primeira execução.
        DatabaseManager.inicializar();

        try (Connection conn = DatabaseManager.getConnection()) {
            if (conn != null) {
                System.out.println("==================================================");
                System.out.println("Tudo certo! Conexão com SQLite estabelecida.");
                System.out.println("O arquivo 'despensa.db' foi criado com sucesso!");
                System.out.println("Tabelas 'produtos' e 'lotes' inicializadas.");
                System.out.println("==================================================");
            }
        } catch (SQLException e) {
            System.out.println("Deu erro ao conectar: " + e.getMessage());
        }
    }
}
