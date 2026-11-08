package dev.rentaldisplay.probe;

import java.io.IOException;

/** A generated stationary test can run with restriction mode active; it cannot override the car. */
final class DiagnosticPolicy {
    static void requireStationary(int restriction, int speed) throws IOException {
        if ((restriction != 0 && restriction != 1) || speed != 0)
            throw new IOException("Stationary grid stopped: car reported motion or unrecognised state");
    }
}
