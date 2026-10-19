package br.com.unisinos.despensa.service;

import br.com.unisinos.despensa.model.Lote;
import br.com.unisinos.despensa.model.Produto;
import br.com.unisinos.despensa.repository.ProdutoRepositoryEmMemoria;
import br.com.unisinos.despensa.util.Constantes;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

final class AmbienteDeTeste {

    static final Instant INSTANTE = Instant.parse("2026-10-19T01:30:00Z");
    static final LocalDateTime AGORA = LocalDateTime.of(2026, 10, 18, 22, 30);
    static final LocalDate HOJE = AGORA.toLocalDate();

    static final String USUARIO = "usuario-a";
    static final String OUTRO_USUARIO = "usuario-b";

    final ProdutoRepositoryEmMemoria repositorio = new ProdutoRepositoryEmMemoria();
    final Clock relogio = Clock.fixed(INSTANTE, Constantes.FUSO_HORARIO);
    final ProdutoService produtoService = new ProdutoService(repositorio, relogio);
    final LoteService loteService = new LoteService(produtoService, repositorio, relogio);

    private int sequencia;

    Produto novoProduto(String usuarioId) {
        sequencia++;
        return produtoService.cadastrar(usuarioId, "TESTE-" + sequencia, "Produto de Teste " + sequencia,
                "Categoria Teste");
    }

    Produto recarregar(String usuarioId, Produto produto) {
        return produtoService.buscarPorId(usuarioId, produto.getId());
    }

    List<Lote> lotes(String usuarioId, Produto produto) {
        return recarregar(usuarioId, produto).getLotes();
    }

    Lote lote(String usuarioId, Produto produto, String loteId) {
        return recarregar(usuarioId, produto).buscarLote(loteId).orElseThrow();
    }

    Lote loteVencido(String usuarioId, Produto produto, double quantidade, double preco, int diasVencido) {
        Lote lote = loteService.cadastrar(usuarioId, produto.getId(), quantidade, preco, HOJE.plusDays(1));
        return loteService.editar(usuarioId, produto.getId(), lote.getId(), quantidade, preco,
                HOJE.minusDays(diasVencido));
    }
}
