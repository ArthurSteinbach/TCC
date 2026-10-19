package br.com.unisinos.despensa;

import br.com.unisinos.despensa.util.Constantes;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

import java.util.TimeZone;

@SpringBootApplication
public class DespensaWebApplication {

    public static void main(String[] args) {
        TimeZone.setDefault(TimeZone.getTimeZone(Constantes.FUSO_HORARIO));
        SpringApplication.run(DespensaWebApplication.class, args);
    }
}
