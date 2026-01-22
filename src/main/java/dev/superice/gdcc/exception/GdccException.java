package dev.superice.gdcc.exception;

public abstract class GdccException extends RuntimeException {
    public GdccException(String message) {
        super(message);
    }
}
