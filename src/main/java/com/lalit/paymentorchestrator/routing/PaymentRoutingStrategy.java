package com.lalit.paymentorchestrator.routing;

import com.lalit.paymentorchestrator.enums.PaymentMethod;
import com.lalit.paymentorchestrator.enums.PaymentProviderType;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class PaymentRoutingStrategy implements RoutingStrategy {

    @Override
    public PaymentRoute route(PaymentMethod paymentMethod) {
        return switch (paymentMethod) {
            case CARD -> new PaymentRoute(PaymentProviderType.PROVIDER_A, List.of(PaymentProviderType.PROVIDER_B));
            case UPI -> new PaymentRoute(PaymentProviderType.PROVIDER_B, List.of(PaymentProviderType.PROVIDER_A));
        };
    }
}
