package br.com.unisinos.despensa.status;

import org.bson.Document;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.stereotype.Service;

@Service
public class StatusBancoService {

    private final MongoTemplate mongoTemplate;

    public StatusBancoService(MongoTemplate mongoTemplate) {
        this.mongoTemplate = mongoTemplate;
    }

    public StatusBanco verificar() {
        String nomeBanco = mongoTemplate.getDb().getName();
        try {
            mongoTemplate.executeCommand(new Document("ping", 1));
            return StatusBanco.ok(nomeBanco);
        } catch (RuntimeException e) {
            return StatusBanco.falha(nomeBanco, e.getClass().getSimpleName());
        }
    }
}
