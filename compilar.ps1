# Compila remap.apk desde app/ (launcher + servicio de botones) sin Android Studio ni Gradle.
# Uso:  powershell -ExecutionPolicy Bypass -File compilar.ps1 [-PlayStore]
#       -PlayStore: sin leer la pantalla de otras apps (Xuper) con accesibilidad, que Play Store no admite.
# Necesita: JDK 17+ y el SDK de Android con una plataforma (android-37*) y build-tools (36+).
# La primera vez crea remap.keystore junto al script. Guardala: sin ella las versiones nuevas
# no se pueden instalar encima y hay que desinstalar la APK antes (git no la guarda).

param([switch]$PlayStore)

$ErrorActionPreference = "Stop"
$app   = Join-Path $PSScriptRoot "app"
$build = Join-Path $PSScriptRoot "build"
$apk   = Join-Path $PSScriptRoot "remap.apk"
$ks    = Join-Path $PSScriptRoot "remap.keystore"
$pass  = "remap-onn"   # la llave solo firma esta APK; lo que hay que proteger es el archivo
$minSdk = 26; $targetSdk = 34

# --- localizar JDK ---
$jdk = $env:JAVA_HOME
if (-not $jdk -or -not (Test-Path "$jdk\bin\javac.exe")) {
    $jdk = Get-ChildItem "C:\Program Files\Java","C:\Program Files\Eclipse Adoptium","C:\Program Files\Microsoft" -Directory -Filter "jdk*" -ErrorAction SilentlyContinue |
        Sort-Object Name | Select-Object -Last 1 -ExpandProperty FullName
}
if (-not $jdk -or -not (Test-Path "$jdk\bin\javac.exe")) { Write-Host "No encuentro el JDK. Instala un JDK 17 o mas nuevo."; exit 1 }
$env:JAVA_HOME = $jdk
$env:Path = "$jdk\bin;$env:Path"

# --- localizar SDK ---
$sdk = $env:ANDROID_HOME
if (-not $sdk) { $sdk = "$env:LOCALAPPDATA\Android\Sdk" }
$plat = Get-ChildItem "$sdk\platforms" -Directory -Filter "android-*" -ErrorAction SilentlyContinue |
    Where-Object { Test-Path "$($_.FullName)\android.jar" } | Sort-Object { [version](($_.Name -replace '^android-','' -replace '[^0-9.]','') + '.0') } |
    Select-Object -Last 1
$bt = Get-ChildItem "$sdk\build-tools" -Directory -ErrorAction SilentlyContinue |
    Where-Object { $_.Name -match '^\d+\.\d+\.\d+$' } | Sort-Object { [version]$_.Name } | Select-Object -Last 1
if (-not $plat -or -not $bt) { Write-Host "No encuentro el SDK de Android en $sdk (faltan platforms o build-tools)."; exit 1 }
$jar = "$($plat.FullName)\android.jar"
$bt = $bt.FullName
Write-Host "JDK: $jdk"
Write-Host "SDK: $($plat.Name), build-tools $(Split-Path $bt -Leaf)"

function Run($exe) {
    & $exe @args
    if ($LASTEXITCODE -ne 0) { Write-Host "Fallo: $(Split-Path $exe -Leaf)"; exit 1 }
}

# --- limpiar ---
if (Test-Path $build) { Remove-Item -Recurse -Force $build }
New-Item -ItemType Directory -Force "$build\gen","$build\classes","$build\dex" | Out-Null

Write-Host "1/6 Recursos (aapt2)..."
Run "$bt\aapt2.exe" compile --dir "$app\res" -o "$build\res.zip"
Run "$bt\aapt2.exe" link -o "$build\base.apk" -I $jar --manifest "$app\AndroidManifest.xml" `
    --min-sdk-version $minSdk --target-sdk-version $targetSdk --java "$build\gen" "$build\res.zip"

# clave de TMDB para el catalogo de tus apps (tmdb.key, fuera de git); sin ella solo se aprende lo que ves
$tmdbFile = Join-Path $PSScriptRoot "tmdb.key"
$tmdb = if (Test-Path $tmdbFile) { (Get-Content $tmdbFile -Raw).Trim() } else { "" }
if (-not $tmdb) { Write-Host "Sin tmdb.key: la APK sale sin catalogo (Porque viste / Popular en...)." }
New-Item -ItemType Directory -Force "$build\gen\com\remap\buttons" | Out-Null
$sideload = if ($PlayStore) { "false" } else { "true" }
[System.IO.File]::WriteAllText("$build\gen\com\remap\buttons\Keys.java",
    "package com.remap.buttons;`n/** Generado por compilar.ps1. */`nfinal class Keys {`n    static final String TMDB = `"$tmdb`";`n    static final boolean SIDELOAD = $sideload;`n}`n")

Write-Host "2/6 Codigo Java (javac)..."
$fuentes = Get-ChildItem "$app\src","$build\gen" -Recurse -Filter *.java | ForEach-Object { $_.FullName }
Run "$jdk\bin\javac.exe" --release 8 "-Xlint:-options" -encoding UTF-8 -classpath $jar -d "$build\classes" @fuentes

Write-Host "3/6 Dex (d8)..."
$clases = Get-ChildItem "$build\classes" -Recurse -Filter *.class | ForEach-Object { $_.FullName }
Run "$bt\d8.bat" --release --lib $jar --min-api $minSdk --output "$build\dex" @clases

Write-Host "4/6 Empaquetar..."
Add-Type -AssemblyName System.IO.Compression.FileSystem
$zip = [System.IO.Compression.ZipFile]::Open("$build\base.apk", "Update")
[System.IO.Compression.ZipFileExtensions]::CreateEntryFromFile($zip, "$build\dex\classes.dex", "classes.dex") | Out-Null
$zip.Dispose()
Run "$bt\zipalign.exe" -f -p 4 "$build\base.apk" "$build\aligned.apk"

Write-Host "5/6 Firmar..."
if (-not (Test-Path $ks)) {
    Write-Host "No hay remap.keystore: creo una llave nueva. La APK instalada antes se debe desinstalar (instalar.ps1 -Restaurar) la primera vez."
    Run "$jdk\bin\keytool.exe" -genkeypair -keystore $ks -alias remap -keyalg RSA -keysize 2048 -validity 10000 `
        -dname "CN=remap" -storepass $pass -keypass $pass
}
Run "$bt\apksigner.bat" sign --ks $ks --ks-key-alias remap --ks-pass "pass:$pass" --key-pass "pass:$pass" `
    --v4-signing-enabled false --out $apk "$build\aligned.apk"

Write-Host "6/6 Verificar..."
Run "$bt\apksigner.bat" verify $apk
Write-Host "Listo: $apk"
Write-Host "Para instalarla en el TV box: powershell -ExecutionPolicy Bypass -File instalar.ps1 -Ip <IP>"
