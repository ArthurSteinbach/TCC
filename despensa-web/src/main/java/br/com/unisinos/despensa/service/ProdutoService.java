package br.com.unisinos.despensa.service;

import br.com.unisinos.despensa.exception.ValidacaoException;
import br.com.unisinos.despensa.model.Produto;
import br.com.unisinos.despensa.repository.ProdutoRepository;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Service
public class ProdutoService {

    private final ProdutoRepository produtoRepository;
    private final Clock relogio;

    public ProdutoService(ProdutoRepository produtoRepository, Clock relogio) {
        this.produtoRepository = produtoRepository;
        this.relogio = relogio;
    }

    public Produto cadastrar(String usuarioId, String codigoBarras, String nome, String categoria) {
        validarUsuario(usuarioId);
        if (codigoBarras == null || codigoBarras.isBlank()) {
            throw new ValidacaoException("O código de barras é obrigatório.");
        }
        validarNome(nome);

        String codigo = codigoBarras.trim();
        Optional<Produto> existente = produtoRepository.findByUsuarioIdAndCodigoBarras(usuarioId, codigo);
        if (existente.isPresent()) {
            throw codigoRepetido(existente.get().getNome());
        }

        Produto produto = new Produto(usuarioId, codigo, nome.trim(), normalizarCategoria(categoria),
                LocalDateTime.now(relogio));
        try {
            return produtoRepository.save(produto);
        } catch (DuplicateKeyException e) {
            throw codigoRepetido(produto.getNome());
        }
    }

    public Optional<Produto> buscarPorCodigoBarras(String usuarioId, String codigoBarras) {
        validarUsuario(usuarioId);
        if (codigoBarras == null || codigoBarras.isBlank()) {
            return Optional.empty();
        }
        return produtoRepository.findByUsuarioIdAndCodigoBarras(usuarioId, codigoBarras.trim());
    }

    public Produto buscarPorId(String usuarioId, String produtoId) {
        validarUsuario(usuarioId);
        if (produtoId == null || produtoId.isBlank()) {
            throw new ValidacaoException("É necessário informar o produto.");
        }
        return produtoRepository.findByIdAndUsuarioId(produtoId, usuarioId)
                .orElseThrow(() -> new ValidacaoException("Produto não encontrado."));
    }

    public List<Produto> listar(String usuarioId) {
        validarUsuario(usuarioId);
        return produtoRepository.findByUsuarioIdOrderByNomeAsc(usuarioId);
    }

    public Produto editar(String usuarioId, String produtoId, String nome, String categoria) {
        validarNome(nome);

        Produto produto = buscarPorId(usuarioId, produtoId);
        produto.setNome(nome.trim());
        produto.setCategoria(normalizarCategoria(categoria));
        return produtoRepository.save(produto);
    }

    public void excluir(String usuarioId, String produtoId) {
        Produto produto = buscarPorId(usuarioId, produtoId);

        int lotes = produto.getLotes().size();
        if (lotes > 0) {
            throw new ValidacaoException("O produto \"" + produto.getNome() + "\" não pode ser excluído porque tem "
                    + lotes + " lote(s) registrado(s) (ativos, consumidos ou descartados). "
                    + "Esses lotes fazem parte do histórico de consumo e do relatório de perdas.");
        }

        produtoRepository.delete(produto);
    }

    private static void validarUsuario(String usuarioId) {
        if (usuarioId == null || usuarioId.isBlank()) {
            throw new IllegalArgumentException("O usuário é obrigatório.");
        }
    }

    private static void validarNome(String nome) {
        if (nome == null || nome.isBlank()) {
            throw new ValidacaoException("O nome do produto é obrigatório.");
        }
    }

    private static String normalizarCategoria(String categoria) {
        return categoria == null || categoria.isBlank() ? null : categoria.trim();
    }

    private static ValidacaoException codigoRepetido(String nomeExistente) {
        return new ValidacaoException(
                "Já existe um produto cadastrado com este código de barras: " + nomeExistente);
    }
}
