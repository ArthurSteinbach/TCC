package br.com.unisinos.despensa;

import org.junit.jupiter.api.Test;
import org.springframework.boot.env.PropertiesPropertySourceLoader;
import org.springframework.core.env.MapPropertySource;
import org.springframework.core.env.MutablePropertySources;
import org.springframework.core.env.PropertySource;
import org.springframework.core.env.StandardEnvironment;
import org.springframework.core.io.ClassPathResource;

import java.io.IOException;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ConfiguracaoDePortaTest {

    @Test
    void semAVariavelPort_deveUsarAPorta8080() throws IOException {
        assertEquals("8080", ambienteCom(Map.of()).getProperty("server.port"));
    }

    @Test
    void comAVariavelPortDoRender_deveUsarAPortaInformada() throws IOException {
        assertEquals("10000", ambienteCom(Map.of("PORT", "10000")).getProperty("server.port"));
    }

    @Test
    void connectionStringDeveVirSoDaVariavelMongodbUri() throws IOException {
        StandardEnvironment ambiente = ambienteCom(Map.of("MONGODB_URI", "mongodb://exemplo:27017"));

        assertEquals("mongodb://exemplo:27017", ambiente.getProperty("spring.mongodb.uri"));
    }

    private static StandardEnvironment ambienteCom(Map<String, Object> variaveis) throws IOException {
        StandardEnvironment ambiente = new StandardEnvironment();
        MutablePropertySources fontes = ambiente.getPropertySources();
        fontes.remove(StandardEnvironment.SYSTEM_ENVIRONMENT_PROPERTY_SOURCE_NAME);
        fontes.remove(StandardEnvironment.SYSTEM_PROPERTIES_PROPERTY_SOURCE_NAME);
        fontes.addFirst(new MapPropertySource("variaveis", variaveis));
        for (PropertySource<?> fonte : new PropertiesPropertySourceLoader()
                .load("application", new ClassPathResource("application.properties"))) {
            fontes.addLast(fonte);
        }
        return ambiente;
    }
}
