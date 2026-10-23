# Reglas del proyecto

## Comandos
- Corre los comandos tú mismo desde Claude Code, con la herramienta **Bash** (Git Bash). No uses la herramienta PowerShell ni le pidas al usuario que abra PowerShell para correrlos.
- Los scripts `.ps1` se lanzan desde Bash: `powershell.exe -ExecutionPolicy Bypass -File compilar.ps1` (o `instalar.ps1 -Ip <IP>`).
- Si un paso solo lo puede hacer el usuario (algo en el TV box con el control), díselo; todo lo demás lo corres tú.

## Compilar e instalar
- Compilar: `powershell.exe -ExecutionPolicy Bypass -File compilar.ps1` → genera `remap.apk` desde `app/`.
- JDK: `C:\Program Files\Java\jdk-21.0.12.1`. SDK: `%LOCALAPPDATA%\Android\Sdk` (`platforms/android-37.0`, `build-tools/36.0.0`). adb: `..\platform-tools\adb.exe`.
- `remap.keystore` firma la APK y no está en git: no la borres ni la regeneres.
