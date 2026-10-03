package com.labo.anapath.common.audit;

import com.labo.anapath.common.NomComplet;
import com.labo.anapath.common.dto.ApiResponse;
import com.labo.anapath.common.dto.PageResponse;
import com.labo.anapath.user.User;
import com.labo.anapath.user.UserRepository;
import jakarta.persistence.criteria.Predicate;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/** Lecture seule du journal des consultations (lot 6). Aucune route de modification. */
@RestController
@RequestMapping("/api/v1/audit")
@RequiredArgsConstructor
public class JournalAccesController {

    private final JournalAccesRepository repository;
    private final UserRepository userRepository;

    public record Ligne(UUID id, LocalDateTime at, UUID userId, String utilisateur, UUID branchId,
                        String action, String entityType, String entityId, String ip) {}

    @GetMapping("/acces")
    @PreAuthorize("hasAuthority('view-audit')")
    public ResponseEntity<ApiResponse<PageResponse<Ligne>>> acces(
            @RequestParam(required = false) JournalAcces.Entite entityType,
            @RequestParam(required = false) String entityId,
            @RequestParam(required = false) UUID userId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime to,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size) {
        // Specification plutôt qu'un « (:x IS NULL OR col = :x) » : PostgreSQL
        // refuse de typer un paramètre qu'il ne voit que dans un test de nullité.
        Specification<JournalAcces> filtre = (racine, requete, cb) -> {
            List<Predicate> p = new ArrayList<>();
            if (entityType != null) p.add(cb.equal(racine.get("entityType"), entityType));
            if (entityId != null && !entityId.isBlank()) p.add(cb.equal(racine.get("entityId"), entityId.trim()));
            if (userId != null) p.add(cb.equal(racine.get("userId"), userId));
            if (from != null) p.add(cb.greaterThanOrEqualTo(racine.get("at"), from));
            if (to != null) p.add(cb.lessThan(racine.get("at"), to));
            return cb.and(p.toArray(Predicate[]::new));
        };
        var resultat = repository.findAll(filtre,
                PageRequest.of(page, Math.min(Math.max(size, 1), 500), Sort.by(Sort.Direction.DESC, "at")));

        Map<UUID, String> noms = userRepository.findAllById(
                        resultat.getContent().stream().map(JournalAcces::getUserId).collect(Collectors.toSet()))
                .stream().collect(Collectors.toMap(User::getId, u -> NomComplet.de(u.getLastname(), u.getFirstname())));
        Function<JournalAcces, Ligne> versLigne = j -> new Ligne(j.getId(), j.getAt(), j.getUserId(),
                noms.getOrDefault(j.getUserId(), "Utilisateur supprimé"), j.getBranchId(),
                j.getAction().name(), j.getEntityType().name(), j.getEntityId(), j.getIp());
        return ResponseEntity.ok(ApiResponse.success(PageResponse.of(resultat.map(versLigne))));
    }
}
