package dev.vality.adapter.common.v2.mapper;

import com.fasterxml.jackson.core.JsonParseException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.vality.adapter.common.v2.exception.ErrorMappingException;
import dev.vality.adapter.common.v2.mapper.model.Error;
import dev.vality.adapter.common.v2.mapper.model.MappingExceptions;
import dev.vality.damsel.domain.Failure;
import dev.vality.geck.serializer.kit.tbase.TErrorUtil;
import dev.vality.woody.api.flow.error.*;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.CharsetEncoder;
import java.nio.charset.StandardCharsets;
import java.util.*;

public class ErrorMapping {

    private static final String DEFAULT_PATTERN_REASON = "'%s' - '%s'";
    private static final String DEFAULT_BASE64_PREFIX = "base64:";
    private static final CharsetEncoder ASCII_ENCODER = StandardCharsets.US_ASCII.newEncoder();

    /**
     * Pattern for reason failure
     */
    private final String patternReason;

    private final List<Error> errors;

    public ErrorMapping(InputStream inputStream) {
        this(inputStream, DEFAULT_PATTERN_REASON);
    }

    public ErrorMapping(InputStream inputStream, String patternReason) {
        this(inputStream, patternReason, new ObjectMapper());
    }

    public ErrorMapping(InputStream inputStream, String patternReason, ObjectMapper objectMapper) {
        this(patternReason, initErrorList(inputStream, objectMapper));
    }

    public ErrorMapping(String patternReason, List<Error> errors) {
        this.patternReason = patternReason;
        this.errors = errors;
    }

    public Failure mapFailure(String code) {
        return mapFailure(code, null, null);
    }

    public Failure mapFailure(String code, String description) {
        return mapFailure(code, description, null);
    }

    public Failure mapFailure(String code, String description, String state) {
        var error = findError(code, description, state);
        checkWoodyError(error, code, description);
        var failure = TErrorUtil.toGeneral(error.getMapping());
        failure.setReason(prepareReason(code, description));
        return failure;
    }

    private static List<Error> initErrorList(InputStream inputStream, ObjectMapper objectMapper) {
        try {
            return objectMapper.readValue(
                    inputStream,
                    new TypeReference<>() {
                    });
        } catch (JsonParseException e) {
            throw new ErrorMappingException("Json can't parse data from file", e);
        } catch (JsonMappingException e) {
            throw new ErrorMappingException("Json can't mapping data from file", e);
        } catch (IOException e) {
            throw new ErrorMappingException("Failed to initErrorList", e);
        }
    }

    private Error findError(String code, String description, String state) {
        return findErrorInConfig(code, description, state)
                .orElseThrow(() -> getUnexpectedError(code, description, state));
    }

    private Optional<Error> findErrorInConfig(String code, String description, String state) {
        Objects.requireNonNull(code, "Code must be set");
        return errors.stream()
                .filter(e -> matchError(e, code, description, state))
                .findFirst();
    }

    private boolean matchNullableStrings(String str, String regex) {
        if (regex == null) {
            return true;
        }
        str = Objects.requireNonNullElse(str, "");
        return str.matches(regex);
    }

    private boolean equalsNullableStrings(String str1, String str2) {
        if (str1 == null || str2 == null) {
            return true;
        }
        return str1.equals(str2);
    }

    private boolean matchError(Error error, String code, String description, String state) {
        return code.matches(error.getCodeRegex())
                && matchNullableStrings(description, error.getDescriptionRegex())
                && equalsNullableStrings(state, error.getState());
    }

    /**
     * Prepare reason for {@link Failure}
     *
     * @param code        String
     * @param description String
     * @return String
     */
    private String prepareReason(String code, String description) {
        return String.format(this.patternReason, code, description);
    }

    private void checkWoodyError(Error error, String code, String description) {
        if (MappingExceptions.RESULT_UNDEFINED.getMappingException().equals(error.getMapping())) {
            throw new WUndefinedResultException(
                    String.format("Undefined result %s, code = %s, description = %s", error, code, description));
        } else if (MappingExceptions.RESULT_UNAVAILABLE.getMappingException().equals(error.getMapping())) {
            throw new WUnavailableResultException(
                    String.format("Unavailable result %s, code = %s, description = %s", error, code, description));
        } else if (MappingExceptions.RESULT_UNEXPECTED.getMappingException().equals(error.getMapping())) {
            throw getUnexpectedError(code, description, null);
        }
    }

    private WRuntimeException getUnexpectedError(String code, String description, String state) {
        var errorMessage = String.format("Unexpected result, code = %s, description = %s, state = %s",
                code, description, state);
        var errorDefinition = new WErrorDefinition(WErrorSource.INTERNAL);
        errorDefinition.setErrorType(WErrorType.UNEXPECTED_ERROR);
        errorDefinition.setErrorReason(String.format("code = %s, description = %s",
                makeCompatibleWithHttpHeader(code),
                makeCompatibleWithHttpHeader(description)));
        return new WRuntimeException(errorMessage, errorDefinition);
    }

    private String makeCompatibleWithHttpHeader(String text) {
        if (text == null || !containsNonAsciiSymbols(text)) {
            return text;
        }
        return DEFAULT_BASE64_PREFIX +
                Base64.getEncoder().withoutPadding().encodeToString(text.getBytes(StandardCharsets.UTF_8));
    }

    private boolean containsNonAsciiSymbols(String text) {
        for (char c : text.toCharArray()) {
            if (!ASCII_ENCODER.canEncode(c)) {
                return true;
            }
        }
        return false;
    }
}
