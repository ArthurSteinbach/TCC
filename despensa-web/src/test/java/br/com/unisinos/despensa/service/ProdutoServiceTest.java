package br.com.unisinos.despensa.service;

import br.com.unisinos.despensa.exception.ValidacaoException;
import br.com.unisinos.despensa.model.Lote;
import br.com.unisinos.despensa.model.Produto;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static br.com.unisinos.despensa.service.AmbienteDeTeste.AGORA;
import static br.com.unisinos.despensa.service.AmbienteDeTeste.HOJE;
import static br.com.unisinos.despensa.service.AmbienteDeTeste.OUTRO_USUARIO;
import static br.com.unisinos.despensa.service.AmbienteDeTeste.USUARIO;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ProdutoServiceTest {

    private final AmbienteDeTeste ambiente = new AmbienteDeTeste();
    private final ProdutoService produtoService = ambiente.produtoService;
    private final LoteService loteService = ambiente.loteService;

    @Test
    void cadastrarComDadosValidos_devePersistirERetornarComId() {
        Produto produto = produtoService.cadastrar(USUARIO, " 7891000100103 ", " Arroz Tio João 5kg ", "Grãos");

        assertNotNull(produto.getId());
        assertEquals(USUARIO, produto.getUsuarioId());
        assertEquals("7891000100103", produto.getCodigoBarras());
        assertEquals("Arroz Tio João 5kg", produto.getNome());
        assertTrue(produto.getLotes().isEmpty());
    }

    @Test
    void cadastrar_deveGravarDataCadastroNoHorarioDeBrasilia() {
        Produto produto = produtoService.cadastrar(USUARIO, "7891000100103", "Arroz", "Grãos");

        assertEquals(AGORA, produto.getDataCadastro());
    }

    @Test
    void cadastrarComCodigoBarrasDuplicado_deveLancarValidacaoException() {
        produtoService.cadastrar(USUARIO, "7891000100103", "Primeiro Produto", "Categoria A");

        ValidacaoException excecao = assertThrows(ValidacaoException.class,
                () -> produtoService.cadastrar(USUARIO, "7891000100103", "Segundo Produto", "Categoria B"));

        assertTrue(excecao.getMessage().contains("Primeiro Produto"));
    }

    @Test
    void cadastrarMesmoCodigoParaUsuariosDiferentes_devePermitir() {
        Produto doUsuario = produtoService.cadastrar(USUARIO, "7891000100103", "Arroz", "Grãos");
        Produto doOutro = produtoService.cadastrar(OUTRO_USUARIO, "7891000100103", "Arroz", "Grãos");

        assertFalse(doUsuario.getId().equals(doOutro.getId()));
        assertEquals(1, produtoService.listar(USUARIO).size());
        assertEquals(1, produtoService.listar(OUTRO_USUARIO).size());
    }

    @Test
    void cadastrarSemCodigoBarras_deveLancarValidacaoException() {
        assertThrows(ValidacaoException.class,
                () -> produtoService.cadastrar(USUARIO, "  ", "Produto Sem Código", "Categoria"));
    }

    @Test
    void cadastrarSemNome_deveLancarValidacaoException() {
        assertThrows(ValidacaoException.class,
                () -> produtoService.cadastrar(USUARIO, "7891000100103", "", "Categoria"));
    }

    @Test
    void buscarPorCodigoBarras_quandoExiste_deveRetornarProduto() {
        produtoService.cadastrar(USUARIO, "7896005800010", "Macarrão Espaguete", "Massas");

        Optional<Produto> encontrado = produtoService.buscarPorCodigoBarras(USUARIO, "7896005800010");

        assertTrue(encontrado.isPresent());
        assertEquals("Macarrão Espaguete", encontrado.get().getNome());
    }

    @Test
    void buscarPorCodigoBarras_quandoNaoExiste_deveRetornarVazio() {
        assertFalse(produtoService.buscarPorCodigoBarras(USUARIO, "CODIGO-QUE-NUNCA-FOI-CADASTRADO").isPresent());
    }

    @Test
    void listar_deveTrazerOsProdutosDoUsuarioEmOrdemAlfabetica() {
        produtoService.cadastrar(USUARIO, "3", "Feijão", null);
        produtoService.cadastrar(USUARIO, "1", "Arroz", null);
        produtoService.cadastrar(USUARIO, "2", "Café", null);

        List<String> nomes = produtoService.listar(USUARIO).stream().map(Produto::getNome).toList();

        assertEquals(List.of("Arroz", "Café", "Feijão"), nomes);
    }

    @Test
    void listarComLotesAtivos_deveIgnorarProdutosSemLotesOuSoComLotesBaixados() {
        Produto comAtivo = produtoService.cadastrar(USUARIO, "1", "Com lote ativo", null);
        loteService.cadastrar(USUARIO, comAtivo.getId(), 1, 1.0, HOJE.plusDays(5));
        produtoService.cadastrar(USUARIO, "2", "Sem lotes", null);
        Produto soBaixados = produtoService.cadastrar(USUARIO, "3", "Só baixados", null);
        Lote baixado = loteService.cadastrar(USUARIO, soBaixados.getId(), 1, 1.0, HOJE.plusDays(5));
        loteService.consumir(USUARIO, soBaixados.getId(), baixado.getId());

        List<String> nomes = produtoService.listarComLotesAtivos(USUARIO).stream().map(Produto::getNome).toList();

        assertEquals(List.of("Com lote ativo"), nomes);
    }

    @Test
    void editar_deveAlterarNomeECategoriaSemMudarOCodigoDeBarras() {
        Produto produto = produtoService.cadastrar(USUARIO, "7891000100103", "Leite Integral", "Laticínios");

        produtoService.editar(USUARIO, produto.getId(), "  Leite Desnatado 1L ", "Bebidas");

        Produto editado = produtoService.buscarPorCodigoBarras(USUARIO, "7891000100103").orElseThrow();
        assertEquals(produto.getId(), editado.getId());
        assertEquals("Leite Desnatado 1L", editado.getNome());
        assertEquals("Bebidas", editado.getCategoria());
        assertEquals("7891000100103", editado.getCodigoBarras());
    }

    @Test
    void editarComCategoriaEmBranco_deveDeixarSemCategoria() {
        Produto produto = produtoService.cadastrar(USUARIO, "7891000100103", "Leite", "Laticínios");

        Produto editado = produtoService.editar(USUARIO, produto.getId(), "Leite", "   ");

        assertNull(editado.getCategoria());
    }

    @Test
    void editarComNomeVazio_deveLancarValidacaoExceptionENaoAlterar() {
        Produto produto = produtoService.cadastrar(USUARIO, "7891000100103", "Feijão Preto", "Grãos");

        assertThrows(ValidacaoException.class, () -> produtoService.editar(USUARIO, produto.getId(), "   ", "Outra"));

        Produto depois = ambiente.recarregar(USUARIO, produto);
        assertEquals("Feijão Preto", depois.getNome());
        assertEquals("Grãos", depois.getCategoria());
    }

    @Test
    void excluirProdutoSemLotes_deveRemoverDoCatalogo() {
        Produto produto = produtoService.cadastrar(USUARIO, "7891000100103", "Produto Sem Lotes", "Categoria");

        produtoService.excluir(USUARIO, produto.getId());

        assertFalse(produtoService.buscarPorCodigoBarras(USUARIO, "7891000100103").isPresent());
    }

    @Test
    void excluirProdutoComLoteAtivo_deveLancarValidacaoExceptionENaoExcluir() {
        Produto produto = produtoService.cadastrar(USUARIO, "7891000100103", "Produto Com Lote", "Categoria");
        loteService.cadastrar(USUARIO, produto.getId(), 2, 5.0, HOJE.plusDays(10));

        ValidacaoException erro = assertThrows(ValidacaoException.class,
                () -> produtoService.excluir(USUARIO, produto.getId()));

        assertTrue(erro.getMessage().contains("lote"));
        assertTrue(produtoService.buscarPorCodigoBarras(USUARIO, "7891000100103").isPresent());
    }

    @Test
    void excluirProdutoSoComLotesConsumidosOuDescartados_deveLancarValidacaoExceptionENaoExcluir() {
        Produto produto = produtoService.cadastrar(USUARIO, "7891000100103", "Produto Com Histórico", "Categoria");
        Lote consumido = loteService.cadastrar(USUARIO, produto.getId(), 1, 5.0, HOJE.plusDays(10));
        Lote descartado = loteService.cadastrar(USUARIO, produto.getId(), 1, 5.0, HOJE.plusDays(3));
        loteService.consumir(USUARIO, produto.getId(), consumido.getId());
        loteService.descartar(USUARIO, produto.getId(), descartado.getId());

        ValidacaoException erro = assertThrows(ValidacaoException.class,
                () -> produtoService.excluir(USUARIO, produto.getId()));

        assertTrue(erro.getMessage().contains("2 lote(s)"));
        assertTrue(produtoService.buscarPorCodigoBarras(USUARIO, "7891000100103").isPresent());
    }
}
