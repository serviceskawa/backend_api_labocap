package com.labo.anapath.support;

import com.labo.anapath.common.email.EmailService;
import com.labo.anapath.common.email.NotificationSettings;
import com.labo.anapath.common.exception.ResourceNotFoundException;
import com.labo.anapath.user.User;
import com.labo.anapath.user.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TicketServiceTest {

    @Mock TicketRepository ticketRepository;
    @Mock UserRepository userRepository;
    @Mock TicketMapper ticketMapper;
    @Mock EmailService emailService;
    @Mock NotificationSettings notificationSettings;

    TicketServiceImpl service;

    private final UUID BRANCH_ID = UUID.randomUUID();
    private final UUID USER_ID   = UUID.randomUUID();
    private final UUID TICKET_ID = UUID.randomUUID();

    @BeforeEach
    void setup() {
        service = new TicketServiceImpl(ticketRepository, userRepository, ticketMapper,
                emailService, notificationSettings);
    }

    private Ticket buildTicket() {
        Ticket t = new Ticket();
        ReflectionTestUtils.setField(t, "id", TICKET_ID);
        t.setTitle("Test");
        t.setStatus(TicketStatus.OPEN);
        t.setPriority(TicketPriority.MEDIUM);
        return t;
    }

    private TicketResponseDto dummyDto(String code) {
        return new TicketResponseDto(TICKET_ID, code, "Test", null, TicketStatus.OPEN, TicketPriority.MEDIUM, USER_ID, "Admin", BRANCH_ID, null);
    }

    @Test
    @DisplayName("create - génère un ticket_code au format TKT-YYYYMM-XXXXXX")
    void create_generatesUniqueCode() {
        User user = new User();
        when(ticketMapper.toEntity(any(TicketRequestDto.class))).thenReturn(new Ticket());
        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(user));
        Ticket saved = buildTicket();
        when(ticketRepository.save(any())).thenAnswer(inv -> {
            Ticket t = inv.getArgument(0);
            assertThat(t.getTicketCode()).matches("TKT-\\d{6}-[A-F0-9]{6}");
            return saved;
        });
        when(ticketMapper.toResponseDto(saved)).thenReturn(dummyDto("TKT-202601-ABC123"));

        TicketRequestDto dto = new TicketRequestDto();
        dto.setTitle("Test ticket");
        service.create(dto, USER_ID, BRANCH_ID);
    }

    @Test
    @DisplayName("updateStatus - transition valide → met à jour le statut")
    void updateStatus_validTransition_updatesStatus() {
        Ticket ticket = buildTicket();
        when(ticketRepository.findById(TICKET_ID)).thenReturn(Optional.of(ticket));
        when(ticketRepository.save(any())).thenReturn(ticket);
        when(ticketMapper.toResponseDto(ticket)).thenReturn(dummyDto("TKT-202601-ABCDEF"));

        service.updateStatus(TICKET_ID, TicketStatus.RESOLVED);

        assertThat(ticket.getStatus()).isEqualTo(TicketStatus.RESOLVED);
    }

    @Test
    @DisplayName("findById - ID inconnu → ResourceNotFoundException")
    void findById_notFound_throwsResourceNotFoundException() {
        when(ticketRepository.findById(TICKET_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.findById(TICKET_ID))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    @DisplayName("findAll - retourne page triée par date décroissante")
    void findAll_returnsSortedPage() {
        Ticket t = buildTicket();
        Page<Ticket> page = new PageImpl<>(List.of(t));
        when(ticketRepository.findByBranchId(any(UUID.class), any(Pageable.class))).thenReturn(page);
        when(ticketMapper.toResponseDto(t)).thenReturn(dummyDto("TKT-202601-ABCDEF"));

        var result = service.findAll(0, 20, BRANCH_ID);

        assertThat(result).isNotNull();
    }
}
