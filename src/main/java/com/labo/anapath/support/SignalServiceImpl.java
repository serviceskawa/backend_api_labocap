package com.labo.anapath.support;

import com.labo.anapath.common.dto.PageResponse;
import com.labo.anapath.common.exception.ResourceNotFoundException;
import com.labo.anapath.testorder.TestOrderRepository;
import com.labo.anapath.user.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;
import com.labo.anapath.common.exception.BusinessException;

@Service
@RequiredArgsConstructor
public class SignalServiceImpl implements SignalService {

    private final SignalRepository signalRepository;
    private final TestOrderRepository testOrderRepository;
    private final UserRepository userRepository;
    private final SignalMapper signalMapper;

    @Override
    @Transactional(readOnly = true)
    public PageResponse<SignalResponseDto> findAll(int page, int size, UUID branchId) {
        return PageResponse.of(signalRepository.findByBranchId(branchId,
                PageRequest.of(page, size, Sort.by("createdAt").descending()))
                .map(signalMapper::toResponseDto));
    }

    @Override
    @Transactional
    public SignalResponseDto create(SignalRequestDto dto, UUID userId, UUID branchId) {
        Signal signal = new Signal();
        signal.setBranchId(branchId);
        signal.setTypeSignal(dto.getTypeSignal());
        signal.setCommentaire(dto.getCommentaire());
        signal.setStatus(false);
        signal.setUser(userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("Utilisateur", userId)));
        signal.setTestOrder(resoudreDemande(dto, branchId));
        return signalMapper.toResponseDto(signalRepository.save(signal));
    }

    /**
     * Retrouve la demande d'examen visée par le signalement.
     *
     * <p>Accepte soit l'identifiant, soit le code saisi par l'utilisateur — c'est
     * le serveur qui résout le code, comme {@code SignalController::store()} en
     * Laravel. Un code inconnu remonte un message explicite plutôt qu'une erreur
     * technique.
     */
    private com.labo.anapath.testorder.TestOrder resoudreDemande(SignalRequestDto dto, UUID branchId) {
        if (dto.getTestOrderId() != null) {
            return testOrderRepository.findById(dto.getTestOrderId())
                    .orElseThrow(() -> new ResourceNotFoundException("Demande d'examen", dto.getTestOrderId()));
        }
        String code = dto.getTestOrderCode() != null ? dto.getTestOrderCode().trim() : "";
        if (code.isEmpty()) {
            throw new BusinessException("Le code de la demande est requis.");
        }
        return testOrderRepository.findByCodeAndBranchId(code, branchId)
                .orElseThrow(() -> new BusinessException(
                        "Aucune demande d'examen ne porte le code « " + code + " »."));
    }
}
