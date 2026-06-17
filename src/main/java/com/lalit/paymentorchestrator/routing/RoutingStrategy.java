package com.lalit.paymentorchestrator.routing;

import com.lalit.paymentorchestrator.enums.PaymentMethod;

public interface RoutingStrategy {
    PaymentRoute route(PaymentMethod paymentMethod);
}
