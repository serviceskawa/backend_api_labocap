package com.labo.anapath.report;

import com.google.zxing.WriterException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class QrCodeServiceTest {

    private final QrCodeService service = new QrCodeService();

    @Test
    @DisplayName("generateBase64 - retourne une chaîne Base64 PNG valide")
    void generateBase64_returnsValidBase64Png() throws WriterException, IOException {
        String result = service.generateBase64("EX26-0001", 200);

        assertThat(result).startsWith("data:image/png;base64,");
        assertThat(result.length()).isGreaterThan(100);
    }

    @Test
    @DisplayName("generateBase64 - petite taille → résultat valide")
    void generateBase64_smallSize_returnsResult() throws WriterException, IOException {
        String result = service.generateBase64("ABCD", 50);

        assertThat(result).startsWith("data:image/png;base64,");
    }

    @Test
    @DisplayName("generateBase64 - contenu vide → refusé par ZXing (IllegalArgumentException)")
    void generateBase64_emptyContent_throws() {
        // ZXing refuse un contenu vide. L'appelant (PdfReportServiceImpl) n'appelle le
        // service qu'avec un code de demande et absorbe l'exception : pas de garde ici.
        assertThatThrownBy(() -> service.generateBase64("", 100))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("generateBase64 - contenu long → résultat valide")
    void generateBase64_longContent_returnsResult() throws WriterException, IOException {
        String content = "https://example.com/report/verify?code=EX26-0001&branch=12345678-1234";
        String result = service.generateBase64(content, 300);

        assertThat(result).startsWith("data:image/png;base64,");
    }
}
