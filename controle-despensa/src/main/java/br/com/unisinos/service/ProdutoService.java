package br.com.unisinos.service;

import br.com.unisinos.dao.ProdutoDAO;
import br.com.unisinos.exception.ValidacaoException;
import br.com.unisinos.model.Produto;

import java.sql.SQLException;
import java.util.List;
import java.util.Optional;

/**
 * Regras de negócio relacionadas ao cadastro de produtos no catálogo.
 * A tela (ou qualquer outro chamador) deve falar com esta classe, não
 * diretamente com o ProdutoDAO — assim as validações não podem ser
 * "esquecidas" por engano em algum ponto do sistema.
 */
public class ProdutoService {

    private final ProdutoDAO produtoDAO;

    public ProdutoService() {
        this.produtoDAO = new ProdutoDAO();
    }

    /** Construtor alternativo, útil para testes com um DAO substituto. */
    public ProdutoService(ProdutoDAO produtoDAO) {
        this.produtoDAO = produtoDAO;
    }

    /**
     * Cadastra um novo produto, validando os campos obrigatórios e
     * impedindo duplicidade de código de barras.
     */
    public Produto cadastrar(String codigoBarras, String nome, String categoria, String unidadeMedida)
            throws SQLException {

        if (codigoBarras == null || codigoBarras.isBlank()) {
            throw new ValidacaoException("O código de barras é obrigatório.");
        }
        if (nome == null || nome.isBlank()) {
            throw new ValidacaoException("O nome do produto é obrigatório.");
        }

        Optional<Produto> existente = produtoDAO.buscarPorCodigoBarras(codigoBarras.trim());
        if (existente.isPresent()) {
            throw new ValidacaoException(
                    "Já existe um produto cadastrado com este código de barras: " + existente.get().getNome());
        }

        Produto produto = new Produto(codigoBarras.trim(), nome.trim(), categoria, unidadeMedida);
        return produtoDAO.inserir(produto);
    }

    /** Usado no fluxo de leitura por câmera: verifica se o produto escaneado já existe. */
    public Optional<Produto> buscarPorCodigoBarras(String codigoBarras) throws SQLException {
        return produtoDAO.buscarPorCodigoBarras(codigoBarras);
    }

    public List<Produto> listarTodos() throws SQLException {
        return produtoDAO.listarTodos();
    }
}
