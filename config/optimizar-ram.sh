#!/bin/bash
# Ahorra RAM en el TV box (2 GB). Uso: bash config/optimizar-ram.sh <IP>        (aplicar)
#                                     bash config/optimizar-ram.sh <IP> revertir
# Hay que repetirlo si el TV box se restablece de fábrica. No toca com.remap.buttons ni los servicios de Google.
IP="${1:?uso: optimizar-ram.sh <IP> [revertir]}"
S="adb -s $IP:5555 shell"
adb connect "$IP:5555" >/dev/null

# apps de terceros: sin segundo plano, sin arranque automático, bucket "restringido"
APPS="com.golite.gnulatv com.esaba.downloader com.fczx.xuper com.cbs.ca io.gh.reisxd.tizentube.cobalt com.magmaplayer com.android.mgstxNF com.cxinventor.file.explorer"

if [ "$2" = "revertir" ]; then
  for p in $APPS; do
    $S "cmd appops set $p RUN_IN_BACKGROUND default; cmd appops set $p RUN_ANY_IN_BACKGROUND default; cmd appops set $p BOOT_COMPLETED default; am set-standby-bucket $p active" >/dev/null 2>&1
  done
  $S "pm enable com.android.dreams.basic; pm enable com.google.android.katniss; pm enable com.google.android.tts; pm enable com.droidlogic.mictoggle"
  $S "settings delete global activity_manager_constants; device_config delete activity_manager max_cached_processes"
  $S "settings put global window_animation_scale 1.0; settings put global transition_animation_scale 1.0; settings put global animator_duration_scale 1.0"
  echo "Revertido."
  exit 0
fi

for p in $APPS; do
  $S "am force-stop $p; cmd appops set $p RUN_IN_BACKGROUND deny; cmd appops set $p RUN_ANY_IN_BACKGROUND deny; cmd appops set $p BOOT_COMPLETED deny; am set-standby-bucket $p restricted" >/dev/null 2>&1
done
$S "pm disable-user --user 0 com.android.dreams.basic"                                   # protector de pantalla
$S "pm disable-user --user 0 com.google.android.katniss"                                 # asistente de voz (botón del micrófono)
$S "pm disable-user --user 0 com.google.android.tts"                                     # voz de TalkBack (ya desactivado)
$S "pm disable-user --user 0 com.droidlogic.mictoggle"                                   # interruptor del micrófono
$S "settings put global activity_manager_constants max_cached_processes=4,max_phantom_processes=16"
$S "device_config put activity_manager max_cached_processes 4; device_config put activity_manager_native_boot use_freezer true"
$S "settings put global window_animation_scale 0.5; settings put global transition_animation_scale 0.5; settings put global animator_duration_scale 0.5"
$S "cmd activity kill-all"
echo "Listo. RAM disponible: $($S "grep MemAvailable /proc/meminfo")"
