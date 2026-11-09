package com.andrerinas.openheadunit.autoshow;

import java.io.IOException;
import java.net.BindException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.ServerSocket;

/**
 * Whether something is listening on a local port, found by trying to bind it, never by connecting.
 * Android Auto's head unit server takes one connection per start: a probe that connected and closed
 * would use it up, and the real session would then get a connection that is accepted and never
 * answered (the "peer accepted the connection and then sent nothing" failure).
 *
 * With SO_REUSEADDR a bind succeeds over sockets in TIME_WAIT but not over a listening socket, so a
 * failed bind means a listener holds the port.
 */
public final class PortListening {
    private PortListening() {}

    public static boolean isListening(int port) {
        try (ServerSocket s = new ServerSocket()) {
            s.setReuseAddress(true);
            s.bind(new InetSocketAddress(InetAddress.getByName("127.0.0.1"), port));
            return false;
        } catch (BindException taken) {
            return true;
        } catch (IOException other) {
            return false;
        }
    }
}
