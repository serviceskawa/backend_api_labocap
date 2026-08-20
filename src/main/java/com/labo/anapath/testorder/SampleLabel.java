package com.labo.anapath.testorder;

import com.labo.anapath.common.audit.AuditableEntity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.UUID;

/**
 * Une étiquette de prélèvement employée par le laboratoire — « L1 », « L2 »…
 *
 * <p>Le catalogue est alimenté par l'usage : chaque étiquette saisie sur une
 * affectation y entre, et l'application la propose ensuite sur les suivantes.
 * Une liste figée dans le code n'aurait convenu à personne, chaque laboratoire
 * ayant ses habitudes de marquage.</p>
 *
 * <p>Cloisonné par branche, comme le reste : deux sites n'étiquettent pas
 * forcément de la même façon.</p>
 */
@Entity
@Table(name = "sample_labels")
@Getter
@Setter
@NoArgsConstructor
public class SampleLabel extends AuditableEntity {

    @Column(name = "value", nullable = false, length = 40)
    private String value;

    public SampleLabel(UUID branchId, String value) {
        setBranchId(branchId);
        this.value = value;
    }
}
