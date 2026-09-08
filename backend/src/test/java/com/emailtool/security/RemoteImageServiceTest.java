package com.emailtool.security;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

class RemoteImageServiceTest {

    private final RemoteImageService service = new RemoteImageService(null, null);

    @Test
    void rejectsNonHttpSchemes() {
        assertThrows(ResponseStatusException.class, () -> service.validateUrl("file:///etc/passwd"));
        assertThrows(ResponseStatusException.class, () -> service.validateUrl("javascript:alert(1)"));
    }

    @Test
    void rejectsLoopback() {
        ResponseStatusException ex =
                assertThrows(ResponseStatusException.class, () -> service.validateUrl("http://127.0.0.1/x.png"));
        assertEquals(400, ex.getStatusCode().value());
    }
}
