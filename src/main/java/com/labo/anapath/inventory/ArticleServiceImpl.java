package com.labo.anapath.inventory;

import com.labo.anapath.common.dto.PageResponse;
import com.labo.anapath.common.exception.ResourceNotFoundException;
import com.labo.anapath.user.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ArticleServiceImpl implements ArticleService {

    private final ArticleRepository articleRepository;
    private final SupplierRepository supplierRepository;
    private final MovementRepository movementRepository;
    private final UserRepository userRepository;
    private final InventoryMapper inventoryMapper;

    @Override
    @Transactional(readOnly = true)
    public ArticlePageResponseDto findAll(int page, int size, UUID branchId) {
        PageResponse<ArticleResponseDto> pageResponse = PageResponse.of(
                articleRepository.findByBranchId(branchId, PageRequest.of(page, size))
                        .map(inventoryMapper::toArticleResponseDto));
        long outOfStockCount = articleRepository.countByBranchIdAndQuantity(branchId, BigDecimal.ZERO);
        long lowStockCount = articleRepository.countLowStock(branchId);
        return new ArticlePageResponseDto(pageResponse, outOfStockCount, lowStockCount);
    }

    @Override
    @Transactional(readOnly = true)
    public ArticleResponseDto findById(UUID id) {
        return inventoryMapper.toArticleResponseDto(
                articleRepository.findById(id)
                        .orElseThrow(() -> new ResourceNotFoundException("Article", id)));
    }

    @Override
    @Transactional
    public ArticleResponseDto create(ArticleRequestDto dto, UUID branchId, UUID userId) {
        Article article = inventoryMapper.toArticleEntity(dto);
        article.setBranchId(branchId);
        article.setDescription(dto.getDescription());
        article.setLotNumber(dto.getLotNumber());
        article.setExpirationDate(dto.getExpirationDate());
        article.setMinimumStock(
                dto.getMinimumStock() != null ? dto.getMinimumStock() : BigDecimal.ZERO);
        // Le formulaire Laravel n'expose pas le prix (`prix` y est commenté) alors
        // que la colonne est NOT NULL : on retombe sur 0, valeur de tout le parc.
        if (article.getPurchasePrice() == null) {
            article.setPurchasePrice(BigDecimal.ZERO);
        }
        if (dto.getSupplierId() != null) {
            article.setSupplier(supplierRepository.findById(dto.getSupplierId())
                    .orElseThrow(() -> new ResourceNotFoundException("Fournisseur", dto.getSupplierId())));
        }
        BigDecimal initialQty = dto.getInitialQuantity() != null ? dto.getInitialQuantity() : BigDecimal.ZERO;
        article.setQuantity(initialQty);
        Article saved = articleRepository.save(article);

        // Laravel trace le stock initial systématiquement, y compris à quantité 0.
        {
            Movement movement = new Movement();
            movement.setBranchId(branchId);
            movement.setArticle(saved);
            movement.setType(MovementType.IN);
            movement.setQuantity(initialQty);
            movement.setNotes("Stock initial");
            movement.setMovementDate(LocalDate.now());
            if (userId != null) {
                userRepository.findById(userId).ifPresent(movement::setUser);
            }
            movementRepository.save(movement);
        }
        return inventoryMapper.toArticleResponseDto(saved);
    }

    @Override
    @Transactional(readOnly = true)
    public List<ArticleResponseDto> search(String q, UUID branchId) {
        return articleRepository.findByBranchIdAndNameContainingIgnoreCase(branchId, q)
                .stream().map(inventoryMapper::toArticleResponseDto).toList();
    }

    @Override
    @Transactional
    public ArticleResponseDto update(UUID id, ArticleRequestDto dto) {
        Article article = articleRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Article", id));
        article.setName(dto.getName());
        // Champs absents du formulaire (Laravel ne les expose pas) : on ne les écrase
        // que si le client les fournit, sinon une simple édition les viderait.
        if (dto.getCode() != null) article.setCode(dto.getCode());
        if (dto.getDescription() != null) article.setDescription(dto.getDescription());
        if (dto.getUnit() != null) article.setUnit(dto.getUnit());
        if (dto.getLotNumber() != null) article.setLotNumber(dto.getLotNumber());
        if (dto.getExpirationDate() != null) article.setExpirationDate(dto.getExpirationDate());
        if (dto.getPurchasePrice() != null) article.setPurchasePrice(dto.getPurchasePrice());
        if (dto.getMinimumStock() != null) article.setMinimumStock(dto.getMinimumStock());
        if (dto.getSupplierId() != null) {
            article.setSupplier(supplierRepository.findById(dto.getSupplierId())
                    .orElseThrow(() -> new ResourceNotFoundException("Fournisseur", dto.getSupplierId())));
        } else {
            article.setSupplier(null);
        }
        return inventoryMapper.toArticleResponseDto(articleRepository.save(article));
    }

    @Override
    @Transactional
    public void delete(UUID id) {
        Article article = articleRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Article", id));
        articleRepository.delete(article);
    }

    @Override
    @Transactional(readOnly = true)
    public long countStockMinimumReached(UUID branchId) {
        return articleRepository.countStockMinimumReached(branchId);
    }
}
