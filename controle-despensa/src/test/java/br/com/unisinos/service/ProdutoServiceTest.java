package br.com.unisinos.service;

import br.com.unisinos.db.DatabaseManager;
import br.com.unisinos.exception.ValidacaoException;
import br.com.unisinos.model.Lote;
import br.com.unisinos.model.Produto;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Testes da camada de negócio de Produto: cadastro válido, validações
 * obrigatórias e bloqueio de código de barras duplicado.
 */
class ProdutoServiceTest {

    private static ProdutoService produtoService;
    private static LoteService loteService;

    @TempDir
    static Path pastaTemporaria;

    @BeforeAll
    static void configurarBancoDeTeste() {
        String caminhoBanco = pastaTemporaria.resolve("despensa_teste.db").toAbsolutePath().toString();
        System.setProperty("despensa.db.path", caminhoBanco);
        DatabaseManager.inicializar();
        produtoService = new ProdutoService();
        loteService = new LoteService();
    }

    private String codigoBarrasUnico() {
        // Garante que cada teste use um código diferente, evitando falso
        // positivo de "duplicado" entre um teste e outro.
        return "TESTE-" + System.nanoTime();
    }

    @Test
    void cadastrarComDadosValidos_devePersistirERetornarComId() throws SQLException {
        Produto produto = produtoService.cadastrar(codigoBarrasUnico(), "Arroz Tio João 5kg", "Grãos");

        assertNotNull(produto.getId(), "O produto cadastrado deve receber um id gerado pelo banco.");
        assertEquals("Arroz Tio João 5kg", produto.getNome());
    }

    @Test
    void cadastrarComCodigoBarrasDuplicado_deveLancarValidacaoException() throws SQLException {
        String codigo = codigoBarrasUnico();
        produtoService.cadastrar(codigo, "Primeiro Produto", "Categoria A");

        ValidacaoException excecao = assertThrows(ValidacaoException.class,
                () -> produtoService.cadastrar(codigo, "Segundo Produto", "Categoria B"));

        assertTrue(excecao.getMessage().contains("Primeiro Produto"),
                "A mensagem de erro deve indicar qual produto já usa esse código.");
    }

    @Test
    void cadastrarSemCodigoBarras_deveLancarValidacaoException() {
        assertThrows(ValidacaoException.class,
                () -> produtoService.cadastrar("  ", "Produto Sem Código", "Categoria"));
    }

    @Test
    void cadastrarSemNome_deveLancarValidacaoException() {
        assertThrows(ValidacaoException.class,
                () -> produtoService.cadastrar(codigoBarrasUnico(), "", "Categoria"));
    }

    @Test
    void buscarPorCodigoBarras_quandoExiste_deveRetornarProduto() throws SQLException {
        String codigo = codigoBarrasUnico();
        produtoService.cadastrar(codigo, "Macarrão Espaguete", "Massas");

        Optional<Produto> encontrado = produtoService.buscarPorCodigoBarras(codigo);

        assertTrue(encontrado.isPresent());
        assertEquals("Macarrão Espaguete", encontrado.get().getNome());
    }

    @Test
    void buscarPorCodigoBarras_quandoNaoExiste_deveRetornarVazio() throws SQLException {
        Optional<Produto> encontrado = produtoService.buscarPorCodigoBarras("CODIGO-QUE-NUNCA-FOI-CADASTRADO");

        assertFalse(encontrado.isPresent());
    }

    @Test
    void editar_deveAlterarNomeECategoriaSemMudarOCodigoDeBarras() throws SQLException {
        String codigo = codigoBarrasUnico();
        Produto produto = produtoService.cadastrar(codigo, "Leite Integral", "Laticínios");

        produtoService.editar(produto.getId(), "  Leite Desnatado 1L ", "Bebidas");

        Produto editado = produtoService.buscarPorCodigoBarras(codigo).orElseThrow();
        assertEquals(produto.getId(), editado.getId());
        assertEquals("Leite Desnatado 1L", editado.getNome());
        assertEquals("Bebidas", editado.getCategoria());
        assertEquals(codigo, editado.getCodigoBarras());
    }

    @Test
    void editarComNomeVazio_deveLancarValidacaoExceptionENaoAlterar() throws SQLException {
        String codigo = codigoBarrasUnico();
        Produto produto = produtoService.cadastrar(codigo, "Feijão Preto", "Grãos");

        assertThrows(ValidacaoException.class, () -> produtoService.editar(produto.getId(), "   ", "Outra"));

        Produto depois = produtoService.buscarPorCodigoBarras(codigo).orElseThrow();
        assertEquals("Feijão Preto", depois.getNome());
        assertEquals("Grãos", depois.getCategoria());
    }

    @Test
    void excluirProdutoSemLotes_deveRemoverDoCatalogo() throws SQLException {
        String codigo = codigoBarrasUnico();
        Produto produto = produtoService.cadastrar(codigo, "Produto Sem Lotes", "Categoria");

        produtoService.excluir(produto.getId());

        assertFalse(produtoService.buscarPorCodigoBarras(codigo).isPresent());
    }

    @Test
    void excluirProdutoComLoteAtivo_deveLancarValidacaoExceptionENaoExcluir() throws SQLException {
        String codigo = codigoBarrasUnico();
        Produto produto = produtoService.cadastrar(codigo, "Produto Com Lote", "Categoria");
        loteService.cadastrar(produto.getId(), 2, 5.0, LocalDate.now().plusDays(10));

        ValidacaoException erro = assertThrows(ValidacaoException.class,
                () -> produtoService.excluir(produto.getId()));

        assertTrue(erro.getMessage().contains("lote"));
        assertTrue(produtoService.buscarPorCodigoBarras(codigo).isPresent());
    }

    @Test
    void excluirProdutoSoComLotesConsumidosOuDescartados_deveLancarValidacaoExceptionENaoExcluir()
            throws SQLException {
        String codigo = codigoBarrasUnico();
        Produto produto = produtoService.cadastrar(codigo, "Produto Com Histórico", "Categoria");
        Lote consumido = loteService.cadastrar(produto.getId(), 1, 5.0, LocalDate.now().plusDays(10));
        Lote descartado = loteService.cadastrar(produto.getId(), 1, 5.0, LocalDate.now().plusDays(3));
        loteService.consumir(consumido.getId());
        loteService.descartar(descartado.getId());

        ValidacaoException erro = assertThrows(ValidacaoException.class,
                () -> produtoService.excluir(produto.getId()));

        assertTrue(erro.getMessage().contains("2 lote(s)"));
        assertTrue(produtoService.buscarPorCodigoBarras(codigo).isPresent());
    }
}
