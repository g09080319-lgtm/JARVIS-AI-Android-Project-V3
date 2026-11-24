using System.Collections.ObjectModel;
using System.Speech.Recognition;
using System.Speech.Synthesis;
using System.Windows;
using System.Windows.Controls;
using System.Windows.Input;

namespace Jarvis.Desktop;

public partial class MainWindow : Window
{
    private readonly LocalSettings _settings = new();
    private readonly SharedAiClient _client = new();
    private readonly SpeechSynthesizer _speaker = new();
    private readonly ObservableCollection<ChatMessage> _messages = new();
    private SpeechRecognitionEngine? _recognizer;
    private string _language = "en-US";
    private bool _busy;

    public MainWindow()
    {
        InitializeComponent();
        DataContext = this;
        EndpointBox.Text = Environment.GetEnvironmentVariable("JARVIS_API_BASE_URL") ?? _settings.ReadEndpoint();
        TokenBox.Password = _settings.ReadToken() ?? "";
        Closing += (_, _) => StopRecognition();
    }

    public ObservableCollection<ChatMessage> Messages => _messages;

    private async void Send_Click(object sender, RoutedEventArgs e) => await SendPromptAsync();

    private async void PromptBox_KeyDown(object sender, KeyEventArgs e)
    {
        if (e.Key != Key.Enter || Keyboard.Modifiers.HasFlag(ModifierKeys.Shift)) return;
        e.Handled = true;
        await SendPromptAsync();
    }

    private void SaveAccess_Click(object sender, RoutedEventArgs e)
    {
        try
        {
            SaveAccess();
            StatusText.Text = "Endpoint and access token saved for this Windows user.";
        }
        catch (Exception exception)
        {
            StatusText.Text = exception.Message;
        }
    }

    private void SaveAccess()
    {
        var endpoint = EndpointBox.Text.Trim().TrimEnd('/');
        if (!Uri.TryCreate(endpoint, UriKind.Absolute, out var uri) || uri.Scheme != Uri.UriSchemeHttps)
        {
            throw new InvalidOperationException("Enter the HTTPS backend origin, without /v1/chat.");
        }
        _settings.WriteEndpoint(endpoint);
        if (!string.IsNullOrWhiteSpace(TokenBox.Password))
        {
            _settings.WriteToken(TokenBox.Password.Trim());
            TokenBox.Password = "";
        }
    }

    private async Task SendPromptAsync()
    {
        var prompt = PromptBox.Text.Trim();
        if (_busy || prompt.Length == 0) return;

        string accessToken;
        try
        {
            SaveAccess();
            accessToken = _settings.ReadToken() ?? "";
            if (accessToken.Length == 0)
                throw new InvalidOperationException("Enter an identity access token, then choose Save access.");
        }
        catch (Exception exception)
        {
            StatusText.Text = exception.Message;
            return;
        }

        PromptBox.Clear();
        _messages.Add(new ChatMessage("user", prompt));
        _busy = true;
        SendButton.IsEnabled = false;
        StatusText.Text = "JARVIS is thinking…";
        try
        {
            var reply = await _client.SendAsync(
                EndpointBox.Text,
                accessToken,
                _language,
                _messages.TakeLast(30).ToArray(),
                CancellationToken.None);
            _messages.Add(new ChatMessage("assistant", reply));
            StatusText.Text = "Ready · session-only conversation";
            if (SpeakRepliesBox.IsChecked == true) _speaker.SpeakAsync(reply);
            ConversationScroll.ScrollToEnd();
        }
        catch (Exception exception)
        {
            _messages.RemoveAt(_messages.Count - 1);
            StatusText.Text = exception.Message;
        }
        finally
        {
            _busy = false;
            SendButton.IsEnabled = true;
        }
    }

    private void Listen_Click(object sender, RoutedEventArgs e)
    {
        if (_recognizer is not null)
        {
            StopRecognition();
            StatusText.Text = "Voice listening stopped.";
            return;
        }

        try
        {
            var culture = System.Globalization.CultureInfo.GetCultureInfo(_language);
            var recognizerInfo = SpeechRecognitionEngine.InstalledRecognizers()
                .FirstOrDefault(item => item.Culture.Name.Equals(culture.Name, StringComparison.OrdinalIgnoreCase))
                ?? throw new InvalidOperationException($"Install the Windows speech recognition language pack for {culture.DisplayName}.");
            _recognizer = new SpeechRecognitionEngine(recognizerInfo);
            _recognizer.LoadGrammar(new DictationGrammar());
            _recognizer.SetInputToDefaultAudioDevice();
            _recognizer.SpeechRecognized += (_, args) =>
            {
                if (args.Result.Confidence < 0.65f) return;
                Dispatcher.Invoke(() => {
                    if (_busy) return;
                    PromptBox.Text = args.Result.Text;
                    _ = SendPromptAsync();
                });
            };
            _recognizer.RecognizeCompleted += (_, _) => Dispatcher.Invoke(StopRecognition);
            _recognizer.RecognizeAsync(RecognizeMode.Multiple);
            StatusText.Text = $"Listening in {culture.DisplayName}. Click Mic to stop.";
        }
        catch (Exception exception)
        {
            StopRecognition();
            StatusText.Text = exception.Message;
        }
    }

    private void LanguageBox_SelectionChanged(object sender, SelectionChangedEventArgs e)
    {
        if (LanguageBox.SelectedItem is ComboBoxItem { Tag: string language }) _language = language;
    }

    private void StopRecognition()
    {
        if (_recognizer is null) return;
        try { _recognizer.RecognizeAsyncCancel(); } catch (InvalidOperationException) { }
        _recognizer.Dispose();
        _recognizer = null;
    }
}
