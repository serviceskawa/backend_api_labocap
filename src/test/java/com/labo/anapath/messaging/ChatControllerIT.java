package com.labo.anapath.messaging;

import com.labo.anapath.testsupport.Jetons;
import com.labo.anapath.auth.LoginRequest;
import com.labo.anapath.auth.LoginResponse;
import com.labo.anapath.common.dto.ApiResponse;
import com.labo.anapath.role.Role;
import com.labo.anapath.role.RoleRepository;
import com.labo.anapath.user.User;
import com.labo.anapath.user.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers
class ChatControllerIT {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:15")
            .withDatabaseName("test_labo")
            .withUsername("test")
            .withPassword("test");

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("spring.flyway.baseline-on-migrate", () -> "true");
    }

    @Autowired private TestRestTemplate restTemplate;
    @Autowired private UserRepository userRepository;
    @Autowired private RoleRepository roleRepository;
    @Autowired private PasswordEncoder passwordEncoder;

    @LocalServerPort private int port;

    @Autowired private Jetons jetons;

    private static final UUID SEED_BRANCH_ID   = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final String SENDER_EMAIL    = "sender_chat_it@labo.bj";
    private static final String RECEIVER_EMAIL  = "receiver_chat_it@labo.bj";
    private static final String PASSWORD        = "adminPass123";

    private UUID receiverId;

    @BeforeEach
    void setup() {
        Role adminRole = roleRepository.findBySlugAndBranchId("admin", SEED_BRANCH_ID)
                .orElseThrow(() -> new IllegalStateException("ADMIN role not seeded"));

        if (userRepository.findByEmail(SENDER_EMAIL).isEmpty()) {
            User sender = new User();
            sender.setBranchId(SEED_BRANCH_ID);
            sender.setFirstname("Expéditeur");
            sender.setLastname("Test");
            sender.setEmail(SENDER_EMAIL);
            sender.setPassword(passwordEncoder.encode(PASSWORD));
            sender.setActive(true);
            sender.setRoles(List.of(adminRole));
            userRepository.save(sender);
        }

        if (userRepository.findByEmail(RECEIVER_EMAIL).isEmpty()) {
            User receiver = new User();
            receiver.setBranchId(SEED_BRANCH_ID);
            receiver.setFirstname("Destinataire");
            receiver.setLastname("Test");
            receiver.setEmail(RECEIVER_EMAIL);
            receiver.setPassword(passwordEncoder.encode(PASSWORD));
            receiver.setActive(true);
            receiver.setRoles(List.of(adminRole));
            userRepository.save(receiver);
        }

        receiverId = userRepository.findByEmail(RECEIVER_EMAIL).orElseThrow().getId();
    }

    private String loginAndGetToken(String email) {
        // Jeton émis directement : la connexion HTTP exige un code à usage
        // unique et n'est pas ce que ce test éprouve (voir Jetons).
        return jetons.pour(email);
    }

    private HttpHeaders authHeaders(String token) {
        HttpHeaders h = new HttpHeaders();
        h.setBearerAuth(token);
        h.setContentType(MediaType.APPLICATION_JSON);
        return h;
    }

    private String chatsUrl() {
        return "http://localhost:" + port + "/api/v1/chats";
    }

    private String sendMessage(String token) {
        Map<String, Object> body = Map.of("receiverId", receiverId.toString(), "message", "Bonjour !");
        ResponseEntity<ApiResponse<Map<String, Object>>> response = restTemplate.exchange(
                chatsUrl(), HttpMethod.POST,
                new HttpEntity<>(body, authHeaders(token)),
                new ParameterizedTypeReference<>() {});
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        return response.getBody().data().get("id").toString();
    }

    @Test
    @DisplayName("POST /chats → 201 message créé avec isRead=false")
    void send_returns201_messageCreated() {
        String token = loginAndGetToken(SENDER_EMAIL);

        Map<String, Object> body = Map.of("receiverId", receiverId.toString(), "message", "Procédure prête");
        ResponseEntity<ApiResponse<Map<String, Object>>> response = restTemplate.exchange(
                chatsUrl(), HttpMethod.POST,
                new HttpEntity<>(body, authHeaders(token)),
                new ParameterizedTypeReference<>() {});

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(response.getBody().data().get("message")).isEqualTo("Procédure prête");
        assertThat(response.getBody().data().get("isRead")).isEqualTo(false);
    }

    @Test
    @DisplayName("GET /chats → 200 liste paginée des conversations")
    void findAll_returns200_paginatedList() {
        String token = loginAndGetToken(SENDER_EMAIL);
        sendMessage(token);

        ResponseEntity<ApiResponse<Map<String, Object>>> response = restTemplate.exchange(
                chatsUrl(),
                HttpMethod.GET,
                new HttpEntity<>(authHeaders(token)),
                new ParameterizedTypeReference<>() {});

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().data()).containsKey("content");
    }

    @Test
    @DisplayName("GET /chats?receiverId={uuid} → 200 conversation entre deux utilisateurs")
    void findConversation_returns200_filteredByReceiver() {
        String token = loginAndGetToken(SENDER_EMAIL);
        sendMessage(token);

        ResponseEntity<ApiResponse<Map<String, Object>>> response = restTemplate.exchange(
                chatsUrl() + "?receiverId=" + receiverId,
                HttpMethod.GET,
                new HttpEntity<>(authHeaders(token)),
                new ParameterizedTypeReference<>() {});

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> content = (List<Map<String, Object>>) response.getBody().data().get("content");
        assertThat(content).isNotEmpty();
    }

    @Test
    @DisplayName("PATCH /chats/{id}/read (destinataire) → 200 isRead=true")
    void markAsRead_byReceiver_returns200() {
        String senderToken   = loginAndGetToken(SENDER_EMAIL);
        String receiverToken = loginAndGetToken(RECEIVER_EMAIL);
        String chatId = sendMessage(senderToken);

        ResponseEntity<ApiResponse<Map<String, Object>>> response = restTemplate.exchange(
                chatsUrl() + "/" + chatId + "/read",
                HttpMethod.PATCH,
                new HttpEntity<>(authHeaders(receiverToken)),
                new ParameterizedTypeReference<>() {});

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().data().get("isRead")).isEqualTo(true);
    }

    @Test
    @DisplayName("PATCH /chats/{id}/read (non destinataire) → 422")
    void markAsRead_notReceiver_returns422() {
        String senderToken = loginAndGetToken(SENDER_EMAIL);
        String chatId = sendMessage(senderToken);

        ResponseEntity<ApiResponse<Void>> response = restTemplate.exchange(
                chatsUrl() + "/" + chatId + "/read",
                HttpMethod.PATCH,
                new HttpEntity<>(authHeaders(senderToken)),
                new ParameterizedTypeReference<>() {});

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
    }

    @Test
    @DisplayName("POST /chats sans token → 401")
    void send_withoutToken_returns401() {
        HttpHeaders h = new HttpHeaders();
        h.setContentType(MediaType.APPLICATION_JSON);
        Map<String, Object> body = Map.of("receiverId", receiverId.toString(), "message", "test");

        ResponseEntity<ApiResponse<Void>> response = restTemplate.exchange(
                chatsUrl(), HttpMethod.POST,
                new HttpEntity<>(body, h),
                new ParameterizedTypeReference<>() {});

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }
}
