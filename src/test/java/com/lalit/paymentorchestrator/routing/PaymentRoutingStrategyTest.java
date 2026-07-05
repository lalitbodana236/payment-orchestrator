package com.lalit.paymentorchestrator.routing;

import com.lalit.paymentorchestrator.enums.PaymentMethod;
import com.lalit.paymentorchestrator.enums.PaymentProviderType;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class PaymentRoutingStrategyTest {

    private final PaymentRoutingStrategy routingStrategy = new PaymentRoutingStrategy();

    @Test
    void debitCardShouldRouteToProviderAFirst() {
        assertRoute(PaymentMethod.DEBIT_CARD, PaymentProviderType.PROVIDER_A, PaymentProviderType.PROVIDER_B);
    }

    @Test
    void creditCardShouldRouteToProviderAFirst() {
        assertRoute(PaymentMethod.CREDIT_CARD, PaymentProviderType.PROVIDER_A, PaymentProviderType.PROVIDER_B);
    }

    @Test
    void netBankingShouldRouteToProviderAFirst() {
        assertRoute(PaymentMethod.NET_BANKING, PaymentProviderType.PROVIDER_A, PaymentProviderType.PROVIDER_B);
    }

    @Test
    void upiShouldRouteToProviderBFirst() {
        assertRoute(PaymentMethod.UPI, PaymentProviderType.PROVIDER_B, PaymentProviderType.PROVIDER_A);
    }

    private void assertRoute(PaymentMethod method,
                             PaymentProviderType primary,
                             PaymentProviderType failover) {
        PaymentRoute route = routingStrategy.route(method);
        assertEquals(primary, route.primaryProvider());
        assertEquals(List.of(primary, failover), route.candidates());
    }
}
