const state = {
  baseUrl: localStorage.getItem("paymentOrchestratorBaseUrl") || "",
  lastPaymentReference: localStorage.getItem("paymentOrchestratorLastPaymentReference") || "",
  recentPaymentReferences: JSON.parse(localStorage.getItem("paymentOrchestratorRecentPaymentReferences") || "[]"),
  sandboxPaymentReference: localStorage.getItem("paymentOrchestratorSandboxPaymentReference") || "",
  sandboxCheckoutUrl: localStorage.getItem("paymentOrchestratorSandboxCheckoutUrl") || "",
};

const elements = {
  apiBaseUrl: document.getElementById("apiBaseUrl"),
  saveBaseUrl: document.getElementById("saveBaseUrl"),
  checkHealth: document.getElementById("checkHealth"),
  demoMode: document.getElementById("demoMode"),
  endpointType: document.getElementById("endpointType"),
  concurrencyCount: document.getElementById("concurrencyCount"),
  amount: document.getElementById("amount"),
  currency: document.getElementById("currency"),
  paymentMethod: document.getElementById("paymentMethod"),
  paymentReference: document.getElementById("paymentReference"),
  recentPaymentReference: document.getElementById("recentPaymentReference"),
  createSandboxPayment: document.getElementById("createSandboxPayment"),
  openSandboxCheckout: document.getElementById("openSandboxCheckout"),
  sandboxStatusBadge: document.getElementById("sandboxStatusBadge"),
  sandboxCheckoutUrl: document.getElementById("sandboxCheckoutUrl"),
  idempotencyKey: document.getElementById("idempotencyKey"),
  batchMode: document.getElementById("batchMode"),
  demoForm: document.getElementById("demoForm"),
  runConcurrent: document.getElementById("runConcurrent"),
  fillUuid: document.getElementById("fillUuid"),
  statusMessage: document.getElementById("statusMessage"),
  responseBox: document.getElementById("responseBox"),
};

function setStatus(message) {
  elements.statusMessage.textContent = message;
}

function setResponse(payload) {
  elements.responseBox.textContent = typeof payload === "string" ? payload : JSON.stringify(payload, null, 2);
}

function saveState() {
  localStorage.setItem("paymentOrchestratorBaseUrl", state.baseUrl);
  localStorage.setItem("paymentOrchestratorLastPaymentReference", state.lastPaymentReference);
  localStorage.setItem("paymentOrchestratorRecentPaymentReferences", JSON.stringify(state.recentPaymentReferences));
  localStorage.setItem("paymentOrchestratorSandboxPaymentReference", state.sandboxPaymentReference);
  localStorage.setItem("paymentOrchestratorSandboxCheckoutUrl", state.sandboxCheckoutUrl);
}

function normalizeBaseUrl(value) {
  return value.trim().replace(/\/+$/, "");
}

function getBaseUrl() {
  const baseUrl = normalizeBaseUrl(elements.apiBaseUrl.value || state.baseUrl);
  if (!baseUrl) {
    throw new Error("Please enter the backend base URL first.");
  }
  state.baseUrl = baseUrl;
  saveState();
  return baseUrl;
}

function getBaseUrlIfSet() {
  const baseUrl = normalizeBaseUrl(elements.apiBaseUrl.value || state.baseUrl);
  return baseUrl || "";
}

function buildUrl(path) {
  return `${getBaseUrl()}${path}`;
}

function newIdempotencyKey() {
  return `demo-${crypto.randomUUID()}`;
}

function paymentBody() {
  return {
    amount: Number(elements.amount.value),
    currency: elements.currency.value.trim().toUpperCase(),
    paymentMethod: elements.paymentMethod.value,
  };
}

async function readResponse(response) {
  const contentType = response.headers.get("content-type") || "";
  if (contentType.includes("application/json")) {
    return response.json();
  }
  return response.text();
}

async function fetchJson(path, options = {}) {
  const { headers: optionHeaders = {}, ...requestOptions } = options;
  const response = await fetch(buildUrl(path), {
    ...requestOptions,
    headers: {
      Accept: "application/json",
      ...(requestOptions.body ? { "Content-Type": "application/json" } : {}),
      ...optionHeaders,
    },
  });
  const body = await readResponse(response);
  if (!response.ok) {
    const error = new Error(`HTTP ${response.status}`);
    error.status = response.status;
    error.body = body;
    throw error;
  }
  return body;
}

function extractData(payload) {
  return payload?.data ?? payload;
}

function updatePaymentReferenceFromResponse(payload) {
  const data = extractData(payload);
  if (data && typeof data === "object" && data.paymentReference) {
    state.lastPaymentReference = data.paymentReference;
    elements.paymentReference.value = data.paymentReference;
    rememberPaymentReference(data.paymentReference);
    saveState();
  }
}

function updateSandboxState(payload) {
  const data = extractData(payload);
  if (data && typeof data === "object") {
    if (data.paymentReference) {
      state.sandboxPaymentReference = data.paymentReference;
    }
    if (data.checkoutUrl) {
      state.sandboxCheckoutUrl = data.checkoutUrl;
    }
    saveState();
    renderSandboxUi();
  }
}

function getSelectedPaymentReference() {
  const selectedReference =
    elements.recentPaymentReference.value.trim() ||
    elements.paymentReference.value.trim() ||
    state.lastPaymentReference;
  return selectedReference || "";
}

function getSandboxReference() {
  return getSelectedPaymentReference() || state.sandboxPaymentReference || "";
}

function rememberPaymentReference(paymentReference) {
  if (!paymentReference) {
    return;
  }

  state.recentPaymentReferences = [
    paymentReference,
    ...state.recentPaymentReferences.filter(existingReference => existingReference !== paymentReference),
  ].slice(0, 8);

  renderPaymentReferenceOptions();
  elements.recentPaymentReference.value = paymentReference;
}

function renderPaymentReferenceOptions() {
  const knownReferences = state.recentPaymentReferences || [];
  elements.recentPaymentReference.innerHTML = [
    '<option value="">Recent payment references</option>',
    ...knownReferences.map(reference => `<option value="${reference}">${reference}</option>`),
  ].join("");
}

async function sendHealth() {
  return fetchJson("/actuator/health");
}

async function sendMetrics() {
  return fetchJson("/api/v1/observability/metrics");
}

async function sendPrometheus() {
  return fetchJson("/actuator/prometheus", { headers: { Accept: "text/plain" } });
}

async function sendCreatePayment(idempotencyKey) {
  return fetchJson("/api/v1/payments", {
    method: "POST",
    headers: { "Idempotency-Key": idempotencyKey },
    body: JSON.stringify(paymentBody()),
  });
}

async function sendCreateSandboxPayment() {
  return fetchJson("/api/v1/sandbox/payments", {
    method: "POST",
    body: JSON.stringify(paymentBody()),
  });
}

async function sendGetPayment() {
  const paymentReference =
    elements.recentPaymentReference.value.trim() ||
    elements.paymentReference.value.trim() ||
    state.lastPaymentReference;
  if (!paymentReference) {
    throw new Error("Payment reference is required. Create a payment first or paste a reference.");
  }
  elements.paymentReference.value = paymentReference;
  return fetchJson(`/api/v1/payments/${encodeURIComponent(paymentReference)}`);
}

async function runSingleEndpoint() {
  const endpoint = elements.endpointType.value;
  setStatus(`Sending ${endpoint.replaceAll("-", " ")} request...`);

  let result;
  if (endpoint === "health") {
    result = await sendHealth();
  } else if (endpoint === "create-payment") {
    const idempotencyKey = elements.idempotencyKey.value.trim() || newIdempotencyKey();
    elements.idempotencyKey.value = idempotencyKey;
    result = await sendCreatePayment(idempotencyKey);
    updatePaymentReferenceFromResponse(result);
  } else if (endpoint === "get-payment") {
    result = await sendGetPayment();
  } else if (endpoint === "metrics") {
    result = await sendMetrics();
  } else if (endpoint === "prometheus") {
    result = await sendPrometheus();
  } else {
    throw new Error(`Unsupported endpoint: ${endpoint}`);
  }

  setResponse(result);
  setStatus(`Completed ${endpoint.replaceAll("-", " ")} request.`);
}

async function runAllDemoApis() {
  setStatus("Running the full demo flow...");
  const steps = [];

  const health = await sendHealth();
  steps.push({ step: "health", response: health });

  const idempotencyKey = elements.idempotencyKey.value.trim() || newIdempotencyKey();
  elements.idempotencyKey.value = idempotencyKey;
  const created = await sendCreatePayment(idempotencyKey);
  updatePaymentReferenceFromResponse(created);
  steps.push({ step: "create-payment", response: created });

  const fetched = await sendGetPayment();
  steps.push({ step: "get-payment", response: fetched });

  const metrics = await sendMetrics();
  steps.push({ step: "metrics", response: metrics });

  const prometheus = await sendPrometheus();
  steps.push({ step: "prometheus", response: prometheus });

  setResponse(steps);
  setStatus("Full demo flow completed.");
}

async function runConcurrentBatch() {
  const count = Number(elements.concurrencyCount.value || "1");
  if (!Number.isInteger(count) || count < 1) {
    throw new Error("Concurrency count must be at least 1.");
  }

  const batchMode = elements.batchMode.value;
  const baseIdempotencyKey = elements.idempotencyKey.value.trim() || newIdempotencyKey();
  const keys = Array.from({ length: count }, (_, index) => (
    batchMode === "same-key" ? baseIdempotencyKey : `${baseIdempotencyKey}-${index + 1}`
  ));

  elements.idempotencyKey.value = baseIdempotencyKey;
  setStatus(`Sending ${count} concurrent create-payment requests...`);

  const startedAt = performance.now();
  const results = await Promise.allSettled(
    keys.map(async (idempotencyKey, index) => ({
      index: index + 1,
      idempotencyKey,
      response: await sendCreatePayment(idempotencyKey),
    }))
  );
  const finishedAt = performance.now();

  const payload = results.map((result, index) => {
    if (result.status === "fulfilled") {
      updatePaymentReferenceFromResponse(result.value.response);
      return {
        requestNumber: index + 1,
        idempotencyKey: keys[index],
        status: "fulfilled",
        response: result.value.response,
      };
    }

    return {
      requestNumber: index + 1,
      idempotencyKey: keys[index],
      status: "rejected",
      error: result.reason?.message || String(result.reason),
      details: result.reason?.body,
    };
  });

  setResponse({
    mode: batchMode,
    requests: count,
    durationMs: Math.round(finishedAt - startedAt),
    results: payload,
  });
  setStatus(`Concurrent batch finished in ${Math.round(finishedAt - startedAt)} ms.`);
}

function renderSandboxUi() {
  const sandboxReference = getSandboxReference();
  const baseUrl = getBaseUrlIfSet();
  const sandboxCheckoutUrl = sandboxReference && baseUrl
    ? `${baseUrl}/api/v1/sandbox/checkout/${encodeURIComponent(sandboxReference)}`
    : "";
  const hasSandboxCheckout = Boolean(sandboxCheckoutUrl);

  elements.openSandboxCheckout.disabled = !hasSandboxCheckout;
  elements.openSandboxCheckout.title = hasSandboxCheckout
    ? `Open ${sandboxReference || "sandbox payment"} checkout`
    : "Create a sandbox payment first";

  elements.sandboxStatusBadge.textContent = state.sandboxPaymentReference
    ? `Sandbox payment created: ${state.sandboxPaymentReference}`
    : "No sandbox payment yet";

  elements.sandboxCheckoutUrl.textContent = hasSandboxCheckout
    ? sandboxCheckoutUrl
    : "Checkout URL will appear here after you create a sandbox payment.";
}

function wireUi() {
  elements.apiBaseUrl.value = state.baseUrl;
  if (state.lastPaymentReference) {
    elements.paymentReference.value = state.lastPaymentReference;
  }
  renderPaymentReferenceOptions();
  renderSandboxUi();
  if (state.lastPaymentReference) {
    elements.recentPaymentReference.value = state.lastPaymentReference;
  }

  elements.saveBaseUrl.addEventListener("click", () => {
    try {
      state.baseUrl = getBaseUrl();
      setStatus(`Saved backend URL: ${state.baseUrl}`);
      setResponse({ message: "Backend URL saved locally in your browser." });
    } catch (error) {
      setStatus(error.message);
    }
  });

  elements.checkHealth.addEventListener("click", async () => {
    try {
      setStatus("Checking backend health...");
      const response = await sendHealth();
      setResponse(response);
      setStatus("Backend health check complete.");
    } catch (error) {
      setStatus(error.message || "Health check failed.");
      setResponse(error.body || { error: error.message });
    }
  });

  elements.fillUuid.addEventListener("click", () => {
    elements.idempotencyKey.value = newIdempotencyKey();
    setStatus("Generated a new idempotency key.");
  });

  elements.createSandboxPayment.addEventListener("click", async () => {
    try {
      setStatus("Creating sandbox payment...");
      const response = await sendCreateSandboxPayment();
      updateSandboxState(response);
      setResponse(response);
      setStatus(`Sandbox payment created: ${state.sandboxPaymentReference}`);
    } catch (error) {
      setStatus(error.message || "Sandbox payment creation failed.");
      setResponse(error.body || { error: error.message });
    }
  });

  elements.openSandboxCheckout.addEventListener("click", () => {
    const sandboxReference = getSandboxReference();
    if (!sandboxReference) {
      setStatus("Create a sandbox payment first.");
      return;
    }
    let sandboxCheckoutUrl;
    try {
      sandboxCheckoutUrl = `${getBaseUrl()}/api/v1/sandbox/checkout/${encodeURIComponent(sandboxReference)}`;
    } catch (error) {
      setStatus(error.message);
      return;
    }
    window.open(sandboxCheckoutUrl, "_blank", "noopener,noreferrer");
    state.sandboxCheckoutUrl = sandboxCheckoutUrl;
    saveState();
    renderSandboxUi();
    setStatus(`Opened sandbox checkout for ${sandboxReference}.`);
  });

  elements.demoForm.addEventListener("submit", async event => {
    event.preventDefault();
    try {
      const mode = elements.demoMode.value;
      if (mode === "all") {
        await runAllDemoApis();
      } else {
        await runSingleEndpoint();
      }
    } catch (error) {
      setStatus(error.message || "Request failed.");
      setResponse(error.body || { error: error.message });
    }
  });

  elements.runConcurrent.addEventListener("click", async () => {
    try {
      await runConcurrentBatch();
    } catch (error) {
      setStatus(error.message || "Concurrent request batch failed.");
      setResponse(error.body || { error: error.message });
    }
  });

  elements.endpointType.addEventListener("change", () => {
    const endpoint = elements.endpointType.value;
    elements.runConcurrent.disabled = endpoint !== "create-payment";
    elements.concurrencyCount.disabled = endpoint !== "create-payment";
    elements.batchMode.disabled = endpoint !== "create-payment";
  });

  elements.endpointType.dispatchEvent(new Event("change"));
  elements.recentPaymentReference.addEventListener("change", () => {
    if (elements.recentPaymentReference.value) {
      elements.paymentReference.value = elements.recentPaymentReference.value;
    }
    renderSandboxUi();
  });

  elements.paymentReference.addEventListener("input", renderSandboxUi);
}

wireUi();
