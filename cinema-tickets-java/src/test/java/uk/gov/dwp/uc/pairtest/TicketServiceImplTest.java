package uk.gov.dwp.uc.pairtest;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import thirdparty.paymentgateway.TicketPaymentService;
import thirdparty.seatbooking.SeatReservationService;
import uk.gov.dwp.uc.pairtest.domain.TicketTypeRequest;
import uk.gov.dwp.uc.pairtest.domain.TicketTypeRequest.Type;
import uk.gov.dwp.uc.pairtest.exception.InvalidPurchaseException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

/**
 * Integration-style tests for {@link TicketServiceImpl}.
 *
 * <p>These tests mock the two thirdparty services and verify:
 * <ul>
 *   <li>On valid requests — correct values are passed to each service</li>
 *   <li>On invalid requests — neither service is called at all</li>
 * </ul>
 *
 * <p>We test behaviour (what the system does), not implementation (how it does it).
 * We do NOT mock the validator or calculator — those are internal private methods.
 */
@ExtendWith(MockitoExtension.class)
class TicketServiceImplTest {

    @Mock
    private TicketPaymentService ticketPaymentService;

    @Mock
    private SeatReservationService seatReservationService;

    private TicketServiceImpl ticketService;

    @BeforeEach
    void setUp() {
        ticketService = new TicketServiceImpl(ticketPaymentService, seatReservationService);
    }

    // -------------------------------------------------------------------------
    // Valid purchases — verify correct amounts and seat counts reach the services
    // times(1) confirms each service is called exactly once per transaction
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("Adult only — charges correct amount and reserves correct seats")
    void shouldChargeAndReserveCorrectlyForAdultsOnly() {
        ticketService.purchaseTickets(1L, new TicketTypeRequest(Type.ADULT, 2));

        verify(ticketPaymentService, times(1)).makePayment(1L, 50);     // 2 x £25, called exactly once
        verify(seatReservationService, times(1)).reserveSeat(1L, 2);    // 2 seats, called exactly once
    }

    @Test
    @DisplayName("Infant excluded from both payment and seat count")
    void shouldExcludeInfantFromPaymentAndSeats() {
        ticketService.purchaseTickets(1L,
            new TicketTypeRequest(Type.ADULT, 1),
            new TicketTypeRequest(Type.CHILD, 1),
            new TicketTypeRequest(Type.INFANT, 1)
        );

        verify(ticketPaymentService, times(1)).makePayment(1L, 40);     // £25 + £15, infant is £0
        verify(seatReservationService, times(1)).reserveSeat(1L, 2);    // adult + child only, infant gets no seat
    }

    @Test
    @DisplayName("Mixed order — calculates total price and seats correctly")
    void shouldCalculateCorrectlyForMixedOrder() {
        ticketService.purchaseTickets(5L,
            new TicketTypeRequest(Type.ADULT, 3),
            new TicketTypeRequest(Type.CHILD, 2)
        );

        verify(ticketPaymentService, times(1)).makePayment(5L, 105);    // (3 x £25) + (2 x £15)
        verify(seatReservationService, times(1)).reserveSeat(5L, 5);    // 3 adults + 2 children
    }

    @Test
    @DisplayName("Maximum 25 tickets — accepted at the boundary")
    void shouldAcceptExactlyTwentyFiveTickets() {
        ticketService.purchaseTickets(1L, new TicketTypeRequest(Type.ADULT, 25));

        verify(ticketPaymentService, times(1)).makePayment(1L, 625);    // 25 x £25
        verify(seatReservationService, times(1)).reserveSeat(1L, 25);
    }

    @Test
    @DisplayName("Duplicate ticket types — quantities summed correctly, not rejected")
    void shouldSumDuplicateTicketTypesCorrectly() {
        // Two separate ADULT requests — system should sum them: 2 + 3 = 5 adults
        // Total = 5 x £25 = £125, seats = 5
        ticketService.purchaseTickets(1L,
            new TicketTypeRequest(Type.ADULT, 2),
            new TicketTypeRequest(Type.ADULT, 3)
        );

        verify(ticketPaymentService, times(1)).makePayment(1L, 125);
        verify(seatReservationService, times(1)).reserveSeat(1L, 5);
    }

    // -------------------------------------------------------------------------
    // Invalid purchases — verify NEITHER service is called on bad input
    // verifyNoInteractions proves the system fails safely with no side effects
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("Account ID zero — rejected with correct message, no services called")
    void shouldRejectZeroAccountIdAndNotCallServices() {
        var ex = assertThrows(InvalidPurchaseException.class, () ->
            ticketService.purchaseTickets(0L, new TicketTypeRequest(Type.ADULT, 1))
        );

        assertEquals("Invalid account ID: must be greater than zero", ex.getMessage());
        verifyNoInteractions(ticketPaymentService);
        verifyNoInteractions(seatReservationService);
    }

    @Test
    @DisplayName("Negative account ID — rejected, no services called")
    void shouldRejectNegativeAccountIdAndNotCallServices() {
        assertThrows(InvalidPurchaseException.class, () ->
            ticketService.purchaseTickets(-1L, new TicketTypeRequest(Type.ADULT, 1))
        );

        verifyNoInteractions(ticketPaymentService);
        verifyNoInteractions(seatReservationService);
    }

    @Test
    @DisplayName("Child ticket with no adult — rejected with correct message, no services called")
    void shouldRejectChildOnlyOrderAndNotCallServices() {
        var ex = assertThrows(InvalidPurchaseException.class, () ->
            ticketService.purchaseTickets(1L, new TicketTypeRequest(Type.CHILD, 2))
        );

        assertEquals("Child and infant tickets require at least one adult ticket", ex.getMessage());
        verifyNoInteractions(ticketPaymentService);
        verifyNoInteractions(seatReservationService);
    }

    @Test
    @DisplayName("Infant ticket with no adult — rejected, no services called")
    void shouldRejectInfantOnlyOrderAndNotCallServices() {
        assertThrows(InvalidPurchaseException.class, () ->
            ticketService.purchaseTickets(1L, new TicketTypeRequest(Type.INFANT, 1))
        );

        verifyNoInteractions(ticketPaymentService);
        verifyNoInteractions(seatReservationService);
    }

    @Test
    @DisplayName("26 tickets — exceeds maximum, no services called")
    void shouldRejectOrderExceedingMaxTicketsAndNotCallServices() {
        assertThrows(InvalidPurchaseException.class, () ->
            ticketService.purchaseTickets(1L, new TicketTypeRequest(Type.ADULT, 26))
        );

        verifyNoInteractions(ticketPaymentService);
        verifyNoInteractions(seatReservationService);
    }

    @Test
    @DisplayName("Zero ticket quantity — rejected, no services called")
    void shouldRejectZeroQuantityAndNotCallServices() {
        assertThrows(InvalidPurchaseException.class, () ->
            ticketService.purchaseTickets(1L, new TicketTypeRequest(Type.ADULT, 0))
        );

        verifyNoInteractions(ticketPaymentService);
        verifyNoInteractions(seatReservationService);
    }

    @Test
    @DisplayName("Negative ticket quantity — rejected, no services called")
    void shouldRejectNegativeQuantityAndNotCallServices() {
        assertThrows(InvalidPurchaseException.class, () ->
            ticketService.purchaseTickets(1L, new TicketTypeRequest(Type.ADULT, -1))
        );

        verifyNoInteractions(ticketPaymentService);
        verifyNoInteractions(seatReservationService);
    }

    @Test
    @DisplayName("Null request array — rejected, no services called")
    void shouldRejectNullRequestAndNotCallServices() {
        assertThrows(InvalidPurchaseException.class, () ->
            ticketService.purchaseTickets(1L, (TicketTypeRequest[]) null)
        );

        verifyNoInteractions(ticketPaymentService);
        verifyNoInteractions(seatReservationService);
    }

    @Test
    @DisplayName("Empty request array — rejected, no services called")
    void shouldRejectEmptyRequestArrayAndNotCallServices() {
        // Calling purchaseTickets with no ticket arguments produces an empty varargs array
        assertThrows(InvalidPurchaseException.class, () ->
            ticketService.purchaseTickets(1L)
        );

        verifyNoInteractions(ticketPaymentService);
        verifyNoInteractions(seatReservationService);
    }
}