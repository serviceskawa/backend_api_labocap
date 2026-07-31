package com.labo.anapath.support;

import com.labo.anapath.common.dto.PageResponse;
import com.labo.anapath.common.email.EmailService;
import com.labo.anapath.common.email.NotificationSettings;
import com.labo.anapath.common.exception.ResourceNotFoundException;
import com.labo.anapath.user.User;
import com.labo.anapath.user.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.UUID;

/**
 * Implémentation de {@link TicketService} gérant la logique métier
 * des tickets de support interne du laboratoire.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class TicketServiceImpl implements TicketService {

    private final TicketRepository ticketRepository;
    private final UserRepository userRepository;
    private final TicketMapper ticketMapper;
    private final EmailService emailService;
    private final NotificationSettings notificationSettings;

    /** Adresse notifiée à la création d'un ticket (Laravel : {@code serviceskawa@gmail.com}). */
    @Value("${app.support.ticket-notify-email:serviceskawa@gmail.com}")
    private String ticketNotifyEmail;

    /**
     * {@inheritDoc}
     * Les tickets sont triés par date de création décroissante (plus récents en premier).
     */
    @Override
    @Transactional(readOnly = true)
    public PageResponse<TicketResponseDto> findAll(int page, int size, UUID branchId) {
        return PageResponse.of(ticketRepository.findByBranchId(branchId,
                PageRequest.of(page, size, Sort.by("createdAt").descending()))
                .map(ticketMapper::toResponseDto));
    }

    /** {@inheritDoc} */
    @Override
    @Transactional(readOnly = true)
    public long countOpen(UUID userId, UUID branchId) {
        return ticketRepository.countOpen(branchId, userId, userRepository.isSuperAdmin(userId));
    }

    /** {@inheritDoc} */
    @Override
    @Transactional(readOnly = true)
    public TicketResponseDto findById(UUID id) {
        return ticketMapper.toResponseDto(ticketRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Ticket", id)));
    }

    /**
     * {@inheritDoc}
     * Le statut initial est forcé à {@link TicketStatus#OPEN}.
     * Si aucune priorité n'est fournie, {@link TicketPriority#MEDIUM} est appliquée par défaut.
     */
    @Override
    @Transactional
    public TicketResponseDto create(TicketRequestDto dto, UUID userId, UUID branchId) {
        Ticket ticket = ticketMapper.toEntity(dto);
        ticket.setBranchId(branchId);
        ticket.setStatus(TicketStatus.OPEN);
        ticket.setPriority(dto.getPriority() != null ? dto.getPriority() : TicketPriority.MEDIUM);
        ticket.setTicketCode(generateTicketCode());
        User author = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("Utilisateur", userId));
        ticket.setUser(author);
        Ticket saved = ticketRepository.save(ticket);
        notifySupportOfNewTicket(saved, author, branchId);
        return ticketMapper.toResponseDto(saved);
    }

    /** Notifie le support qu'un nouveau ticket vient d'être créé (Laravel : {@code NotificationCreateNewTicket}). */
    private void notifySupportOfNewTicket(Ticket ticket, User author, UUID branchId) {
        if (ticketNotifyEmail == null || ticketNotifyEmail.isBlank()) {
            return;
        }
        String createdByName = (author.getFirstname() + " " + author.getLastname()).trim();
        String labName = notificationSettings.labName(branchId);
        emailService.sendNewTicketAlert(ticketNotifyEmail, ticket.getTicketCode(), createdByName, labName);
    }

    /**
     * {@inheritDoc}
     * La priorité n'est mise à jour que si une valeur est explicitement fournie.
     */
    @Override
    @Transactional
    public TicketResponseDto update(UUID id, TicketRequestDto dto) {
        Ticket ticket = ticketRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Ticket", id));
        ticket.setTitle(dto.getTitle());
        ticket.setDescription(dto.getDescription());
        if (dto.getPriority() != null) ticket.setPriority(dto.getPriority());
        return ticketMapper.toResponseDto(ticketRepository.save(ticket));
    }

    /** {@inheritDoc} */
    @Override
    @Transactional
    public TicketResponseDto updateStatus(UUID id, TicketStatus status) {
        Ticket ticket = ticketRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Ticket", id));
        ticket.setStatus(status);
        return ticketMapper.toResponseDto(ticketRepository.save(ticket));
    }

    /**
     * Code d'un ticket, au format Laravel {@code generateCodeTicket()} :
     * {@code TI-} + les deux derniers chiffres de l'année + un compteur sur
     * 4 chiffres, remis à {@code 0001} à chaque nouvelle année — p. ex.
     * {@code TI-250004}.
     *
     * <p>Le compteur est lu sur les tickets de l'année, y compris ceux
     * supprimés (suppression logique) : réattribuer leur numéro violerait la
     * contrainte d'unicité sur {@code ticket_code}.</p>
     */
    private String generateTicketCode() {
        String yearPrefix = "TI-" + (LocalDate.now().getYear() % 100);
        Integer maxSequence = ticketRepository.findMaxSequenceForYear(yearPrefix);
        int next = (maxSequence == null ? 0 : maxSequence) + 1;
        return yearPrefix + String.format("%04d", next);
    }

    /** {@inheritDoc} */
    @Override
    @Transactional
    public void delete(UUID id) {
        Ticket ticket = ticketRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Ticket", id));
        ticketRepository.delete(ticket);
    }
}
