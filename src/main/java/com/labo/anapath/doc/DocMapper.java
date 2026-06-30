package com.labo.anapath.doc;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.ReportingPolicy;

@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.IGNORE)
public interface DocMapper {

    @Mapping(target = "documentationCategoryId", source = "documentationCategory.id")
    @Mapping(target = "userId", source = "user.id")
    @Mapping(target = "roleId", source = "role.id")
    DocResponseDto toResponseDto(Doc doc);
}
