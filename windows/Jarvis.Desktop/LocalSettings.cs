using System.Security.Cryptography;
using System.Text;

namespace Jarvis.Desktop;

internal sealed class LocalSettings
{
    private readonly string _directory = Path.Combine(
        Environment.GetFolderPath(Environment.SpecialFolder.ApplicationData),
        "JARVIS AI");

    public string ReadEndpoint()
    {
        var path = Path.Combine(_directory, "api-url.txt");
        return File.Exists(path) ? File.ReadAllText(path, Encoding.UTF8).Trim() : "";
    }

    public void WriteEndpoint(string endpoint)
    {
        Directory.CreateDirectory(_directory);
        File.WriteAllText(Path.Combine(_directory, "api-url.txt"), endpoint.Trim(), Encoding.UTF8);
    }

    public string? ReadToken()
    {
        var path = Path.Combine(_directory, "access-token.bin");
        if (!File.Exists(path)) return null;
        try
        {
            var clearText = ProtectedData.Unprotect(File.ReadAllBytes(path), null, DataProtectionScope.CurrentUser);
            return Encoding.UTF8.GetString(clearText);
        }
        catch (CryptographicException)
        {
            return null;
        }
    }

    public void WriteToken(string token)
    {
        Directory.CreateDirectory(_directory);
        var protectedToken = ProtectedData.Protect(
            Encoding.UTF8.GetBytes(token),
            null,
            DataProtectionScope.CurrentUser);
        File.WriteAllBytes(Path.Combine(_directory, "access-token.bin"), protectedToken);
    }

    public void ClearToken()
    {
        var path = Path.Combine(_directory, "access-token.bin");
        if (File.Exists(path)) File.Delete(path);
    }
}
