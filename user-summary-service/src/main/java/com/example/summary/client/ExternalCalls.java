package com.example.summary.client;

import com.example.summary.client.ExternalServiceException.Reason;
import org.springframework.http.HttpStatusCode;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.net.SocketTimeoutException;
import java.net.http.HttpTimeoutException;
import java.util.function.Function;

/**
 * A class for the external REST calls.
 */

final class ExternalCalls {

    private ExternalCalls() {
    }

    /**
     * Executes the request and extracts the body.
     */
    static <T> T call(String service, RestClient.RequestHeadersSpec<?> request,
                      Function<RestClient.ResponseSpec, T> bodyExtractor) {
        T body;
        try {
            RestClient.ResponseSpec response = request.retrieve()
                    .onStatus(HttpStatusCode::isError, (req, res) -> {
                        int status = res.getStatusCode().value();
                        throw new ExternalServiceException(retrieveReason(status), status,
                                service + " service responded with HTTP " + status, null);
                    });
            body = bodyExtractor.apply(response);
        } catch (ResourceAccessException e) {
            Reason reason = isTimeout(e) ? Reason.TIMEOUT : Reason.CONNECTION_FAILED;
            throw new ExternalServiceException(reason, ExternalServiceException.NO_STATUS,
                    service + " service call failed: " + reason, e);
        } catch (RestClientException ex) {
            throw new ExternalServiceException(Reason.INVALID_RESPONSE, ExternalServiceException.NO_STATUS,
                    service + " service returned an invalid response", ex);
        }
        if (body == null) {
            throw new ExternalServiceException(Reason.INVALID_RESPONSE, ExternalServiceException.NO_STATUS,
                    service + " service returned an empty body", null);
        }
        return body;
    }

    private static Reason retrieveReason(int status) {
        if (status == 404) {
            return Reason.NOT_FOUND;
        }
        return status >= 500 ? Reason.SERVER_ERROR : Reason.CLIENT_ERROR;
    }

    private static boolean isTimeout(Throwable ex) {
        for (Throwable t = ex; t != null; t = t.getCause()) {
            if (t instanceof HttpTimeoutException || t instanceof SocketTimeoutException) {
                return true;
            }
        }
        return false;
    }

}
