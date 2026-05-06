package uk.gov.dwp.uc.pairtest;

import thirdparty.paymentgateway.TicketPaymentService;
import thirdparty.seatbooking.SeatReservationService;
import uk.gov.dwp.uc.pairtest.domain.TicketTypeRequest;
import uk.gov.dwp.uc.pairtest.domain.TicketTypeRequest.Type;
import uk.gov.dwp.uc.pairtest.exception.InvalidPurchaseException;

/**
 * Implementation of {@link TicketService} that orchestrates the full ticket purchase flow.
 *
 * <p>Responsibilities in order:
 * <ol>
 *   <li>Validate the account ID and ticket requests</li>
 *   <li>Calculate the total payment amount</li>
 *   <li>Calculate the total number of seats to reserve</li>
 *   <li>Call the payment service</li>
 *   <li>Call the seat reservation service</li>
 * </ol>
 *
 * <p>The two thirdparty services are injected via constructor, making this class
 * fully testable without relying on real implementations.
 *
 * <p>Per the template constraint, all helper logic is contained in private methods.
 */
public class TicketServiceImpl implements TicketService {

    // --- Constants ---

    /** Maximum number of tickets allowed in a single purchase. */
    private static final int MAX_TICKETS = 25;

    // --- Dependencies (injected, never newed up internally) ---

    private final TicketPaymentService ticketPaymentService;
    private final SeatReservationService seatReservationService;

    /**
     * Constructs the service with the required thirdparty dependencies.
     *
     * @param ticketPaymentService   handles charging the account
     * @param seatReservationService handles reserving seats
     */
    public TicketServiceImpl(TicketPaymentService ticketPaymentService,
                             SeatReservationService seatReservationService) {
        this.ticketPaymentService = ticketPaymentService;
        this.seatReservationService = seatReservationService;
    }

    /**
     * Processes a ticket purchase request.
     *
     * <p>Validates the request fully before touching either external service.
     * If validation fails, an {@link InvalidPurchaseException} is thrown and
     * neither service is called — no partial state is created.
     *
     * @param accountId          the account making the purchase — must be greater than zero
     * @param ticketTypeRequests one or more ticket requests declaring type and quantity
     * @throws InvalidPurchaseException if any business rule is violated
     */
    @Override
    public void purchaseTickets(Long accountId, TicketTypeRequest... ticketTypeRequests)
            throws InvalidPurchaseException {

        validateAccountId(accountId);
        validateTicketRequests(ticketTypeRequests);
        validateBusinessRules(ticketTypeRequests);

        int totalAmount = calculateTotalAmount(ticketTypeRequests);
        int totalSeats = calculateTotalSeats(ticketTypeRequests);

        ticketPaymentService.makePayment(accountId, totalAmount);
        seatReservationService.reserveSeat(accountId, totalSeats);
    }

    // -------------------------------------------------------------------------
    // Validation — all rules live here, throws immediately on first failure
    // -------------------------------------------------------------------------

    /**
     * Validates that the account ID is a positive integer.
     * All accounts with an ID greater than zero are considered valid per the spec.
     */
    private void validateAccountId(Long accountId) {
        if (accountId == null || accountId <= 0) {
            throw new InvalidPurchaseException("Invalid account ID: must be greater than zero");
        }
    }

    /**
     * Validates that the ticket request array is present and non-empty,
     * and that no individual request has a zero or negative quantity.
     *
     * <p>A null or empty request array means the caller sent nothing — nothing to process.
     * A zero-quantity line item is likely a bug in the calling code and should be rejected.
     */
    private void validateTicketRequests(TicketTypeRequest... ticketTypeRequests) {
        if (ticketTypeRequests == null || ticketTypeRequests.length == 0) {
            throw new InvalidPurchaseException("No ticket requests provided");
        }

        for (TicketTypeRequest request : ticketTypeRequests) {
            if (request.getNoOfTickets() <= 0) {
                throw new InvalidPurchaseException(
                    "Ticket quantity must be greater than zero for type: " + request.getTicketType()
                );
            }
        }
    }

    /**
     * Validates the business rules that span across the full set of requests:
     * <ul>
     *   <li>Total tickets must not exceed {@value MAX_TICKETS}</li>
     *   <li>Child and infant tickets require at least one adult</li>
     * </ul>
     *
     * <p>Infants do not require a seat or payment but they still count as people
     * in the group — and the group cannot exist without an adult.
     */
    private void validateBusinessRules(TicketTypeRequest... ticketTypeRequests) {
        int totalTickets = 0;
        int adultCount = 0;
        int childCount = 0;
        int infantCount = 0;

        for (TicketTypeRequest request : ticketTypeRequests) {
            int qty = request.getNoOfTickets();
            totalTickets += qty;

            switch (request.getTicketType()) {
                case ADULT  -> adultCount  += qty;
                case CHILD  -> childCount  += qty;
                case INFANT -> infantCount += qty;
            }
        }

        if (totalTickets > MAX_TICKETS) {
            throw new InvalidPurchaseException(
                "Cannot purchase more than " + MAX_TICKETS + " tickets at a time. Requested: " + totalTickets
            );
        }

        // Children and infants cannot attend without an adult present
        if ((childCount > 0 || infantCount > 0) && adultCount == 0) {
            throw new InvalidPurchaseException(
                "Child and infant tickets require at least one adult ticket"
            );
        }
    }

    // -------------------------------------------------------------------------
    // Calculation — pure logic, no side effects
    // -------------------------------------------------------------------------

    /**
     * Calculates the total payment amount in pounds.
     *
     * <p>Infant tickets cost £0 — they contribute nothing to the total.
     * Prices are sourced from the {@link Type} enum to avoid magic numbers.
     *
     * @return total amount in £ to charge the account
     */
    private int calculateTotalAmount(TicketTypeRequest... ticketTypeRequests) {
        int total = 0;
        for (TicketTypeRequest request : ticketTypeRequests) {
            total += request.getTicketType().getPrice() * request.getNoOfTickets();
        }
        return total;
    }

    /**
     * Calculates the total number of seats to reserve.
     *
     * <p>Infants are excluded — they sit on an adult's lap and require no seat.
     * Only adult and child tickets result in a reserved seat.
     *
     * @return number of seats to allocate
     */
    private int calculateTotalSeats(TicketTypeRequest... ticketTypeRequests) {
        int seats = 0;
        for (TicketTypeRequest request : ticketTypeRequests) {
            // Infants do not get a seat — they sit on an adult's lap
            if (request.getTicketType() != Type.INFANT) {
                seats += request.getNoOfTickets();
            }
        }
        return seats;
    }
}
