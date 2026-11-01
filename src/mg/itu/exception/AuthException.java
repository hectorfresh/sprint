package mg.itu.exception;

import jakarta.servlet.ServletException;

/**
 * Exception levée par le framework lors d'un échec d'autorisation.
 * Le code HTTP associé (401 ou 403) est utilisé pour construire la réponse.
 */
public class AuthException extends ServletException {

    private final int statusCode;

    public AuthException(int statusCode, String message) {
        super(message);
        this.statusCode = statusCode;
    }

    public int getStatusCode() {
        return statusCode;
    }

    public static AuthException unauthorized(String message) {
        return new AuthException(401, message);
    }

    public static AuthException forbidden(String message) {
        return new AuthException(403, message);
    }
}