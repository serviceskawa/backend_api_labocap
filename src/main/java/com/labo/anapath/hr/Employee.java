package com.labo.anapath.hr;

import com.labo.anapath.common.audit.AuditableEntity;
import com.labo.anapath.user.User;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.SQLDelete;
import org.hibernate.annotations.SQLRestriction;

import java.math.BigDecimal;
import java.time.LocalDate;

@Entity
@Table(name = "employees")
@SQLDelete(sql = "UPDATE employees SET deleted_at = NOW() WHERE id = ?")
@SQLRestriction("deleted_at IS NULL")
@Getter
@Setter
@NoArgsConstructor
public class Employee extends AuditableEntity {

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id")
    private User user;

    @Column(name = "first_name", nullable = false, length = 100)
    private String firstName;

    @Column(name = "last_name", nullable = false, length = 100)
    private String lastName;

    @Column(name = "phone", length = 20)
    private String phone;

    @Column(name = "email", length = 100)
    private String email;

    @Column(name = "position", length = 200)
    private String position;

    @Column(name = "salary", nullable = false, precision = 10, scale = 2)
    private BigDecimal salary = BigDecimal.ZERO;

    @Column(name = "hire_date")
    private LocalDate hireDate;

    // ── Identité étendue (calque du profil employé Laravel) ─────────────────

    @Column(name = "address", length = 255)
    private String address;

    @Column(name = "date_of_birth")
    private LocalDate dateOfBirth;

    @Column(name = "place_of_birth", length = 200)
    private String placeOfBirth;

    @Column(name = "cnss_number", length = 100)
    private String cnssNumber;

    @Column(name = "photo_url", length = 500)
    private String photoUrl;

    @Column(name = "gender", length = 20)
    private String gender;

    @Column(name = "nationality", length = 100)
    private String nationality;

    @Column(name = "city", length = 100)
    private String city;
}
