const JSON_HEADERS = {
  "content-type": "application/json; charset=utf-8",
  "cache-control": "no-store",
  "x-content-type-options": "nosniff",
};
const MAX_BODY_BYTES = 128_000;
const MAX_MESSAGES = 30;
const MAX_MESSAGE_CHARS = 4_000;
const SUPPORTED_LANGUAGES = new Set(["en-US", "ur-PK", "hi-IN"]);
const jwksCache = new Map();
const utf8 = new TextEncoder();

function json(data, status = 200, headers = {}) {
  return new Response(JSON.stringify(data), {
    status,
    headers: { ...JSON_HEADERS, ...headers },
  });
}

function base64UrlBytes(value) {
  const base64 = value.replace(/-/g, "+").replace(/_/g, "/");
  const padded = base64.padEnd(Math.ceil(base64.length / 4) * 4, "=");
  const binary = atob(padded);
  return Uint8Array.from(binary, (char) => char.charCodeAt(0));
}

function decodeJsonPart(value) {
  return JSON.parse(new TextDecoder().decode(base64UrlBytes(value)));
}

async function loadJwks(url, forceRefresh = false) {
  const now = Date.now();
  const cached = jwksCache.get(url);
  if (!forceRefresh && cached && cached.expiresAt > now) return cached.keys;

  const response = await fetch(url, {
    headers: { accept: "application/json" },
  });
  if (!response.ok) throw new Error("Identity key service unavailable");
  const body = await response.json();
  if (!Array.isArray(body.keys)) throw new Error("Invalid identity key set");
  jwksCache.set(url, { keys: body.keys, expiresAt: now + 5 * 60_000 });
  return body.keys;
}

function validateClaims(claims, env) {
  const now = Math.floor(Date.now() / 1000);
  const audiences = Array.isArray(claims.aud) ? claims.aud : [claims.aud];
  if (claims.iss !== env.OIDC_ISSUER) return false;
  if (!audiences.includes(env.OIDC_AUDIENCE)) return false;
  if (typeof claims.sub !== "string" || claims.sub.length === 0 || claims.sub.length > 256) return false;
  if (!Number.isFinite(claims.exp) || claims.exp <= now) return false;
  if (claims.nbf !== undefined && (!Number.isFinite(claims.nbf) || claims.nbf > now + 60)) return false;
  if (claims.iat !== undefined && (!Number.isFinite(claims.iat) || claims.iat > now + 60)) return false;
  return true;
}

async function verifyAccessToken(request, env) {
  if (!env.OIDC_ISSUER || !env.OIDC_AUDIENCE || !env.OIDC_JWKS_URL) return null;

  let jwksUrl;
  try {
    jwksUrl = new URL(env.OIDC_JWKS_URL);
    if (jwksUrl.protocol !== "https:") return null;
  } catch {
    return null;
  }

  const authorization = request.headers.get("authorization") || "";
  const match = /^Bearer ([A-Za-z0-9._~-]+)$/.exec(authorization);
  if (!match || match[1].length > 16_384) return null;

  const parts = match[1].split(".");
  if (parts.length !== 3) return null;

  try {
    const header = decodeJsonPart(parts[0]);
    const claims = decodeJsonPart(parts[1]);
    if (!["RS256", "ES256"].includes(header.alg) || typeof header.kid !== "string") return null;
    if (!validateClaims(claims, env)) return null;

    let keys = await loadJwks(jwksUrl.href);
    const matchesAlgorithm = (key) =>
      key.kid === header.kid &&
      key.alg === header.alg &&
      (key.use === undefined || key.use === "sig") &&
      (header.alg === "RS256"
        ? key.kty === "RSA"
        : key.kty === "EC" && key.crv === "P-256");
    let jwk = keys.find(matchesAlgorithm);
    if (!jwk) {
      keys = await loadJwks(jwksUrl.href, true);
      jwk = keys.find(matchesAlgorithm);
    }
    if (!jwk) return null;

    const algorithm = header.alg === "RS256"
      ? { name: "RSASSA-PKCS1-v1_5", hash: "SHA-256" }
      : { name: "ECDSA", namedCurve: "P-256" };
    const key = await crypto.subtle.importKey(
      "jwk",
      jwk,
      algorithm,
      false,
      ["verify"],
    );
    const signedContent = utf8.encode(`${parts[0]}.${parts[1]}`);
    const signature = base64UrlBytes(parts[2]);
    const verifyAlgorithm = header.alg === "RS256"
      ? "RSASSA-PKCS1-v1_5"
      : { name: "ECDSA", hash: "SHA-256" };
    if (!await crypto.subtle.verify(verifyAlgorithm, key, signature, signedContent)) return null;
    return { subject: claims.sub };
  } catch {
    return null;
  }
}

function corsHeaders(request, env) {
  const origin = request.headers.get("origin");
  if (!origin) return {};
  const allowed = (env.ALLOWED_ORIGINS || "")
    .split(",")
    .map((value) => value.trim())
    .filter(Boolean);
  if (!allowed.includes(origin)) return null;
  return {
    "access-control-allow-origin": origin,
    "access-control-allow-methods": "POST, OPTIONS",
    "access-control-allow-headers": "authorization, content-type",
    "access-control-max-age": "600",
    vary: "Origin",
  };
}

function validateMessages(value) {
  if (!Array.isArray(value) || value.length === 0 || value.length > MAX_MESSAGES) return null;
  const messages = [];
  let totalChars = 0;
  for (const item of value) {
    if (!item || !["user", "assistant"].includes(item.role) || typeof item.content !== "string") return null;
    const content = item.content.trim();
    if (!content || content.length > MAX_MESSAGE_CHARS) return null;
    totalChars += content.length;
    if (totalChars > 50_000) return null;
    messages.push({ role: item.role, content });
  }
  if (messages[messages.length - 1].role !== "user") return null;
  return messages;
}

async function readBoundedBody(request) {
  const reader = request.body?.getReader();
  if (!reader) return "";
  const chunks = [];
  let length = 0;
  try {
    while (true) {
      const { done, value } = await reader.read();
      if (done) break;
      length += value.byteLength;
      if (length > MAX_BODY_BYTES) {
        await reader.cancel();
        throw new RangeError("request_too_large");
      }
      chunks.push(value);
    }
  } finally {
    reader.releaseLock();
  }
  const bytes = new Uint8Array(length);
  let offset = 0;
  for (const chunk of chunks) {
    bytes.set(chunk, offset);
    offset += chunk.byteLength;
  }
  return new TextDecoder().decode(bytes);
}

export default {
  async fetch(request, env) {
    const cors = corsHeaders(request, env);
    if (cors === null) return json({ error: "origin_not_allowed" }, 403);
    if (request.method === "OPTIONS") {
      return new Response(null, { status: 204, headers: { ...JSON_HEADERS, ...cors } });
    }

    const url = new URL(request.url);
    if (request.method === "GET" && url.pathname === "/health") {
      return json({ status: "ok" }, 200, cors);
    }
    if (request.method !== "POST" || url.pathname !== "/v1/chat") {
      return json({ error: "not_found" }, 404, cors);
    }

    const principal = await verifyAccessToken(request, env);
    if (!principal) return json({ error: "unauthorized" }, 401, cors);
    if (!env.ANTHROPIC_API_KEY || !env.RATE_LIMITER?.limit) {
      return json({ error: "service_not_configured" }, 503, cors);
    }

    let limitResult;
    try {
      const digest = await crypto.subtle.digest("SHA-256", new TextEncoder().encode(principal.subject));
      const key = Array.from(new Uint8Array(digest), (byte) => byte.toString(16).padStart(2, "0")).join("");
      limitResult = await env.RATE_LIMITER.limit({ key });
    } catch {
      return json({ error: "service_not_configured" }, 503, cors);
    }
    if (!limitResult?.success) return json({ error: "rate_limited" }, 429, cors);

    const declaredLength = Number(request.headers.get("content-length") || 0);
    if (declaredLength > MAX_BODY_BYTES) return json({ error: "request_too_large" }, 413, cors);

    let text;
    try {
      text = await readBoundedBody(request);
    } catch (error) {
      if (error instanceof RangeError) return json({ error: "request_too_large" }, 413, cors);
      return json({ error: "invalid_json" }, 400, cors);
    }

    let body;
    try {
      body = JSON.parse(text);
    } catch {
      return json({ error: "invalid_json" }, 400, cors);
    }

    const messages = validateMessages(body?.messages);
    if (!messages) return json({ error: "invalid_messages" }, 400, cors);
    if (body.language !== undefined && !SUPPORTED_LANGUAGES.has(body.language)) {
      return json({ error: "invalid_language" }, 400, cors);
    }
    const language = body.language || "en-US";
    const payload = {
      model: env.ANTHROPIC_MODEL || "claude-sonnet-4-5-20250929",
      max_tokens: 800,
      system: `You are AIVO, a careful personal assistant. Reply concisely in the user's language (${language}). Never claim a device action was completed; the client performs device actions.`,
      messages,
    };

    try {
      const upstream = await fetch("https://api.anthropic.com/v1/messages", {
        method: "POST",
        headers: {
          "content-type": "application/json",
          "x-api-key": env.ANTHROPIC_API_KEY,
          "anthropic-version": "2023-06-01",
        },
        body: JSON.stringify(payload),
        signal: AbortSignal.timeout(45_000),
      });
      if (!upstream.ok) return json({ error: "ai_upstream_error" }, 502, cors);

      const result = await upstream.json();
      const reply = (result.content || [])
        .filter((block) => block.type === "text" && typeof block.text === "string")
        .map((block) => block.text)
        .join("\n")
        .trim();
      if (!reply) return json({ error: "empty_ai_response" }, 502, cors);
      return json({ reply }, 200, cors);
    } catch {
      return json({ error: "ai_unavailable" }, 502, cors);
    }
  },
};
