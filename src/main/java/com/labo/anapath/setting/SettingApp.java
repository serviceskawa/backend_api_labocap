package com.labo.anapath.setting;

import com.labo.anapath.common.audit.AuditableEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.SQLDelete;
import org.hibernate.annotations.SQLRestriction;

@Entity
@Table(name = "setting_apps")
@SQLDelete(sql = "UPDATE setting_apps SET deleted_at = NOW() WHERE id = ?")
@SQLRestriction("deleted_at IS NULL")
@Getter
@Setter
@NoArgsConstructor
public class SettingApp extends AuditableEntity {

    /** Pied de page du compte rendu affiché par défaut lorsque le réglage report_footer est vide. */
    public static final String DEFAULT_REPORT_FOOTER =
            "Centre ADECHINA Anatomie Pathologique • Adresse : Carre 1915 \"G\" Fifadji, "
            + "072 BP 059 Cotonou, Bénin • Téléphone : (+229) 97761721 • WhatsApp: (+229)61191975 "
            + "• RCCM RB/COT/18 B22364 • IFU : 3201810410828 • contact@caap.bj "
            + "• Ouvert du Lundi au Vendredi de 08:00 - 17:00 • www.caap.bj";

    @Column(name = "key", nullable = false, length = 100)
    private String key;

    @Column(name = "value", columnDefinition = "TEXT")
    private String value;

    @Column(name = "label", length = 200)
    private String label;
}
