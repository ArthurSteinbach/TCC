package br.com.unisinos.camera;

import java.awt.image.BufferedImage;

/**
 * Abstrai de onde vêm os frames de imagem para leitura de código de
 * barras, permitindo tratar webcam física e câmera de celular (via rede)
 * de forma intercambiável no restante do código.
 */
public interface FonteDeFrames {

    /** Retorna o próximo frame disponível, ou null se nenhum estiver pronto ainda. */
    BufferedImage proximoFrame() throws Exception;

    /** Libera os recursos da fonte (fecha a webcam ou a conexão de rede). */
    void fechar();
}
