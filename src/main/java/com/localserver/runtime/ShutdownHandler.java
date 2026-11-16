package com.localserver.runtime;

import com.localserver.constants.Constants;

/**
 * Connects JVM shutdown signals (SIGTERM, Ctrl-C) to the runtime lifecycle.
 * Under a systemd user service this is the hook used for "systemctl --user stop".
 */
public final class ShutdownHandler {

    private final ServerRuntime runtime;
    private Thread hook;

    public ShutdownHandler(ServerRuntime runtime) {
        this.runtime = runtime;
    }

    public void register() {
        if (hook != null) {
            return;
        }

        hook = new Thread(() -> {
            System.out.println(Constants.Shutdown.MESSAGE);
            runtime.stop();
        }, Constants.Shutdown.THREAD_NAME);

        Runtime.getRuntime().addShutdownHook(hook);
    }

    public void unregister() {
        Thread current = hook;
        if (current == null) {
            return;
        }

        try {
            Runtime.getRuntime().removeShutdownHook(current);
        } catch (IllegalStateException e) {
            // The JVM is already shutting down, so the hook will run (or is running).
        }
        hook = null;
    }
}
