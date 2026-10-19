package br.com.unisinos.despensa;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest(properties = "spring.mongodb.uri=mongodb://localhost:27017")
class DespensaWebApplicationTests {

    @Test
    void contextLoads() {
    }
}
