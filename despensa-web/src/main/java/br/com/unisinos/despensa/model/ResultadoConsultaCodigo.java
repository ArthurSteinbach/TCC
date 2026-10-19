package br.com.unisinos.despensa.model;

public record ResultadoConsultaCodigo(Situacao situacao, String mensagem, String nome, String categoria) {

    public enum Situacao {
        CODIGO_VAZIO,
        EXISTENTE,
        ENCONTRADO,
        NAO_ENCONTRADO,
        FALHA,
        SEM_CONSULTA
    }

    public static ResultadoConsultaCodigo codigoVazio() {
        return new ResultadoConsultaCodigo(Situacao.CODIGO_VAZIO, "Informe o código de barras.", null, null);
    }

    public static ResultadoConsultaCodigo existente(Produto produto) {
        return new ResultadoConsultaCodigo(Situacao.EXISTENTE,
                "Este produto já está cadastrado. Agora informe os dados do lote.",
                produto.getNome(), produto.getCategoria());
    }

    public static ResultadoConsultaCodigo encontrado(String nome, String categoria) {
        return new ResultadoConsultaCodigo(Situacao.ENCONTRADO,
                "Encontramos este produto na Open Food Facts. Confira o nome e a categoria.", nome, categoria);
    }

    public static ResultadoConsultaCodigo naoEncontrado() {
        return new ResultadoConsultaCodigo(Situacao.NAO_ENCONTRADO,
                "Produto não encontrado na Open Food Facts. Preencha o nome e a categoria.", null, null);
    }

    public static ResultadoConsultaCodigo falha() {
        return new ResultadoConsultaCodigo(Situacao.FALHA,
                "Não foi possível consultar a Open Food Facts agora (sem internet ou serviço fora do ar). "
                        + "Preencha o nome e a categoria.", null, null);
    }

    public static ResultadoConsultaCodigo semConsulta() {
        return new ResultadoConsultaCodigo(Situacao.SEM_CONSULTA,
                "Este código não segue o padrão dos códigos de barras de produtos. Preencha o nome e a categoria.",
                null, null);
    }
}
