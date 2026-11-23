// Cloudflare Worker proxy. The Anthropic API key stays in a Worker secret, never in the APK.
// Deploy: wrangler deploy proxy/worker.js --name jarvis-proxy
// Secret: wrangler secret put ANTHROPIC_API_KEY
// Optional: wrangler secret put ENABLE_WEB_SEARCH (value "true")
// Optional: set ANTHROPIC_MODEL as a Worker variable if you want to pin another supported model.

const SYSTEM = (lang) =>
  `You are JARVIS, a witty, polite personal assistant that answers by voice. Keep replies short (1-4 sentences) unless asked for more. ` +
  `Detect the language and script the user wrote in (English, Hindi, Urdu or mixed) and reply in the same language and script. ` +
  `You can explain, translate, summarize, write, calculate, brainstorm and solve problems. No markdown unless asked. ` +
  `If you did not use web search, your knowledge may be out of date for current events; say so when it matters. If you used web search, say "According to a web search". ` +
  `You cannot send messages, place calls, set alarms or change phone settings yourself; the app does those. Never claim an action succeeded. ` +
  `(Speech locale hint: ${String(lang || "en-US").slice(0, 24)}.)`;

const corsHeaders = {
  "Access-Control-Allow-Origin": "*",
  "Access-Control-Allow-Methods": "POST, OPTIONS",
  "Access-Control-Allow-Headers": "Content-Type",
  "Content-Type": "application/json; charset=utf-8",
  "Cache-Control": "no-store",
};

const json = (data, status = 200) =>
  new Response(JSON.stringify(data), { status, headers: corsHeaders });

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
    if (req.method === "OPTIONS") return new Response(null, { status: 204, headers: corsHeaders });
    if (req.method !== "POST") return json({ error: "method_not_allowed" }, 405);
    if (!env.ANTHROPIC_API_KEY) return json({ error: "proxy_not_configured" }, 503);

    const declaredLength = Number(req.headers.get("content-length") || 0);
    if (declaredLength > 128_000) return json({ error: "request_too_large" }, 413);

    let body;
    try {
      body = await req.json();
    } catch {
      return json({ error: "invalid_json" }, 400);
    }

    if (!Array.isArray(body?.messages)) return json({ error: "messages_required" }, 400);
    const messages = body.messages.slice(-30).map((message) => ({
      role: message?.role === "assistant" ? "assistant" : "user",
      content: String(message?.content ?? "").slice(0, 4000),
    })).filter((message) => message.content.trim().length > 0);

    if (!messages.length) return json({ error: "messages_required" }, 400);
    if (messages.reduce((total, message) => total + message.content.length, 0) > 100_000) {
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
