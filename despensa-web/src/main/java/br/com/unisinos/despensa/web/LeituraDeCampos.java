package br.com.unisinos.despensa.web;

import br.com.unisinos.despensa.exception.ValidacaoException;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;

final class LeituraDeCampos {

    private LeituraDeCampos() {
    }

    static int quantidadeInteira(String texto) {
        int valor;
        try {
            valor = Integer.parseInt(texto == null ? "" : texto.trim());
        } catch (NumberFormatException e) {
            valor = 0;
        }
        if (valor <= 0) {
            throw new ValidacaoException("Informe a quantidade (um número inteiro maior que zero).");
        }
        return valor;
    }

    static double preco(String texto) {
        try {
            return new BigDecimal(texto == null ? "" : texto.trim().replace(',', '.')).doubleValue();
        } catch (NumberFormatException e) {
            throw new ValidacaoException("Informe o preço unitário (ex: 4,99).");
        }
    }

    static LocalDate data(String texto, String mensagemSeVazio) {
        if (texto == null || texto.isBlank()) {
            throw new ValidacaoException(mensagemSeVazio);
        }
        try {
            return LocalDate.parse(texto.trim());
        } catch (DateTimeParseException e) {
            throw new ValidacaoException("Data de validade inválida.");
        }
    }
}
