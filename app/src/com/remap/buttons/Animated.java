package com.remap.buttons;

/** Fondo que se mueve: el inicio lo enciende al verse y lo apaga al irse (no gasta mientras ves algo). */
interface Animated {
    void start();
    void stop();
}
