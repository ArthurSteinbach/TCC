package br.com.unisinos.despensa.web;

import java.util.Map;

final class Destinos {

    private static final Map<String, String> PAGINAS = Map.of(
            "despensa", "/",
            "alertas", "/alertas");

    private Destinos() {
    }

    static String origemValida(String origem) {
        return origem != null && PAGINAS.containsKey(origem) ? origem : "despensa";
    }

    static String caminho(String origem) {
        return PAGINAS.get(origemValida(origem));
    }

    static String redirecionar(String origem) {
        return "redirect:" + caminho(origem);
    }
}
