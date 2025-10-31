package dev.vality.adapter.common.mapper.model;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
@Getter
public enum MappingExceptions {

    RESULT_UNAVAILABLE("ResourceUnavailable"),
    RESULT_UNDEFINED("ResultUnknown"),
    RESULT_UNEXPECTED("ResultUnexpected");

    private final String mappingException;

}
