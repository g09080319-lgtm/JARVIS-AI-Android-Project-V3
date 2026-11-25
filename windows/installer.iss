#define AppName "AIVO"
#define AppVersion "4.0.0"
#define AppPublisher "AIVO"
#define AppExeName "Jarvis.Desktop.exe"

[Setup]
AppId={{81E40583-8092-49D7-9E86-0D2C7F8F46B1}
AppName={#AppName}
AppVersion={#AppVersion}
AppPublisher={#AppPublisher}
DefaultDirName={autopf}\JARVIS AI
DefaultGroupName={#AppName}
OutputDir=Output
OutputBaseFilename=AIVO-Setup
ArchitecturesInstallIn64BitMode=x64
PrivilegesRequired=lowest
WizardStyle=modern
UninstallDisplayIcon={app}\{#AppExeName}

[Files]
Source: "publish\*"; DestDir: "{app}"; Flags: ignoreversion recursesubdirs createallsubdirs

[Icons]
Name: "{group}\AIVO"; Filename: "{app}\{#AppExeName}"
Name: "{autodesktop}\AIVO"; Filename: "{app}\{#AppExeName}"; Tasks: desktopicon

[Tasks]
Name: "desktopicon"; Description: "Create a desktop shortcut"; GroupDescription: "Additional icons:"

[Run]
Filename: "{app}\{#AppExeName}"; Description: "Launch AIVO"; Flags: nowait postinstall skipifsilent
