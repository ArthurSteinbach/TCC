package br.com.unisinos.despensa.web;

import br.com.unisinos.despensa.model.Usuario;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.ResultActions;

import static org.hamcrest.Matchers.containsString;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class CadastroUsuarioWebTest extends TesteWebBase {

    private static final String SENHA = "senha-segura-123";

    @Autowired
    private PasswordEncoder codificador;

    @Test
    void cadastroValido_deveSalvarComSenhaEmHashEIrParaOLogin() throws Exception {
        cadastrar("Carla", "Carla@Exemplo.com", SENHA, SENHA)
                .andExpect(redirectedUrl("/login?cadastrado"));

        Usuario salvo = usuarios().findByEmail("carla@exemplo.com").orElseThrow();
        assertNotEquals(SENHA, salvo.getSenhaHash());
        assertTrue(codificador.matches(SENHA, salvo.getSenhaHash()));
        assertTrue(salvo.recebeAvisos());
    }

    @Test
    void cadastroComEmailRepetido_deveMostrarMensagemENaoSalvar() throws Exception {
        int antes = usuarios().quantidade();

        cadastrar("Ana", "ana@exemplo.com", SENHA, SENHA)
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Já existe uma conta com este e-mail.")))
                .andExpect(content().string(containsString("value=\"ana@exemplo.com\"")));

        assertEquals(antes, usuarios().quantidade());
    }

    @Test
    void cadastroComSenhaCurta_deveMostrarMensagemENaoSalvar() throws Exception {
        cadastrar("Carla", "carla@exemplo.com", "curta12", "curta12")
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("A senha deve ter pelo menos 8 caracteres.")))
                .andExpect(content().string(containsString("value=\"Carla\"")));

        assertFalse(usuarios().existsByEmail("carla@exemplo.com"));
    }

    @Test
    void cadastroComConfirmacaoDiferente_deveMostrarMensagemENaoSalvar() throws Exception {
        cadastrar("Carla", "carla@exemplo.com", SENHA, "outra-senha-123")
                .andExpect(content().string(containsString("A confirmação da senha não confere.")));

        assertFalse(usuarios().existsByEmail("carla@exemplo.com"));
    }

    private ResultActions cadastrar(String nome, String email, String senha, String confirmacao) throws Exception {
        return mockMvc.perform(post("/usuarios/novo").with(csrf())
                .param("nome", nome)
                .param("email", email)
                .param("senha", senha)
                .param("confirmacaoSenha", confirmacao));
    }
}
