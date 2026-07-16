package de.neitzel.dnscheck.service.api;

import de.neitzel.dnscheck.core.key.MissingKeyFileException;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;

import java.util.LinkedHashMap;
import java.util.Map;

@Provider
public class MissingKeyFileExceptionMapper implements ExceptionMapper<MissingKeyFileException> {

    @Override
    public Response toResponse(MissingKeyFileException exception) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("message", exception.getMessage());
        body.put("scopedKeyPath", exception.scopedKeyPath());
        return Response.status(Response.Status.CONFLICT)
                .type(MediaType.APPLICATION_JSON)
                .entity(body)
                .build();
    }
}
