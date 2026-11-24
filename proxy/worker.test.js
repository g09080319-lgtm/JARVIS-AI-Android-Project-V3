import assert from "node:assert/strict";
import { test } from "node:test";
import worker from "./worker.js";

const serverKey = "test-only-server-secret";

function request(path = "/chat", messages = [{ role: "user", content: "Hello" }]) {
  return new Request(`https://proxy.test${path}`, {
    method: "POST",
    headers: {
      "CF-Connecting-IP": "198.51.100.12",
      "Content-Type": "application/json",
    },
    body: JSON.stringify({ messages, language: "en-US" }),
  });
}

test("forwards the app chat contract to Anthropic and returns { reply }", async (t) => {
  const originalFetch = globalThis.fetch;
  t.after(() => {
    globalThis.fetch = originalFetch;
  });

  let upstreamPayload;
  let rateLimitKey;
  globalThis.fetch = async (url, options) => {
    assert.equal(url, "https://api.anthropic.com/v1/messages");
    assert.equal(options.headers["x-api-key"], serverKey);
    upstreamPayload = JSON.parse(options.body);
    return new Response(JSON.stringify({
      content: [{ type: "text", text: "JARVIS is ready." }],
    }), { status: 200, headers: { "Content-Type": "application/json" } });
  };

  const response = await worker.fetch(request(), {
    ANTHROPIC_API_KEY: serverKey,
    AI_RATE_LIMITER: {
      async limit({ key }) {
        rateLimitKey = key;
        return { success: true };
      },
    },
  });

  assert.equal(response.status, 200);
  assert.deepEqual(await response.json(), { reply: "JARVIS is ready." });
  assert.equal(rateLimitKey, "198.51.100.12");
  assert.deepEqual(upstreamPayload.messages, [{ role: "user", content: "Hello" }]);
  assert.equal(upstreamPayload.max_tokens, 700);
});

test("fails closed when the provider key or rate limiter is not configured", async () => {
  const response = await worker.fetch(request(), { ANTHROPIC_API_KEY: serverKey });

  assert.equal(response.status, 503);
  assert.deepEqual(await response.json(), { error: "proxy_not_configured" });
});

test("rejects requests over the configured IP rate limit before calling Anthropic", async (t) => {
  const originalFetch = globalThis.fetch;
  t.after(() => {
    globalThis.fetch = originalFetch;
  });
  globalThis.fetch = async () => {
    assert.fail("Anthropic must not be called for a rate-limited request");
  };

  const response = await worker.fetch(request(), {
    ANTHROPIC_API_KEY: serverKey,
    AI_RATE_LIMITER: { async limit() { return { success: false }; } },
  });

  assert.equal(response.status, 429);
  assert.deepEqual(await response.json(), { error: "rate_limited" });
});

test("only serves the documented /chat endpoint", async () => {
  const response = await worker.fetch(request("/"), {
    ANTHROPIC_API_KEY: serverKey,
    AI_RATE_LIMITER: { async limit() { return { success: true }; } },
  });

  assert.equal(response.status, 404);
  assert.deepEqual(await response.json(), { error: "not_found" });
});
