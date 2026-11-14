package de.neitzel.dnscheck.service.api;

import de.neitzel.dnscheck.core.key.KeyInUseException;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Translates a key-in-use failure into HTTP 409 when a TSIG key is still referenced by zone catalog
 * entries.
 */
@Provider
public class KeyInUseExceptionMapper implements ExceptionMapper<KeyInUseException> {

    /**
     * Builds the conflict response for a TSIG key that zone catalog entries still reference.
     *
     * @param exception Supplies the error message and how many zone catalog entries reference the key.
     * @return HTTP 409 response whose JSON body contains the message and the reference count.
     */
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
