package com.example.util;

import javax.persistence.OptimisticLockException;

public final class Locking {
    private Locking() { }
    /** Le commit peut emballer OptimisticLockException dans RollbackException. */
    public static OptimisticLockException optimisticCause(Throwable error) {
        for(Throwable current=error;current!=null;current=current.getCause()) {
            if(current instanceof OptimisticLockException optimistic) return optimistic;
        }
        return null;
    }
}
