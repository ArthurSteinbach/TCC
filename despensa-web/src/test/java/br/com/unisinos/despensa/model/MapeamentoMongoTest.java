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

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Date;
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
    void documentoAntigoComAsMarcasDeAvisoNosLotes_deveSerLidoSemErroEGravadoSemElas() {
        String loteId = new ObjectId().toHexString();
        Document loteAntigo = new Document("_id", loteId)
                .append("quantidade", 2.0)
                .append("precoUnitario", 4.5)
                .append("dataValidade", Date.from(Instant.parse("2026-10-25T03:00:00Z")))
                .append("status", "ATIVO")
                .append("avisoVencendoEm", Date.from(Instant.parse("2026-10-20T11:00:00Z")))
                .append("avisoVencidoEm", Date.from(Instant.parse("2026-10-26T11:00:00Z")));
        Document antigo = new Document("_id", new ObjectId())
                .append("versao", 7L)
                .append("usuarioId", "usuario-a")
                .append("codigoBarras", "7891000100103")
                .append("nome", "Produto inventado")
                .append("lotes", List.of(loteAntigo));

        Produto lido = conversor.read(Produto.class, antigo);

        assertEquals("Produto inventado", lido.getNome());
        assertEquals(1, lido.getLotes().size());
        assertEquals(loteId, lido.getLotes().get(0).getId());
        assertEquals(StatusLote.ATIVO, lido.getLotes().get(0).getStatus());

        Document regravado = new Document();
        conversor.write(lido, regravado);
        Document loteRegravado = (Document) ((List<?>) regravado.get("lotes")).get(0);
        assertTrue(!loteRegravado.containsKey("avisoVencendoEm") && !loteRegravado.containsKey("avisoVencidoEm"));
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
