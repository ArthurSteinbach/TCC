package br.com.unisinos.despensa.repository;

import br.com.unisinos.despensa.model.Produto;
import org.springframework.data.repository.Repository;

import java.util.List;
import java.util.Optional;

public interface ProdutoRepository extends Repository<Produto, String> {

    Produto save(Produto produto);

    void delete(Produto produto);

    Optional<Produto> findByIdAndUsuarioId(String id, String usuarioId);

    Optional<Produto> findByUsuarioIdAndCodigoBarras(String usuarioId, String codigoBarras);

    List<Produto> findByUsuarioIdOrderByNomeAsc(String usuarioId);
}
