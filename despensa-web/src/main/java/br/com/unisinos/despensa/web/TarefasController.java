package br.com.unisinos.despensa.web;

import br.com.unisinos.despensa.email.EnviadorDeEmail;
import br.com.unisinos.despensa.model.ResultadoEnvioAlertas;
import br.com.unisinos.despensa.service.AlertaVencimentoService;
import br.com.unisinos.despensa.util.Tokens;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;
import java.util.Optional;

@RestController
public class TarefasController {

    private static final Logger LOG = LoggerFactory.getLogger(TarefasController.class);

    private final AlertaVencimentoService alertaVencimentoService;
    private final EnviadorDeEmail enviador;
    private final String tokenEsperado;

    public TarefasController(AlertaVencimentoService alertaVencimentoService, EnviadorDeEmail enviador,
                             @Value("${despensa.alertas.token:}") String tokenEsperado) {
        this.alertaVencimentoService = alertaVencimentoService;
        this.enviador = enviador;
        this.tokenEsperado = tokenEsperado;
        if (tokenEsperado.isBlank()) {
            LOG.warn("ALERTAS_TOKEN não definido: o endpoint de avisos por e-mail vai recusar todas as chamadas.");
        }
    }

    @PostMapping("/tarefas/enviar-alertas")
    public ResponseEntity<?> enviarAlertas(@RequestHeader(value = "X-Alertas-Token", required = false) String token) {
        if (!Tokens.iguais(tokenEsperado, token)) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).cacheControl(CacheControl.noStore()).build();
        }
        if (!enviador.ativo()) {
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).cacheControl(CacheControl.noStore())
                    .body(Map.of("envioAtivo", false));
        }
        Optional<ResultadoEnvioAlertas> resultado = alertaVencimentoService.executar();
        if (resultado.isEmpty()) {
            return ResponseEntity.status(HttpStatus.CONFLICT).cacheControl(CacheControl.noStore())
                    .body(Map.of("emExecucao", true));
        }
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(resultado.get());
    }
}
