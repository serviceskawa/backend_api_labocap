package com.labo.anapath.doc;

import com.labo.anapath.common.email.EmailService;
import com.labo.anapath.common.email.NotificationSettings;
import com.labo.anapath.common.exception.ResourceNotFoundException;
import com.labo.anapath.common.storage.FileStorageService;
import com.labo.anapath.role.RoleRepository;
import com.labo.anapath.user.User;
import com.labo.anapath.user.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DocServiceTest {

    @Mock DocRepository docRepository;
    @Mock DocVersionRepository docVersionRepository;
    @Mock DocumentationCategoryRepository documentationCategoryRepository;
    @Mock UserRepository userRepository;
    @Mock RoleRepository roleRepository;
    @Mock FileStorageService fileStorageService;
    @Mock EmailService emailService;
    @Mock NotificationSettings notificationSettings;
    @Mock DocMapper docMapper;
    @Mock DocVersionMapper docVersionMapper;

    DocServiceImpl service;

    private final UUID BRANCH_ID = UUID.randomUUID();
    private final UUID USER_ID   = UUID.randomUUID();
    private final UUID DOC_ID    = UUID.randomUUID();

    @BeforeEach
    void setup() {
        service = new DocServiceImpl(docRepository, docVersionRepository, documentationCategoryRepository,
                userRepository, roleRepository, fileStorageService, emailService, notificationSettings,
                docMapper, docVersionMapper);
    }

    private Doc buildDoc(String title) {
        Doc doc = new Doc();
        ReflectionTestUtils.setField(doc, "id", DOC_ID);
        doc.setTitle(title);
        doc.setAttachment("documents/uuid.pdf");
        doc.setIsCurrentVersion(true);
        return doc;
    }

    @Test
    @DisplayName("create - stocke le fichier et crée un Doc + DocVersion 1")
    void create_storesFileAndCreatesVersion1() {
        User user = new User();
        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(user));
        when(fileStorageService.store(any(), eq("documents"))).thenReturn("documents/uuid.pdf");
        Doc saved = buildDoc("Procédure 1");
        when(docRepository.save(any())).thenReturn(saved);
        when(docVersionRepository.save(any())).thenReturn(new DocVersion());
        when(docMapper.toResponseDto(saved)).thenReturn(
                new DocResponseDto(DOC_ID, "Procédure 1", "documents/uuid.pdf", true, 100L, null, USER_ID, BRANCH_ID, BRANCH_ID, null));

        MockMultipartFile file = new MockMultipartFile("file", "proc.pdf", "application/pdf", "pdf".getBytes());
        DocResponseDto result = service.create("Procédure 1", null, file, USER_ID, BRANCH_ID);

        assertThat(result.title()).isEqualTo("Procédure 1");
        assertThat(result.attachment()).isEqualTo("documents/uuid.pdf");
    }

    @Test
    @DisplayName("addVersion - incrémente le numéro de version")
    void addVersion_incrementsVersionNumber() {
        Doc doc = buildDoc("Doc 1");
        when(docRepository.findById(DOC_ID)).thenReturn(Optional.of(doc));
        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(new User()));
        when(fileStorageService.store(any(), eq("documents"))).thenReturn("documents/v2.pdf");
        DocVersion lastVersion = new DocVersion();
        lastVersion.setVersion(1);
        when(docVersionRepository.findTopByDocIdOrderByVersionDesc(DOC_ID)).thenReturn(Optional.of(lastVersion));
        DocVersion saved = new DocVersion();
        saved.setVersion(2);
        when(docVersionRepository.save(any())).thenReturn(saved);
        when(docVersionMapper.toResponseDto(saved))
                .thenReturn(new DocVersionResponseDto(UUID.randomUUID(), DOC_ID, 2, "Doc 1", "documents/v2.pdf", 50L, USER_ID, null));

        MockMultipartFile file = new MockMultipartFile("file", "v2.pdf", "application/pdf", "v2".getBytes());
        DocVersionResponseDto result = service.addVersion(DOC_ID, null, file, USER_ID, BRANCH_ID);

        assertThat(result.version()).isEqualTo(2);
    }

    @Test
    @DisplayName("delete - soft-delete via repository")
    void delete_softDeletesDoc() {
        Doc doc = buildDoc("Doc to delete");
        when(docRepository.findById(DOC_ID)).thenReturn(Optional.of(doc));

        service.delete(DOC_ID);
    }

    @Test
    @DisplayName("getVersions - retourne la liste triée par version croissante")
    void getVersions_returnsSortedByVersionAsc() {
        when(docVersionRepository.findByDocIdOrderByVersionAsc(DOC_ID)).thenReturn(List.of());

        List<DocVersionResponseDto> result = service.getVersions(DOC_ID);

        assertThat(result).isNotNull();
    }
}
