package com.labo.anapath.inventory;

import com.labo.anapath.common.exception.ResourceNotFoundException;
import com.labo.anapath.user.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ArticleServiceTest {

    @Mock ArticleRepository articleRepository;
    @Mock SupplierRepository supplierRepository;
    @Mock MovementRepository movementRepository;
    @Mock UserRepository userRepository;
    @Mock InventoryMapper inventoryMapper;

    ArticleServiceImpl service;

    private final UUID BRANCH_ID  = UUID.randomUUID();
    private final UUID ARTICLE_ID = UUID.randomUUID();

    @BeforeEach
    void setup() {
        service = new ArticleServiceImpl(articleRepository, supplierRepository, movementRepository, userRepository, inventoryMapper);
    }

    private Article buildArticle() {
        Article a = new Article();
        ReflectionTestUtils.setField(a, "id", ARTICLE_ID);
        a.setName("Réactif HIV");
        a.setQuantity(BigDecimal.ZERO);
        return a;
    }

    private ArticleResponseDto dummyArticleDto(BigDecimal qty) {
        return new ArticleResponseDto(ARTICLE_ID, "Réactif HIV", null, qty,
                BigDecimal.ZERO, null, BigDecimal.ZERO, null, null, BRANCH_ID, null, null, null, null);
    }

    @Test
    @DisplayName("create - avec initialQuantity > 0 → article et mouvement IN créés")
    void create_withInitialQuantity_shouldCreateArticleAndMovement() {
        Article saved = buildArticle();
        saved.setQuantity(new BigDecimal("10"));
        when(articleRepository.save(any())).thenReturn(saved);
        when(inventoryMapper.toArticleEntity(any())).thenReturn(new Article());
        when(inventoryMapper.toArticleResponseDto(any())).thenReturn(dummyArticleDto(new BigDecimal("10")));

        ArticleRequestDto dto = new ArticleRequestDto();
        dto.setName("Réactif HIV");
        dto.setInitialQuantity(new BigDecimal("10"));

        ArticleResponseDto result = service.create(dto, BRANCH_ID, null);

        assertThat(result).isNotNull();
        verify(movementRepository).save(any(Movement.class));
    }

    @Test
    @DisplayName("create - sans initialQuantity → quantité 0, mouvement « Stock initial » à 0 tracé")
    void create_withoutInitialQuantity_shouldSetQuantityToZeroAndTraceInitialMovement() {
        Article saved = buildArticle();
        when(articleRepository.save(any())).thenReturn(saved);
        when(inventoryMapper.toArticleEntity(any())).thenReturn(new Article());
        when(inventoryMapper.toArticleResponseDto(any())).thenReturn(dummyArticleDto(BigDecimal.ZERO));

        ArticleRequestDto dto = new ArticleRequestDto();
        dto.setName("Gants");

        service.create(dto, BRANCH_ID, null);

        // Laravel trace le stock initial systématiquement, y compris à quantité 0.
        ArgumentCaptor<Movement> captor = ArgumentCaptor.forClass(Movement.class);
        verify(movementRepository).save(captor.capture());
        assertThat(captor.getValue().getType()).isEqualTo(MovementType.IN);
        assertThat(captor.getValue().getQuantity()).isEqualByComparingTo(BigDecimal.ZERO);
    }

    @Test
    @DisplayName("create - avec initialQuantity et userId → mouvement tracé avec user")
    void create_withInitialQuantityAndUserId_tracksUser() {
        UUID userId = UUID.randomUUID();
        Article saved = buildArticle();
        saved.setQuantity(new BigDecimal("5"));
        com.labo.anapath.user.User user = new com.labo.anapath.user.User();
        when(articleRepository.save(any())).thenReturn(saved);
        when(inventoryMapper.toArticleEntity(any())).thenReturn(new Article());
        when(inventoryMapper.toArticleResponseDto(any())).thenReturn(dummyArticleDto(new BigDecimal("5")));
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));

        ArticleRequestDto dto = new ArticleRequestDto();
        dto.setName("Réactif");
        dto.setInitialQuantity(new BigDecimal("5"));

        service.create(dto, BRANCH_ID, userId);

        verify(userRepository).findById(userId);
        verify(movementRepository).save(any(Movement.class));
    }

    @Test
    @DisplayName("create - nouveaux champs description/lotNumber/expirationDate persistés")
    void create_newFields_arePersisted() {
        Article captured = new Article();
        when(inventoryMapper.toArticleEntity(any())).thenReturn(captured);
        when(articleRepository.save(any())).thenReturn(captured);
        when(inventoryMapper.toArticleResponseDto(any())).thenReturn(dummyArticleDto(BigDecimal.ZERO));

        ArticleRequestDto dto = new ArticleRequestDto();
        dto.setName("Sérum");
        dto.setDescription("Sérum pour tests VIH");
        dto.setLotNumber("LOT-2025-001");

        service.create(dto, BRANCH_ID, null);

        assertThat(captured.getDescription()).isEqualTo("Sérum pour tests VIH");
        assertThat(captured.getLotNumber()).isEqualTo("LOT-2025-001");
    }

    @Test
    @DisplayName("update - ne modifie pas la quantité")
    void update_shouldNotModifyQuantity() {
        Article article = buildArticle();
        article.setQuantity(new BigDecimal("50"));
        when(articleRepository.findById(ARTICLE_ID)).thenReturn(Optional.of(article));
        when(articleRepository.save(any())).thenReturn(article);
        when(inventoryMapper.toArticleResponseDto(any())).thenReturn(dummyArticleDto(new BigDecimal("50")));

        ArticleRequestDto dto = new ArticleRequestDto();
        dto.setName("Updated");
        dto.setInitialQuantity(new BigDecimal("999"));

        service.update(ARTICLE_ID, dto);

        assertThat(article.getQuantity()).isEqualByComparingTo("50");
    }

    @Test
    @DisplayName("findAll - retourne outOfStockCount et lowStockCount")
    void findAll_shouldIncludeStockCounts() {
        Article a = buildArticle();
        Page<Article> page = new PageImpl<>(List.of(a));
        when(articleRepository.findByBranchId(eq(BRANCH_ID), any(Pageable.class))).thenReturn(page);
        when(articleRepository.countByBranchIdAndQuantity(BRANCH_ID, BigDecimal.ZERO)).thenReturn(3L);
        when(articleRepository.countLowStock(BRANCH_ID)).thenReturn(2L);
        when(inventoryMapper.toArticleResponseDto(any())).thenReturn(dummyArticleDto(BigDecimal.ZERO));

        ArticlePageResponseDto result = service.findAll(0, 20, BRANCH_ID);

        assertThat(result.outOfStockCount()).isEqualTo(3L);
        assertThat(result.lowStockCount()).isEqualTo(2L);
    }

    @Test
    @DisplayName("findById - ID inconnu → ResourceNotFoundException")
    void findById_unknownId_throws() {
        when(articleRepository.findById(ARTICLE_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.findById(ARTICLE_ID))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    @DisplayName("search - retourne liste filtrée")
    void search_returnsMatchingResults() {
        Article a = buildArticle();
        when(articleRepository.findByBranchIdAndNameContainingIgnoreCase(BRANCH_ID, "réactif"))
                .thenReturn(List.of(a));
        when(inventoryMapper.toArticleResponseDto(any())).thenReturn(dummyArticleDto(BigDecimal.ZERO));

        List<ArticleResponseDto> result = service.search("réactif", BRANCH_ID);

        assertThat(result).hasSize(1);
    }
}
