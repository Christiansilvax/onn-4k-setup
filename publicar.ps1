# Sube remap.apk al servidor para que todas las cajas se actualicen solas (lo revisan cada hora y al encender).
# Uso:  powershell -ExecutionPolicy Bypass -File compilar.ps1
#       powershell -ExecutionPolicy Bypass -File publicar.ps1 [-Servidor root@107.175.69.207]
# La carpeta sale de update.url (fuera de git): https://<host>/<carpeta>/ -> /var/www/tv-onn/<carpeta>/ en el servidor.
# Primero se sube la APK y al final version.json: una caja nunca ve una version sin su APK completa.
param([string]$Servidor = "root@107.175.69.207")

$ErrorActionPreference = "Stop"
$apk = Join-Path $PSScriptRoot "remap.apk"
$ver = Join-Path $PSScriptRoot "build\version.txt"
$urlFile = Join-Path $PSScriptRoot "update.url"
if (-not (Test-Path $urlFile)) { Write-Host "Falta update.url (la direccion de la carpeta en el servidor)."; exit 1 }
if (-not (Test-Path $apk) -or -not (Test-Path $ver)) { Write-Host "Primero compila: compilar.ps1"; exit 1 }
if ((Get-Item $ver).LastWriteTime -lt (Get-Item $apk).LastWriteTime.AddMinutes(-1)) { Write-Host "build\version.txt no es de esta APK: vuelve a compilar."; exit 1 }

$url = (Get-Content $urlFile -Raw).Trim().TrimEnd('/')
$carpeta = "/var/www/tv-onn/" + ($url -split '/')[-1]
$code = (Get-Content $ver -Raw).Trim()
$sha = (Get-FileHash $apk -Algorithm SHA256).Hash.ToLower()
$json = Join-Path $env:TEMP "version.json"
[System.IO.File]::WriteAllText($json, "{`"code`": $code, `"apk`": `"remap-$code.apk`", `"sha256`": `"$sha`"}", (New-Object System.Text.UTF8Encoding $false))

Write-Host "Subiendo version $code a $Servidor`:$carpeta ..."
scp -q $apk "${Servidor}:$carpeta/remap-$code.apk"
if ($LASTEXITCODE -ne 0) { Write-Host "Fallo al subir la APK."; exit 1 }
scp -q $json "${Servidor}:$carpeta/version.json"
if ($LASTEXITCODE -ne 0) { Write-Host "Fallo al subir version.json."; exit 1 }
# solo se guardan las 3 ultimas APK
ssh $Servidor "cd $carpeta && ls -t remap-*.apk | tail -n +4 | xargs -r rm --"
Remove-Item $json

$check = (Invoke-WebRequest -UseBasicParsing "$url/version.json").Content
Write-Host "Publicado: $check"
Write-Host "Las cajas la instalan en menos de una hora (o al encender)."
