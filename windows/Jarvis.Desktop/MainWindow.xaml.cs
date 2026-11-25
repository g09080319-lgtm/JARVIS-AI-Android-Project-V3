using System.Collections.ObjectModel;
using System.Net.NetworkInformation;
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
    private bool _connectionVerified;

    public MainWindow()
    {
        InitializeComponent();
        DataContext = this;
        EndpointBox.Text = Environment.GetEnvironmentVariable("JARVIS_PROXY_URL")
            ?? Environment.GetEnvironmentVariable("JARVIS_API_BASE_URL")
            ?? _settings.ReadEndpoint();
        TokenBox.Password = _settings.ReadToken() ?? "";
        UpdateConnectionStatus();
        NetworkStateText.Text = NetworkInterface.GetIsNetworkAvailable() ? "Online" : "Offline";
        NetworkChange.NetworkAvailabilityChanged += NetworkAvailabilityChanged;
        Closing += (_, _) =>
        {
            NetworkChange.NetworkAvailabilityChanged -= NetworkAvailabilityChanged;
            StopRecognition();
            _speaker.SpeakAsyncCancelAll();
            _speaker.Dispose();
        };
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
            _connectionVerified = false;
            UpdateConnectionStatus();
            SetCoreState("IDLE", "Connection settings saved; backend not tested.", "#52F29B");
        }
        catch (Exception exception)
        {
            SetError(exception.Message);
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
                throw new InvalidOperationException("Add a short-lived access token from your OIDC identity provider in Settings.");
        }
        catch (Exception exception)
        {
            SetError(exception.Message);
            return;
        }

        PromptBox.Clear();
        _messages.Add(new ChatMessage("user", prompt));
        _busy = true;
        SendButton.IsEnabled = false;
        SetCoreState("THINKING", "Processing your request…", "#20DFFF");
        try
        {
            var reply = await _client.SendAsync(
                EndpointBox.Text,
                accessToken,
                _language,
                _messages.TakeLast(30).ToArray(),
                CancellationToken.None);
            _messages.Add(new ChatMessage("assistant", reply));
            _connectionVerified = true;
            UpdateConnectionStatus();
            SetCoreState("IDLE", "Ready · session-only conversation", "#52F29B");
            if (SpeakRepliesBox.IsChecked == true)
            {
                _speaker.SpeakCompleted -= Speaker_SpeakCompleted;
                _speaker.SpeakCompleted += Speaker_SpeakCompleted;
                _speaker.SpeakAsync(reply);
                SetCoreState("SPEAKING", "Speaking · choose Stop voice to interrupt.", "#52F29B");
                StopVoiceButton.IsEnabled = true;
            }
            ConversationScroll.ScrollToEnd();
        }
        catch (Exception exception)
        {
            _messages.RemoveAt(_messages.Count - 1);
            var message = exception.Message;
            if (message.Contains("expired", StringComparison.OrdinalIgnoreCase) ||
                message.Contains("access token", StringComparison.OrdinalIgnoreCase))
            {
                BackendStateText.Text = "Auth required";
            }
            else
            {
                BackendStateText.Text = "Request failed";
            }
            SetError(message);
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
            SetCoreState("IDLE", "Voice listening stopped.", "#52F29B");
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
            MicrophoneStateText.Text = "Ready";
            _recognizer.SpeechRecognized += (_, args) =>
            {
                if (args.Result.Confidence < 0.65f) return;
                Dispatcher.Invoke(() => {
                    if (_busy) return;
                    PromptBox.Text = args.Result.Text;
                    SetCoreState("THINKING", "Voice captured · processing request…", "#20DFFF");
                    _ = SendPromptAsync();
                });
            };
            _recognizer.RecognizeCompleted += (_, _) => Dispatcher.Invoke(() =>
            {
                StopRecognition();
                if (!_busy) SetCoreState("IDLE", "Ready to help you…", "#52F29B");
            });
            _recognizer.RecognizeAsync(RecognizeMode.Multiple);
            SetCoreState("LISTENING", $"Listening in {culture.DisplayName}. Click Mic to stop.", "#20DFFF");
        }
        catch (Exception exception)
        {
            StopRecognition();
            MicrophoneStateText.Text = "Unavailable";
            SetError(exception.Message);
        }
    }

    private void LanguageBox_SelectionChanged(object sender, SelectionChangedEventArgs e)
    {
        if (LanguageBox.SelectedItem is ComboBoxItem { Tag: string language })
        {
            _language = language;
            SelectedLanguageText.Text = LanguageBox.SelectedItem is ComboBoxItem item
                ? item.Content?.ToString() ?? language
                : language;
            if (_recognizer is not null) StopRecognition();
        }
    }

    private void StopRecognition()
    {
        if (_recognizer is null) return;
        try { _recognizer.RecognizeAsyncCancel(); } catch (InvalidOperationException) { }
        _recognizer.Dispose();
        _recognizer = null;
        MicrophoneStateText.Text = "Ready";
    }

    private void Settings_Click(object sender, RoutedEventArgs e)
    {
        SettingsPanel.Visibility = SettingsPanel.Visibility == Visibility.Visible
            ? Visibility.Collapsed
            : Visibility.Visible;
    }

    private void StopVoice_Click(object sender, RoutedEventArgs e)
    {
        StopRecognition();
        _speaker.SpeakAsyncCancelAll();
        StopVoiceButton.IsEnabled = false;
        SetCoreState("IDLE", "Voice output stopped.", "#52F29B");
    }

    private void ChatShortcut_Click(object sender, RoutedEventArgs e)
    {
        PromptBox.Focus();
        Keyboard.Focus(PromptBox);
    }

    private void AndroidFeature_Click(object sender, RoutedEventArgs e)
    {
        SetCoreState(
            "IDLE",
            "WhatsApp and call announcements are available in the Android app; Windows notification integration is not implemented.",
            "#20DFFF");
    }

    private void Speaker_SpeakCompleted(object? sender, SpeakCompletedEventArgs e)
    {
        Dispatcher.Invoke(() =>
        {
            StopVoiceButton.IsEnabled = false;
            if (!_busy) SetCoreState("IDLE", "Ready · session-only conversation", "#52F29B");
        });
    }

    private void NetworkAvailabilityChanged(object? sender, NetworkAvailabilityEventArgs e)
    {
        Dispatcher.Invoke(() => NetworkStateText.Text = e.IsAvailable ? "Online" : "Offline");
    }

    private void UpdateConnectionStatus()
    {
        var endpoint = EndpointBox.Text.Trim();
        var hasEndpoint = Uri.TryCreate(endpoint, UriKind.Absolute, out var uri) &&
            uri.Scheme == Uri.UriSchemeHttps;
        var hasToken = !string.IsNullOrWhiteSpace(_settings.ReadToken());
        BackendStateText.Text = _connectionVerified
            ? "Connected"
            : !hasEndpoint ? "URL needed"
            : !hasToken ? "Token needed"
            : "Not checked";
        if (_connectionVerified)
        {
            SetCoreState("IDLE", "Shared AI backend verified by the last successful response.", "#52F29B");
        }
        else if (!hasEndpoint)
        {
            StatusText.Text = "Set the deployed HTTPS Worker origin in Settings. No backend URL is configured.";
        }
        else if (!hasToken)
        {
            StatusText.Text = "Add a short-lived access token from your configured OIDC provider in Settings.";
        }
        else
        {
            StatusText.Text = "Backend configuration is present; connection is unverified until a request succeeds.";
        }
    }

    private void SetError(string message)
    {
        StatusText.Text = message;
        CoreStateText.Text = "ERROR";
        CoreStateText.Foreground = new System.Windows.Media.SolidColorBrush(
            (System.Windows.Media.Color)System.Windows.Media.ColorConverter.ConvertFromString("#FFFF657A"));
    }

    private void SetCoreState(string state, string message, string color)
    {
        CoreStateText.Text = state;
        CoreStateText.Foreground = new System.Windows.Media.SolidColorBrush(
            (System.Windows.Media.Color)System.Windows.Media.ColorConverter.ConvertFromString(color));
        CoreStatusText.Text = message;
        StatusText.Text = message;
    }
}
