package sample.client.exception;

public class RDBMSClientException extends Exception {

    public RDBMSClientException(String message) {
        super(message);
    }

    public RDBMSClientException(String message, Throwable cause) {
        super(message, cause);
    }

}
