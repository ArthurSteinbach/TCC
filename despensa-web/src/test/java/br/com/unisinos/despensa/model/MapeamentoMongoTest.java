package br.com.unisinos.despensa.model;

import org.bson.Document;
import org.bson.types.ObjectId;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.mongodb.core.convert.MappingMongoConverter;
import org.springframework.data.mongodb.core.convert.MongoCustomConversions;
import org.springframework.data.mongodb.core.convert.NoOpDbRefResolver;
import org.springframework.data.mongodb.core.index.IndexDefinition;
import org.springframework.data.mongodb.core.index.IndexResolver;
import org.springframework.data.mongodb.core.mapping.MongoMappingContext;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.StreamSupport;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MapeamentoMongoTest {

    private MongoMappingContext contexto;
    private MappingMongoConverter conversor;

    @BeforeEach
    void criarConversor() {
        MongoCustomConversions conversoes = new MongoCustomConversions(List.of());
        contexto = new MongoMappingContext();
        contexto.setSimpleTypeHolder(conversoes.getSimpleTypeHolder());
        contexto.afterPropertiesSet();
        conversor = new MappingMongoConverter(NoOpDbRefResolver.INSTANCE, contexto);
        conversor.setCustomConversions(conversoes);
        conversor.afterPropertiesSet();
    }

    @Test
    void produto_deveSerGravadoComOsLotesDentroDoMesmoDocumento() {
        Produto produto = new Produto("usuario-a", "7891000100103", "Leite", "Laticínios",
                LocalDateTime.of(2026, 10, 18, 22, 30));
        produto.setId(new ObjectId().toHexString());
        produto.setVersao(3L);
        Lote lote = new Lote(new ObjectId().toHexString(), 2, 4.5, LocalDate.of(2026, 11, 1),
                LocalDateTime.of(2026, 10, 18, 22, 30));
        produto.getLotes().add(lote);

        Document documento = new Document();
        conversor.write(produto, documento);

        assertInstanceOf(ObjectId.class, documento.get("_id"));
        assertEquals(3L, documento.get("versao"));
        assertEquals("usuario-a", documento.get("usuarioId"));
        assertEquals("7891000100103", documento.get("codigoBarras"));
        List<?> lotes = (List<?>) documento.get("lotes");
        assertEquals(1, lotes.size());
        Document loteGravado = (Document) lotes.get(0);
        assertEquals("ATIVO", loteGravado.get("status"));
        assertEquals(4.5, loteGravado.get("precoUnitario"));

        Produto lido = conversor.read(Produto.class, documento);
        assertEquals(produto.getId(), lido.getId());
        assertEquals(3L, lido.getVersao());
        assertEquals(produto.getDataCadastro(), lido.getDataCadastro());
        Lote loteLido = lido.getLotes().get(0);
        assertEquals(lote.getId(), loteLido.getId());
        assertEquals(LocalDate.of(2026, 11, 1), loteLido.getDataValidade());
        assertEquals(StatusLote.ATIVO, loteLido.getStatus());
    }

    @Test
    void produto_deveTerIndiceUnicoPorUsuarioECodigoDeBarras() {
        List<IndexDefinition> indices = indicesDe(Produto.class);

        assertTrue(indices.stream().anyMatch(i ->
                i.getIndexKeys().equals(new Document("usuarioId", 1).append("codigoBarras", 1))
                        && Boolean.TRUE.equals(i.getIndexOptions().get("unique"))));
        assertTrue(indices.stream().noneMatch(i ->
                i.getIndexKeys().equals(new Document("codigoBarras", 1))));
    }

    @Test
    void usuario_deveTerEmailUnico() {
        List<IndexDefinition> indices = indicesDe(Usuario.class);

        assertTrue(indices.stream().anyMatch(i ->
                i.getIndexKeys().equals(new Document("email", 1))
                        && Boolean.TRUE.equals(i.getIndexOptions().get("unique"))));
    }

    private List<IndexDefinition> indicesDe(Class<?> tipo) {
        Iterable<? extends IndexDefinition> indices = IndexResolver.create(contexto).resolveIndexFor(tipo);
        return StreamSupport.stream(indices.spliterator(), false).map(IndexDefinition.class::cast).toList();
    }
}
