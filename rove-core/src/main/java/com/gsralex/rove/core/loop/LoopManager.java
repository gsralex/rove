package com.gsralex.rove.core.loop;

import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ThreadFactory;

public final class LoopManager implements AutoCloseable {

    private static final LoopManager SHARED = new LoopManager();

    private final ExecutorService executor;

    public LoopManager() {
        ThreadFactory factory = Thread.ofVirtual().name("rove-loop-", 0).factory();
        this.executor = Executors.newThreadPerTaskExecutor(factory);
    }

    public LoopManager(ExecutorService executor) {
        this.executor = executor;
    }

    public static LoopManager shared() {
        return SHARED;
    }

    public <T> T call(Callable<T> task) {
        try {
            return executor.submit(task).get();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("loop interrupted", e);
        } catch (ExecutionException e) {
            Throwable c = e.getCause();
            if (c instanceof RuntimeException re) {
                throw re;
            }
            if (c instanceof Error err) {
                throw err;
            }
            throw new RuntimeException(c);
        }
    }

    public <T> Future<T> submit(Callable<T> task) {
        return executor.submit(task);
    }

    public ExecutorService executor() {
        return executor;
    }

    @Override
    public void close() {
        if (this != SHARED) {
            executor.close();
        }
    }
}
