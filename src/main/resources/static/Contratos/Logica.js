(function () {
  const params = new URLSearchParams(location.search);
  const CONTRACT_STORAGE_KEY = "haro_contract_access_payload";
  const PAYMENT_STATUS_STORAGE_KEY = "haro_payment_status_summary";
  const API_BASE_DEFAULT = "https://harorepositoty2-590358146556.europe-west1.run.app";
  const rootWrap = document.querySelector(".wrap");

  function readStoredAccess() {
    try {
      const raw = sessionStorage.getItem(CONTRACT_STORAGE_KEY);
      if (!raw) return {};
      const parsed = JSON.parse(raw);
      return parsed && typeof parsed === "object" ? parsed : {};
    } catch {
      return {};
    }
  }

  function saveStoredAccess(payload) {
    try {
      sessionStorage.setItem(CONTRACT_STORAGE_KEY, JSON.stringify(payload || {}));
    } catch {
      // ignore
    }
  }

  function readStoredPaymentStatus() {
    try {
      const raw = localStorage.getItem(PAYMENT_STATUS_STORAGE_KEY);
      if (!raw) return {};
      const parsed = JSON.parse(raw);
      return parsed && typeof parsed === "object" ? parsed : {};
    } catch {
      return {};
    }
  }

  function escapeHtml(value) {
    return String(value || "")
      .replace(/&/g, "&amp;")
      .replace(/</g, "&lt;")
      .replace(/>/g, "&gt;")
      .replace(/\"/g, "&quot;")
      .replace(/'/g, "&#39;");
  }

  function firstNotBlank() {
    for (let i = 0; i < arguments.length; i++) {
      const value = arguments[i];
      if (value === null || value === undefined) continue;
      const s = String(value).trim();
      if (s) return s;
    }
    return "";
  }

  function sanitizeApiBase(raw) {
    const candidate = String(raw || "").trim();
    if (!candidate) return "";
    try {
      const url = new URL(candidate);
      if (!/^https?:$/i.test(url.protocol)) return "";
      return (url.origin + url.pathname).replace(/\/+$/, "");
    } catch {
      return "";
    }
  }

  function uniqueValues(values) {
    const out = [];
    values.forEach((value) => {
      const v = firstNotBlank(value);
      if (v && !out.includes(v)) out.push(v);
    });
    return out;
  }

  function normalizePaymentMethod(raw) {
    const value = firstNotBlank(raw);
    if (!value) return "";
    const upper = value
      .toUpperCase()
      .normalize("NFD")
      .replace(/[\u0300-\u036f]/g, "")
      .replace(/[^A-Z0-9]+/g, "_")
      .replace(/^_+|_+$/g, "");

    if (!upper) return "";
    if (upper.includes("PSE") || upper.includes("BANK") || upper.includes("BANCO")) return "PSE";
    if (upper.includes("CREDITO")) return "TARJETA_CREDITO";
    if (upper.includes("DEBITO")) return "TARJETA_DEBITO";
    if (upper.includes("TARJETA") || upper.includes("VISA") || upper.includes("MASTERCARD") || upper.includes("AMEX")) return "TARJETA";
    if (upper.includes("TRANSFER")) return "TRANSFERENCIA";
    if (upper.includes("NEQUI") || upper.includes("DAVIPLATA") || upper.includes("BILLETERA")) return "BILLETERA_DIGITAL";
    if (upper.includes("EFECTIVO")) return "EFECTIVO";
    return upper;
  }

  function combinePaymentNote() {
    return uniqueValues(Array.from(arguments)).join(" | ");
  }

  function toIsoDate(raw) {
    const value = firstNotBlank(raw);
    if (!value) return "";
    if (/^\d{4}-\d{2}-\d{2}$/.test(value)) return value;
    const dmyMatch = value.match(/^(\d{2})\/(\d{2})\/(\d{4})$/);
    if (dmyMatch) return dmyMatch[3] + "-" + dmyMatch[2] + "-" + dmyMatch[1];
    return "";
  }

  function toDisplayDate(raw) {
    const value = firstNotBlank(raw);
    if (!value) return "";
    if (/^\d{2}\/\d{2}\/\d{4}$/.test(value)) return value;
    const isoMatch = value.match(/^(\d{4})-(\d{2})-(\d{2})$/);
    if (isoMatch) return isoMatch[3] + "/" + isoMatch[2] + "/" + isoMatch[1];
    return "";
  }

  const storedAccess = readStoredAccess();
  const storedPaymentStatus = readStoredPaymentStatus();
  const contractEmail = firstNotBlank(params.get("email"), storedAccess.email).toLowerCase();
  const contractCode = firstNotBlank(params.get("code"), params.get("token"), storedAccess.code);
  const apiBaseParam = firstNotBlank(params.get("apiBase"), params.get("api_base"), storedAccess.apiBaseHint);
  const paymentRefPayco = firstNotBlank(params.get("ref_payco"), params.get("refPayco"), storedAccess.refPayco);
  const paymentInvoice = firstNotBlank(params.get("invoice"), params.get("x_id_invoice"), storedAccess.invoice);
  const paymentAmount = firstNotBlank(params.get("amount"), params.get("x_amount"), storedAccess.amount);
  const paymentCurrency = firstNotBlank(params.get("currency"), params.get("x_currency_code"), storedAccess.currency);
  const paymentTransactionId = firstNotBlank(params.get("trx"), params.get("x_transaction_id"), storedAccess.transactionId);
  const paymentMethod = normalizePaymentMethod(firstNotBlank(
    params.get("payment_method"),
    params.get("paymentMethod"),
    params.get("method"),
    params.get("x_payment_method"),
    storedAccess.paymentMethod,
    storedPaymentStatus.paymentMethod,
    storedPaymentStatus.method
  ));
  const paymentMethodDetail = combinePaymentNote(
    params.get("payment_note"),
    params.get("paymentNote"),
    params.get("x_bank_name"),
    params.get("bank_name"),
    params.get("bank"),
    params.get("x_franchise"),
    params.get("franchise"),
    storedAccess.paymentNote,
    storedPaymentStatus.paymentNote,
    storedPaymentStatus.bankName,
    storedPaymentStatus.franchise
  );

  const apiBaseCandidates = uniqueValues([
    sanitizeApiBase(apiBaseParam),
    sanitizeApiBase(window.CONTRACT_API_BASE_URL),
    sanitizeApiBase(storedAccess.apiBaseResolved),
    sanitizeApiBase(API_BASE_DEFAULT),
    // Ultimo recurso: el mismo dominio donde esta hosteado el HTML (usualmente NO es el backend).
    sanitizeApiBase(location.origin)
  ]);

  let apiBase = apiBaseCandidates[0] || sanitizeApiBase(API_BASE_DEFAULT);
  let contractAccessPayload = {};
  let contractFlowState = null;
  let categoryFlow = [];
  let categoryIndex = 0;
  let currentCategoryCode = "";
  let currentCategoryLabel = "";

  saveStoredAccess({
    email: contractEmail,
    code: contractCode,
    apiBaseHint: sanitizeApiBase(apiBaseParam),
    apiBaseResolved: apiBase,
    refPayco: paymentRefPayco,
    invoice: paymentInvoice,
    amount: paymentAmount,
    currency: paymentCurrency,
    transactionId: paymentTransactionId,
    paymentMethod: paymentMethod,
    paymentNote: paymentMethodDetail
  });

  const evidenceRef = contractCode ? "****" + contractCode.slice(-4) : "N/A";
  const tokenPillEl = document.getElementById("tokenPill");
  if (tokenPillEl) {
    tokenPillEl.textContent = contractCode ? "Codigo: " + evidenceRef : "Codigo: (faltante)";
  }

  if (rootWrap) rootWrap.style.display = "none";
  if (location.search) history.replaceState({}, document.title, location.pathname);

  function denyAccessAndThrow(reason) {
    const err = new Error(reason || "Acceso denegado");
    err.name = "AccessDeniedError";
    document.body.innerHTML =
      "<div style=\"max-width:720px;margin:70px auto;padding:18px;font-family:Arial;color:#111\">" +
      "<h2>Acceso denegado</h2><p>" + escapeHtml(reason || "No fue posible validar el enlace del contrato.") + "</p></div>";
    throw err;
  }

  function buildPaymentPayload() {
    const src = (contractAccessPayload && typeof contractAccessPayload === "object") ? contractAccessPayload : {};
    const paymentMethodEl = document.getElementById("shared_payment_method");
    const paymentNoteEl = document.getElementById("shared_payment_note");
    const payload = {
      refPayco: firstNotBlank(paymentRefPayco, src.refPayco, src.ref_payco),
      invoice: firstNotBlank(paymentInvoice, src.invoice, src.x_id_invoice),
      amount: firstNotBlank(paymentAmount, src.amount, src.x_amount),
      currency: firstNotBlank(paymentCurrency, src.currency, src.x_currency_code).toUpperCase(),
      transactionId: firstNotBlank(paymentTransactionId, src.transactionId, src.transaction_id, src.x_transaction_id),
      method: normalizePaymentMethod(firstNotBlank(
        paymentMethodEl && paymentMethodEl.value,
        paymentMethod,
        src.shared_payment_method,
        src.paymentMethod,
        src.payment_method,
        src.medio_pago
      )),
      note: firstNotBlank(
        paymentNoteEl && paymentNoteEl.value,
        paymentMethodDetail,
        src.shared_payment_note,
        src.paymentNote,
        src.payment_note,
        src.bankName,
        src.franchise
      ),
      document: firstNotBlank(src.document, src.x_extra1),
      email: firstNotBlank(contractEmail, src.email, src.customer_email)
    };
    const hasAny = !!payload.refPayco || !!payload.invoice || !!payload.amount || !!payload.transactionId || !!payload.method;
    return hasAny ? payload : null;
  }

  async function readApiPayload(res) {
    const raw = await res.text().catch(() => "");
    if (!raw) return {};
    try {
      return JSON.parse(raw);
    } catch {
      return { message: raw };
    }
  }

  function syncContractFlowFromPayload(payload) {
    const src = payload && typeof payload === "object" ? payload : {};
    const flow = src.contractFlow && typeof src.contractFlow === "object" ? src.contractFlow : {};
    const categories = Array.isArray(flow.categories) ? flow.categories : [];
    const requiredCategories = categories.filter((item) => item && item.requiresContract !== false);
    const flowIndex = Number.isFinite(Number(flow.currentCategoryIndex)) ? Number(flow.currentCategoryIndex) : 0;
    const indexedRequiredCategory =
      requiredCategories.find((item) => Number.isFinite(Number(item && item.orderIndex)) && Number(item.orderIndex) === flowIndex) ||
      requiredCategories[flowIndex] ||
      requiredCategories.find((item) => String(item && item.status || "").toUpperCase() !== "COMPLETED") ||
      requiredCategories[0] ||
      null;
    contractFlowState = flow;
    categoryFlow = categories;
    categoryIndex = Math.max(0, flowIndex);
    currentCategoryCode = firstNotBlank(
      flow.currentCategoryCode,
      indexedRequiredCategory && indexedRequiredCategory.categoryCode
    );
    currentCategoryLabel = firstNotBlank(
      flow.currentCategoryLabel,
      indexedRequiredCategory && indexedRequiredCategory.categoryLabel,
      currentCategoryCode
    );
    contractIndex = Math.max(0, Math.min(CONTRACTS.length - 1, Number.isFinite(Number(flow.currentContractIndex)) ? Number(flow.currentContractIndex) : 0));
  }

  async function fetchValidatedAccessState(baseOverride) {
    const candidate = sanitizeApiBase(baseOverride || apiBase);
    if (!candidate) throw new Error("No hay backend disponible para validar el acceso.");
    const res = await fetch(candidate + "/api/verification/contract/access", {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({ email: contractEmail, code: contractCode })
    });
    const out = await readApiPayload(res);
    if (!res.ok || !out || !out.ok) {
      throw new Error((out && out.message) || "No se pudo validar el enlace del contrato.");
    }
    apiBase = candidate;
    contractAccessPayload =
      (out && typeof out.data === "object" && out.data) ||
      (out && typeof out.payload === "object" && out.payload) ||
      {};
    syncContractFlowFromPayload(contractAccessPayload);
    saveStoredAccess({
      email: contractEmail,
      code: contractCode,
      apiBaseHint: sanitizeApiBase(apiBaseParam),
      apiBaseResolved: apiBase,
      refPayco: paymentRefPayco,
      invoice: paymentInvoice,
      amount: paymentAmount,
      currency: paymentCurrency,
      transactionId: paymentTransactionId,
      paymentMethod: paymentMethod,
      paymentNote: paymentMethodDetail
    });
    return out;
  }

  async function validateContractAccessOrFail() {
    if (!contractEmail || !contractCode) denyAccessAndThrow("Falta el enlace completo del contrato.");

    let lastError = "";
    let logicalAccessError = "";
    for (const candidate of apiBaseCandidates) {
      if (!candidate) continue;
      try {
        const res = await fetch(candidate + "/api/verification/contract/access", {
          method: "POST",
          headers: { "Content-Type": "application/json" },
          body: JSON.stringify({ email: contractEmail, code: contractCode })
        });
        const out = await readApiPayload(res);

        if (res.ok && out && out.ok) {
          await fetchValidatedAccessState(candidate);
          return;
        }

        if (res.status === 400 && out && Object.prototype.hasOwnProperty.call(out, "ok")) {
          logicalAccessError = out.message || "Codigo invalido o vencido.";
          continue;
        }

        lastError = out && out.message ? out.message : ("No se pudo validar acceso en " + candidate);
      } catch (error) {
        lastError = String(error && error.message ? error.message : error);
      }
    }

    denyAccessAndThrow(logicalAccessError || lastError || "No fue posible validar el enlace del contrato.");
  }

  async function completeContractInBackend() {
    const body = { email: contractEmail, code: contractCode };
    const payment = buildPaymentPayload();
    if (payment) body.payment = payment;
    const res = await fetch(apiBase + "/api/verification/contract/complete", {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify(body)
    });
    const out = await readApiPayload(res);
    if (!res.ok || !out || !out.ok) {
      return { ok: false, message: (out && out.message) || "No se pudo completar la firma en el backend." };
    }
    return { ok: true, data: out };
  }

  function resolveSignerName(form) {
    const c = getCurrentContract();
    if (!c) return "firmante";
    if (c.pdfFile === "Contrato2.pdf") return (form.c2_nombre || "").trim() || "firmante";
    if (c.pdfFile === "Contrato3.pdf") return (form.c3_est_nombre || "").trim() || "firmante";
    return (form.est_nombre || "").trim() || "firmante";
  }

  async function uploadSignedPdfToApi(blob, outputFileName, form) {
    const c = getCurrentContract();
    const signerName = resolveSignerName(form);
    const contractName = (c && c.name ? c.name : (outputFileName || "Contrato")).trim();
    const file = new File([blob], outputFileName || "Contrato_Firmado.pdf", { type: "application/pdf" });
    const fd = new FormData();
    fd.append("email", contractEmail);
    fd.append("code", contractCode);
    fd.append("categoryCode", currentCategoryCode);
    fd.append("signerName", signerName);
    fd.append("contractName", contractName);
    fd.append("pdfFile", c && c.pdfFile ? c.pdfFile : "");
    fd.append("formData", JSON.stringify(form || {}));
    fd.append("file", file);
    const res = await fetch(apiBase + "/api/verification/contract/upload", { method: "POST", body: fd });
    const out = await readApiPayload(res);
    if (!res.ok || !out || !out.ok) throw new Error((out && out.message) || "No se pudo subir el PDF firmado al servidor.");
    return out;
  }

  const st1 = document.getElementById("st1");
  const st2 = document.getElementById("st2");
  const st3 = document.getElementById("st3");
  const st4 = document.getElementById("st4");
  const barFill = document.getElementById("barFill");
  const stepLabel = document.getElementById("stepLabel");
  const flowMeta = document.getElementById("flowMeta");
  const STEP_MAP = { 1: "Paso: Ver PDF", 2: "Paso: Rellenar", 3: "Paso: Firmar", 4: "Paso: Finalizar" };
  let currentStep = 1;

  function setStep(n) {
    currentStep = n;
    const steps = [st1, st2, st3, st4];
    steps.forEach((s) => s.classList.remove("active", "done"));
    steps.forEach((s, i) => {
      if (i + 1 < n) s.classList.add("done");
      if (i + 1 === n) s.classList.add("active");
    });
    barFill.style.width = getGlobalProgressPercent(n) + "%";
    stepLabel.textContent = STEP_MAP[n] || STEP_MAP[1];
    updateFlowMeta(n);
  }

  function stamp() {
    const d = new Date();
    const p = (n) => String(n).padStart(2, "0");
    return p(d.getDate()) + "/" + p(d.getMonth() + 1) + "/" + d.getFullYear() + " " + p(d.getHours()) + ":" + p(d.getMinutes()) + ":" + p(d.getSeconds());
  }

  setInterval(() => {
    const timeNowEl = document.getElementById("timeNow");
    if (!timeNowEl) return;
    timeNowEl.textContent = stamp();
  }, 500);

  async function ensurePdfJs() {
    if (window.pdfjsLib) return "already";
    try {
      const modern = await import("https://cdn.jsdelivr.net/npm/pdfjs-dist@5.4.296/build/pdf.mjs");
      modern.GlobalWorkerOptions.workerSrc = "https://cdn.jsdelivr.net/npm/pdfjs-dist@5.4.296/build/pdf.worker.mjs";
      window.pdfjsLib = modern;
      return "modern";
    } catch (modernErr) {
      const legacy = await new Promise((resolve, reject) => {
        const s = document.createElement("script");
        s.src = "https://cdn.jsdelivr.net/npm/pdfjs-dist@3.11.174/legacy/build/pdf.min.js";
        s.onload = () => resolve(window.pdfjsLib);
        s.onerror = reject;
        document.head.appendChild(s);
      });
      if (!legacy) throw modernErr;
      legacy.GlobalWorkerOptions.workerSrc = "https://cdn.jsdelivr.net/npm/pdfjs-dist@3.11.174/legacy/build/pdf.worker.min.js";
      return "legacy";
    }
  }

  const CONTRACTS = [
    { name: "Contrato 1", pdfFile: "Contrato1.pdf", outFile: "Contrato1_Firmado.pdf" },
    { name: "Contrato 2", pdfFile: "Contrato2.pdf", outFile: "Contrato2_Firmado.pdf" },
    { name: "Contrato 3", pdfFile: "Contrato3.pdf", outFile: "Contrato3_Firmado.pdf" }
  ];

  let contractIndex = 0;
  const contractTitle = document.getElementById("contractTitle");
  const pdfCardTitle = document.getElementById("pdfCardTitle");
  const formCardTitle = document.getElementById("formCardTitle");
  const contract1Fields = document.getElementById("contract1Fields");
  const contract1ExtraFields = document.getElementById("contract1ExtraFields");
  const contract2Fields = document.getElementById("contract2Fields");
  const contract3Fields = document.getElementById("contract3Fields");
  const signatureSection = document.getElementById("signatureSection");

  function getGlobalProgressPercent(stepNumber) {
    const safeStep = Math.max(1, Math.min(4, stepNumber));
    const stepRatio = (safeStep - 1) / 3;
    const totalCategories = Math.max(1, (categoryFlow && categoryFlow.length) || (contractFlowState && contractFlowState.totalCategories) || 1);
    const completedContracts = (categoryIndex * CONTRACTS.length) + contractIndex + stepRatio;
    return Math.round((completedContracts / (totalCategories * CONTRACTS.length)) * 100);
  }

  function updateFlowMeta(stepNumber) {
    const totalCategories = Math.max(1, (categoryFlow && categoryFlow.length) || (contractFlowState && contractFlowState.totalCategories) || 1);
    const label = firstNotBlank(currentCategoryLabel, currentCategoryCode, "Categoria");
    flowMeta.textContent =
      "Categoria " + (categoryIndex + 1) + " de " + totalCategories +
      " (" + label + ")" +
      " · Contrato " + (contractIndex + 1) + " de " + CONTRACTS.length +
      " · " + getGlobalProgressPercent(stepNumber) + "%";
  }

  function getCurrentContract() { return CONTRACTS[contractIndex]; }

  function updateContractUI() {
    const c = getCurrentContract();
    if (!c) return;
    const categoryLabel = firstNotBlank(currentCategoryLabel, currentCategoryCode);
    const suffix = categoryLabel ? " - " + categoryLabel : "";
    contractTitle.textContent = c.name + suffix;
    pdfCardTitle.textContent = c.name + suffix + " (PDF)";
    formCardTitle.textContent = "Campos del " + c.name.toLowerCase() + (categoryLabel ? (" - " + categoryLabel) : "");
    const isC2 = c.pdfFile === "Contrato2.pdf";
    const isC3 = c.pdfFile === "Contrato3.pdf";
    contract1Fields.style.display = (isC2 || isC3) ? "none" : "block";
    contract1ExtraFields.style.display = (isC2 || isC3) ? "none" : "block";
    contract2Fields.style.display = isC2 ? "block" : "none";
    contract3Fields.style.display = isC3 ? "block" : "none";
    signatureSection.style.display = isC3 ? "none" : "block";
    const categoriaField = document.getElementById("c2_categoria");
    if (categoriaField && currentCategoryLabel) categoriaField.value = currentCategoryLabel;
    updateFlowMeta(currentStep);
    syncAcuSignatureUI();
    scheduleSignatureResize();
  }
  const statusBox = document.getElementById("statusBox");
  const pdfCanvas = document.getElementById("pdfCanvas");
  const ctx = pdfCanvas.getContext("2d");
  let pdfDoc = null;
  let currentPage = 1;
  let pageCount = 0;
  let scale = 1.35;

  async function loadPDF() {
    setFormVisible(false);
    scale = 1.35;
    if (window.updateZoomUI) window.updateZoomUI();
    const c = getCurrentContract();
    if (!c) return;
    try {
      statusBox.className = "alert";
      statusBox.textContent = "Cargando " + c.pdfFile + "...";
      const url = new URL("./" + c.pdfFile, location.href).href;
      const task = pdfjsLib.getDocument({ url: url, disableStream: true, disableAutoFetch: true });
      pdfDoc = await task.promise;
      pageCount = pdfDoc.numPages;
      document.getElementById("pageCount").textContent = pageCount;
      currentPage = 1;
      await renderPage(currentPage);
      statusBox.className = "alert";
      statusBox.textContent = c.pdfFile + " cargado.\n\n" + guideMessage(1);
      setStep(1);
      setFormVisible(false);
    } catch (e) {
      console.error(e);
      statusBox.className = "alert err";
      statusBox.innerHTML = "No se pudo cargar <b>" + escapeHtml(c.pdfFile) + "</b>.<br>Debe estar en la misma carpeta del HTML (<code>/Contratos/</code>).<br><span style=\"font-size:12px\">" + escapeHtml(e && e.message ? e.message : e) + "</span>";
    }
  }

  async function renderPage(n) {
    if (!pdfDoc) return;
    const page = await pdfDoc.getPage(n);
    const viewport = page.getViewport({ scale: scale });
    pdfCanvas.width = viewport.width;
    pdfCanvas.height = viewport.height;
    await page.render({ canvasContext: ctx, viewport: viewport }).promise;
    document.getElementById("pageNum").textContent = n;
  }

  const formMsg = document.getElementById("formMsg");
  function info(m) { formMsg.className = "alert"; formMsg.textContent = m; }
  function ok(m) { formMsg.className = "alert ok"; formMsg.textContent = m; }
  function err(m) { formMsg.className = "alert err"; formMsg.textContent = m; }
  function must(v) { return (v || "").trim(); }
  function resetContractEditorState() {
    accept.checked = false;
    hasAcudienteCheck.checked = false;
    hasRep2Check.checked = false;
    hasAcu3Check.checked = false;
    signaturePad.clear();
    signaturePadAcu.clear();
    studentSigDataUrl = null;
    acuSigDataUrl = null;
    contract3Photo3x4DataUrl = null;
    contract3Photo3x4FileName = "";
    contract3SelfieDataUrl = null;
    contract3SelfieFileName = "";
    setImagePreview(c3PhotoPreview, "", "Sin imagen cargada");
    setImagePreview(c3SelfiePreview, "", "Sin imagen cargada");
    syncAcuSignatureUI();
  }
  function readFieldValue(id) {
    const field = document.getElementById(id);
    if (!field) return "";
    return field.type === "date" ? toDisplayDate(field.value) : must(field.value);
  }
  function setDateFieldIfBlank(id) {
    const field = document.getElementById(id);
    if (!field || field.type !== "date" || must(field.value)) return;
    for (let i = 1; i < arguments.length; i++) {
      const candidate = toIsoDate(arguments[i]);
      if (!candidate) continue;
      field.value = candidate;
      break;
    }
  }
  const SHARED_FORM_IDS = [
    "shared_contact_email",
    "shared_contact_phone",
    "shared_contact_address",
    "shared_sede",
    "shared_payment_method",
    "shared_payment_note"
  ];

  const accept = document.getElementById("accept");
  const formCard = document.getElementById("formCard");

  function setFormVisible(isVisible) {
    if (!formCard) return;
    formCard.style.display = isVisible ? "flex" : "none";
    document.body.classList.toggle("form-visible", isVisible);
    document.body.classList.toggle("form-hidden", !isVisible);
    if (isVisible) scheduleSignatureResize();
  }

  function guideMessage(step) {
    if (step === 1) return "Acciones para continuar:\n1) Lee el contrato en el visor PDF.\n2) Marca la casilla de aceptacion.\n3) Diligencia la informacion solicitada y firma.";
    if (step === 2) return "Acciones para continuar:\n1) Diligencia la informacion solicitada.\n2) Firma en el recuadro.\n3) Haz clic en Generar PDF firmado.";
    return "Acciones para continuar:\n1) Revisa la informacion.\n2) Completa la firma.\n3) Genera el PDF firmado.";
  }

  accept.addEventListener("change", () => {
    if (accept.checked) {
      setFormVisible(true);
      setStep(2);
      scheduleSignatureResize();
      info(guideMessage(2));
    } else {
      setFormVisible(false);
      setStep(1);
      info(guideMessage(1));
    }
  });

  setFormVisible(false);

  document.querySelectorAll("input,select").forEach((el) => {
    el.addEventListener("input", () => { if (accept.checked) setStep(2); });
    el.addEventListener("change", () => { if (accept.checked) setStep(2); });
  });

  const FIELD_META = {
    shared_contact_email: { label: "Correo de contacto *", info: "Correo principal para matricula, activacion y notificaciones." },
    shared_contact_phone: { label: "Celular de contacto *", info: "Celular del estudiante o del responsable para seguimiento del proceso." },
    shared_contact_address: { label: "Direccion de residencia *", info: "Direccion usada para completar el perfil del estudiante." },
    c2_inscripcion_fecha: { label: "Fecha de inscripcion", info: "Selecciona la fecha desde el calendario; se guardara en formato DD/MM/AAAA." },
    est_nombre: { label: "Nombres y apellidos completos *", info: "Escriba el nombre completo del estudiante tal como aparece en su documento." },
    est_doc_tipo: { label: "Documento de identidad *", info: "Seleccione el tipo de documento del estudiante." },
    est_doc_num: { label: "No. (Numero) *", info: "Ingrese el numero de identificacion sin puntos ni comas." },
    verbal: { label: "Puedo comunicarme verbalmente *", info: "Indique SI si el estudiante puede comunicarse verbalmente." },
    lentes_contacto: { label: "Lentes de contacto *", info: "Indique SI si el estudiante usa lentes de contacto." },
    gafas: { label: "Gafas *", info: "Indique SI si el estudiante usa gafas formuladas." },
    auditivos: { label: "Equipos auditivos *", info: "Indique SI si usa dispositivos de apoyo auditivo." },
    ortopedicos: { label: "Equipos ortopedicos *", info: "Indique SI si usa ortesis, protesis, baston o apoyo similar." },
    cual_dispositivo: { label: "Cual? (si aplica)", info: "Describa el equipo ortopedico especifico cuando responda SI." },
    crc_info_real: { label: "Entrego informacion real al CRC (Res. 5228 de 2016) *", info: "Confirme que la informacion suministrada es veraz." },
    tm_animo: { label: "Trastorno estado de animo *", info: "Marque SI si tiene diagnostico o antecedente relacionado." },
    tm_intelectual: { label: "Desarrollo intelectual *", info: "Marque SI si existe antecedente o diagnostico del desarrollo intelectual." },
    tm_disociativo: { label: "Disociativo *", info: "Marque SI si presenta trastornos disociativos diagnosticados." },
    tm_personalidad: { label: "Personalidad *", info: "Marque SI si tiene diagnostico de trastorno de personalidad." },
    tm_impulsos: { label: "Control de impulsos *", info: "Marque SI si presenta alteraciones de control de impulsos." },
    tm_sueno: { label: "Trastorno del sueno *", info: "Marque SI si tiene diagnostico o antecedente de trastorno del sueno." },
    tm_tdah: { label: "Deficit de atencion *", info: "Marque SI si tiene diagnostico de TDAH u otro deficit atencional." },
    tm_otros: { label: "Otros trastornos mentales *", info: "Marque SI si presenta otros trastornos mentales no listados." },
    ciudad: { label: "Ciudad *", info: "Ciudad donde se firma la autorizacion." },
    fecha: { label: "Fecha *", info: "Selecciona la fecha desde el calendario; se guardara en formato DD/MM/AAAA." },
    autorizo_tratamiento: { label: "Manifiesto que SI / NO otorgo autorizacion *", info: "Autorizacion general para tratamiento de datos personales." },
    autorizo_cea_consorcio: { label: "CEA y CONSORCIO mantener/manejar info (SI/NO) *", info: "Autorizacion especifica para gestion de datos por CEA y consorcio." },
    acu_nombre: { label: "Nombre del acudiente *", info: "Nombre completo del acudiente responsable." },
    acu_doc_tipo: { label: "Tipo de identificacion (acudiente) *", info: "Tipo de documento del acudiente." },
    acu_doc_num: { label: "Numero (acudiente) *", info: "Numero de identificacion del acudiente sin puntos ni comas." },
    c2_nacimiento: { label: "Fecha nacimiento", info: "Selecciona la fecha desde el calendario; se guardara en formato DD/MM/AAAA." },
    c2_estrato: { label: "Estrato", info: "Selecciona el estrato reportado por el estudiante." },
    c2_rh: { label: "RH", info: "Selecciona el grupo sanguineo y factor RH del estudiante." }
  };

  function findLabelForField(field) {
    let prev = field.previousElementSibling;
    while (prev) {
      if (prev.tagName === "LABEL") return prev;
      prev = prev.previousElementSibling;
    }
    return null;
  }

  function applyFieldMetadata() {
    Object.entries(FIELD_META).forEach(([id, meta]) => {
      const field = document.getElementById(id);
      if (!field) return;
      field.name = id;
      const label = findLabelForField(field);
      if (!label) return;
      label.textContent = meta.label;
      if (meta.info) {
        const infoDot = document.createElement("span");
        infoDot.className = "infoDot";
        infoDot.textContent = "i";
        infoDot.title = meta.info;
        infoDot.setAttribute("aria-label", meta.info);
        label.appendChild(infoDot);
      }
    });
  }

  function setFieldValueIfBlank(id) {
    const field = document.getElementById(id);
    if (!field || must(field.value)) return;
    for (let i = 1; i < arguments.length; i++) {
      const candidate = must(arguments[i]);
      if (!candidate) continue;
      field.value = candidate;
      break;
    }
  }

  function prefillSharedEnrollmentFields() {
    const src = (contractAccessPayload && typeof contractAccessPayload === "object") ? contractAccessPayload : {};
    setFieldValueIfBlank("shared_contact_email",
      src.shared_contact_email,
      src.email,
      contractEmail
    );
    setFieldValueIfBlank("shared_contact_phone",
      src.shared_contact_phone,
      src.telefono,
      src.phone
    );
    setFieldValueIfBlank("shared_contact_address",
      src.shared_contact_address,
      src.direccion,
      src.address
    );
    setFieldValueIfBlank("shared_sede",
      src.shared_sede,
      src.sede
    );
    setFieldValueIfBlank("shared_payment_method",
      src.shared_payment_method,
      src.paymentMethod,
      src.payment_method,
      paymentMethod
    );
    setFieldValueIfBlank("shared_payment_note",
      src.shared_payment_note,
      src.paymentNote,
      src.payment_note,
      paymentMethodDetail
    );
    setDateFieldIfBlank("fecha", src.fecha);
    setDateFieldIfBlank("c2_nacimiento", src.c2_nacimiento);
    setDateFieldIfBlank("c2_inscripcion_fecha",
      src.c2_inscripcion_fecha,
      src.c2_fecha_inscripcion,
      (src.c2_ins_dd && src.c2_ins_mmaaaa) ? (src.c2_ins_dd + "/" + src.c2_ins_mmaaaa) : ""
    );
  }

  const cualWrap = document.getElementById("cualWrap");
  const ortopedicosSelect = document.getElementById("ortopedicos");
  function syncCualDispositivoUI() {
    const isOrtopedico = must(ortopedicosSelect.value) === "SI";
    cualWrap.style.display = isOrtopedico ? "block" : "none";
    if (!isOrtopedico) document.getElementById("cual_dispositivo").value = "";
  }
  ortopedicosSelect.addEventListener("change", syncCualDispositivoUI);
  ortopedicosSelect.addEventListener("input", syncCualDispositivoUI);
  applyFieldMetadata();
  syncCualDispositivoUI();

  const hasAcudienteCheck = document.getElementById("has_acudiente");
  const hasRep2Check = document.getElementById("has_rep2");
  const hasAcu3Check = document.getElementById("has_acu3");
  const acuFieldsBlock = document.getElementById("acuFieldsBlock");
  const rep2FieldsBlock = document.getElementById("rep2FieldsBlock");
  const c3AcuFieldsBlock = document.getElementById("c3AcuFieldsBlock");
  const sigCanvas = document.getElementById("signature-pad");
  const sigCanvasAcu = document.getElementById("signature-pad-acu");
  const acuSigBlock = document.getElementById("acuSigBlock");
  const c3PhotoFileInput = document.getElementById("c3_photo_3x4_file");
  const c3SelfieFileInput = document.getElementById("c3_selfie_file");
  const c3PhotoPreview = document.getElementById("c3_photo_3x4_preview");
  const c3SelfiePreview = document.getElementById("c3_selfie_preview");
  const signaturePad = new SignaturePad(sigCanvas, { minWidth: 1.2, maxWidth: 2.8 });
  const signaturePadAcu = new SignaturePad(sigCanvasAcu, { minWidth: 1.2, maxWidth: 2.8 });

  let studentSigDataUrl = null;
  let acuSigDataUrl = null;
  let contract3Photo3x4DataUrl = null;
  let contract3Photo3x4FileName = "";
  let contract3SelfieDataUrl = null;
  let contract3SelfieFileName = "";

  function drawPngIntoSignatureCanvas(canvas, dataUrl) {
    return new Promise((resolve, reject) => {
      const img = new Image();
      img.onload = () => {
        const c = canvas.getContext("2d");
        c.clearRect(0, 0, canvas.width, canvas.height);
        const fitScale = Math.min(canvas.width / img.width, canvas.height / img.height) * 0.92;
        const nw = img.width * fitScale;
        const nh = img.height * fitScale;
        const x = (canvas.width - nw) / 2;
        const y = (canvas.height - nh) / 2;
        c.drawImage(img, x, y, nw, nh);
        resolve();
      };
      img.onerror = reject;
      img.src = dataUrl;
    });
  }

  sigCanvas.addEventListener("pointerdown", () => { studentSigDataUrl = null; });
  sigCanvasAcu.addEventListener("pointerdown", () => { acuSigDataUrl = null; });

  function resizeSigCanvas(canvas, pad) {
    if (!canvas || !pad) return false;
    const rect = canvas.getBoundingClientRect();
    if (rect.width < 8 || rect.height < 8) return false;
    const data = pad.toData();
    const ratio = Math.max(window.devicePixelRatio || 1, 1);
    canvas.width = rect.width * ratio;
    canvas.height = rect.height * ratio;
    const c = canvas.getContext("2d");
    c.setTransform(ratio, 0, 0, ratio, 0, 0);
    pad.clear();
    if (data && data.length) pad.fromData(data);
    return true;
  }

  let signatureResizeTimer = 0;
  function scheduleSignatureResize() {
    clearTimeout(signatureResizeTimer);
    signatureResizeTimer = setTimeout(() => { resizeAllSignatures(); }, 40);
  }

  function resizeAllSignatures() {
    resizeSigCanvas(sigCanvas, signaturePad);
    if (acuSigBlock.style.display !== "none") resizeSigCanvas(sigCanvasAcu, signaturePadAcu);
  }

  function getAcuState() {
    const current = getCurrentContract();
    const isC2 = current && current.pdfFile === "Contrato2.pdf";
    const isC3 = current && current.pdfFile === "Contrato3.pdf";
    if (isC3) {
      const enabled = hasAcu3Check.checked;
      const complete = ["c3_acu_nombre", "c3_acu_doc_tipo", "c3_acu_doc_num"].every((id) => Boolean(must(document.getElementById(id).value)));
      return { enabled: enabled, complete: complete };
    }
    const enabled = isC2 ? hasRep2Check.checked : hasAcudienteCheck.checked;
    let complete = true;
    if (isC2) {
      const repReq = ["c2_rep_nombre", "c2_rep_cc", "c2_rep_parentesco", "c2_rep_ocupacion", "c2_rep_tel", "c2_rep_direccion"];
      complete = repReq.every((id) => Boolean(must(document.getElementById(id).value)));
    } else {
      complete = Boolean(must(document.getElementById("acu_nombre").value) && must(document.getElementById("acu_doc_tipo").value) && must(document.getElementById("acu_doc_num").value));
    }
    return { enabled: enabled, complete: complete };
  }

  function syncAcuSignatureUI() {
    const current = getCurrentContract();
    const isC2 = current && current.pdfFile === "Contrato2.pdf";
    const isC3 = current && current.pdfFile === "Contrato3.pdf";
    const acu = getAcuState();

    if (isC3) {
      acuFieldsBlock.style.display = "none";
      rep2FieldsBlock.style.display = "none";
      c3AcuFieldsBlock.style.display = acu.enabled ? "block" : "none";
      acuSigBlock.style.display = "none";
      if (!acu.enabled) ["c3_acu_nombre", "c3_acu_doc_tipo", "c3_acu_doc_num"].forEach((id) => { document.getElementById(id).value = ""; });
      signaturePadAcu.clear();
      scheduleSignatureResize();
      return;
    }

    acuFieldsBlock.style.display = (!isC2 && acu.enabled) ? "block" : "none";
    rep2FieldsBlock.style.display = (isC2 && acu.enabled) ? "block" : "none";
    c3AcuFieldsBlock.style.display = "none";
    acuSigBlock.style.display = acu.enabled ? "block" : "none";

    if (!acu.enabled) {
      if (isC2) ["c2_rep_nombre", "c2_rep_cc", "c2_rep_parentesco", "c2_rep_ocupacion", "c2_rep_tel", "c2_rep_direccion"].forEach((id) => { document.getElementById(id).value = ""; });
      else {
        document.getElementById("acu_nombre").value = "";
        document.getElementById("acu_doc_tipo").value = "";
        document.getElementById("acu_doc_num").value = "";
      }
      signaturePadAcu.clear();
    } else {
      resizeSigCanvas(sigCanvasAcu, signaturePadAcu);
    }
    scheduleSignatureResize();
  }
  window.addEventListener("resize", resizeAllSignatures);
  resizeAllSignatures();
  syncAcuSignatureUI();

  const uploadSigBtn = document.getElementById("uploadSig");
  const uploadSigFile = document.getElementById("uploadSigFile");
  const uploadSigBtnAcu = document.getElementById("uploadSigAcu");
  const uploadSigFileAcu = document.getElementById("uploadSigFileAcu");

  function setImagePreview(previewEl, dataUrl, emptyText) {
    if (!previewEl) return;
    if (!dataUrl) {
      previewEl.classList.remove("has-image");
      previewEl.innerHTML = "<span>" + escapeHtml(emptyText || "Sin imagen cargada") + "</span>";
      return;
    }
    previewEl.classList.add("has-image");
    previewEl.innerHTML = "<img alt=\"Vista previa\" src=\"" + escapeHtml(dataUrl) + "\">";
  }

  function loadImageAsDataUrl(file) {
    return new Promise((resolve, reject) => {
      if (!file) return reject(new Error("Archivo requerido."));
      if (!/^image\/(png|jpe?g)$/i.test(file.type)) {
        return reject(new Error("Solo se permiten imagenes PNG o JPG."));
      }
      const reader = new FileReader();
      reader.onload = () => resolve(reader.result);
      reader.onerror = () => reject(new Error("No se pudo leer la imagen."));
      reader.readAsDataURL(file);
    });
  }

  async function handleContract3ImageChange(fileInput, kind) {
    const file = fileInput && fileInput.files && fileInput.files[0];
    if (!file) return;
    try {
      const dataUrl = await loadImageAsDataUrl(file);
      if (kind === "photo3x4") {
        contract3Photo3x4DataUrl = dataUrl;
        contract3Photo3x4FileName = file.name || "foto-3x4";
        setImagePreview(c3PhotoPreview, dataUrl, "Sin imagen cargada");
      } else {
        contract3SelfieDataUrl = dataUrl;
        contract3SelfieFileName = file.name || "selfie";
        setImagePreview(c3SelfiePreview, dataUrl, "Sin imagen cargada");
      }
      ok("Imagen cargada correctamente para el contrato 3.");
    } catch (imageError) {
      err(imageError.message || "No se pudo cargar la imagen.");
      if (kind === "photo3x4") {
        contract3Photo3x4DataUrl = null;
        contract3Photo3x4FileName = "";
        setImagePreview(c3PhotoPreview, "", "Sin imagen cargada");
      } else {
        contract3SelfieDataUrl = null;
        contract3SelfieFileName = "";
        setImagePreview(c3SelfiePreview, "", "Sin imagen cargada");
      }
    } finally {
      if (fileInput) fileInput.value = "";
    }
  }

  setImagePreview(c3PhotoPreview, "", "Sin imagen cargada");
  setImagePreview(c3SelfiePreview, "", "Sin imagen cargada");
  c3PhotoFileInput && c3PhotoFileInput.addEventListener("change", () => { handleContract3ImageChange(c3PhotoFileInput, "photo3x4"); });
  c3SelfieFileInput && c3SelfieFileInput.addEventListener("change", () => { handleContract3ImageChange(c3SelfieFileInput, "selfie"); });

  if (uploadSigBtn && uploadSigFile) {
    uploadSigBtn.onclick = () => uploadSigFile.click();
    uploadSigFile.addEventListener("change", async (e) => {
      const file = e.target.files && e.target.files[0];
      if (!file) return;
      if (file.type !== "image/png") {
        err("Solo se permite PNG para la firma.");
        uploadSigFile.value = "";
        return;
      }
      const reader = new FileReader();
      reader.onload = async () => {
        try {
          resizeSigCanvas(sigCanvas, signaturePad);
          signaturePad.clear();
          studentSigDataUrl = reader.result;
          await drawPngIntoSignatureCanvas(sigCanvas, studentSigDataUrl);
          if (accept.checked) setStep(3);
          ok("Firma PNG cargada correctamente. Puedes generar el PDF firmado.");
        } catch (ex) {
          console.error(ex);
          err("No se pudo cargar la firma PNG.");
        } finally {
          uploadSigFile.value = "";
        }
      };
      reader.readAsDataURL(file);
    });
  }

  if (uploadSigBtnAcu && uploadSigFileAcu) {
    uploadSigBtnAcu.onclick = () => uploadSigFileAcu.click();
    uploadSigFileAcu.addEventListener("change", async (e) => {
      const file = e.target.files && e.target.files[0];
      if (!file) return;
      if (file.type !== "image/png") {
        err("Solo se permite PNG para la firma.");
        uploadSigFileAcu.value = "";
        return;
      }
      const reader = new FileReader();
      reader.onload = async () => {
        try {
          resizeSigCanvas(sigCanvasAcu, signaturePadAcu);
          signaturePadAcu.clear();
          acuSigDataUrl = reader.result;
          await drawPngIntoSignatureCanvas(sigCanvasAcu, acuSigDataUrl);
          if (accept.checked) setStep(3);
          ok("Firma PNG del acudiente cargada correctamente.");
        } catch (ex) {
          console.error(ex);
          err("No se pudo cargar la firma PNG del acudiente.");
        } finally {
          uploadSigFileAcu.value = "";
        }
      };
      reader.readAsDataURL(file);
    });
  }

  document.getElementById("clearSig").onclick = () => { studentSigDataUrl = null; signaturePad.clear(); };
  document.getElementById("undoSig").onclick = () => {
    const data = signaturePad.toData();
    if (data && data.length) { data.pop(); signaturePad.fromData(data); }
  };
  document.getElementById("clearSigAcu").onclick = () => { acuSigDataUrl = null; signaturePadAcu.clear(); };
  document.getElementById("undoSigAcu").onclick = () => {
    const data = signaturePadAcu.toData();
    if (data && data.length) { data.pop(); signaturePadAcu.fromData(data); }
  };

  hasAcudienteCheck.addEventListener("change", syncAcuSignatureUI);
  hasRep2Check.addEventListener("change", syncAcuSignatureUI);
  hasAcu3Check.addEventListener("change", syncAcuSignatureUI);
  sigCanvas.addEventListener("pointerdown", () => { if (accept.checked) setStep(3); });
  sigCanvasAcu.addEventListener("pointerdown", () => { if (accept.checked) setStep(3); });

  const BASE_P2 = {
    nombre: { x: 92, y: 644, size: 11, maxWidth: 185, minSize: 8 },
    docNum: { x: 307, y: 631, size: 11, maxWidth: 120, minSize: 8 },
    docChecks: { "C.C.": { x: 167, y: 631, size: 11 }, "C.E.": { x: 202, y: 631, size: 11 }, "T.I.": { x: 235, y: 631, size: 11 }, "PAS": { x: 271, y: 631, size: 11 } },
    verbal: { si: { x: 546, y: 631, size: 11 }, no: { x: 38, y: 622, size: 11 } },
    lentes: { si: { x: 546, y: 611, size: 11 }, no: { x: 39, y: 600, size: 11 } },
    gafas: { si: { x: 88, y: 591, size: 11 }, no: { x: 136, y: 591, size: 11 } },
    auditivos: { si: { x: 271, y: 591, size: 11 }, no: { x: 324, y: 593, size: 11 } },
    ortopedicos: { si: { x: 472, y: 591, size: 11 }, no: { x: 521, y: 590, size: 11 } },
    crc: { si: { x: 546, y: 571, size: 11 }, no: { x: 40, y: 561, size: 11 } },
    tm_animo: { si: { x: 337, y: 551, size: 11 }, no: { x: 367, y: 552, size: 11 } },
    tm_intelectual: { si: { x: 546, y: 551, size: 11 }, no: { x: 40, y: 541, size: 11 } },
    tm_disociativo: { si: { x: 133, y: 531, size: 11 }, no: { x: 165, y: 531, size: 11 } },
    tm_personalidad: { si: { x: 324, y: 531, size: 11 }, no: { x: 355, y: 530, size: 11 } },
    tm_impulsos: { si: { x: 546, y: 531, size: 11 }, no: { x: 40, y: 521, size: 11 } },
    tm_sueno: { si: { x: 130, y: 511, size: 11 }, no: { x: 162, y: 509, size: 11 } },
    tm_tdah: { si: { x: 335, y: 509, size: 11 }, no: { x: 363, y: 509, size: 11 } },
    tm_otros: { si: { x: 546, y: 511, size: 11 }, no: { x: 40, y: 501, size: 11 } },
    cualDispositivo: { x: 90, y: 582, size: 10, maxWidth: 250, minSize: 8 },
    ciudad: { x: 230, y: 197, size: 11, maxWidth: 175, minSize: 8 },
    fecha: { x: 420, y: 197, size: 11, maxWidth: 150, minSize: 8 },
    aut1: { si: { x: 314, y: 120, size: 11 }, no: { x: 364, y: 161, size: 11 } },
    aut2: { si: { x: 429, y: 120, size: 11 }, no: { x: 481, y: 123, size: 11 } }
  };

  const BASE_P3 = {
    firmaEstImg: { x: 120, y: 626, w: 195, h: 24 },
    firmaAcuImg: { x: 430, y: 626, w: 138, h: 24 },
    nombreEst: { x: 120, y: 615, size: 10, maxWidth: 200, minSize: 8 },
    tipoIdEst: { x: 110, y: 599, size: 10, maxWidth: 120, minSize: 8 },
    numEst: { x: 70, y: 583, size: 10, maxWidth: 150, minSize: 8 },
    nombreAcu: { x: 395, y: 615, size: 10, maxWidth: 175, minSize: 8 },
    tipoIdAcu: { x: 440, y: 599, size: 10, maxWidth: 110, minSize: 8 },
    numAcu: { x: 395, y: 583, size: 10, maxWidth: 170, minSize: 8 },
    evidencia: { x: 42, y: 566, size: 7, maxWidth: 260, minSize: 6 }
  };

  const CONTRACT_LAYOUTS = {
    "Contrato1.pdf": { P2: BASE_P2, P3: BASE_P3 },
    "Contrato2.pdf": {
      P1: {
        insDD: { x: 60, y: 223, size: 13, maxWidth: 55, minSize: 8 }, insMM: { x: 129, y: 223, size: 13, maxWidth: 55, minSize: 8 }, insYYYY: { x: 210, y: 223, size: 13, maxWidth: 70, minSize: 8 },
        tramite: { primera: { x: 334, y: 224, size: 11 }, recateg: { x: 450, y: 223, size: 11 } }, categoria: { a2: { x: 243, y: 242, size: 11 }, b1: { x: 421, y: 242, size: 11 }, c1: { x: 470, y: 242, size: 11 } },
        nombre: { x: 150, y: 261, size: 13, maxWidth: 440, minSize: 8 },
        docChecks: { CC: { x: 166, y: 276, size: 11 }, TI: { x: 196, y: 276, size: 11 }, CE: { x: 228, y: 276, size: 11 }, PAS: { x: 149, y: 286, size: 11 } },
        docNum: { x: 304, y: 280, size: 10, maxWidth: 78, minSize: 8 }, docDe: { x: 428, y: 280, size: 10, maxWidth: 165, minSize: 8 }, celular: { x: 45, y: 316, size: 10, maxWidth: 130, minSize: 8 },
        direccion: { x: 191, y: 316, size: 10, maxWidth: 232, minSize: 8 }, correo: { x: 390, y: 316, size: 11, maxWidth: 165, minSize: 7 }, eps: { x: 54, y: 350, size: 11, maxWidth: 150, minSize: 8 },
        estadoCivil: { SOLTERO: { x: 158, y: 345, size: 11 }, UNION_LIBRE: { x: 223, y: 345, size: 11 }, CASADO: { x: 277, y: 345, size: 11 }, DIVORCIADO: { x: 346, y: 345, size: 11 }, VIUDO: { x: 255, y: 356, size: 11 } },
        nacDD: { x: 427, y: 352, size: 10, maxWidth: 35, minSize: 8 }, nacMM: { x: 474, y: 352, size: 10, maxWidth: 35, minSize: 8 }, nacYYYY: { x: 530, y: 352, size: 10, maxWidth: 55, minSize: 8 },
        sexo: { F: { x: 53, y: 388, size: 11 }, M: { x: 95, y: 388, size: 11 } }, ocupacion: { EMPLEADO: { x: 156, y: 383, size: 11 }, INDEPENDIENTE: { x: 227, y: 383, size: 11 }, DESEMPLEADO: { x: 295, y: 383, size: 11 }, BACHILLER: { x: 343, y: 383, size: 11 }, UNIVERSITARIO: { x: 123, y: 394, size: 11 } },
        edad: { x: 427, y: 386, size: 10, maxWidth: 40, minSize: 8 }, estrato: { x: 482, y: 386, size: 10, maxWidth: 45, minSize: 8 }, rh: { x: 534, y: 386, size: 10, maxWidth: 35, minSize: 8 },
        nivel: { POSGRADO: { x: 236, y: 406, size: 11 }, PREGRADO: { x: 296, y: 406, size: 11 }, TECNICO: { x: 348, y: 406, size: 11 }, BACHILLER_11: { x: 446, y: 406, size: 11 }, BASICO_9: { x: 508, y: 407, size: 11 }, PRIMARIA: { x: 198, y: 415, size: 11 } },
        necesidad: { NINGUNA: { x: 273, y: 435, size: 11 }, IDIOMA: { x: 319, y: 433, size: 11 }, DISCAPACIDAD: { x: 388, y: 433, size: 11 }, OTRA: { x: 424, y: 433, size: 11 } }, necesidadCual: { x: 466, y: 433, size: 10, maxWidth: 138, minSize: 8 },
        repNombre: { x: 140, y: 483, size: 10, maxWidth: 180, minSize: 8 }, repCc: { x: 345, y: 483, size: 10, maxWidth: 220, minSize: 8 }, repParentesco: { x: 106, y: 496, size: 10, maxWidth: 205, minSize: 8 }, repOcupacion: { x: 392, y: 495, size: 10, maxWidth: 175, minSize: 8 }, repTel: { x: 110, y: 508, size: 10, maxWidth: 205, minSize: 8 }, repDir: { x: 392, y: 508, size: 10, maxWidth: 175, minSize: 8 }
      },
      P4: {
        firmaEstImg: { x: 44, y: 460, w: 170, h: 45 }, firmaRepImg: { x: 318, y: 457, w: 170, h: 45 }, repNombre: { x: 350, y: 535, size: 10, maxWidth: 145, minSize: 8 }, repCc: { x: 351, y: 548, size: 10, maxWidth: 145, minSize: 8 }, repCel: { x: 350, y: 561, size: 10, maxWidth: 145, minSize: 8 }, repDir: { x: 410, y: 577, size: 10, maxWidth: 170, minSize: 8 }, evidencia: { x: 42, y: 472, size: 7, maxWidth: 260, minSize: 6 }
      }
    },
    "Contrato3.pdf": {
      P3: {
        estNombre: { x: 118, y: 126, size: 10, maxWidth: 220, minSize: 8 }, estDocChecks: { CC: { x: 127, y: 144, size: 11 }, CE: { x: 159, y: 144, size: 11 }, TI: { x: 187, y: 144, size: 11 }, PAS: { x: 219, y: 144, size: 11 } }, estDocNum: { x: 248, y: 145, size: 10, maxWidth: 88, minSize: 8 }, acuNombre: { x: 406, y: 126, size: 10, maxWidth: 160, minSize: 8 }, acuDocTipo: { x: 442, y: 145, size: 10, maxWidth: 60, minSize: 8 }, acuDocNum: { x: 533, y: 145, size: 10, maxWidth: 36, minSize: 8 }, evidencia: { x: 37, y: 168, size: 7, maxWidth: 250, minSize: 6 }
      }
    }
  };
  function convertTopToPdfCoords(node, pageHeight) {
    if (pageHeight === undefined) pageHeight = 792;
    if (!node || typeof node !== "object") return node;
    if (Array.isArray(node)) return node.map((v) => convertTopToPdfCoords(v, pageHeight));
    const out = {};
    Object.entries(node).forEach(([k, v]) => { out[k] = convertTopToPdfCoords(v, pageHeight); });
    if (typeof out.y === "number") out.y = (typeof out.h === "number" && typeof out.w === "number") ? pageHeight - out.y - out.h : pageHeight - out.y;
    return out;
  }

  function getContractLayout(pdfFile) {
    const base = CONTRACT_LAYOUTS[pdfFile] || CONTRACT_LAYOUTS["Contrato1.pdf"];
    return (pdfFile === "Contrato2.pdf" || pdfFile === "Contrato3.pdf") ? convertTopToPdfCoords(base) : base;
  }

  function drawX(page, x, y, size, font) {
    const opts = { x: x, y: y, size: size || 11 };
    if (font) opts.font = font;
    page.drawText("X", opts);
  }

  function drawFitText(page, text, cfg, font) {
    const t = must(text);
    if (!t) return;
    let size = cfg.size || 10;
    const minSize = cfg.minSize || 7;
    const maxWidth = cfg.maxWidth || 0;
    while (maxWidth > 0 && size > minSize) {
      const w = font ? font.widthOfTextAtSize(t, size) : (t.length * size * 0.5);
      if (w <= maxWidth) break;
      size -= 0.5;
    }
    page.drawText(t, { x: cfg.x, y: cfg.y, size: size, font: font });
  }

  function markYesNo(page, cfg, value) {
    if (!cfg) return;
    if (value === "SI") drawX(page, cfg.si.x, cfg.si.y, cfg.si.size || 11);
    if (value === "NO") drawX(page, cfg.no.x, cfg.no.y, cfg.no.size || 11);
  }

  function splitDisplayDateParts(displayDate) {
    const value = toDisplayDate(displayDate);
    if (!value) return ["", "", ""];
    return value.split("/");
  }

  function fitImageBox(img, maxWidth, maxHeight) {
    const width = img.width || maxWidth;
    const height = img.height || maxHeight;
    const ratio = Math.min(maxWidth / width, maxHeight / height);
    return {
      width: Math.max(1, Math.round(width * ratio)),
      height: Math.max(1, Math.round(height * ratio))
    };
  }

  async function embedImageFromDataUrl(pdf, dataUrl) {
    const value = firstNotBlank(dataUrl);
    if (!value) return null;
    if (/^data:image\/png/i.test(value)) return pdf.embedPng(value);
    if (/^data:image\/jpe?g/i.test(value)) return pdf.embedJpg(value);
    throw new Error("Solo se permiten imagenes PNG o JPG para los anexos.");
  }

  async function appendContract3PhotoAnnex(pdf, helv) {
    if (!contract3Photo3x4DataUrl || !contract3SelfieDataUrl) {
      throw new Error("Debes cargar la foto 3x4 y la selfie para el contrato 3.");
    }

    const annexPage = pdf.addPage([595.28, 841.89]);
    const titleY = 790;
    annexPage.drawText("Anexo fotografico del contrato 3", { x: 44, y: titleY, size: 18, font: helv });
    annexPage.drawText("Referencia: " + evidenceRef, { x: 44, y: titleY - 22, size: 10, font: helv });
    annexPage.drawText("Generado: " + stamp(), { x: 44, y: titleY - 38, size: 10, font: helv });

    const photoImg = await embedImageFromDataUrl(pdf, contract3Photo3x4DataUrl);
    const selfieImg = await embedImageFromDataUrl(pdf, contract3SelfieDataUrl);
    const sections = [
      { title: "Foto 3x4", fileName: contract3Photo3x4FileName || "foto-3x4", image: photoImg, box: { x: 44, y: 420, w: 180, h: 250 } },
      { title: "Selfie", fileName: contract3SelfieFileName || "selfie", image: selfieImg, box: { x: 280, y: 420, w: 270, h: 250 } }
    ];

    sections.forEach((section) => {
      annexPage.drawText(section.title, { x: section.box.x, y: section.box.y + section.box.h + 18, size: 12, font: helv });
      annexPage.drawRectangle({
        x: section.box.x,
        y: section.box.y,
        width: section.box.w,
        height: section.box.h,
        borderWidth: 1,
        borderColor: PDFLib.rgb(0.85, 0.85, 0.85)
      });
      const fitted = fitImageBox(section.image, section.box.w - 16, section.box.h - 16);
      annexPage.drawImage(section.image, {
        x: section.box.x + ((section.box.w - fitted.width) / 2),
        y: section.box.y + ((section.box.h - fitted.height) / 2),
        width: fitted.width,
        height: fitted.height
      });
      annexPage.drawText("Archivo: " + section.fileName, {
        x: section.box.x,
        y: section.box.y - 18,
        size: 9,
        font: helv
      });
    });

    annexPage.drawText("Estas imagenes fueron anexadas por el estudiante durante el proceso de firma.", {
      x: 44,
      y: 92,
      size: 10,
      font: helv
    });
  }

  function getForm() {
    const current = getCurrentContract();
    const isC2 = current && current.pdfFile === "Contrato2.pdf";
    const isC3 = current && current.pdfFile === "Contrato3.pdf";
    const ids = SHARED_FORM_IDS.concat(isC2
      ? ["c2_inscripcion_fecha","c2_tramite","c2_categoria","c2_nombre","c2_doc_tipo","c2_doc_num","c2_doc_de","c2_celular","c2_direccion","c2_correo","c2_eps","c2_estado_civil","c2_nacimiento","c2_sexo","c2_ocupacion","c2_edad","c2_estrato","c2_rh","c2_nivel","c2_necesidad","c2_necesidad_cual","c2_rep_nombre","c2_rep_cc","c2_rep_parentesco","c2_rep_ocupacion","c2_rep_tel","c2_rep_direccion"]
      : (isC3
        ? ["c3_est_nombre","c3_est_doc_tipo","c3_est_doc_num","c3_acu_nombre","c3_acu_doc_tipo","c3_acu_doc_num"]
        : ["est_nombre","est_doc_tipo","est_doc_num","verbal","lentes_contacto","gafas","auditivos","ortopedicos","cual_dispositivo","crc_info_real","tm_animo","tm_intelectual","tm_disociativo","tm_personalidad","tm_impulsos","tm_sueno","tm_tdah","tm_otros","ciudad","fecha","autorizo_tratamiento","autorizo_cea_consorcio","acu_nombre","acu_doc_tipo","acu_doc_num"]));
    const out = {};
    ids.forEach((id) => { out[id] = readFieldValue(id); });
    if (isC2) {
      const [insDD, insMM, insYYYY] = splitDisplayDateParts(out.c2_inscripcion_fecha);
      out.c2_ins_dd = insDD || "";
      out.c2_ins_mmaaaa = insMM && insYYYY ? (insMM + "/" + insYYYY) : "";
      delete out.c2_inscripcion_fecha;
    }
    if (isC3) {
      out.c3_photo_3x4_file_name = contract3Photo3x4FileName;
      out.c3_selfie_file_name = contract3SelfieFileName;
    }
    return out;
  }

  function isValidContactEmail(value) {
    return /^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(must(value).toLowerCase());
  }

  function isValidContactPhone(value) {
    const digits = must(value).replace(/\D/g, "");
    return digits.length >= 8 && digits.length <= 15;
  }

  function validate() {
    if (!accept.checked) return "Debes aceptar el contrato.";
    const current = getCurrentContract();
    const isC2 = current && current.pdfFile === "Contrato2.pdf";
    const isC3 = current && current.pdfFile === "Contrato3.pdf";
    const acu = getAcuState();

    const sharedReq = [
      ["shared_contact_email", "Correo de contacto"],
      ["shared_contact_phone", "Celular de contacto"],
      ["shared_contact_address", "Direccion de residencia"]
    ];
    for (const [id, label] of sharedReq) if (!must(document.getElementById(id).value)) return "Falta: " + label;
    if (!isValidContactEmail(document.getElementById("shared_contact_email").value)) return "El correo de contacto no es valido.";
    if (!isValidContactPhone(document.getElementById("shared_contact_phone").value)) return "El celular de contacto no es valido.";

    if (isC2) {
      const req = [["c2_nombre","Nombre completo"],["c2_doc_tipo","Tipo de documento"],["c2_doc_num","Numero de documento"],["c2_celular","Celular"],["c2_direccion","Direccion"],["c2_correo","Correo electronico"]];
      for (const [id, label] of req) if (!must(document.getElementById(id).value)) return "Falta: " + label;
      if (acu.enabled && !acu.complete) return "Completa todos los datos del representante.";
    } else if (isC3) {
      const req = [["c3_est_nombre","Nombre del estudiante"],["c3_est_doc_tipo","Tipo de documento del estudiante"],["c3_est_doc_num","Numero de documento del estudiante"]];
      for (const [id, label] of req) if (!must(document.getElementById(id).value)) return "Falta: " + label;
      if (acu.enabled && !acu.complete) return "Completa todos los datos del acudiente.";
      if (!contract3Photo3x4DataUrl) return "Debes cargar la foto 3x4 del contrato 3.";
      if (!contract3SelfieDataUrl) return "Debes cargar la selfie del contrato 3.";
    } else {
      const req = [["est_nombre","Nombres y apellidos completos"],["est_doc_tipo","Documento de identidad"],["est_doc_num","Numero de documento"],["verbal","Puedo comunicarme verbalmente"],["lentes_contacto","Lentes de contacto"],["gafas","Gafas"],["auditivos","Equipos auditivos"],["ortopedicos","Equipos ortopedicos"],["crc_info_real","Declaracion CRC"],["tm_animo","Trastorno estado de animo"],["tm_intelectual","Desarrollo intelectual"],["tm_disociativo","Disociativo"],["tm_personalidad","Personalidad"],["tm_impulsos","Control de impulsos"],["tm_sueno","Trastorno del sueno"],["tm_tdah","Deficit de atencion"],["tm_otros","Otros trastornos mentales"],["ciudad","Ciudad"],["fecha","Fecha"],["autorizo_tratamiento","Autorizo tratamiento (SI/NO)"],["autorizo_cea_consorcio","Autorizo CEA/Consorcio (SI/NO)"]];
      for (const [id, label] of req) if (!must(document.getElementById(id).value)) return "Falta: " + label;
      if (must(document.getElementById("ortopedicos").value) === "SI" && !must(document.getElementById("cual_dispositivo").value)) return "Falta: Cual equipo ortopedico utiliza.";
      if (acu.enabled && !acu.complete) return "Completa todos los datos del acudiente.";
    }

    if (!isC3 && signaturePad.isEmpty() && !studentSigDataUrl) return "Debes firmar como estudiante (dibujando o subiendo PNG).";
    if (!isC3 && acu.enabled && signaturePadAcu.isEmpty() && !acuSigDataUrl) return isC2 ? "Debes firmar como representante (dibujando o subiendo PNG)." : "Debes firmar como acudiente (dibujando o subiendo PNG).";
    return "";
  }

  document.getElementById("btnGenerate").onclick = async () => {
    try {
      const vErr = validate();
      if (vErr) return err(vErr);
      const c = getCurrentContract();
      if (!c) return err("No hay contrato configurado.");
      const layout = getContractLayout(c.pdfFile);
      setStep(3);
      const form = getForm();
      const baseResp = await fetch(c.pdfFile);
      if (!baseResp.ok) throw new Error("No se encontro " + c.pdfFile);
      const baseBytes = await baseResp.arrayBuffer();
      const pdf = await PDFLib.PDFDocument.load(baseBytes);
      const pages = pdf.getPages();
      if (c.pdfFile === "Contrato2.pdf" && pages.length < 4) throw new Error("Contrato2.pdf debe tener al menos 4 paginas.");
      if (c.pdfFile !== "Contrato2.pdf" && pages.length < 3) throw new Error("El contrato debe tener al menos 3 paginas.");
      const helv = await pdf.embedFont(PDFLib.StandardFonts.Helvetica);
      const acu = getAcuState();
      let firmaPng = null;
      let firmaAcuPng = null;
      if (c.pdfFile !== "Contrato3.pdf") {
        firmaPng = await pdf.embedPng(studentSigDataUrl || signaturePad.toDataURL("image/png"));
        if (acu.enabled) firmaAcuPng = await pdf.embedPng(acuSigDataUrl || signaturePadAcu.toDataURL("image/png"));
      }
      if (c.pdfFile === "Contrato2.pdf") {
        const P1 = layout.P1; const P4 = layout.P4; const page1 = pages[0]; const page4 = pages[3];
        const [insMM, insYYYY] = (form.c2_ins_mmaaaa || "").split("/");
        const [nacDD, nacMM, nacYYYY] = splitDisplayDateParts(form.c2_nacimiento);
        drawFitText(page1, form.c2_ins_dd, P1.insDD, helv); drawFitText(page1, insMM || "", P1.insMM, helv); drawFitText(page1, insYYYY || "", P1.insYYYY, helv);
        if (form.c2_tramite === "PRIMERA") drawX(page1, P1.tramite.primera.x, P1.tramite.primera.y, P1.tramite.primera.size, helv);
        if (form.c2_tramite === "RECATEGORIZACION") drawX(page1, P1.tramite.recateg.x, P1.tramite.recateg.y, P1.tramite.recateg.size, helv);
        if (form.c2_categoria === "A2") drawX(page1, P1.categoria.a2.x, P1.categoria.a2.y, P1.categoria.a2.size, helv);
        if (form.c2_categoria === "B1") drawX(page1, P1.categoria.b1.x, P1.categoria.b1.y, P1.categoria.b1.size, helv);
        if (form.c2_categoria === "C1") drawX(page1, P1.categoria.c1.x, P1.categoria.c1.y, P1.categoria.c1.size, helv);
        drawFitText(page1, form.c2_nombre, P1.nombre, helv); if (P1.docChecks[form.c2_doc_tipo]) drawX(page1, P1.docChecks[form.c2_doc_tipo].x, P1.docChecks[form.c2_doc_tipo].y, P1.docChecks[form.c2_doc_tipo].size, helv);
        drawFitText(page1, form.c2_doc_num, P1.docNum, helv); drawFitText(page1, form.c2_doc_de, P1.docDe, helv); drawFitText(page1, form.c2_celular, P1.celular, helv); drawFitText(page1, form.c2_direccion, P1.direccion, helv); drawFitText(page1, form.c2_correo, P1.correo, helv); drawFitText(page1, form.c2_eps, P1.eps, helv);
        if (P1.estadoCivil[form.c2_estado_civil]) drawX(page1, P1.estadoCivil[form.c2_estado_civil].x, P1.estadoCivil[form.c2_estado_civil].y, P1.estadoCivil[form.c2_estado_civil].size, helv);
        drawFitText(page1, nacDD || "", P1.nacDD, helv); drawFitText(page1, nacMM || "", P1.nacMM, helv); drawFitText(page1, nacYYYY || "", P1.nacYYYY, helv);
        if (P1.sexo[form.c2_sexo]) drawX(page1, P1.sexo[form.c2_sexo].x, P1.sexo[form.c2_sexo].y, P1.sexo[form.c2_sexo].size, helv);
        if (P1.ocupacion[form.c2_ocupacion]) drawX(page1, P1.ocupacion[form.c2_ocupacion].x, P1.ocupacion[form.c2_ocupacion].y, P1.ocupacion[form.c2_ocupacion].size, helv);
        drawFitText(page1, form.c2_edad, P1.edad, helv); drawFitText(page1, form.c2_estrato, P1.estrato, helv); drawFitText(page1, form.c2_rh, P1.rh, helv);
        if (P1.nivel[form.c2_nivel]) drawX(page1, P1.nivel[form.c2_nivel].x, P1.nivel[form.c2_nivel].y, P1.nivel[form.c2_nivel].size, helv);
        if (P1.necesidad[form.c2_necesidad]) drawX(page1, P1.necesidad[form.c2_necesidad].x, P1.necesidad[form.c2_necesidad].y, P1.necesidad[form.c2_necesidad].size, helv);
        if (form.c2_necesidad === "OTRA") drawFitText(page1, form.c2_necesidad_cual, P1.necesidadCual, helv);
        if (acu.enabled) { drawFitText(page1, form.c2_rep_nombre, P1.repNombre, helv); drawFitText(page1, form.c2_rep_cc, P1.repCc, helv); drawFitText(page1, form.c2_rep_parentesco, P1.repParentesco, helv); drawFitText(page1, form.c2_rep_ocupacion, P1.repOcupacion, helv); drawFitText(page1, form.c2_rep_tel, P1.repTel, helv); drawFitText(page1, form.c2_rep_direccion, P1.repDir, helv); }
        page4.drawImage(firmaPng, { x: P4.firmaEstImg.x, y: P4.firmaEstImg.y, width: P4.firmaEstImg.w, height: P4.firmaEstImg.h });
        if (acu.enabled) { page4.drawImage(firmaAcuPng, { x: P4.firmaRepImg.x, y: P4.firmaRepImg.y, width: P4.firmaRepImg.w, height: P4.firmaRepImg.h }); drawFitText(page4, form.c2_rep_nombre, P4.repNombre, helv); drawFitText(page4, form.c2_rep_cc, P4.repCc, helv); drawFitText(page4, form.c2_rep_tel, P4.repCel, helv); drawFitText(page4, form.c2_rep_direccion, P4.repDir, helv); }
        drawFitText(page4, ("Ref: " + evidenceRef + " | " + stamp()).slice(0, 120), P4.evidencia, helv);
      } else if (c.pdfFile === "Contrato3.pdf") {
        const P3C = layout.P3; const page3 = pages[2];
        drawFitText(page3, form.c3_est_nombre, P3C.estNombre, helv); if (P3C.estDocChecks[form.c3_est_doc_tipo]) drawX(page3, P3C.estDocChecks[form.c3_est_doc_tipo].x, P3C.estDocChecks[form.c3_est_doc_tipo].y, P3C.estDocChecks[form.c3_est_doc_tipo].size, helv); drawFitText(page3, form.c3_est_doc_num, P3C.estDocNum, helv);
        if (acu.enabled) { drawFitText(page3, form.c3_acu_nombre, P3C.acuNombre, helv); drawFitText(page3, form.c3_acu_doc_tipo, P3C.acuDocTipo, helv); drawFitText(page3, form.c3_acu_doc_num, P3C.acuDocNum, helv); }
        drawFitText(page3, ("Ref: " + evidenceRef + " | " + stamp()).slice(0, 120), P3C.evidencia, helv);
        await appendContract3PhotoAnnex(pdf, helv);
      } else {
        const P2 = layout.P2; const P3 = layout.P3; const page2 = pages[1];
        drawFitText(page2, form.est_nombre, P2.nombre, helv); if (P2.docChecks[form.est_doc_tipo]) drawX(page2, P2.docChecks[form.est_doc_tipo].x, P2.docChecks[form.est_doc_tipo].y, P2.docChecks[form.est_doc_tipo].size, helv); drawFitText(page2, form.est_doc_num, P2.docNum, helv);
        markYesNo(page2, P2.verbal, form.verbal); markYesNo(page2, P2.lentes, form.lentes_contacto); markYesNo(page2, P2.gafas, form.gafas); markYesNo(page2, P2.auditivos, form.auditivos); markYesNo(page2, P2.ortopedicos, form.ortopedicos); if (form.ortopedicos === "SI") drawFitText(page2, form.cual_dispositivo, P2.cualDispositivo, helv);
        markYesNo(page2, P2.crc, form.crc_info_real); markYesNo(page2, P2.tm_animo, form.tm_animo); markYesNo(page2, P2.tm_intelectual, form.tm_intelectual); markYesNo(page2, P2.tm_disociativo, form.tm_disociativo); markYesNo(page2, P2.tm_personalidad, form.tm_personalidad); markYesNo(page2, P2.tm_impulsos, form.tm_impulsos); markYesNo(page2, P2.tm_sueno, form.tm_sueno); markYesNo(page2, P2.tm_tdah, form.tm_tdah); markYesNo(page2, P2.tm_otros, form.tm_otros);
        drawFitText(page2, form.ciudad, P2.ciudad, helv); drawFitText(page2, form.fecha, P2.fecha, helv); markYesNo(page2, P2.aut1, form.autorizo_tratamiento); markYesNo(page2, P2.aut2, form.autorizo_cea_consorcio);
        const page3 = pages[2]; page3.drawImage(firmaPng, { x: P3.firmaEstImg.x, y: P3.firmaEstImg.y, width: P3.firmaEstImg.w, height: P3.firmaEstImg.h }); drawFitText(page3, form.est_nombre, P3.nombreEst, helv); drawFitText(page3, form.est_doc_tipo, P3.tipoIdEst, helv); drawFitText(page3, form.est_doc_num, P3.numEst, helv);
        if (acu.enabled) { page3.drawImage(firmaAcuPng, { x: P3.firmaAcuImg.x, y: P3.firmaAcuImg.y, width: P3.firmaAcuImg.w, height: P3.firmaAcuImg.h }); drawFitText(page3, form.acu_nombre, P3.nombreAcu, helv); drawFitText(page3, form.acu_doc_tipo, P3.tipoIdAcu, helv); drawFitText(page3, form.acu_doc_num, P3.numAcu, helv); }
        drawFitText(page3, ("Ref: " + evidenceRef + " | " + stamp()).slice(0, 120), P3.evidencia, helv);
      }

      const outBytes = await pdf.save();
      const blob = new Blob([outBytes], { type: "application/pdf" });
      const previousCategoryCode = currentCategoryCode;
      const previousCategoryLabel = currentCategoryLabel;
      const signedName = c.name;
      const uploadResult = await uploadSignedPdfToApi(blob, c.outFile, form);
      await fetchValidatedAccessState(apiBase);
      prefillSharedEnrollmentFields();
      const a = document.createElement("a"); a.href = URL.createObjectURL(blob); a.download = c.outFile; a.click(); URL.revokeObjectURL(a.href);
      if (!(contractFlowState && contractFlowState.allCompleted)) {
        updateContractUI();
        resetContractEditorState();
        setStep(1);
        await loadPDF();
        const movedToNextCategory = previousCategoryCode && currentCategoryCode && previousCategoryCode !== currentCategoryCode;
        const nextMessage = movedToNextCategory
          ? ("Ahora continua con la categoria " + currentCategoryLabel + " y " + getCurrentContract().name + ".")
          : ("Ahora continua con " + getCurrentContract().name + (currentCategoryLabel ? (" de la categoria " + currentCategoryLabel) : "") + ".");
        ok(signedName + " firmado y subido correctamente." + " " + nextMessage);
      } else {
        setStep(4);
        const completion = await completeContractInBackend();
        if (completion.ok) {
          const studentId = completion.data && completion.data.studentId;
          const backendMsg = completion.data && completion.data.message ? (" " + completion.data.message + ".") : "";
          ok("PDF firmado y cargado correctamente (" + (((uploadResult && uploadResult.fileName) || c.outFile)) + ")." + backendMsg + (studentId ? (" Ref estudiante: " + studentId + ".") : ""));
        }
        else {
          const storedName = ((uploadResult && uploadResult.fileName) || c.outFile);
          const storedUrl = uploadResult && uploadResult.fileUrl ? ("\nArchivo almacenado: " + uploadResult.fileUrl) : "";
          ok("PDF firmado y cargado correctamente en almacenamiento de contratos (" + storedName + "). La finalizacion en backend quedo pendiente: " + completion.message + storedUrl);
          console.warn("Finalizacion de contrato pendiente en backend:", completion.message, completion);
        }
      }
    } catch (e) {
      console.error(e);
      err("Error generando PDF firmado: " + (e && e.message ? e.message : e));
    }
  };

  document.getElementById("btnReset").onclick = () => {
    const formIds = ["shared_contact_email","shared_contact_phone","shared_contact_address","shared_sede","shared_payment_method","shared_payment_note","est_nombre","est_doc_tipo","est_doc_num","verbal","lentes_contacto","gafas","auditivos","ortopedicos","cual_dispositivo","crc_info_real","tm_animo","tm_intelectual","tm_disociativo","tm_personalidad","tm_impulsos","tm_sueno","tm_tdah","tm_otros","ciudad","fecha","autorizo_tratamiento","autorizo_cea_consorcio","acu_nombre","acu_doc_tipo","acu_doc_num","c2_inscripcion_fecha","c2_ins_dd","c2_ins_mmaaaa","c2_tramite","c2_categoria","c2_nombre","c2_doc_tipo","c2_doc_num","c2_doc_de","c2_celular","c2_direccion","c2_correo","c2_eps","c2_estado_civil","c2_nacimiento","c2_sexo","c2_ocupacion","c2_edad","c2_estrato","c2_rh","c2_nivel","c2_necesidad","c2_necesidad_cual","c2_rep_nombre","c2_rep_cc","c2_rep_parentesco","c2_rep_ocupacion","c2_rep_tel","c2_rep_direccion","c3_est_nombre","c3_est_doc_tipo","c3_est_doc_num","c3_acu_nombre","c3_acu_doc_tipo","c3_acu_doc_num"];
    formIds.forEach((id) => { const el = document.getElementById(id); if (el) el.value = ""; });
    const sharedSedeEl = document.getElementById("shared_sede");
    const sharedPaymentMethodEl = document.getElementById("shared_payment_method");
    const sharedPaymentNoteEl = document.getElementById("shared_payment_note");
    if (sharedSedeEl) sharedSedeEl.value = firstNotBlank(contractAccessPayload.shared_sede, contractAccessPayload.sede);
    if (sharedPaymentMethodEl) sharedPaymentMethodEl.value = paymentMethod;
    if (sharedPaymentNoteEl) sharedPaymentNoteEl.value = paymentMethodDetail;
    resetContractEditorState();
    syncCualDispositivoUI();
    contractIndex = Math.max(0, Math.min(CONTRACTS.length - 1, Number.isFinite(Number(contractFlowState && contractFlowState.currentContractIndex)) ? Number(contractFlowState.currentContractIndex) : 0));
    updateContractUI();
    loadPDF();
    setStep(1);
    info("Reiniciado. Continua en " + getCurrentContract().name + (currentCategoryLabel ? (" de la categoria " + currentCategoryLabel) : "") + ".");
  };

  (function setupToolbar() {
    const prevBtn = document.getElementById("prevPage"); const nextBtn = document.getElementById("nextPage"); const zinBtn = document.getElementById("zoomIn"); const zoutBtn = document.getElementById("zoomOut"); const zoomHost = document.getElementById("zoomTools");
    prevBtn.title = "Pagina anterior"; nextBtn.title = "Pagina siguiente"; zinBtn.title = "Acercar (Zoom +)"; zoutBtn.title = "Alejar (Zoom -)";
    async function safeRender() { if (!pdfDoc) return; await renderPage(currentPage); }
    prevBtn.onclick = async () => { if (!pdfDoc || currentPage <= 1) return; currentPage--; await safeRender(); setStep(1); };
    nextBtn.onclick = async () => { if (!pdfDoc || currentPage >= pageCount) return; currentPage++; await safeRender(); setStep(1); };
    const label = document.createElement("div"); label.className = "zoom-val"; label.id = "zoomValue"; label.textContent = Math.round(scale * 100) + "%";
    const wrap = document.createElement("div"); wrap.className = "zoom-ui"; zoomHost.innerHTML = ""; wrap.appendChild(zoutBtn); wrap.appendChild(label); wrap.appendChild(zinBtn); zoomHost.appendChild(wrap); zoutBtn.textContent = "-"; zinBtn.textContent = "+";
    function updateZoomLabel() { label.textContent = Math.round(scale * 100) + "%"; }
    zoutBtn.onclick = async () => { if (!pdfDoc) return; scale = Math.max(scale - 0.15, 1.35); await safeRender(); updateZoomLabel(); };
    zinBtn.onclick = async () => { if (!pdfDoc) return; scale = Math.min(scale + 0.15, 2.4); await safeRender(); updateZoomLabel(); };
    window.updateZoomUI = updateZoomLabel; updateZoomLabel();
  })();

  async function boot() {
    try {
      await validateContractAccessOrFail();
      if (rootWrap) rootWrap.style.display = "";
      const pdfMode = await ensurePdfJs();
      prefillSharedEnrollmentFields();
      updateContractUI();
      setStep(1);
      await loadPDF();
      const fallbackMsg = apiBase !== sanitizeApiBase(apiBaseParam) && sanitizeApiBase(apiBaseParam) ? "\nAPI usada: " + apiBase : "";
      const categoryMsg = currentCategoryLabel ? ("\nCategoria actual: " + currentCategoryLabel + ".") : "";
      info("Acceso validado. Completa el paso 1 (aceptar) para continuar. Motor PDF.js: " + pdfMode + "." + categoryMsg + fallbackMsg);
    } catch (e) {
      console.error(e);
      if (e && e.name === "AccessDeniedError") return;
      if (rootWrap) rootWrap.style.display = "";
      statusBox.className = "alert err";
      statusBox.textContent = "No se pudo iniciar la pagina del contrato. Revisa conexion o configuracion.";
      err("No se pudo iniciar el visor PDF.");
    }
  }

  boot();
})();
