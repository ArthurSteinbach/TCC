package br.com.unisinos.exception;

/**
 * Lançada quando uma regra de negócio é violada (ex: cadastrar um lote
 * já vencido, ou um produto sem nome). Separada de SQLException porque
 * representa um erro de validação, não uma falha técnica de banco.
 */
public class ValidacaoException extends RuntimeException {

    public ValidacaoException(String mensagem) {
        super(mensagem);
    }
}
