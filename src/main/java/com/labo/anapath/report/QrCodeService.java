package com.labo.anapath.report;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.EncodeHintType;
import com.google.zxing.WriterException;
import com.google.zxing.client.j2se.MatrixToImageWriter;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.QRCodeWriter;
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.EnumMap;
import java.util.Map;

import java.util.Base64;

@Service
public class QrCodeService {

    public String generateBase64(String content, int size) throws WriterException, IOException {
        return generateBase64(content, size, null);
    }

    /**
     * Génère un QR code PNG en data URI.
     *
     * @param level niveau de correction d'erreur, ou null pour le défaut de ZXing (L).
     *              Le QR de la facture normalisée exige {@link ErrorCorrectionLevel#H},
     *              comme la génération côté client de Laravel (viewjs/invoice/print.js).
     */
    public String generateBase64(String content, int size, ErrorCorrectionLevel level)
            throws WriterException, IOException {
        return generateBase64(content, size, size, level);
    }

    /**
     * Variante à dimensions libres, pour rendre le symbole aux proportions attendues
     * plutôt que de l'étirer à l'affichage.
     */
    public String generateBase64(String content, int width, int height, ErrorCorrectionLevel level)
            throws WriterException, IOException {
        QRCodeWriter writer = new QRCodeWriter();
        BitMatrix matrix;
        if (level == null) {
            matrix = writer.encode(content, BarcodeFormat.QR_CODE, width, height);
        } else {
            Map<EncodeHintType, Object> hints = new EnumMap<>(EncodeHintType.class);
            hints.put(EncodeHintType.ERROR_CORRECTION, level);
            matrix = writer.encode(content, BarcodeFormat.QR_CODE, width, height, hints);
        }
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        MatrixToImageWriter.writeToStream(matrix, "PNG", outputStream);
        return "data:image/png;base64," + Base64.getEncoder().encodeToString(outputStream.toByteArray());
    }
}
