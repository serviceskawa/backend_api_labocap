package com.labo.anapath.testorder;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.ReportingPolicy;

/**
 * Mapper MapStruct pour la conversion entre l'entité {@link TestOrder} et les DTOs de réponse.
 *
 * <p>Les associations JPA (patient, médecin, hôpital, contrat, type de bon) sont aplaties
 * vers des champs scalaires (id, nom) dans le DTO afin d'éviter les sérialisations profondes.
 */
@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.IGNORE)
public interface TestOrderMapper {

    /**
     * Convertit un {@link TestOrder} en {@link TestOrderResponseDto} en aplatissant
     * les associations vers leurs champs id/nom respectifs.
     *
     * @param testOrder l'entité source
     * @return le DTO de réponse correspondant
     */
    @Mapping(target = "patientId", source = "patient.id")
    @Mapping(target = "patientFirstname", source = "patient.firstname")
    @Mapping(target = "patientLastname", source = "patient.lastname")
    @Mapping(target = "doctorId", source = "doctor.id")
    @Mapping(target = "doctorName", source = "doctor.name")
    @Mapping(target = "hospitalId", source = "hospital.id")
    @Mapping(target = "hospitalName", source = "hospital.name")
    @Mapping(target = "contratId", source = "contrat.id")
    @Mapping(target = "contratName", source = "contrat.name")
    @Mapping(target = "typeOrderId", source = "typeOrder.id")
    @Mapping(target = "typeOrderTitle", source = "typeOrder.title")
    @Mapping(target = "archive", source = "archive")
    @Mapping(target = "testAffiliate", source = "testAffiliate")
    @Mapping(target = "reportId", ignore = true)
    @Mapping(target = "reportStatus", ignore = true)
    @Mapping(target = "reportIsDelivered", ignore = true)
    @Mapping(target = "invoiceId", ignore = true)
    TestOrderResponseDto toResponseDto(TestOrder testOrder);

    /**
     * Convertit un {@link DetailTestOrder} en {@link DetailTestOrderDto}.
     *
     * @param detail le détail d'analyse source
     * @return le DTO correspondant
     */
    @Mapping(target = "labTestId", source = "labTest.id")
    @Mapping(target = "labTestName", source = "labTest.name")
    DetailTestOrderDto toDetailDto(DetailTestOrder detail);
}
