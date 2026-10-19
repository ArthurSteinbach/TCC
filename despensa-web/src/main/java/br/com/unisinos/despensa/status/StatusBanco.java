package br.com.unisinos.despensa.status;

public record StatusBanco(boolean conectado, String nomeBanco, String erro) {

    public static StatusBanco ok(String nomeBanco) {
        return new StatusBanco(true, nomeBanco, null);
    }

    public static StatusBanco falha(String nomeBanco, String erro) {
        return new StatusBanco(false, nomeBanco, erro);
    }
}
