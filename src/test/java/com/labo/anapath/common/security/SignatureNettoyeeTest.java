package com.labo.anapath.common.security;

import com.labo.anapath.common.exception.BusinessException;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SignatureNettoyeeTest {

    @Test
    void unSvgPiegeRessortSansScriptNiGestionnaire() {
        String propre = SignatureNettoyee.nettoyer(
                "<svg xmlns=\"http://www.w3.org/2000/svg\" onload=\"alert(1)\" viewBox=\"0 0 10 10\">"
                        + "<script>alert(2)</script><foreignObject><body/></foreignObject>"
                        + "<a href=\"javascript:alert(3)\"><path d=\"M0 0L5 5\" stroke=\"#000\"/></a></svg>");
        assertThat(propre).contains("<svg").contains("<path").contains("d=\"M0 0L5 5\"")
                .doesNotContain("onload").doesNotContain("script").doesNotContain("foreignObject")
                .doesNotContain("javascript").doesNotContain("<a");
    }

    @Test
    void uneImageBase64PasseTelleQuelleEtLeResteEstRefuse() {
        String png = "data:image/png;base64,iVBORw0KGgo=";
        assertThat(SignatureNettoyee.nettoyer(png)).isEqualTo(png);
        assertThat(SignatureNettoyee.nettoyer("  ")).isNull();
        assertThat(SignatureNettoyee.nettoyer("Admin_Admin.png")).isEqualTo("Admin_Admin.png");
        assertThatThrownBy(() -> SignatureNettoyee.nettoyer("../../etc/passwd.png"))
                .isInstanceOf(BusinessException.class);
        assertThatThrownBy(() -> SignatureNettoyee.nettoyer("<img src=x onerror=alert(1)>"))
                .isInstanceOf(BusinessException.class);
        assertThatThrownBy(() -> SignatureNettoyee.nettoyer("data:text/html;base64,PHNjcmlwdD4="))
                .isInstanceOf(BusinessException.class);
    }
}
