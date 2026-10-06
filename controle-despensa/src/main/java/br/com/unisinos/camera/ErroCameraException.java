package br.com.unisinos.camera;

import java.io.IOException;

public class ErroCameraException extends IOException {

    public ErroCameraException(String mensagem) {
        super(mensagem);
    }
}
