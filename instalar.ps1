# Ajusta un onn. 4K Streaming Box (Google TV): quita bloat y rastreo, instala el launcher + remapeo de botones.
# Uso:  powershell -ExecutionPolicy Bypass -File instalar.ps1 -Ip 192.168.1.3 [-SinYoutube] [-SinNetflix]
#       powershell -ExecutionPolicy Bypass -File instalar.ps1 -Ip 192.168.1.3 -Restaurar
param(
    [Parameter(Mandatory = $true)][string]$Ip,
    [switch]$SinYoutube,   # quita YouTube y YouTube Music del sistema (usa TizenTube en su lugar)
    [switch]$SinNetflix,   # quita Netflix del sistema
    [switch]$Restaurar     # devuelve todo lo que este script quito
)

$ErrorActionPreference = "Continue"
$apk = Join-Path $PSScriptRoot "remap.apk"

# --- localizar adb ---
$adb = (Get-Command adb -ErrorAction SilentlyContinue).Source
if (-not $adb) { $adb = Join-Path $PSScriptRoot "platform-tools\adb.exe" }
if (-not (Test-Path $adb)) { $adb = Join-Path $PSScriptRoot "..\platform-tools\adb.exe" }
if (-not (Test-Path $adb)) { $adb = "$env:LOCALAPPDATA\Android\Sdk\platform-tools\adb.exe" }
if (-not (Test-Path $adb)) { Write-Host "No encuentro adb. Descarga platform-tools de Google y descomprimelo junto a este script (carpeta platform-tools)."; exit 1 }
function A { & $adb -s "${Ip}:5555" @args }

# --- paquetes ---
$rastreo = "com.android.adservices.api","com.android.ondevicepersonalization.services","com.android.federatedcompute.services",
  "com.google.android.partnersetup","com.google.android.syncadapters.calendar","com.android.providers.calendar",
  "com.android.healthconnect.controller","com.android.health.connect.backuprestore","com.android.nearby.halfsheet",
  "com.android.printspooler","com.android.htmlviewer","com.android.wallpaperbackup",
  "com.google.android.feedback","com.android.tv.feedbackconsent","com.google.android.apps.tv.dreamx",
  "com.google.android.marvin.talkback","com.google.android.play.games"
$youtube = "com.google.android.youtube.tv","com.google.android.youtube.tvmusic"
$netflix = "com.netflix.ninja","com.netflix.tokenmanager"

Write-Host "Conectando a ${Ip}:5555 ..."
& $adb connect "${Ip}:5555" | Out-Host
$modelo = (A shell getprop ro.product.model).Trim()
if (-not $modelo) { Write-Host "No hay conexion. Revisa la IP y que la depuracion ADB este activada (mira la guia)."; exit 1 }
Write-Host "Dispositivo: $modelo"
if ($modelo -notmatch "onn") { Write-Host "OJO: no parece un onn. Seguir puede no ser seguro."; if ((Read-Host "Escribe SI para continuar") -ne "SI") { exit 1 } }

if ($Restaurar) {
    foreach ($p in ($rastreo + $youtube + $netflix)) { "$p -> " + (A shell cmd package install-existing $p) }
    A shell settings put secure enabled_accessibility_services '""' | Out-Null
    A shell settings delete secure limit_ad_tracking | Out-Null
    A shell settings put global encoded_surround_output 0 | Out-Null
    A shell settings put global wifi_scan_always_enabled 1 | Out-Null
    A shell settings delete secure screensaver_components | Out-Null
    A uninstall com.remap.buttons | Out-Host
    Write-Host "Restaurado. Reinicia el TV box."
    exit 0
}

# --- 1. quitar bloat y rastreo (reversible, no borra la app del sistema) ---
$quitar = $rastreo
if ($SinYoutube) { $quitar += $youtube }
if ($SinNetflix) { $quitar += $netflix }
# Una app que guardo una red Wi-Fi no se quita: Android borraria esa red (idea de free-my-tv).
$wifi = (A shell "dumpsys wifi | grep -o -E 'cname=[^ ]*'") -replace 'cname=', ''
foreach ($p in $quitar) {
    if ($wifi -contains $p) { "se queda $p (guardo una red Wi-Fi)"; continue }
    if ((A shell pm list packages $p) -match [regex]::Escape($p)) { "quitando $p -> " + (A shell pm uninstall -k --user 0 $p) }
}
# Sin dreamx no queda protector de pantalla: usa el basico de Android para no marcar la TV.
if ((A shell settings get secure screensaver_components) -match "dreamx") {
    A shell settings put secure screensaver_components com.android.dreams.basic/.Colors | Out-Null
}

# --- 2. instalar la APK ---
if (-not (Test-Path $apk)) { Write-Host "Falta remap.apk junto al script."; exit 1 }
$r = A install -r $apk 2>&1 | Out-String; Write-Host $r
if ($r -match "UPDATE_INCOMPATIBLE") {   # firmada con otra llave (p. ej. remap.keystore nueva): hay que desinstalar antes
    Write-Host "La APK instalada tiene otra firma. La desinstalo y la vuelvo a instalar (se pierde el historial de 'Seguir mirando')."
    A uninstall com.remap.buttons | Out-Host
    A install $apk | Out-Host
}

# --- 3. activar servicios (hay que repetirlo cada vez que se reinstala la APK) ---
A shell appops set --uid com.remap.buttons MANAGE_EXTERNAL_STORAGE allow | Out-Null   # antes del servicio: al conectarse recupera el respaldo de botones en Documents/RemapBotones
A shell settings put secure enabled_accessibility_services com.remap.buttons/com.remap.buttons.KeyService | Out-Null
A shell settings put secure accessibility_enabled 1 | Out-Null
A shell cmd notification allow_listener com.remap.buttons/com.remap.buttons.MediaWatcher | Out-Null

# --- 4. ajustes del sistema ---
A shell settings put global encoded_surround_output 1 | Out-Null     # PCM: evita TV sin sonido por HDMI
A shell settings put global window_animation_scale 0.5 | Out-Null
A shell settings put global transition_animation_scale 0.5 | Out-Null
A shell settings put global animator_duration_scale 0.5 | Out-Null
A shell settings put global wifi_scan_always_enabled 0 | Out-Null
A shell settings put global ble_scan_always_enabled 0 | Out-Null
A shell settings put secure limit_ad_tracking 1 | Out-Null

# --- 5. launcher predeterminado (NO se desactiva el de Google TV) ---
A shell cmd package set-home-activity com.remap.buttons/.HomeActivity | Out-Host

Write-Host ""
Write-Host "Listo. Reinicia el TV box. Luego pulsa Home con el CONTROL (por adb no funciona)."
Write-Host "Falta, en la TV: abrir 'Botones del control' y elegir la app de cada boton."
