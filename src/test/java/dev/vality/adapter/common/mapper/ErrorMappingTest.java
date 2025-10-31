package dev.vality.adapter.common.mapper;

import dev.vality.adapter.common.mapper.model.Error;
import dev.vality.woody.api.flow.error.WRuntimeException;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

public class ErrorMappingTest {

    @Test
    public void errorMappingTest() {
        var error = new Error();
        error.setMapping("authorization_failed:unknown");
        error.setCodeRegex("00001");
        error.setDescriptionRegex("Invalid Merchant ID");
        var error1 = new Error();
        error1.setMapping("authorization_failed:provider_malfunction");
        error1.setCodeRegex("00002");
        error1.setDescriptionRegex("Invalid Merchant Name");
        var errorMapping = new ErrorMapping("'%s' - '%s'", List.of(error, error1));
        assertThrows(WRuntimeException.class, () -> errorMapping.mapFailure("unknown"));
        assertThrows(WRuntimeException.class, () -> errorMapping.mapFailure("00001"));

        var error2 = new Error();
        error2.setMapping("authorization_failed:insufficient_funds");
        error2.setCodeRegex("00001");
        var errorMappingFull = new ErrorMapping("'%s' - '%s'", List.of(error, error1, error2));
        assertEquals(
                "Failure(code:authorization_failed, reason:'00001' - 'null', sub:SubFailure(code:insufficient_funds))",
                errorMappingFull.mapFailure("00001").toString());
    }

    @Test
    public void nonAsciiTest() {
        var errorMappingFull = new ErrorMapping("'%s' - '%s'", List.of());
        var exception = assertThrows(WRuntimeException.class, () -> errorMappingFull.mapFailure("ы"));
        assertEquals("code = base64:0Ys, description = null", exception.getErrorDefinition().getErrorReason());
    }

    @Test
    public void testNullDesc() {
        Error error = new Error();
        error.setCodeRegex("01");
        error.setDescriptionRegex("desc");
        error.setMapping("authorization_failed:unknown");
        var errorMapping = new ErrorMapping("'%s' - '%s'", List.of(error));
        assertThrows(WRuntimeException.class, () -> errorMapping.mapFailure("01"));

        error.setDescriptionRegex(null);
        assertNotNull(errorMapping.mapFailure("01"));
    }
}
