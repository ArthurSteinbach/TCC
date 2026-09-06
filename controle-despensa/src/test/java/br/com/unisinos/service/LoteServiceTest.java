package br.com.unisinos.service;

import br.com.unisinos.db.DatabaseManager;
import br.com.unisinos.exception.ValidacaoException;
import br.com.unisinos.model.Lote;
import br.com.unisinos.model.Produto;
import br.com.unisinos.model.StatusLote;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Testes da camada de negócio de Lote: validações de cadastro, a
 * ordenação FEFO, o filtro de alerta de vencimento, as baixas
 * (consumido/descartado) e o cálculo de perda financeira.
 */
class LoteServiceTest {

    private static LoteService loteService;
    private static ProdutoService produtoService;

    @TempDir
    static Path pastaTemporaria;

    private Produto produtoDeTeste;

    @BeforeAll
    static void configurarBancoDeTeste() {
        String caminhoBanco = pastaTemporaria.resolve("despensa_teste.db").toAbsolutePath().toString();
        System.setProperty("despensa.db.path", caminhoBanco);
        DatabaseManager.inicializar();
        loteService = new LoteService();
        produtoService = new ProdutoService();
    }

    /** Cada teste cadastra o seu próprio produto, com código único, para não interferir nos demais. */
    @BeforeEach
    void cadastrarProdutoDeTeste() throws SQLException {
        String codigo = "TESTE-" + System.nanoTime();
        produtoDeTeste = produtoService.cadastrar(codigo, "Produto de Teste", "Categoria Teste");
    }

    @Test
    void cadastrarComDadosValidos_devePersistirComStatusAtivo() throws SQLException {
        Lote lote = loteService.cadastrar(produtoDeTeste.getId(), 2, 10.0, LocalDate.now().plusDays(10));

        assertNotNull(lote.getId());
        assertEquals(StatusLote.ATIVO, lote.getStatus());
    }

    @Test
    void cadastrarComValidadeNoPassado_deveLancarValidacaoException() {
        assertThrows(ValidacaoException.class, () ->
                loteService.cadastrar(produtoDeTeste.getId(), 1, 10.0, LocalDate.now().minusDays(1)));
    }

    @Test
    void cadastrarComQuantidadeZero_deveLancarValidacaoException() {
        assertThrows(ValidacaoException.class, () ->
                loteService.cadastrar(produtoDeTeste.getId(), 0, 10.0, LocalDate.now().plusDays(5)));
    }

    @Test
    void cadastrarComPrecoNegativo_deveLancarValidacaoException() {
        assertThrows(ValidacaoException.class, () ->
                loteService.cadastrar(produtoDeTeste.getId(), 1, -5.0, LocalDate.now().plusDays(5)));
    }

    /**
     * Teste central do sistema: prova que, independente da ordem de
     * cadastro, a listagem sempre devolve os lotes ordenados pela regra
     * FEFO (validade mais próxima primeiro).
     */
    @Test
    void listarAtivosFEFO_deveOrdenarPorValidadeMaisProximaPrimeiro() throws SQLException {
        loteService.cadastrar(produtoDeTeste.getId(), 1, 10.0, LocalDate.now().plusDays(30));
        loteService.cadastrar(produtoDeTeste.getId(), 1, 10.0, LocalDate.now().plusDays(3));
        loteService.cadastrar(produtoDeTeste.getId(), 1, 10.0, LocalDate.now().plusDays(10));

        List<Lote> lotesDoProduto = loteService.listarAtivosFEFO().stream()
                .filter(l -> l.getProdutoId().equals(produtoDeTeste.getId()))
                .toList();

        assertEquals(3, lotesDoProduto.size());
        assertEquals(LocalDate.now().plusDays(3), lotesDoProduto.get(0).getDataValidade());
        assertEquals(LocalDate.now().plusDays(10), lotesDoProduto.get(1).getDataValidade());
        assertEquals(LocalDate.now().plusDays(30), lotesDoProduto.get(2).getDataValidade());
    }

    @Test
    void listarAlertaVencimento_deveIncluirSoItensDentroDaJanela() throws SQLException {
        loteService.cadastrar(produtoDeTeste.getId(), 1, 10.0, LocalDate.now().plusDays(3));  // dentro (padrão 7 dias)
        loteService.cadastrar(produtoDeTeste.getId(), 1, 10.0, LocalDate.now().plusDays(30)); // fora

        List<Lote> emAlerta = loteService.listarAlertaVencimento().stream()
                .filter(l -> l.getProdutoId().equals(produtoDeTeste.getId()))
                .toList();

        assertEquals(1, emAlerta.size());
        assertEquals(LocalDate.now().plusDays(3), emAlerta.get(0).getDataValidade());
    }

    @Test
    void descartar_deveSomarValorNaPerdaFinanceiraTotal() throws SQLException {
        double perdaAntes = loteService.calcularPerdaFinanceiraTotal();

        Lote lote = loteService.cadastrar(produtoDeTeste.getId(), 3, 12.50, LocalDate.now().plusDays(5));
        loteService.descartar(lote.getId());

        double perdaDepois = loteService.calcularPerdaFinanceiraTotal();

        assertEquals(perdaAntes + 37.50, perdaDepois, 0.001,
                "3 unidades a R$ 12,50 descartadas devem somar R$ 37,50 na perda total.");
    }

    @Test
    void consumir_naoDeveAfetarPerdaFinanceira() throws SQLException {
        double perdaAntes = loteService.calcularPerdaFinanceiraTotal();

        Lote lote = loteService.cadastrar(produtoDeTeste.getId(), 5, 100.0, LocalDate.now().plusDays(5));
        loteService.consumir(lote.getId());

        double perdaDepois = loteService.calcularPerdaFinanceiraTotal();

        assertEquals(perdaAntes, perdaDepois, 0.001,
                "Consumir um item não é desperdício, então não deve alterar a perda financeira.");
    }

    @Test
    void editar_deveAtualizarQuantidadePrecoEValidade() throws SQLException {
        Lote lote = loteService.cadastrar(produtoDeTeste.getId(), 1, 10.0, LocalDate.now().plusDays(5));

        LocalDate novaValidade = LocalDate.now().plusDays(20);
        loteService.editar(lote.getId(), 4, 25.0, novaValidade);

        Lote atualizado = loteService.listarAtivosFEFO().stream()
                .filter(l -> l.getId().equals(lote.getId()))
                .findFirst()
                .orElseThrow();

        assertEquals(4, atualizado.getQuantidade());
        assertEquals(25.0, atualizado.getPrecoUnitario());
        assertEquals(novaValidade, atualizado.getDataValidade());
    }

}
