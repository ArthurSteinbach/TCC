package br.com.unisinos.despensa.config;

import br.com.unisinos.despensa.model.Produto;
import br.com.unisinos.despensa.model.Usuario;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBooleanProperty;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.dao.DataAccessException;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.index.IndexDefinition;
import org.springframework.data.mongodb.core.index.IndexOperations;
import org.springframework.data.mongodb.core.index.IndexResolver;
import org.springframework.data.mongodb.core.mapping.MongoMappingContext;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@ConditionalOnBooleanProperty(name = "despensa.indices.criar-ao-iniciar", matchIfMissing = true)
public class CriacaoDeIndices {

    private static final Logger LOG = LoggerFactory.getLogger(CriacaoDeIndices.class);
    private static final List<Class<?>> DOCUMENTOS = List.of(Produto.class, Usuario.class);

    private final MongoTemplate mongoTemplate;
    private final MongoMappingContext contextoDeMapeamento;

    public CriacaoDeIndices(MongoTemplate mongoTemplate, MongoMappingContext contextoDeMapeamento) {
        this.mongoTemplate = mongoTemplate;
        this.contextoDeMapeamento = contextoDeMapeamento;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void criarIndices() {
        IndexResolver resolvedor = IndexResolver.create(contextoDeMapeamento);
        for (Class<?> documento : DOCUMENTOS) {
            try {
                IndexOperations operacoes = mongoTemplate.indexOps(documento);
                for (IndexDefinition indice : resolvedor.resolveIndexFor(documento)) {
                    operacoes.createIndex(indice);
                }
            } catch (DataAccessException | IllegalStateException e) {
                LOG.warn("Não foi possível criar os índices de {}: {}", documento.getSimpleName(), e.getMessage());
            }
        }
    }
}
