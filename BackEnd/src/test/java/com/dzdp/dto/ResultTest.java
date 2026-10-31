package com.dzdp.dto;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ResultTest {

    @Test
    void successUsesZeroCode() {
        Result result = Result.ok("data");

        assertEquals(0, result.getCode());
        assertTrue(result.getSuccess());
    }

    @Test
    void requestedStringConstructorMapsKnownStatusCode() {
        Result result = new Result("该功能未实现");

        assertEquals(StatusCode.NOT_IMPLEMENTED.getCode(), result.getCode());
        assertFalse(result.getSuccess());
    }

    @Test
    void unknownMessageFallsBackToBusinessError() {
        Result result = Result.fail("未知业务错误");

        assertEquals(StatusCode.BUSINESS_ERROR.getCode(), result.getCode());
        assertFalse(result.getSuccess());
    }
}
