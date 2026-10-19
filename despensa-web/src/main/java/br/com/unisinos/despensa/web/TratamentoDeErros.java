package br.com.unisinos.despensa.web;

import com.mongodb.MongoException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.servlet.ModelAndView;

@ControllerAdvice
public class TratamentoDeErros {

    private static final Logger LOG = LoggerFactory.getLogger(TratamentoDeErros.class);

    @ExceptionHandler(OptimisticLockingFailureException.class)
    public ModelAndView alteracaoConcorrente() {
        return paginaDeErro(HttpStatus.CONFLICT, "Item alterado em outra tela",
                DespensaController.MENSAGEM_ALTERACAO_CONCORRENTE + ".");
    }

    @ExceptionHandler({DataAccessException.class, MongoException.class})
    public ModelAndView bancoIndisponivel(RuntimeException e) {
        LOG.warn("Falha ao acessar o banco de dados: {}", e.getMessage());
        return paginaDeErro(HttpStatus.SERVICE_UNAVAILABLE, "Banco de dados indisponível",
                "Não foi possível acessar o banco de dados agora. Tente novamente em alguns instantes.");
    }

    private static ModelAndView paginaDeErro(HttpStatus status, String titulo, String mensagem) {
        ModelAndView pagina = new ModelAndView("erro");
        pagina.setStatus(status);
        pagina.addObject("titulo", titulo);
        pagina.addObject("mensagem", mensagem);
        return pagina;
    }
}
