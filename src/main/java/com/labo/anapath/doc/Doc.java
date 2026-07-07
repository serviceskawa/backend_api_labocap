package com.labo.anapath.doc;

import com.labo.anapath.common.audit.AuditableEntity;
import com.labo.anapath.role.Role;
import com.labo.anapath.user.User;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.SQLDelete;
import org.hibernate.annotations.SQLRestriction;

@Entity
@Table(name = "docs")
@SQLDelete(sql = "UPDATE docs SET deleted_at = NOW() WHERE id = ?")
@SQLRestriction("deleted_at IS NULL")
@Getter
@Setter
@NoArgsConstructor
public class Doc extends AuditableEntity {

    @Column(name = "title", nullable = false, length = 300)
    private String title;

    @Column(name = "attachment", length = 500)
    private String attachment;

    @Column(name = "is_current_version", nullable = false)
    private Boolean isCurrentVersion = true;

    @Column(name = "file_size")
    private Long fileSize;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "documentation_category_id")
    private DocumentationCategory documentationCategory;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id")
    private User user;

    /** Rôle avec lequel le document est partagé (réplique Laravel {@code docs.role_id}). */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "role_id")
    private Role role;
}
