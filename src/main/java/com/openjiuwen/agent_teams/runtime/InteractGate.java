/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.agent_teams.runtime;

/**
 * Counter of in-flight interact payloads with a close-and-drain step.
 *
 * <p>Mirrors Python's {@code InteractGate} in
 * {@code openjiuwen/agent_teams/runtime/gate.py}.</p>
 */
public class InteractGate {

    private boolean closed;
    private int inflight;

    public synchronized boolean isClosed() {
        return closed;
    }

    public synchronized int getInflight() {
        return inflight;
    }

    public synchronized AdmissionTicket admit() {
        if (closed) {
            return null;
        }
        inflight += 1;
        return new AdmissionTicket(this);
    }

    public synchronized void consumeDone(AdmissionTicket ticket) {
        if (ticket == null || ticket.gate() != this || inflight <= 0) {
            return;
        }
        inflight -= 1;
        if (inflight == 0) {
            notifyAll();
        }
    }

    public void closeAndDrain() {
        synchronized (this) {
            closed = true;
            while (inflight > 0) {
                try {
                    wait();
                } catch (InterruptedException interrupted) {
                    Thread.currentThread().interrupt();
                    throw new IllegalStateException("Interrupted while draining interact gate", interrupted);
                }
            }
        }
    }

    public synchronized void reset() {
        closed = false;
        inflight = 0;
        notifyAll();
    }
}
