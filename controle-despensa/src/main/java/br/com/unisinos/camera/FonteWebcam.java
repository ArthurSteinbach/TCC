package br.com.unisinos.camera;

import com.github.sarxos.webcam.Webcam;

import java.awt.Dimension;
import java.awt.image.BufferedImage;

/** Fonte de frames vinda de uma webcam física conectada ao computador. */
public class FonteWebcam implements FonteDeFrames {

    private final Webcam webcam;

    public FonteWebcam(Webcam webcam) {
        this.webcam = webcam;
        if (!webcam.isOpen()) {
            webcam.setViewSize(new Dimension(640, 480));
            webcam.open();
        }
    }

    @Override
    public BufferedImage proximoFrame() {
        return webcam.getImage();
    }

    @Override
    public void fechar() {
        webcam.close();
    }
}
