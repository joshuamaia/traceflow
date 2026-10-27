package dev.traceflow.scenarios.simulated;

/** Thrown by the external payment API client when the provider answers with HTTP 503. */
public class PaymentProviderUnavailableException extends RuntimeException {
    public PaymentProviderUnavailableException(String message) {
        super(message);
    }
}
