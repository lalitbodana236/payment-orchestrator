package com.lalit.paymentorchestrator.service;

import com.lalit.paymentorchestrator.dto.PaymentRequest;
import com.lalit.paymentorchestrator.dto.SandboxWebhookRequest;
import com.lalit.paymentorchestrator.entity.PaymentEntity;
import com.lalit.paymentorchestrator.enums.PaymentStatus;
import com.lalit.paymentorchestrator.enums.SandboxPaymentOutcome;
import com.lalit.paymentorchestrator.exception.PaymentNotFoundException;
import com.lalit.paymentorchestrator.metrics.PaymentMetricsRecorder;
import com.lalit.paymentorchestrator.repository.PaymentRepository;
import com.lalit.paymentorchestrator.routing.RoutingStrategy;
import com.lalit.paymentorchestrator.util.PaymentReferenceGenerator;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class SandboxPaymentService {

    private final PaymentRepository paymentRepository;
    private final RoutingStrategy routingStrategy;
    private final PaymentReferenceGenerator paymentReferenceGenerator;
    private final PaymentMetricsRecorder metricsRecorder;

    public SandboxPaymentService(PaymentRepository paymentRepository,
                                 RoutingStrategy routingStrategy,
                                 PaymentReferenceGenerator paymentReferenceGenerator,
                                 PaymentMetricsRecorder metricsRecorder) {
        this.paymentRepository = paymentRepository;
        this.routingStrategy = routingStrategy;
        this.paymentReferenceGenerator = paymentReferenceGenerator;
        this.metricsRecorder = metricsRecorder;
    }

    @Transactional
    public PaymentEntity createSandboxPayment(PaymentRequest request) {
        var sample = metricsRecorder.startSample();
        try {
            PaymentEntity paymentEntity = PaymentEntity.builder()
                    .paymentReference(paymentReferenceGenerator.nextReference())
                    .amount(request.amount())
                    .currency(request.currency())
                    .paymentMethod(request.paymentMethod())
                    .provider(routingStrategy.route(request.paymentMethod()).primaryProvider())
                    .status(PaymentStatus.PENDING)
                    .retryCount(0)
                    .build();
            PaymentEntity saved = paymentRepository.save(paymentEntity);
            metricsRecorder.recordApiLatency(sample, "sandbox_create_payment", "success");
            metricsRecorder.incrementPaymentOutcome("sandbox_pending", saved.getProvider());
            return saved;
        } catch (RuntimeException exception) {
            metricsRecorder.recordApiLatency(sample, "sandbox_create_payment", "failure");
            throw exception;
        }
    }

    @Transactional
    public PaymentEntity completeSandboxPayment(String paymentReference, SandboxWebhookRequest request) {
        var sample = metricsRecorder.startSample();
        try {
            PaymentEntity paymentEntity = paymentRepository.findByPaymentReference(paymentReference)
                    .orElseThrow(() -> new PaymentNotFoundException(paymentReference));
            paymentEntity.setStatus(resolveStatus(request.outcome()));
            paymentEntity.setFailureReason(request.outcome() == SandboxPaymentOutcome.SUCCESS ? null : request.failureReason());
            PaymentEntity saved = paymentRepository.save(paymentEntity);
            metricsRecorder.recordApiLatency(sample, "sandbox_webhook", "success");
            metricsRecorder.incrementPaymentOutcome(request.outcome().name().toLowerCase(), saved.getProvider());
            return saved;
        } catch (RuntimeException exception) {
            metricsRecorder.recordApiLatency(sample, "sandbox_webhook", "failure");
            throw exception;
        }
    }

    @Transactional(readOnly = true)
    public PaymentEntity getSandboxPayment(String paymentReference) {
        return paymentRepository.findByPaymentReference(paymentReference)
                .orElseThrow(() -> new PaymentNotFoundException(paymentReference));
    }

    public String renderCheckoutPage(PaymentEntity paymentEntity) {
        String paymentReference = escapeHtml(paymentEntity.getPaymentReference());
        String amount = escapeHtml(paymentEntity.getAmount().toPlainString());
        String currency = escapeHtml(paymentEntity.getCurrency());
        String paymentMethod = escapeHtml(paymentEntity.getPaymentMethod().name());
        String provider = escapeHtml(paymentEntity.getProvider() == null ? "UNKNOWN" : paymentEntity.getProvider().name());

        return """
                <!doctype html>
                <html lang="en">
                <head>
                  <meta charset="utf-8">
                  <meta name="viewport" content="width=device-width, initial-scale=1">
                  <title>Sandbox Checkout</title>
                  <style>
                    body { margin: 0; font-family: Inter, Arial, sans-serif; background: #0b1020; color: #e7ecff; }
                    .wrap { max-width: 760px; margin: 40px auto; padding: 24px; }
                    .card { background: #11172b; border: 1px solid rgba(255,255,255,0.08); border-radius: 20px; padding: 24px; }
                    .meta { color: #a7b0d6; }
                    .row { display: grid; gap: 12px; grid-template-columns: repeat(2, minmax(0, 1fr)); margin-top: 18px; }
                    .panel { background: rgba(255,255,255,0.04); border: 1px solid rgba(255,255,255,0.08); border-radius: 16px; padding: 16px; }
                    button { border: 0; border-radius: 12px; padding: 12px 16px; font-weight: 700; cursor: pointer; }
                    .success { background: linear-gradient(135deg, #7c9cff, #9cb4ff); color: #09111f; }
                    .fail { background: #1d2947; color: #e7ecff; }
                    pre { white-space: pre-wrap; word-break: break-word; background: rgba(0,0,0,0.32); padding: 16px; border-radius: 16px; }
                    .actions { display: flex; gap: 12px; flex-wrap: wrap; margin-top: 18px; }
                    .hint { margin-top: 12px; color: #a7b0d6; }
                  </style>
                </head>
                <body>
                  <div class="wrap">
                    <div class="card">
                      <div class="meta">Sandbox provider checkout</div>
                      <h1>Payment reference %s</h1>
                      <p class="meta">This page simulates a hosted checkout page from a payment provider.</p>
                      <div class="row">
                        <div class="panel"><strong>Provider</strong><div>%s</div></div>
                        <div class="panel"><strong>Status</strong><div id="status">%s</div></div>
                        <div class="panel"><strong>Amount</strong><div>%s %s</div></div>
                        <div class="panel"><strong>Payment method</strong><div>%s</div></div>
                      </div>
                      <div class="actions">
                        <button class="success" onclick="submitOutcome('SUCCESS')">Simulate Success</button>
                        <button class="fail" onclick="submitOutcome('FAILED')">Simulate Failure</button>
                      </div>
                      <p class="hint">Webhook target: /api/v1/sandbox/webhooks/payments/%s</p>
                      <pre id="result">Waiting for webhook...</pre>
                    </div>
                  </div>
                  <script>
                    async function submitOutcome(outcome) {
                      const payload = {
                        outcome,
                        failureReason: outcome === 'FAILED' ? 'Sandbox provider reported a failure' : null
                      };
                      const response = await fetch('/api/v1/sandbox/webhooks/payments/%s', {
                        method: 'POST',
                        headers: { 'Content-Type': 'application/json' },
                        body: JSON.stringify(payload)
                      });
                      const data = await response.json();
                      document.getElementById('result').textContent = JSON.stringify(data, null, 2);
                      document.getElementById('status').textContent = data.data?.status || outcome;
                    }
                  </script>
                </body>
                </html>
                """.formatted(paymentReference, provider, paymentEntity.getStatus().name(), amount, currency, paymentMethod,
                paymentReference, paymentReference);
    }

    private PaymentStatus resolveStatus(SandboxPaymentOutcome outcome) {
        return outcome == SandboxPaymentOutcome.SUCCESS ? PaymentStatus.SUCCESS : PaymentStatus.FAILED;
    }

    private String escapeHtml(String value) {
        return value
                .replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&#39;");
    }
}
