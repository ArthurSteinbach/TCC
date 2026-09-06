package br.com.unisinos.integracao;

/** Dados de um produto retornados por uma base pública, a partir do código de barras. */
public record InfoProdutoExterno(String nome, String categoria) {
}
