package com.labo.anapath.hr;

import com.labo.anapath.common.exception.ResourceNotFoundException;
import com.labo.anapath.common.security.UserPrincipal;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.Resource;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class EmployeeDocumentControllerTest {

    @Mock EmployeeDocumentRepository documentRepository;
    @Mock EmployeeRepository employeeRepository;
    @Mock FileStorageService fileStorageService;

    EmployeeDocumentController controller;

    private final UUID BRANCH_ID = UUID.randomUUID();
    private final UUID EMP_ID    = UUID.randomUUID();
    private final UUID DOC_ID    = UUID.randomUUID();

    @BeforeEach
    void setup() {
        controller = new EmployeeDocumentController(documentRepository, employeeRepository, fileStorageService);
    }

    private UserPrincipal mockPrincipal() {
        UserPrincipal p = org.mockito.Mockito.mock(UserPrincipal.class);
        // `lenient` : ce fabricant est partagé, et le test du salarié inconnu
        // échoue avant de lire la branche. Supprimer l'amorce casserait les autres.
        org.mockito.Mockito.lenient().when(p.getBranchId()).thenReturn(BRANCH_ID);
        return p;
    }

    private Employee buildEmployee() {
        Employee e = new Employee();
        ReflectionTestUtils.setField(e, "id", EMP_ID);
        e.setSalary(BigDecimal.ZERO);
        return e;
    }

    private EmployeeDocument buildDoc(Employee emp) {
        EmployeeDocument d = new EmployeeDocument();
        ReflectionTestUtils.setField(d, "id", DOC_ID);
        d.setEmployee(emp);
        d.setName("Contrat CDI");
        d.setBranchId(BRANCH_ID);
        return d;
    }

    @Test
    @DisplayName("upload - avec fichier → filePath et fileSize calculés → 201")
    void uploadDocument_withFile_storesAndReturns201() {
        Employee emp = buildEmployee();
        EmployeeDocument saved = buildDoc(emp);
        saved.setFilePath("documents/file.pdf");
        saved.setFileSize(1024L);

        when(employeeRepository.findById(EMP_ID)).thenReturn(Optional.of(emp));
        when(fileStorageService.store(any(), eq("documents"))).thenReturn("documents/file.pdf");
        when(documentRepository.save(any())).thenReturn(saved);

        MockMultipartFile file = new MockMultipartFile("file", "contrat.pdf", "application/pdf",
                "fake pdf content".getBytes());

        ResponseEntity<?> response = controller.upload(EMP_ID, "Contrat CDI", null, file, mockPrincipal());

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        verify(fileStorageService).store(any(), eq("documents"));
    }

    @Test
    @DisplayName("upload - sans fichier → filePath et fileSize null → 201")
    void uploadDocument_withoutFile_returnsNullPath() {
        Employee emp = buildEmployee();
        EmployeeDocument saved = buildDoc(emp);

        when(employeeRepository.findById(EMP_ID)).thenReturn(Optional.of(emp));
        when(documentRepository.save(any())).thenReturn(saved);

        ResponseEntity<?> response = controller.upload(EMP_ID, "Note interne", null, null, mockPrincipal());

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
    }

    @Test
    @DisplayName("upload - employé inconnu → ResourceNotFoundException")
    void uploadDocument_unknownEmployee_throws() {
        when(employeeRepository.findById(EMP_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> controller.upload(EMP_ID, "Doc", null, null, mockPrincipal()))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    @DisplayName("findAll - retourne page paginée filtrée par employeeId")
    void listDocuments_byEmployeeId_returnsPaginated() {
        Employee emp = buildEmployee();
        EmployeeDocument doc = buildDoc(emp);
        Page<EmployeeDocument> page = new PageImpl<>(List.of(doc));
        when(documentRepository.findByEmployeeId(eq(EMP_ID), any(Pageable.class))).thenReturn(page);

        ResponseEntity<?> response = controller.findAll(EMP_ID, 0, 20);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    @Test
    @DisplayName("findById - ID existant → 200")
    void getDocument_byId_returns200() {
        Employee emp = buildEmployee();
        EmployeeDocument doc = buildDoc(emp);
        when(documentRepository.findById(DOC_ID)).thenReturn(Optional.of(doc));

        ResponseEntity<?> response = controller.findById(DOC_ID);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    @Test
    @DisplayName("findById - ID inconnu → ResourceNotFoundException")
    void getDocument_unknownId_throws() {
        when(documentRepository.findById(DOC_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> controller.findById(DOC_ID))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    @DisplayName("update - modifie name et type → 200")
    void updateDocument_changesNameAndType_returns200() {
        Employee emp = buildEmployee();
        EmployeeDocument doc = buildDoc(emp);
        when(documentRepository.findById(DOC_ID)).thenReturn(Optional.of(doc));
        when(documentRepository.save(any())).thenReturn(doc);

        EmployeeDocumentController.EmployeeDocumentUpdateDto dto =
                new EmployeeDocumentController.EmployeeDocumentUpdateDto();
        dto.setName("Diplôme mis à jour");
        dto.setType("diplome");

        ResponseEntity<?> response = controller.update(DOC_ID, dto);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(doc.getName()).isEqualTo("Diplôme mis à jour");
        assertThat(doc.getType()).isEqualTo("diplome");
    }

    @Test
    @DisplayName("delete - soft delete via repository.delete → 200")
    void deleteDocument_softDeletes_returnsOk() {
        Employee emp = buildEmployee();
        EmployeeDocument doc = buildDoc(emp);
        when(documentRepository.findById(DOC_ID)).thenReturn(Optional.of(doc));

        ResponseEntity<?> response = controller.delete(DOC_ID);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        verify(documentRepository).delete(doc);
    }

    @Test
    @DisplayName("download - fichier existant → Resource avec Content-Disposition")
    void downloadDocument_existingFile_returnsResource() {
        Employee emp = buildEmployee();
        EmployeeDocument doc = buildDoc(emp);
        doc.setFilePath("documents/test.pdf");

        Resource resource = new ByteArrayResource("pdf content".getBytes()) {
            @Override public String getFilename() { return "test.pdf"; }
            @Override public boolean exists() { return true; }
        };

        when(documentRepository.findById(DOC_ID)).thenReturn(Optional.of(doc));
        when(fileStorageService.load(anyString())).thenReturn(resource);

        ResponseEntity<Resource> response = controller.download(DOC_ID);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getHeaders().getFirst("Content-Disposition")).contains("attachment");
    }

    @Test
    @DisplayName("download - fichier physique absent → ResourceNotFoundException")
    void downloadDocument_missingPhysicalFile_throws() {
        Employee emp = buildEmployee();
        EmployeeDocument doc = buildDoc(emp);
        doc.setFilePath("documents/missing.pdf");

        Resource resource = new ByteArrayResource(new byte[0]) {
            @Override public boolean exists() { return false; }
        };

        when(documentRepository.findById(DOC_ID)).thenReturn(Optional.of(doc));
        when(fileStorageService.load(anyString())).thenReturn(resource);

        assertThatThrownBy(() -> controller.download(DOC_ID))
                .isInstanceOf(ResourceNotFoundException.class);
    }
}
