package uk.gov.dwp.uc.pairtest.exception;

/**
 * Thrown when a ticket purchase request violates one or more business rules.
 *
 * <p>Extends {@link RuntimeException} as per the template contract — callers are
 * not forced to catch it, but it carries a clear message explaining exactly what
 * was invalid so the cause is never ambiguous.
 */
public class InvalidPurchaseException extends RuntimeException {

    /**
     * Constructs an exception with a descriptive message explaining the violation.
     *
     * @param message human-readable description of why the purchase was rejected
     */
    public InvalidPurchaseException(String message) {
        super(message);
    }
}
