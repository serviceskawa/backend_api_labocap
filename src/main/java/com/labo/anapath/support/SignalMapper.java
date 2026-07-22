package com.labo.anapath.support;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.ReportingPolicy;

@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.IGNORE)
public interface SignalMapper {

    @Mapping(target = "testOrderId", source = "testOrder.id")
    @Mapping(target = "testOrderCode", source = "testOrder.code")
    @Mapping(target = "userId", source = "user.id")
    @Mapping(target = "userName", expression = "java(signal.getUser() != null ? "
            + "(signal.getUser().getLastname() + \" \" + signal.getUser().getFirstname()).trim() : null)")
    SignalResponseDto toResponseDto(Signal signal);
}
