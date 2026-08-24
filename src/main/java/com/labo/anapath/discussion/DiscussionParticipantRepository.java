package com.labo.anapath.discussion;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface DiscussionParticipantRepository
        extends JpaRepository<DiscussionParticipant, UUID> {

    Optional<DiscussionParticipant> findByDiscussionIdAndUserId(UUID discussionId, UUID userId);
}
