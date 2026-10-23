# Guía: onn. 4K Streaming Box limpio, con launcher propio y botones remapeados

Resultado final:
- Sin rastreadores ni bloat de Google.
- Un launcher parecido a Google TV: banner grande con videos, barra "Reproduciendo ahora", clima, fondo que cambia con la hora del día, apps en el orden que quieras, "Seguir mirando" y "Recomendaciones" de **TizenTube**.
- Los botones del control (YouTube, Netflix y otros) abren **la app que tú elijas**.
- Sin que la Play Store salte pidiendo bajar YouTube o Netflix.

Todo es **reversible**. Si algo sale mal, ve a "Restaurar" al final.

Archivos del repo: `README.md` (esta guía), `instalar.ps1` (hace el trabajo por ti), `remap.apk` (el launcher y el servicio de botones), `app/` (código fuente de la APK), `compilar.ps1` (vuelve a generar `remap.apk` desde `app/`).

> **Aviso:** hazlo bajo tu propia responsabilidad. Es para el onn. 4K Streaming Box; en otros equipos no está probado. Lo único que se instala de terceros es `remap.apk`, firmada con una llave de depuración.

> **Fuera de EE. UU.:** el onn. solo se deja configurar desde EE. UU., Canadá o Puerto Rico (revisa la IP en la configuración inicial). Si estás en otro país, **no le hagas restablecimiento de fábrica**: después pide una conexión de EE. UU. (VPN) para volver a configurarla.
>
> **No quites más paquetes del sistema** que los de este script. Quitar otros (por ejemplo `com.android.se` o `com.android.managedprovisioning`) dejó la caja reiniciándose con "data corrupt" y solo se arregló con restablecimiento de fábrica.

---

## 0. Qué necesitas
- Un PC con Windows en **la misma red Wi-Fi/cable** que el TV box.
- El control del TV box.
- **platform-tools de Google (ADB)**: bájalo tú desde el sitio oficial de Android y descomprímelo en una carpeta `platform-tools` junto a `instalar.ps1`.
- Tus apps ya instaladas en el TV box: **TizenTube** (para YouTube), tu app de IPTV, etc. El script no instala ninguna app de terceros, solo la mía.

## 1. Activar la depuración ADB (en el TV box)
1. Ajustes → Sistema → Acerca de.
2. Pulsa **7 veces** sobre "Compilación de Android TV" hasta que diga "Ahora eres desarrollador".
3. Vuelve atrás: Ajustes → Sistema → **Opciones para desarrolladores**.
4. Activa **Depuración USB** (en Google TV esto abre también ADB por la red).

## 2. Encontrar la IP del TV box
Cualquiera de estas sirve:
- En el TV box: Ajustes → Red e Internet → toca tu red → ahí sale la IP (por ejemplo `192.168.1.3`).
- En el router: lista de dispositivos conectados, busca "onn" o "Android".
- En el PC (PowerShell): `arp -a` y busca la IP nueva que no reconoces.

Anota la IP. Conviene **reservarla en el router** para que no cambie.

## 3. Conectar y autorizar
En PowerShell, dentro de la carpeta de la guía:
```
.\platform-tools\adb.exe connect 192.168.1.3:5555
```
En la TV aparece "¿Permitir depuración USB?". Marca **"Permitir siempre desde este equipo"** y acepta.

Si dice `unauthorized`: revoca autorizaciones en Opciones para desarrolladores y repite.
Si no conecta: revisa que PC y TV estén en la misma red, reinicia el TV box y vuelve a probar.

## 4. Correr el script
```
powershell -ExecutionPolicy Bypass -File instalar.ps1 -Ip 192.168.1.3 -SinYoutube -SinNetflix
```
- `-SinYoutube` quita YouTube y YouTube Music del sistema (úsalo si vas a ver YouTube en TizenTube).
- `-SinNetflix` quita Netflix. Quítalo del comando si el amigo sí usa Netflix.
- Quita **solo** lo que existe en ese equipo; lo que no esté, lo ignora.

El script hace, en orden:
1. Quita bloat y rastreo (reversible): anuncios de Android 14, personalización en el dispositivo, aprendizaje federado, Health Connect, calendarios, Nearby, impresión, feedback, TalkBack, juegos de Google.
2. Instala `remap.apk`.
3. Activa el servicio de botones y el lector de TizenTube.
4. Ajustes: sonido en PCM, animaciones a 0.5x, menos escaneo Wi-Fi/Bluetooth, anuncios limitados.
5. Deja mi launcher como predeterminado.

Además: no quita apps que guardaron una red Wi-Fi, y si quita el protector de pantalla de Google pone el básico de Android.

Después **reinicia el TV box**.

## 5. Configurar los botones (en la TV)
1. Abre **"Botones"** (el ícono del control en la fila "Tus apps" del inicio).
2. Sigue las pantallas: pulsa el botón del control que quieres cambiar y elige la app.
3. Típico: botón **YouTube → TizenTube**, botón **Netflix → tu app de IPTV**.
4. Prueba cada botón. La Play Store ya no debe abrirse.

Teclas que **no** se pueden remapear (para no quedarte sin control): flechas, OK, Atrás, Home, Encendido.

## 6. Ordenar el launcher
**Arranca directo:** al encender la caja (o si por cualquier vía aparece el inicio de Google TV), el servicio de botones lo cambia por este launcher. Se puede apagar en ⚙ Ajustes → Apariencia → "Abrir siempre este inicio".

**⚙ Ajustes del launcher** (engranaje junto a la hora): botones del control; tema (automático: claro de día y oscuro de noche, o fijo); mostrar u ocultar banner, clima y fondo; ocultar apps y restablecer el orden; barra de reproducción y su cierre automático; recomendaciones y borrar historial; respaldo (guardar/restaurar) y estado de los permisos. Los ajustes también van al respaldo JSON.

**Qué hay en el inicio:**
- **Clima** junto al reloj, con la ubicación real de la caja.
- **Fondo según la hora:** amanecer, día, atardecer y noche, con la hora real de salida y puesta del sol.
- **Reproduciendo ahora:** si algo suena o está en pausa (TizenTube, YouTube Music…), sale una barra con carátula, título, progreso y botones anterior / pausa / siguiente. OK sobre la barra vuelve a la app.
- **Banner grande:** rota entre lo último que viste y recomendaciones. Con ← → cambias de video y con OK lo reproduces.

**Mover, ver info o desinstalar una app:** en la fila "Tus apps" del inicio, deja **OK sostenido** sobre la app (o pulsa Menú). Sale un menú con Abrir, Mover, Información de la app y Desinstalar. En *Mover*, usa las flechas izquierda/derecha y pulsa OK o Atrás para terminar; el orden se guarda. Las apps del sistema y "Botones" no muestran *Desinstalar*. Al principio, las primeras apps salen en el orden de `Prefs.FIRST` y el resto alfabético; las apps nuevas aparecen al final.

---

## Errores que cometimos (y cómo evitarlos)

| Error | Qué pasó | Qué hacer |
|---|---|---|
| **Desactivar el launcher de Google TV** (`pm disable-user ...launcherx`) | Se abre el asistente de recuperación del sistema | **Nunca lo desactives.** El script no lo toca. |
| Probar el botón Home con `adb input keyevent HOME` | Siempre abría Google TV y parecía que no funcionaba | Esa tecla por ADB **no pasa** por el servicio. Prueba con el **botón real del control**. |
| Reinstalar la APK y quedar sin botones | Reinstalar apaga el servicio de accesibilidad y el lector | Tras cada reinstalación, repite el paso 3 del script (el script ya lo hace). |
| Cambiar la llave de firma y perder las asignaciones de botones | Con otra firma hay que desinstalar, y eso borra los datos de la app | Usa siempre la misma `remap.keystore`. Además, los botones y el orden se respaldan en `Documents/RemapBotones/botones.json` en la caja; al reinstalar se recuperan solos. |
| TV sin sonido en todas las apps | La salida "Automática" mandaba Dolby por HDMI y la TV no lo acepta | Forzar **PCM**: `settings put global encoded_surround_output 1` y **reiniciar**. |
| Pensar que no había filas en el home | La consulta por ADB no ve las filas de otras apps | No te fíes de eso: las recomendaciones de YouTube sí existen en Google TV, solo se reemplazan cambiando de launcher. |
| Querer leer el historial de TizenTube | Sus datos están protegidos (app no depurable) | Se lee lo que **reproduce** (MediaSession), no su base de datos. |
| Fila "Seguir mirando" vacía al inicio | Solo guarda lo que ves después de instalar | Es normal. Se llena viendo videos. |
| Una app IPTV "desapareció" sola | Cambió de paquete o la quitó el usuario | Después de limpiar, revisa con `adb shell pm list packages` que tus apps sigan ahí. |
| Descargas pesadas por mi cuenta (SDK, JDK) | El usuario prefería bajarlas él | Cada quien baja sus descargas; el script usa solo lo que está en la carpeta. |
| Quitar paquetes del sistema fuera de la lista (`com.android.se`, `managedprovisioning`, `sdksandbox`...) y reiniciar | Bucle de reinicio con "data corrupt"; el menú de reset no respondía | **Solo la lista del script.** Si pasa: mantener el botón de la caja mientras se conecta la corriente → Factory reset. |
| Restablecer la caja en Colombia | La configuración dijo "región no compatible" | Configurarla con un PC con VPN de EE. UU. compartiendo internet (punto de acceso de Windows + compartir el adaptador de la VPN). Ya configurada, funciona con cualquier red. |
| Quitar una app que guardó la red Wi-Fi | Android borra esa red | El script ya no quita apps que tengan una red Wi-Fi guardada. |
| Abrir pantallas por ADB mientras se prueba | Se abrieron apps por error (Magma, TizenTube) | Prueba con el control en la mano, no con teclas simuladas. |

## Qué NO tocar
- Servicios de Google (GMS), **Play Store**, WebView.
- Control remoto: `tv.remote.service` y el asistente de voz (katniss).
- Launcher de Google TV (`launcherx`).
- Avisos de actualización: dejarlos para recibir parches de seguridad.

## Dos ajustes que se hacen con el control (ADB no llega)
- Ajustes → Privacidad → **Anuncios**: apagar personalización.
- Ajustes → Privacidad → **Uso y diagnóstico**: apagar.

---

## Comandos útiles
```
adb connect IP:5555
adb shell pm list packages                      # ver apps instaladas
adb shell cmd package install-existing PAQUETE  # devolver una app quitada
adb shell settings put secure enabled_accessibility_services ""   # apagar el servicio de botones (Home vuelve a Google TV)
```

## Restaurar todo
```
powershell -ExecutionPolicy Bypass -File instalar.ps1 -Ip 192.168.1.3 -Restaurar
```
Devuelve las apps quitadas, apaga el servicio, desinstala la APK y revierte los ajustes. Reinicia el TV box.

Si algo queda muy mal: Ajustes → Sistema → Restablecer (borra apps y cuentas, pero devuelve todo lo del sistema).

---

## Datos técnicos (por si quieres cambiar algo)
- Paquete de la APK: `com.remap.buttons`. Servicios: `KeyService` (botones y Home), `MediaWatcher` (lee TizenTube).
- Teclas del control onn: YouTube = BUTTON_3 (keycode 190), Netflix = BUTTON_4 (191).
- Respaldo: cada cambio de botones u orden se escribe en `/sdcard/Documents/RemapBotones/botones.json` (sobrevive a desinstalar la APK). Si la app arranca sin datos, lo lee y se restaura sola. Necesita el permiso "acceso a todos los archivos", que `instalar.ps1` da con `appops set --uid com.remap.buttons MANAGE_EXTERNAL_STORAGE allow`. Para guardar una copia en el PC: `adb pull /sdcard/Documents/RemapBotones/botones.json`.
- TizenTube abre videos con `https://www.youtube.com/tv?v=ID` sobre el paquete `io.gh.reisxd.tizentube.cobalt`.
- Recomendaciones (sin claves ni cuenta), aprendidas solo de lo que se ve en la caja, intercaladas y sin repetir lo ya visto: las sugerencias que YouTube muestra junto a tus 3 últimos videos y lo nuevo de los canales que ves. Nada genérico: si no hay de dónde sacar, la fila se oculta. Se recalculan cada 30 min o al ver algo nuevo, y la última lista se guarda para mostrarse al instante.
- Clima: la ubicación es la que Google calcula en la caja por Wi-Fi (permiso `ACCESS_COARSE_LOCATION`, `instalar.ps1` lo da con `pm grant`); el nombre del lugar sale de `api.bigdatacloud.net` y, si no hay ubicación, se usa la IP (`ipwho.is`). `api.open-meteo.com` da el clima, el amanecer y el atardecer (cada 20 min). Ninguno pide clave.
- Frecuencia de la tele: `settings put secure match_content_frame_rate 2` ("Siempre") hace que la tele cambie a 24/30/60 Hz según el video. `instalar.ps1` lo deja así. Solo funciona si la app de video pide su frecuencia.
- El respaldo `botones.json` (versión 2) también guarda historial, canales y recomendaciones, y se restauran solos al reinstalar.
- Compilar la APK desde el código: `powershell -ExecutionPolicy Bypass -File compilar.ps1`. Hace aapt2 → javac (--release 8) → d8 → zipalign → apksigner y deja `remap.apk` en la carpeta. Necesita un JDK 17+ y el SDK de Android (`platforms;android-37.0` y `build-tools;36.0.0`, en `%LOCALAPPDATA%\Android\Sdk`). El código fuente está en `app/`.
- Firma: la primera vez `compilar.ps1` crea `remap.keystore` (git no la guarda). **Guárdala**: con otra llave la APK no se instala encima y hay que desinstalar la anterior, cosa que `instalar.ps1` hace sola aunque se pierde el historial de "Seguir mirando".
