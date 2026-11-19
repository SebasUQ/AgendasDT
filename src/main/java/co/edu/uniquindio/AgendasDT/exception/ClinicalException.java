package co.edu.uniquindio.AgendasDT.exception;
import org.springframework.http.HttpStatus;
public class ClinicalException extends RuntimeException {
    private final HttpStatus status;
    public ClinicalException(HttpStatus status, String message) { super(message); this.status = status; }
    public HttpStatus getStatus() { return status; }
}
