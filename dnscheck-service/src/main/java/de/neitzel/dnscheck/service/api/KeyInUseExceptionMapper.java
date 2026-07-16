package de.neitzel.dnscheck.service.api;

import de.neitzel.dnscheck.core.key.KeyInUseException;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;

import java.util.LinkedHashMap;
import java.util.Map;

@Provider
public class KeyInUseExceptionMapper implements ExceptionMapper<KeyInUseException> {

    @Override
    public Response toResponse(KeyInUseException exception) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("message", exception.getMessage());
        body.put("referencedByCount", exception.referencedByCount());
        return Response.status(Response.Status.CONFLICT)
                .type(MediaType.APPLICATION_JSON)
                .entity(body)
                .build();
    }
}
