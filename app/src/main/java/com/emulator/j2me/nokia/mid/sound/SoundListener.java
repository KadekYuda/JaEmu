package com.nokia.mid.sound;
 
/**
 * Listener for state changes of a {@link Sound}.
 *
 * Part of the legacy Nokia UI API ({@code com.nokia.mid.sound}). Many older
 * Nokia-targeted J2ME titles (e.g. God of War, Spider-Man 3) reference this
 * interface; without it on the classpath the game's class fails to resolve
 * with {@code NoClassDefFoundError}.
 */
public interface SoundListener {
    void soundStateChanged(Sound sound, int event);
}