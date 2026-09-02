package br.com.unisinos;

import br.com.unisinos.db.DatabaseManager;
import br.com.unisinos.ui.TelaPrincipal;

import javax.swing.SwingUtilities;

/**
 * Ponto de entrada da aplicação: inicializa o banco de dados e abre a
 * tela principal do sistema.
 */
public class App {

    public static void main(String[] args) {
        DatabaseManager.inicializar();

        SwingUtilities.invokeLater(() -> {
            TelaPrincipal tela = new TelaPrincipal();
            tela.setVisible(true);
        });
    }
}
