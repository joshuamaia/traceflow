package dev.traceflow.scenarios.simulated;

import java.util.Map;

import dev.traceflow.scenarios.engine.ScenarioContext;
import dev.traceflow.scenarios.engine.Span;

/**
 * HTTP client of a third party payment provider. The provider does not share our logs, so it appears as a declared
 * peer ({@code peerService=external-payment-api}) of the client spans. In the scenario the provider is unavailable.
 */
public final class ExternalPaymentApiClient {

    public static final String PEER = "external-payment-api";
    private static final String LOGGER = ExternalPaymentApiClient.class.getName();

    private final ScenarioContext ctx;
    private int attempt;

    public ExternalPaymentApiClient(ScenarioContext ctx) {
        this.ctx = ctx;
    }

    public String charge(Span parent, String orderId) {
        attempt++;
        Span call = ctx.startSpan(OrderService.SERVICE, "service", "POST https://payments.example.com/v2/charges",
                "CLIENT", parent).peer(PEER, "external");
        call.debug(LOGGER, "Calling external-payment-api for order " + orderId + " (attempt " + attempt + ")");
        ctx.advance(120);
        call.end("ERROR", LOGGER, "external-payment-api responded 503 Service Unavailable", 503, null,
                Map.of("attempt", attempt, "http.url", "https://payments.example.com/v2/charges"));
        throw new PaymentProviderUnavailableException("503 Service Unavailable from external-payment-api (attempt "
                + attempt + ")");
    }

    public int attempts() {
        return attempt;
    }
}
