using System.Net.Http;
using System.Net.Http.Headers;
using System.Net.Http.Json;
using System.Text.Json;

namespace Jarvis.Desktop;

internal sealed class SharedAiClient
{
    private static readonly HttpClient Http = new()
    {
        Timeout = TimeSpan.FromSeconds(50),
    };

    public async Task<string> SendAsync(
        string baseUrl,
        string accessToken,
        string language,
        IReadOnlyList<ChatMessage> messages,
        CancellationToken cancellationToken)
    {
        if (!Uri.TryCreate(baseUrl.Trim().TrimEnd('/'), UriKind.Absolute, out var origin) ||
            origin.Scheme != Uri.UriSchemeHttps)
        {
            throw new InvalidOperationException("Set the shared API HTTPS origin in Settings.");
        }

        using var request = new HttpRequestMessage(HttpMethod.Post, new Uri(origin, "/v1/chat"));
        request.Headers.Authorization = new AuthenticationHeaderValue("Bearer", accessToken);
        request.Content = JsonContent.Create(new
        {
            language,
            messages = messages.Select(message => new { role = message.Role, content = message.Content }),
        });

        using var response = await Http.SendAsync(request, cancellationToken);
        var responseBody = await response.Content.ReadAsStringAsync(cancellationToken);
        if (!response.IsSuccessStatusCode)
        {
            var error = ReadError(responseBody);
            throw new InvalidOperationException(response.StatusCode switch
            {
                System.Net.HttpStatusCode.Unauthorized => "The access token is missing or expired. Sign in again.",
                System.Net.HttpStatusCode.TooManyRequests => "The shared AI rate limit was reached. Try again shortly.",
                _ => error ?? $"The AI service returned HTTP {(int)response.StatusCode}.",
            });
        }

        using var document = JsonDocument.Parse(responseBody);
        var reply = document.RootElement.TryGetProperty("reply", out var value) ? value.GetString()?.Trim() : null;
        return string.IsNullOrWhiteSpace(reply)
            ? throw new InvalidOperationException("The AI service returned an empty reply.")
            : reply;
    }

    private static string? ReadError(string body)
    {
        try
        {
            using var document = JsonDocument.Parse(body);
            return document.RootElement.TryGetProperty("error", out var error) ? error.GetString() : null;
        }
        catch (JsonException)
        {
            return null;
        }
    }
}
