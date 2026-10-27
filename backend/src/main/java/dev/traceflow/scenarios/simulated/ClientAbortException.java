package dev.traceflow.scenarios.simulated;

import java.io.IOException;

/** Raised when the response cannot be written because the caller already closed the connection. */
public class ClientAbortException extends IOException {
    public ClientAbortException(String message) {
        super(message);
    }
}
