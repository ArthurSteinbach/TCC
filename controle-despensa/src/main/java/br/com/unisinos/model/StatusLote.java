package br.com.unisinos.model;

/**
 * Representa o estado de um lote de produto na despensa.
 * ATIVO: ainda disponível para consumo.
 * CONSUMIDO: o usuário deu baixa porque usou o item.
 * DESCARTADO: o usuário deu baixa porque o item venceu e foi jogado fora
 *             (é este status que alimenta o relatório de perdas financeiras).
 */
public enum StatusLote {
    ATIVO,
    CONSUMIDO,
    DESCARTADO
}
