package uk.gov.dwp.uc.pairtest.domain;

/**
 * Immutable value object representing a request for a specific number of tickets of a given type.
 *
 * <p>Immutability is enforced by:
 * <ul>
 *   <li>Declaring the class {@code final} — cannot be subclassed to bypass state</li>
 *   <li>All fields are {@code private final} — set once at construction, never changed</li>
 *   <li>No setters exist — state cannot be mutated after creation</li>
 * </ul>
 *
 * <p>This matters because a {@code TicketTypeRequest} represents what a customer declared
 * at the point of purchase. That fact should be fixed — immutability ensures no part of
 * the system can silently alter it between validation, payment, and seat reservation.
 */
public final class TicketTypeRequest {

    private final int noOfTickets;
    private final Type type;

    /**
     * Constructs an immutable ticket request.
     *
     * @param type        the type of ticket (ADULT, CHILD, or INFANT)
     * @param noOfTickets the number of tickets requested
     */
    public TicketTypeRequest(Type type, int noOfTickets) {
        this.type = type;
        this.noOfTickets = noOfTickets;
    }

    /**
     * Returns the number of tickets requested.
     *
     * @return number of tickets
     */
    public int getNoOfTickets() {
        return noOfTickets;
    }

    /**
     * Returns the type of ticket requested.
     *
     * @return ticket type
     */
    public Type getTicketType() {
        return type;
    }

    /**
     * Supported ticket types, each with their price in pounds.
     *
     * <p>Embedding the price in the enum avoids magic numbers elsewhere in the codebase.
     * If pricing changes, it changes here and nowhere else.
     */
    public enum Type {
        ADULT(25),
        CHILD(15),
        INFANT(0);

        private final int price;

        Type(int price) {
            this.price = price;
        }

        /**
         * Returns the ticket price in pounds.
         *
         * @return price in £
         */
        public int getPrice() {
            return price;
        }
    }
}
