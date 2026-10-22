package br.com.unisinos.despensa.service;

import br.com.unisinos.despensa.exception.ValidacaoException;
import br.com.unisinos.despensa.model.Lote;
import br.com.unisinos.despensa.model.Produto;
import br.com.unisinos.despensa.model.StatusLote;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.Map;

import static br.com.unisinos.despensa.service.AmbienteDeTeste.HOJE;
import static br.com.unisinos.despensa.service.AmbienteDeTeste.OUTRO_USUARIO;
import static br.com.unisinos.despensa.service.AmbienteDeTeste.USUARIO;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class IsolamentoEntreUsuariosTest {

    private final AmbienteDeTeste ambiente = new AmbienteDeTeste();
    private final ProdutoService produtoService = ambiente.produtoService;
    private final LoteService loteService = ambiente.loteService;

    private Produto produtoDoOutro;
    private Lote loteDoOutro;

    @BeforeEach
    void cadastrarProdutoDoOutroUsuario() {
        produtoDoOutro = produtoService.cadastrar(OUTRO_USUARIO, "7891000100103", "Leite do Outro", "Laticínios");
        loteDoOutro = loteService.cadastrar(OUTRO_USUARIO, produtoDoOutro.getId(), 4, 5.0, HOJE.plusDays(3));
    }

    @Test
    void usuarioNaoEnxergaProdutosDeOutroUsuario() {
        assertTrue(produtoService.listar(USUARIO).isEmpty());
        assertFalse(produtoService.buscarPorCodigoBarras(USUARIO, "7891000100103").isPresent());
        assertThrows(ValidacaoException.class, () -> produtoService.buscarPorId(USUARIO, produtoDoOutro.getId()));
        assertTrue(loteService.listarAtivosFEFO(USUARIO).isEmpty());
        assertTrue(loteService.listarAlertaVencimento(USUARIO).isEmpty());
    }

    @Test
    void usuarioNaoAlteraNemExcluiProdutoDeOutroUsuario() {
        String id = produtoDoOutro.getId();

        assertThrows(ValidacaoException.class, () -> produtoService.editar(USUARIO, id, "Invadido", "X"));
        assertThrows(ValidacaoException.class, () -> produtoService.excluir(USUARIO, id));
        assertThrows(ValidacaoException.class,
                () -> loteService.cadastrar(USUARIO, id, 1, 1.0, HOJE.plusDays(5)));
        assertThrows(ValidacaoException.class,
                () -> loteService.cadastrarPorValidade(USUARIO, id, Map.of(HOJE.plusDays(5), 1), 1.0));
        assertThrows(ValidacaoException.class,
                () -> loteService.editar(USUARIO, id, loteDoOutro.getId(), 99, 99.0, HOJE.plusDays(50)));
        assertThrows(ValidacaoException.class, () -> loteService.consumir(USUARIO, id, loteDoOutro.getId()));
        assertThrows(ValidacaoException.class, () -> loteService.descartar(USUARIO, id, loteDoOutro.getId()));
        assertThrows(ValidacaoException.class, () -> loteService.planejarConsumoFEFO(USUARIO, id, 1));
        assertThrows(ValidacaoException.class, () -> loteService.consumirPorFEFO(USUARIO, id, 1));
        assertThrows(ValidacaoException.class, () -> loteService.descartar(USUARIO, id, loteDoOutro.getId(), 1));
        assertThrows(ValidacaoException.class, () -> loteService.desfazerBaixa(USUARIO, id, loteDoOutro.getId()));

        Produto intacto = ambiente.recarregar(OUTRO_USUARIO, produtoDoOutro);
        assertEquals("Leite do Outro", intacto.getNome());
        assertEquals("Laticínios", intacto.getCategoria());
        assertEquals(1, intacto.getLotes().size());
        Lote lote = intacto.getLotes().get(0);
        assertEquals(StatusLote.ATIVO, lote.getStatus());
        assertEquals(4, lote.getQuantidade());
        assertEquals(HOJE.plusDays(3), lote.getDataValidade());
    }

    @Test
    void perdasEDescartes_soConsideramOsProdutosDoProprioUsuario() {
        loteService.descartar(OUTRO_USUARIO, produtoDoOutro.getId(), loteDoOutro.getId());
        Produto meuProduto = produtoService.cadastrar(USUARIO, "7891000100103", "Meu Leite", "Laticínios");
        Lote meuLote = loteService.cadastrar(USUARIO, meuProduto.getId(), 1, 3.0, LocalDate.of(2026, 12, 1));
        loteService.descartar(USUARIO, meuProduto.getId(), meuLote.getId());

        assertEquals(3.0, loteService.calcularPerdaFinanceiraTotal(USUARIO), 0.001);
        assertEquals(20.0, loteService.calcularPerdaFinanceiraTotal(OUTRO_USUARIO), 0.001);
        assertEquals(1, loteService.listarDescartados(USUARIO).size());
        assertEquals("Meu Leite", loteService.listarDescartados(USUARIO).get(0).nomeProduto());
    }
}
