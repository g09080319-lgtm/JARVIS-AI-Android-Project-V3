// Cloudflare Worker proxy. The Anthropic API key stays in a Worker secret, never in the APK.

const MAX_REQUEST_BYTES = 512_000;
const MAX_CONVERSATION_CHARACTERS = 64_000;
const MAX_MESSAGE_CHARACTERS = 4_000;
const MAX_MESSAGES = 30;

const SYSTEM = (lang) =>
  `You are JARVIS, a witty, polite personal assistant that answers by voice. Keep replies short (1-4 sentences) unless asked for more. ` +
  `Detect the language and script the user wrote in (English, Hindi, Urdu or mixed) and reply in the same language and script. ` +
  `You can explain, translate, summarize, write, calculate, brainstorm and solve problems. No markdown unless asked. ` +
  `If you did not use web search, your knowledge may be out of date for current events; say so when it matters. If you used web search, say "According to a web search". ` +
  `You cannot send messages, place calls, set alarms or change phone settings yourself; the app does those. Never claim an action succeeded. ` +
  `(Speech locale hint: ${String(lang || "en-US").slice(0, 24)}.)`;

const responseHeaders = {
  "Content-Type": "application/json; charset=utf-8",
  "Cache-Control": "no-store",
};

const json = (data, status = 200) =>
  new Response(JSON.stringify(data), { status, headers: responseHeaders });

const wait = (ms) => new Promise((resolve) => setTimeout(resolve, ms));

async function callAnthropic(payload, apiKey) {
  let lastResponse;
  for (let attempt = 0; attempt < 2; attempt++) {
    try {
      const response = await fetch("https://api.anthropic.com/v1/messages", {
        method: "POST",
        headers: {
          "content-type": "application/json",
          "x-api-key": apiKey,
          "anthropic-version": "2023-06-01",
        },
        body: JSON.stringify(payload),
        signal: AbortSignal.timeout(45_000),
      });
      if (response.ok) return response;
      lastResponse = response;
      // Retry transient upstream throttling/server failures once.
      if (![429, 500, 502, 503, 504].includes(response.status) || attempt === 1) return response;
    } catch (error) {
      if (attempt === 1) throw error;
    }
    await wait(500 * (attempt + 1));
  }
  return lastResponse;
}

export default {
  async fetch(req, env) {
    if (new URL(req.url).pathname !== "/chat") return json({ error: "not_found" }, 404);
    if (req.method === "OPTIONS") return new Response(null, { status: 204, headers: { "Cache-Control": "no-store" } });
    if (req.method !== "POST") return json({ error: "method_not_allowed" }, 405);
    if (!env.ANTHROPIC_API_KEY || !env.AI_RATE_LIMITER) {
      return json({ error: "proxy_not_configured" }, 503);
    }

    const clientIp = req.headers.get("CF-Connecting-IP");
    if (!clientIp) return json({ error: "client_ip_unavailable" }, 503);

    try {
      const { success } = await env.AI_RATE_LIMITER.limit({ key: clientIp });
      if (!success) return json({ error: "rate_limited" }, 429);
    } catch {
      return json({ error: "rate_limiter_unavailable" }, 503);
    }

    const contentLength = req.headers.get("content-length");
    if (contentLength !== null && (!/^\d+$/.test(contentLength) || Number(contentLength) > MAX_REQUEST_BYTES)) {
      return json({ error: "request_too_large" }, 413);
    }

    let body;
    try {
      const requestText = await req.text();
      if (new TextEncoder().encode(requestText).byteLength > MAX_REQUEST_BYTES) {
        return json({ error: "request_too_large" }, 413);
      }
      body = JSON.parse(requestText);
    } catch {
      return json({ error: "invalid_json" }, 400);
    }

    if (!Array.isArray(body?.messages)) return json({ error: "messages_required" }, 400);
    const messages = body.messages.slice(-MAX_MESSAGES)
      .filter((message) =>
        (message?.role === "user" || message?.role === "assistant") &&
        typeof message?.content === "string")
      .map((message) => ({
        role: message.role,
        content: message.content.slice(0, MAX_MESSAGE_CHARACTERS),
      }))
      .filter((message) => message.content.trim().length > 0);

    if (!messages.length) return json({ error: "messages_required" }, 400);
    if (messages.reduce((total, message) => total + message.content.length, 0) > MAX_CONVERSATION_CHARACTERS) {
      return json({ error: "conversation_too_large" }, 413);
    }

    const payload = {
      model: env.ANTHROPIC_MODEL || "claude-sonnet-4-5-20250929",
      max_tokens: 700,
      ...(env.ENABLE_WEB_SEARCH === "true"
        ? { tools: [{ type: "web_search_20250305", name: "web_search", max_uses: 3 }] }
        : {}),
      system: SYSTEM(body.language),
      messages,
    };

    try {
      const upstream = await callAnthropic(payload, env.ANTHROPIC_API_KEY);
      if (!upstream?.ok) {
        // Do not leak upstream response bodies or secret/configuration details to the app.
        return json({ error: "ai_upstream_error", status: upstream?.status || 502 }, 502);
      }
      const data = await upstream.json();
      const reply = (data.content || [])
        .filter((block) => block.type === "text" && typeof block.text === "string")
        .map((block) => block.text)
        .join("\n")
        .trim();

      if (!reply) return json({ error: "empty_ai_response" }, 502);
      return json({ reply });
    } catch (error) {
      const timedOut = error?.name === "TimeoutError" || error?.name === "AbortError";
      return json({ error: timedOut ? "ai_timeout" : "ai_unavailable" }, 502);
    }
  },
};
