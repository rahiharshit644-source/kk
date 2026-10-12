package com.soltini.app.overlay

/**
 * Common contract for every floating-orb style (wireframe / particle sphere).
 * OverlayService only talks to this interface, so styles can be swapped live from Settings.
 */
interface OrbIndicator {
    /** Raw mic/speaker amplitude, 0f..~1f. */
    var amplitude: Float

    /** Drives the colour / pulse behaviour of the orb. */
    var state: RingState
}
