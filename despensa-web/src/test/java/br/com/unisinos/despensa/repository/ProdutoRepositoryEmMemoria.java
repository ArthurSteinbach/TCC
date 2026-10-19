package br.com.unisinos.despensa.repository;

import br.com.unisinos.despensa.model.Lote;
import br.com.unisinos.despensa.model.Produto;
import org.bson.types.ObjectId;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.dao.OptimisticLockingFailureException;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

public class ProdutoRepositoryEmMemoria implements ProdutoRepository {

    private final Map<String, Produto> documentos = new LinkedHashMap<>();
    private boolean simularAlteracaoConcorrente;
    private boolean bancoForaDoAr;
    private int gravacoes;

    @Override
    public Produto save(Produto produto) {
        verificarBanco();
        if (simularAlteracaoConcorrente) {
            simularAlteracaoConcorrente = false;
            throw new OptimisticLockingFailureException("Versão desatualizada do produto " + produto.getId());
        }

        if (produto.getId() == null) {
            verificarCodigoUnico(produto);
            produto.setId(new ObjectId().toHexString());
            produto.setVersao(0L);
        } else {
            Produto atual = documentos.get(produto.getId());
            if (atual == null || !Objects.equals(atual.getVersao(), produto.getVersao())) {
                throw new OptimisticLockingFailureException("Versão desatualizada do produto " + produto.getId());
            }
            verificarCodigoUnico(produto);
            produto.setVersao(produto.getVersao() + 1);
        }

        documentos.put(produto.getId(), copiar(produto));
        gravacoes++;
        return produto;
    }

    @Override
    public void delete(Produto produto) {
        verificarBanco();
        Produto atual = documentos.get(produto.getId());
        if (atual == null || !Objects.equals(atual.getVersao(), produto.getVersao())) {
            throw new OptimisticLockingFailureException("Versão desatualizada do produto " + produto.getId());
        }
        documentos.remove(produto.getId());
    }

    @Override
    public Optional<Produto> findByIdAndUsuarioId(String id, String usuarioId) {
        verificarBanco();
        return Optional.ofNullable(documentos.get(id))
                .filter(p -> p.getUsuarioId().equals(usuarioId))
                .map(ProdutoRepositoryEmMemoria::copiar);
    }

    @Override
    public Optional<Produto> findByUsuarioIdAndCodigoBarras(String usuarioId, String codigoBarras) {
        verificarBanco();
        return documentos.values().stream()
                .filter(p -> p.getUsuarioId().equals(usuarioId) && p.getCodigoBarras().equals(codigoBarras))
                .findFirst()
                .map(ProdutoRepositoryEmMemoria::copiar);
    }

    @Override
    public List<Produto> findByUsuarioIdOrderByNomeAsc(String usuarioId) {
        verificarBanco();
        return documentos.values().stream()
                .filter(p -> p.getUsuarioId().equals(usuarioId))
                .sorted(Comparator.comparing(Produto::getNome))
                .map(ProdutoRepositoryEmMemoria::copiar)
                .toList();
    }

    public void simularAlteracaoConcorrenteNaProximaGravacao() {
        simularAlteracaoConcorrente = true;
    }

    public void simularBancoForaDoAr(boolean foraDoAr) {
        bancoForaDoAr = foraDoAr;
    }

    public void limpar() {
        documentos.clear();
        simularAlteracaoConcorrente = false;
        bancoForaDoAr = false;
        gravacoes = 0;
    }

    public int getGravacoes() {
        return gravacoes;
    }

    private void verificarBanco() {
        if (bancoForaDoAr) {
            throw new DataAccessResourceFailureException("Timed out while waiting for a server");
        }
    }

    private void verificarCodigoUnico(Produto produto) {
        boolean repetido = documentos.values().stream()
                .anyMatch(p -> !p.getId().equals(produto.getId())
                        && p.getUsuarioId().equals(produto.getUsuarioId())
                        && p.getCodigoBarras().equals(produto.getCodigoBarras()));
        if (repetido) {
            throw new DuplicateKeyException("usuario_codigo_barras");
        }
    }

    private static Produto copiar(Produto origem) {
        Produto copia = new Produto(origem.getUsuarioId(), origem.getCodigoBarras(), origem.getNome(),
                origem.getCategoria(), origem.getDataCadastro());
        copia.setId(origem.getId());
        copia.setVersao(origem.getVersao());
        List<Lote> lotes = new ArrayList<>();
        for (Lote lote : origem.getLotes()) {
            Lote l = new Lote(lote.getId(), lote.getQuantidade(), lote.getPrecoUnitario(), lote.getDataValidade(),
                    lote.getDataCadastro());
            l.setStatus(lote.getStatus());
            l.setDataBaixa(lote.getDataBaixa());
            lotes.add(l);
        }
        copia.setLotes(lotes);
        return copia;
    }
}
