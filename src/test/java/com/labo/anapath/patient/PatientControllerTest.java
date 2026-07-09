package com.labo.anapath.patient;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.labo.anapath.common.dto.ApiResponse;
import com.labo.anapath.common.dto.PageResponse;
import com.labo.anapath.common.exception.ResourceNotFoundException;
import com.labo.anapath.common.security.JwtAuthenticationFilter;
import com.labo.anapath.common.security.UserPrincipal;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(
        controllers = PatientController.class,
        excludeFilters = @ComponentScan.Filter(type = FilterType.ASSIGNABLE_TYPE, classes = JwtAuthenticationFilter.class)
)
class PatientControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private PatientService patientService;

    private final UUID BRANCH_ID = UUID.randomUUID();
    private final UUID PATIENT_ID = UUID.randomUUID();

    private UserPrincipal buildPrincipal() {
        return new UserPrincipal(
                UUID.randomUUID(), "admin@test.com", "pass", BRANCH_ID,
                true,
                List.of(
                        new SimpleGrantedAuthority("view-patients"),
                        new SimpleGrantedAuthority("create-patients"),
                        new SimpleGrantedAuthority("edit-patients"),
                        new SimpleGrantedAuthority("delete-patients")
                )
        );
    }

    @Test
    @DisplayName("GET /patients - should return 200 with page")
    @WithMockUser
    void findAll_200() throws Exception {
        UserPrincipal principal = buildPrincipal();
        PatientResponseDto dto = new PatientResponseDto(
                PATIENT_ID, null, "Jean", "Dupont", "M",
                "0600000001", null, null, null, null,
                LocalDate.of(1990, 1, 1), null, null, null,
                BRANCH_ID, LocalDateTime.now(),
                BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO);

        PageResponse<PatientResponseDto> page = new PageResponse<>(List.of(dto), 0, 20, 1L, 1, true);
        when(patientService.findAll(eq(0), eq(20), any(), eq(BRANCH_ID))).thenReturn(page);

        mockMvc.perform(get("/api/v1/patients")
                        .with(user(principal)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.content[0].firstname").value("Jean"));
    }

    @Test
    @DisplayName("GET /patients/{id} - should return 200 when found")
    @WithMockUser
    void findById_200() throws Exception {
        UserPrincipal principal = buildPrincipal();
        PatientResponseDto dto = new PatientResponseDto(
                PATIENT_ID, null, "Jean", "Dupont", "M",
                "0600000001", null, null, null, null,
                LocalDate.of(1990, 1, 1), null, null, null,
                BRANCH_ID, LocalDateTime.now(),
                BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO);

        when(patientService.findById(eq(PATIENT_ID), eq(BRANCH_ID))).thenReturn(dto);

        mockMvc.perform(get("/api/v1/patients/{id}", PATIENT_ID)
                        .with(user(principal)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(PATIENT_ID.toString()));
    }

    @Test
    @DisplayName("GET /patients/{id} - should return 404 when not found")
    @WithMockUser
    void findById_404() throws Exception {
        UserPrincipal principal = buildPrincipal();
        when(patientService.findById(eq(PATIENT_ID), eq(BRANCH_ID))).thenThrow(new ResourceNotFoundException("Patient", PATIENT_ID));

        mockMvc.perform(get("/api/v1/patients/{id}", PATIENT_ID)
                        .with(user(principal)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    @DisplayName("POST /patients - should return 201 on valid request")
    @WithMockUser
    void create_201() throws Exception {
        UserPrincipal principal = buildPrincipal();
        PatientRequestDto requestDto = new PatientRequestDto();
        requestDto.setCode("P-MARIE-001");
        requestDto.setFirstname("Marie");
        requestDto.setLastname("Curie");
        requestDto.setGenre("F");

        PatientResponseDto responseDto = new PatientResponseDto(
                UUID.randomUUID(), null, "Marie", "Curie", "F",
                null, null, null, null, null, null,
                null, null, null, BRANCH_ID, LocalDateTime.now(),
                BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO);

        when(patientService.create(any(PatientRequestDto.class), eq(BRANCH_ID))).thenReturn(responseDto);

        mockMvc.perform(post("/api/v1/patients")
                        .with(user(principal))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(requestDto)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.firstname").value("Marie"));
    }

    @Test
    @DisplayName("POST /patients - should return 400 on missing required fields")
    @WithMockUser
    void create_400_validation() throws Exception {
        UserPrincipal principal = buildPrincipal();
        PatientRequestDto requestDto = new PatientRequestDto();
        // Missing firstname, lastname - required fields

        mockMvc.perform(post("/api/v1/patients")
                        .with(user(principal))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(requestDto)))
                .andExpect(status().isBadRequest());
    }
}
