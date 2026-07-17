package com.labo.anapath.inventory;

import com.labo.anapath.common.dto.PageResponse;
import com.labo.anapath.common.exception.BusinessException;
import com.labo.anapath.common.exception.ResourceNotFoundException;
import com.labo.anapath.user.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class MovementServiceImpl implements MovementService {

    private final MovementRepository movementRepository;
    private final ArticleRepository articleRepository;
    private final UserRepository userRepository;
    private final InventoryMapper inventoryMapper;

    @Override
    @Transactional(readOnly = true)
    public PageResponse<MovementResponseDto> findAll(int page, int size, UUID branchId) {
        return PageResponse.of(movementRepository.findByBranchId(branchId,
                PageRequest.of(page, size, Sort.by("createdAt").descending()))
                .map(inventoryMapper::toMovementResponseDto));
    }

    @Override
    @Transactional(readOnly = true)
    public PageResponse<MovementResponseDto> findAll(int page, int size, UUID branchId, UUID articleId) {
        if (articleId == null) return findAll(page, size, branchId);
        return PageResponse.of(movementRepository.findByBranchIdAndArticleId(branchId, articleId,
                PageRequest.of(page, size, Sort.by("createdAt").descending()))
                .map(inventoryMapper::toMovementResponseDto));
    }

    @Override
    @Transactional
    public MovementResponseDto create(MovementRequestDto dto, UUID branchId, UUID userId) {
        Article article = articleRepository.findById(dto.getArticleId())
                .orElseThrow(() -> new ResourceNotFoundException("Article", dto.getArticleId()));

        Movement movement = new Movement();
        movement.setBranchId(branchId);
        movement.setArticle(article);
        movement.setType(dto.getType());
        movement.setQuantity(dto.getQuantity());
        movement.setNotes(dto.getNotes());
        movement.setMovementDate(dto.getMovementDate() != null ? dto.getMovementDate() : LocalDate.now());

        if (userId != null) {
            userRepository.findById(userId).ifPresent(movement::setUser);
        }

        if (dto.getType() == MovementType.IN) {
            article.setQuantity(article.getQuantity().add(dto.getQuantity()));
        } else if (dto.getType() == MovementType.OUT) {
            BigDecimal newQty = article.getQuantity().subtract(dto.getQuantity());
            if (newQty.compareTo(BigDecimal.ZERO) < 0) {
                throw new BusinessException(
                        "Échec de l'enregistrement, la quantite en stock est inferieur a la quantite a diminuer ! ");
            }
            article.setQuantity(newQty);
        } else if (dto.getType() == MovementType.ADJUSTMENT) {
            article.setQuantity(dto.getQuantity());
        }

        articleRepository.save(article);
        Movement saved = movementRepository.save(movement);
        log.info("Mouvement créé: {} - {} - {}", dto.getType(), article.getName(), dto.getQuantity());
        return inventoryMapper.toMovementResponseDto(saved);
    }
}
