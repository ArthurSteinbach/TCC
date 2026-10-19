package br.com.unisinos.despensa.web;

import br.com.unisinos.despensa.config.SegurancaConfig;
import br.com.unisinos.despensa.status.StatusBanco;
import br.com.unisinos.despensa.status.StatusBancoService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(InicioController.class)
@Import(SegurancaConfig.class)
class InicioControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private StatusBancoService statusBancoService;

    @Test
    void paginaInicial_comBancoConectado_deveMostrarConexaoOkEONomeDoBanco() throws Exception {
        when(statusBancoService.verificar()).thenReturn(StatusBanco.ok("despensa"));

        mockMvc.perform(get("/"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Conexão com o banco OK")))
                .andExpect(content().string(containsString("despensa")));
    }

    @Test
    void paginaInicial_comFalhaNoBanco_deveMostrarErroSemConexaoOk() throws Exception {
        when(statusBancoService.verificar())
                .thenReturn(StatusBanco.falha("despensa", "MongoTimeoutException"));

        mockMvc.perform(get("/"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Não foi possível conectar ao banco")))
                .andExpect(content().string(not(containsString("Conexão com o banco OK"))));
    }
}
