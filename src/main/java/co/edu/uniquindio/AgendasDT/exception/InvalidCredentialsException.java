package co.edu.uniquindio.AgendasDT.exception;

public class InvalidCredentialsException extends RuntimeException {
    public InvalidCredentialsException() { super("Correo o contraseña incorrectos."); }
}
