package br.com.unisinos.despensa.util;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TokensTest {

    @Test
    void comparacao_soDeveAceitarOTokenIgual() {
        assertTrue(Tokens.iguais("segredo-do-endpoint", "segredo-do-endpoint"));
        assertFalse(Tokens.iguais("segredo-do-endpoint", "segredo-do-endpoinT"));
        assertFalse(Tokens.iguais("segredo-do-endpoint", "segredo"));
        assertFalse(Tokens.iguais("segredo-do-endpoint", null));
    }

    @Test
    void semTokenConfigurado_nenhumTokenDeveSerAceito() {
        assertFalse(Tokens.iguais("", ""));
        assertFalse(Tokens.iguais(null, null));
        assertFalse(Tokens.iguais("   ", "   "));
    }

    @Test
    void tokensNovos_devemSerDiferentesESeguroParaUrl() {
        String primeiro = Tokens.novo();
        String segundo = Tokens.novo();

        assertNotEquals(primeiro, segundo);
        assertTrue(primeiro.matches("[A-Za-z0-9_-]{43}"));
        assertEquals(64, Tokens.hash(primeiro).length());
        assertNotEquals(primeiro, Tokens.hash(primeiro));
    }
}
