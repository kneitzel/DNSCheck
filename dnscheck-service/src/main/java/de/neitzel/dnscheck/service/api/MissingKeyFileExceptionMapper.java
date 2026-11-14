package de.neitzel.dnscheck.service.api;

import de.neitzel.dnscheck.core.key.MissingKeyFileException;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Translates a missing key file failure into HTTP 409 when a zone definition references a key file
 * that is not on disk.
 */
@Provider
public class MissingKeyFileExceptionMapper implements ExceptionMapper<MissingKeyFileException> {

    /**
     * Builds the conflict response for a zone definition whose key file is missing.
     *
     * @param exception Supplies the error message and the scoped path of the missing key file.
     * @return HTTP 409 response whose JSON body contains the message and the scoped key path.
     */
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
