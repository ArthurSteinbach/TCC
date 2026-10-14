package br.com.unisinos.ui;

import br.com.unisinos.model.Produto;

import javax.swing.table.AbstractTableModel;
import java.util.ArrayList;
import java.util.List;

public class ProdutoTableModel extends AbstractTableModel {

    private static final String[] COLUNAS = {"Código de barras", "Nome", "Categoria"};

    private List<Produto> produtos = new ArrayList<>();

    public void setProdutos(List<Produto> produtos) {
        this.produtos = produtos;
        fireTableDataChanged();
    }

    public Produto getProdutoNaLinha(int linha) {
        return produtos.get(linha);
    }

    @Override
    public int getRowCount() {
        return produtos.size();
    }

    @Override
    public int getColumnCount() {
        return COLUNAS.length;
    }

    @Override
    public String getColumnName(int coluna) {
        return COLUNAS[coluna];
    }

    @Override
    public Object getValueAt(int linha, int coluna) {
        Produto produto = produtos.get(linha);
        return switch (coluna) {
            case 0 -> produto.getCodigoBarras();
            case 1 -> produto.getNome();
            case 2 -> produto.getCategoria() == null ? "" : produto.getCategoria();
            default -> "";
        };
    }
}
