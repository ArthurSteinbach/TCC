package br.com.unisinos.despensa.config;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public final class DiagnosticoDeVariaveis {

    public static final List<String> VARIAVEIS_DE_EMAIL =
            List.of("BREVO_API_KEY", "EMAIL_REMETENTE", "EMAIL_REMETENTE_NOME", "ALERTAS_TOKEN");

    private DiagnosticoDeVariaveis() {
    }

    public static List<String> descrever(Map<String, String> ambiente, List<String> esperadas) {
        List<String> linhas = new ArrayList<>();
        for (String nome : esperadas) {
            String valor = ambiente.get(nome);
            if (valor == null) {
                linhas.add(nome + ": não encontrada no ambiente");
            } else if (valor.isBlank()) {
                linhas.add(nome + ": definida, mas vazia");
            } else {
                linhas.add(nome + ": definida (" + valor.trim().length() + " caracteres)");
            }
            for (String outro : ambiente.keySet()) {
                if (!outro.equals(nome) && normalizar(outro).equals(normalizar(nome))) {
                    linhas.add("  atenção: existe a variável \"" + outro + "\", com nome parecido; o nome precisa ser "
                            + "exatamente " + nome);
                }
            }
        }
        return linhas;
    }

    private static String normalizar(String nome) {
        return nome.trim().toUpperCase(Locale.ROOT).replaceAll("[^A-Z0-9]", "");
    }
}
