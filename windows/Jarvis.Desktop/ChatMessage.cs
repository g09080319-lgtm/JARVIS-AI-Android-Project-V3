namespace Jarvis.Desktop;

public sealed record ChatMessage(string Role, string Content)
{
    public string Speaker => Role == "user" ? "YOU" : "AIVO";
}
