package com.emulator.j2me.core

import java.io.InputStream

/**
 * Static proxy invoked by patched game bytecode in place of
 * `Class.getResourceAsStream(name)`.  Since the game uses
 * `"a".getClass().getResourceAsStream(name)` which resolves through
 * the BootClassLoader (and therefore never finds JAR resources), the
 * JarTransformer rewrites every such `invokevirtual` to
 * `invokestatic J2meResourceProxy.getResourceAsStream`, routing the
 * lookup through our ZipFile-backed emulator engine instead.
 */
object J2meResourceProxy {
    @JvmStatic
    fun getResourceAsStream(name: String): InputStream? =
        EmulatorEngine.getResourceAsStream(name)
}
