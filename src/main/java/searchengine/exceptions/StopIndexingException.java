package searchengine.exceptions;

public class StopIndexingException extends RuntimeException {
    public StopIndexingException(String message) {
        super(message);
    }
}
