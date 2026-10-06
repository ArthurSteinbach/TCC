package br.com.unisinos.camera;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.BufferedInputStream;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.ConnectException;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.SocketTimeoutException;
import java.net.URL;
import java.net.UnknownHostException;

/**
 * Fonte de frames vinda de um stream MJPEG via rede (ex: celular rodando
 * DroidCam). Lê os frames JPEG diretamente pelos bytes, procurando os
 * marcadores de início/fim de cada imagem, sem depender de bibliotecas
 * externas de captura de vídeo.
 */
public class FonteCelular implements FonteDeFrames {

    static final String MSG_ENDERECO_INVALIDO =
            "Endereço inválido. Use o formato http://IP:PORTA/video (ex: http://192.168.0.105:4747/video).";
    static final String MSG_HOST_DESCONHECIDO =
            "Endereço não encontrado na rede. Confira o IP digitado.";
    static final String MSG_SEM_RESPOSTA =
            "O celular não respondeu. Confira o IP e se o celular está na mesma rede Wi-Fi do computador.";
    static final String MSG_CONEXAO_RECUSADA =
            "O celular recusou a conexão. Confira se o app DroidCam está aberto, se a porta está correta "
                    + "e se o DroidCam Client do Windows está fechado.";
    static final String MSG_FALHA_CONEXAO =
            "Não foi possível conectar ao celular. Confira o endereço e a rede Wi-Fi.";
    static final String MSG_CAMINHO_INVALIDO =
            "O celular respondeu, mas esse endereço não é o vídeo da câmera. Confira o final do endereço (ex: /video).";
    static final String MSG_PAROU_DE_ENVIAR =
            "O celular parou de enviar imagens. Confira se o app DroidCam continua aberto.";
    static final String MSG_CONEXAO_ENCERRADA =
            "Conexão encerrada pelo celular.";
    static final String MSG_CONEXAO_INTERROMPIDA =
            "A conexão com o celular foi interrompida.";
    static final String MSG_IMAGEM_INVALIDA =
            "Não foi possível ler a imagem enviada pelo celular.";

    private final BufferedInputStream entrada;

    public FonteCelular(String urlStream) throws ErroCameraException {
        URL url;
        try {
            url = new URL(urlStream);
        } catch (MalformedURLException e) {
            throw new ErroCameraException(MSG_ENDERECO_INVALIDO);
        }
        if (!url.getProtocol().equalsIgnoreCase("http") || url.getHost().isEmpty()) {
            throw new ErroCameraException(MSG_ENDERECO_INVALIDO);
        }

        HttpURLConnection conexao;
        int codigoResposta;
        try {
            conexao = (HttpURLConnection) url.openConnection();
            conexao.setConnectTimeout(5000);
            conexao.setReadTimeout(5000);
            codigoResposta = conexao.getResponseCode();
        } catch (UnknownHostException e) {
            throw new ErroCameraException(MSG_HOST_DESCONHECIDO);
        } catch (SocketTimeoutException e) {
            throw new ErroCameraException(MSG_SEM_RESPOSTA);
        } catch (ConnectException e) {
            throw new ErroCameraException(MSG_CONEXAO_RECUSADA);
        } catch (IOException e) {
            throw new ErroCameraException(MSG_FALHA_CONEXAO);
        }

        if (codigoResposta != HttpURLConnection.HTTP_OK) {
            conexao.disconnect();
            throw new ErroCameraException(MSG_CAMINHO_INVALIDO);
        }

        try {
            this.entrada = new BufferedInputStream(conexao.getInputStream());
        } catch (IOException e) {
            conexao.disconnect();
            throw new ErroCameraException(MSG_FALHA_CONEXAO);
        }
    }

    @Override
    public BufferedImage proximoFrame() throws ErroCameraException {
        byte[] frameBytes;
        try {
            frameBytes = lerProximoFrameJpeg(entrada);
        } catch (SocketTimeoutException e) {
            throw new ErroCameraException(MSG_PAROU_DE_ENVIAR);
        } catch (IOException e) {
            throw new ErroCameraException(MSG_CONEXAO_INTERROMPIDA);
        }

        if (frameBytes == null) {
            throw new ErroCameraException(MSG_CONEXAO_ENCERRADA);
        }

        try {
            return ImageIO.read(new ByteArrayInputStream(frameBytes));
        } catch (IOException e) {
            throw new ErroCameraException(MSG_IMAGEM_INVALIDA);
        }
    }

    @Override
    public void fechar() {
        try {
            entrada.close();
        } catch (IOException e) {
            // Conexão já pode estar fechada pelo outro lado; sem problema.
        }
    }

    /**
     * Lê bytes do stream até completar um frame JPEG inteiro, procurando
     * os marcadores padrão de início (0xFFD8) e fim (0xFFD9). Ignora os
     * cabeçalhos de boundary do multipart entre um frame e outro.
     */
    private byte[] lerProximoFrameJpeg(InputStream entrada) throws IOException {
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        int anterior = -1;
        int atual;
        boolean dentroDoFrame = false;

        while ((atual = entrada.read()) != -1) {
            if (!dentroDoFrame) {
                if (anterior == 0xFF && atual == 0xD8) {
                    dentroDoFrame = true;
                    buffer.write(0xFF);
                    buffer.write(0xD8);
                }
            } else {
                buffer.write(atual);
                if (anterior == 0xFF && atual == 0xD9) {
                    return buffer.toByteArray();
                }
            }
            anterior = atual;
        }
        return null;
    }
}
