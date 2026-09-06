package br.com.unisinos.camera;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.BinaryBitmap;
import com.google.zxing.DecodeHintType;
import com.google.zxing.MultiFormatReader;
import com.google.zxing.NotFoundException;
import com.google.zxing.Result;
import com.google.zxing.client.j2se.BufferedImageLuminanceSource;
import com.google.zxing.common.HybridBinarizer;

import java.awt.image.BufferedImage;
import java.util.Collections;
import java.util.EnumMap;
import java.util.Map;
import java.util.Optional;

/**
 * Encapsula a leitura de códigos EAN-13 via ZXing. Restringe o leitor a
 * esse formato (evitando leituras falsas de QR codes e outros símbolos)
 * e passa os hints explicitamente a cada chamada, contornando o
 * comportamento do MultiFormatReader.decode(bitmap) de resetar os hints
 * internamente quando chamado sem esse parâmetro.
 */
public class LeitorEan13 {

    private final MultiFormatReader leitor = new MultiFormatReader();
    private final Map<DecodeHintType, Object> hints;

    public LeitorEan13() {
        hints = new EnumMap<>(DecodeHintType.class);
        hints.put(DecodeHintType.POSSIBLE_FORMATS, Collections.singletonList(BarcodeFormat.EAN_13));
        leitor.setHints(hints);
    }

    /** Tenta ler um código EAN-13 na imagem. Vazio se nenhum for encontrado. */
    public Optional<String> tentarLer(BufferedImage imagem) {
        try {
            BinaryBitmap bitmap = new BinaryBitmap(new HybridBinarizer(
                    new BufferedImageLuminanceSource(imagem)));
            Result resultado = leitor.decode(bitmap, hints);
            return Optional.of(resultado.getText());
        } catch (NotFoundException e) {
            return Optional.empty();
        }
    }
}
