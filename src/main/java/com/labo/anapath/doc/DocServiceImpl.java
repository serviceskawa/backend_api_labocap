package com.labo.anapath.doc;

import com.labo.anapath.common.dto.PageResponse;
import com.labo.anapath.common.email.EmailService;
import com.labo.anapath.common.email.NotificationSettings;
import com.labo.anapath.common.exception.ResourceNotFoundException;
import com.labo.anapath.common.storage.FileStorageService;
import com.labo.anapath.role.Role;
import com.labo.anapath.role.RoleRepository;
import com.labo.anapath.user.User;
import com.labo.anapath.user.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class DocServiceImpl implements DocService {

    private final DocRepository docRepository;
    private final DocVersionRepository docVersionRepository;
    private final DocumentationCategoryRepository documentationCategoryRepository;
    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final FileStorageService fileStorageService;
    private final EmailService emailService;
    private final NotificationSettings notificationSettings;
    private final DocMapper docMapper;
    private final DocVersionMapper docVersionMapper;

    @Override
    @Transactional(readOnly = true)
    public PageResponse<DocResponseDto> findAll(int page, int size, UUID branchId) {
        return PageResponse.of(docRepository.findByBranchId(branchId,
                PageRequest.of(page, size, Sort.by("createdAt").descending()))
                .map(docMapper::toResponseDto));
    }

    @Override
    @Transactional(readOnly = true)
    public DocResponseDto findById(UUID id) {
        return docMapper.toResponseDto(docRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Document", id)));
    }

    @Override
    @Transactional
    public DocResponseDto create(String title, UUID documentationCategoryId, MultipartFile file, UUID userId, UUID branchId) {
        String path = fileStorageService.store(file, "documents");
        long size = file.getSize();

        Doc doc = new Doc();
        doc.setBranchId(branchId);
        doc.setTitle(title);
        doc.setAttachment(path);
        doc.setFileSize(size);
        doc.setIsCurrentVersion(true);
        doc.setUser(userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("Utilisateur", userId)));
        if (documentationCategoryId != null) {
            doc.setDocumentationCategory(documentationCategoryRepository.findById(documentationCategoryId)
                    .orElseThrow(() -> new ResourceNotFoundException("Catégorie", documentationCategoryId)));
        }
        doc = docRepository.save(doc);

        DocVersion version = new DocVersion();
        version.setBranchId(branchId);
        version.setDoc(doc);
        version.setTitle(title);
        version.setAttachment(path);
        version.setFileSize(size);
        version.setVersion(1);
        version.setUser(doc.getUser());
        docVersionRepository.save(version);

        return docMapper.toResponseDto(doc);
    }

    @Override
    @Transactional
    public DocVersionResponseDto addVersion(UUID docId, String title, MultipartFile file, UUID userId, UUID branchId) {
        Doc doc = docRepository.findById(docId)
                .orElseThrow(() -> new ResourceNotFoundException("Document", docId));
        int nextVersion = docVersionRepository.findTopByDocIdOrderByVersionDesc(docId)
                .map(v -> v.getVersion() + 1)
                .orElse(2);
        String path = fileStorageService.store(file, "documents");

        DocVersion version = new DocVersion();
        version.setBranchId(branchId);
        version.setDoc(doc);
        version.setTitle(title != null ? title : doc.getTitle());
        version.setAttachment(path);
        version.setFileSize(file.getSize());
        version.setVersion(nextVersion);
        version.setUser(userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("Utilisateur", userId)));
        return docVersionMapper.toResponseDto(docVersionRepository.save(version));
    }

    @Override
    @Transactional(readOnly = true)
    public List<DocVersionResponseDto> getVersions(UUID docId) {
        return docVersionRepository.findByDocIdOrderByVersionAsc(docId)
                .stream().map(docVersionMapper::toResponseDto).toList();
    }

    @Override
    @Transactional
    public void delete(UUID id) {
        Doc doc = docRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Document", id));
        docRepository.delete(doc);
    }

    @Override
    @Transactional
    public DocResponseDto share(UUID docId, UUID roleId, UUID branchId) {
        Doc doc = docRepository.findById(docId)
                .orElseThrow(() -> new ResourceNotFoundException("Document", docId));
        Role role = roleRepository.findById(roleId)
                .orElseThrow(() -> new ResourceNotFoundException("Rôle", roleId));
        doc.setRole(role);
        docRepository.save(doc);

        // Notifie par email tous les utilisateurs du rôle (réplique Laravel ShareDocEvent).
        String labName = notificationSettings.labName(branchId);
        String sharerName = doc.getUser() != null
                ? (doc.getUser().getFirstname() + " " + doc.getUser().getLastname()).trim()
                : "";
        for (User user : role.getUsers()) {
            if (user.getEmail() == null || user.getEmail().isBlank()) {
                continue;
            }
            String recipientName = (user.getFirstname() + " " + user.getLastname()).trim();
            emailService.sendShareDoc(user.getEmail(), recipientName, sharerName, doc.getTitle(), labName);
        }
        return docMapper.toResponseDto(doc);
    }

    @Override
    @Transactional(readOnly = true)
    public PageResponse<DocResponseDto> findSharedWithMe(int page, int size, UUID userId, UUID branchId) {
        return PageResponse.of(docRepository.findSharedWithMe(userId, branchId,
                PageRequest.of(page, size)).map(docMapper::toResponseDto));
    }

    @Override
    @Transactional(readOnly = true)
    public List<DocResponseDto> findRecent(UUID branchId, int limit) {
        return docRepository.findByBranchId(branchId,
                PageRequest.of(0, Math.max(1, limit), Sort.by("createdAt").descending()))
                .map(docMapper::toResponseDto)
                .getContent();
    }

    @Override
    @Transactional(readOnly = true)
    public PageResponse<DocResponseDto> findTrash(int page, int size, UUID branchId) {
        return PageResponse.of(docRepository.findTrashed(branchId,
                PageRequest.of(page, size)).map(docMapper::toResponseDto));
    }

    @Override
    @Transactional
    public DocResponseDto restore(UUID id) {
        Doc doc = docRepository.findAnyById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Document", id));
        if (doc.getDeletedAt() == null) {
            return docMapper.toResponseDto(doc);
        }
        docRepository.restoreById(id);
        Doc restored = docRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Document", id));
        return docMapper.toResponseDto(restored);
    }

    @Override
    @Transactional
    public void permanentDelete(UUID id) {
        Doc doc = docRepository.findAnyById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Document", id));
        // Supprime les fichiers physiques (best-effort) avant la purge en base.
        for (DocVersion version : docVersionRepository.findByDocIdOrderByVersionAsc(id)) {
            safeDeleteFile(version.getAttachment());
        }
        safeDeleteFile(doc.getAttachment());
        docRepository.hardDeleteVersions(id);
        docRepository.hardDeleteDoc(id);
    }

    private void safeDeleteFile(String path) {
        if (path == null || path.isBlank()) {
            return;
        }
        try {
            fileStorageService.delete(path);
        } catch (Exception ignored) {
            // suppression best-effort : un fichier déjà absent ne doit pas bloquer la purge
        }
    }
}
