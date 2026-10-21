package com.remap.buttons;

import android.view.KeyEvent;

/** Nombres en español sencillo para los botones del control. */
final class KeyNames {
    private KeyNames() { }

    static String of(int k) {
        switch (k) {
            case KeyEvent.KEYCODE_BUTTON_3: return "Botón YouTube";
            case KeyEvent.KEYCODE_BUTTON_4: return "Botón Netflix";
            case KeyEvent.KEYCODE_PROG_RED: return "Botón rojo";
            case KeyEvent.KEYCODE_PROG_GREEN: return "Botón verde";
            case KeyEvent.KEYCODE_PROG_YELLOW: return "Botón amarillo";
            case KeyEvent.KEYCODE_PROG_BLUE: return "Botón azul";
            case KeyEvent.KEYCODE_MENU: return "Botón Menú";
            case KeyEvent.KEYCODE_SETTINGS: return "Botón Ajustes";
            case KeyEvent.KEYCODE_GUIDE: return "Botón Guía";
            case KeyEvent.KEYCODE_TV: return "Botón TV";
            case KeyEvent.KEYCODE_INFO: return "Botón Info";
            case KeyEvent.KEYCODE_SEARCH: return "Botón Buscar";
            case KeyEvent.KEYCODE_ASSIST:
            case KeyEvent.KEYCODE_VOICE_ASSIST: return "Botón del asistente (micrófono)";
            case KeyEvent.KEYCODE_VOLUME_UP: return "Volumen +";
            case KeyEvent.KEYCODE_VOLUME_DOWN: return "Volumen -";
            case KeyEvent.KEYCODE_VOLUME_MUTE: return "Silenciar";
            case KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE: return "Play / Pausa";
            case KeyEvent.KEYCODE_MEDIA_PLAY: return "Play";
            case KeyEvent.KEYCODE_MEDIA_PAUSE: return "Pausa";
            case KeyEvent.KEYCODE_MEDIA_STOP: return "Stop";
            case KeyEvent.KEYCODE_MEDIA_NEXT: return "Siguiente";
            case KeyEvent.KEYCODE_MEDIA_PREVIOUS: return "Anterior";
            case KeyEvent.KEYCODE_MEDIA_REWIND: return "Retroceder";
            case KeyEvent.KEYCODE_MEDIA_FAST_FORWARD: return "Adelantar";
            case KeyEvent.KEYCODE_CHANNEL_UP: return "Canal +";
            case KeyEvent.KEYCODE_CHANNEL_DOWN: return "Canal -";
            case KeyEvent.KEYCODE_DPAD_UP: return "Flecha arriba";
            case KeyEvent.KEYCODE_DPAD_DOWN: return "Flecha abajo";
            case KeyEvent.KEYCODE_DPAD_LEFT: return "Flecha izquierda";
            case KeyEvent.KEYCODE_DPAD_RIGHT: return "Flecha derecha";
            case KeyEvent.KEYCODE_DPAD_CENTER:
            case KeyEvent.KEYCODE_ENTER: return "Botón OK";
            case KeyEvent.KEYCODE_BACK: return "Botón Atrás";
            case KeyEvent.KEYCODE_HOME: return "Botón Inicio";
            case KeyEvent.KEYCODE_POWER: return "Botón Encendido";
            default:
                if (k >= KeyEvent.KEYCODE_0 && k <= KeyEvent.KEYCODE_9) return "Número " + (k - KeyEvent.KEYCODE_0);
                return "Botón nº " + k;
        }
    }
}
