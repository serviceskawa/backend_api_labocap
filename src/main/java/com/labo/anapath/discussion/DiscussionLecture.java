package com.labo.anapath.discussion;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.Objects;
import java.util.UUID;

/**
 * Qui a lu quel message.
 *
 * <p>Une ligne par lecture plutôt qu'un drapeau sur le message : un fil a
 * plusieurs participants, et « lu » n'a de sens que pour quelqu'un. Un drapeau
 * unique ferait disparaître le badge de tout le monde dès que le premier
 * ouvre.</p>
 */
@Entity
@Table(name = "discussion_lectures")
@IdClass(DiscussionLecture.Clef.class)
@Getter
@Setter
@NoArgsConstructor
public class DiscussionLecture {

    @Id
    @Column(name = "message_id", nullable = false)
    private UUID messageId;

    @Id
    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "lu_le", nullable = false)
    private LocalDateTime luLe = LocalDateTime.now();

    public DiscussionLecture(UUID messageId, UUID userId) {
        this.messageId = messageId;
        this.userId = userId;
    }

    /** Clef composite : un message, une personne. */
    @Getter
    @Setter
    @NoArgsConstructor
    public static class Clef implements Serializable {
        private UUID messageId;
        private UUID userId;

        @Override
        public boolean equals(Object autre) {
            if (this == autre) return true;
            if (!(autre instanceof Clef c)) return false;
            return Objects.equals(messageId, c.messageId)
                    && Objects.equals(userId, c.userId);
        }

        @Override
        public int hashCode() {
            return Objects.hash(messageId, userId);
        }
    }
}
