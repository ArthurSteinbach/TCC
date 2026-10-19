package br.com.unisinos.despensa.model;

public record RegistroCompra(Produto produto, int lotesCadastrados, boolean produtoNovo) {
}
