package br.com.unisinos.despensa;

import br.com.unisinos.despensa.util.Constantes;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.time.Clock;

import static org.junit.jupiter.api.Assertions.assertEquals;

@SpringBootTest(properties = {
        "spring.mongodb.uri=mongodb://localhost:27017",
        "despensa.indices.criar-ao-iniciar=false"
})
class DespensaWebApplicationTests {

    @Autowired
    private Clock relogio;

    @Test
    void contextLoads() {
    }

    @Test
    void relogio_deveUsarOHorarioDeBrasilia() {
        assertEquals(Constantes.FUSO_HORARIO, relogio.getZone());
        assertEquals("America/Sao_Paulo", relogio.getZone().getId());
    }
}
