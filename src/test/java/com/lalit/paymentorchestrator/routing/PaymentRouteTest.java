package com.lalit.paymentorchestrator.routing;

import com.lalit.paymentorchestrator.enums.PaymentProviderType;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class PaymentRouteTest {

    @Test
    void candidatesShouldReturnPrimaryProviderFirst() {
        PaymentRoute route = new PaymentRoute(PaymentProviderType.PROVIDER_A, List.of(PaymentProviderType.PROVIDER_B));

        assertEquals(List.of(PaymentProviderType.PROVIDER_A, PaymentProviderType.PROVIDER_B), route.candidates());
    }

    @Test
    void candidatesShouldBeImmutable() {
        PaymentRoute route = new PaymentRoute(PaymentProviderType.PROVIDER_A, List.of(PaymentProviderType.PROVIDER_B));

        assertThrows(UnsupportedOperationException.class, () -> route.candidates().add(PaymentProviderType.PROVIDER_A));
    }
}
