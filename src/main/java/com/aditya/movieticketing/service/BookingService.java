package com.aditya.movieticketing.service;

import com.aditya.movieticketing.domain.AppUser;
import com.aditya.movieticketing.domain.Booking;
import com.aditya.movieticketing.domain.BookingSeat;
import com.aditya.movieticketing.domain.BookingStatus;
import com.aditya.movieticketing.domain.DiscountCode;
import com.aditya.movieticketing.domain.NotificationOutbox;
import com.aditya.movieticketing.domain.NotificationType;
import com.aditya.movieticketing.domain.Payment;
import com.aditya.movieticketing.domain.PaymentStatus;
import com.aditya.movieticketing.domain.SeatStatus;
import com.aditya.movieticketing.domain.Show;
import com.aditya.movieticketing.domain.ShowSeat;
import com.aditya.movieticketing.exception.BookingAccessDeniedException;
import com.aditya.movieticketing.exception.BookingNotFoundException;
import com.aditya.movieticketing.exception.HoldExpiredException;
import com.aditya.movieticketing.exception.InvalidBookingStateException;
import com.aditya.movieticketing.exception.InvalidDiscountException;
import com.aditya.movieticketing.exception.ShowNotFoundException;
import com.aditya.movieticketing.exception.UserNotFoundException;
import com.aditya.movieticketing.repository.AppUserRepository;
import com.aditya.movieticketing.repository.BookingRepository;
import com.aditya.movieticketing.repository.BookingSeatRepository;
import com.aditya.movieticketing.repository.DiscountCodeRepository;
import com.aditya.movieticketing.repository.NotificationOutboxRepository;
import com.aditya.movieticketing.repository.PaymentRepository;
import com.aditya.movieticketing.repository.ShowRepository;
import com.aditya.movieticketing.repository.ShowSeatRepository;
import com.aditya.movieticketing.web.dto.BookingResponse;
import com.aditya.movieticketing.web.dto.CancelResponse;
import com.aditya.movieticketing.web.dto.CreateBookingRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
public class BookingService {

    private final ShowRepository showRepository;
    private final ShowSeatRepository showSeatRepository;
    private final BookingRepository bookingRepository;
    private final BookingSeatRepository bookingSeatRepository;
    private final PaymentRepository paymentRepository;
    private final AppUserRepository appUserRepository;
    private final NotificationOutboxRepository notificationOutboxRepository;
    private final DiscountCodeRepository discountCodeRepository;
    private final PricingService pricingService;
    private final RefundService refundService;

    public BookingService(ShowRepository showRepository,
                          ShowSeatRepository showSeatRepository,
                          BookingRepository bookingRepository,
                          BookingSeatRepository bookingSeatRepository,
                          PaymentRepository paymentRepository,
                          AppUserRepository appUserRepository,
                          NotificationOutboxRepository notificationOutboxRepository,
                          DiscountCodeRepository discountCodeRepository,
                          PricingService pricingService,
                          RefundService refundService) {
        this.showRepository = showRepository;
        this.showSeatRepository = showSeatRepository;
        this.bookingRepository = bookingRepository;
        this.bookingSeatRepository = bookingSeatRepository;
        this.paymentRepository = paymentRepository;
        this.appUserRepository = appUserRepository;
        this.notificationOutboxRepository = notificationOutboxRepository;
        this.discountCodeRepository = discountCodeRepository;
        this.pricingService = pricingService;
        this.refundService = refundService;
    }

    /**
     * Confirms a hold into a booking, all in one transaction: verify the hold token and that the
     * seats are still HELD and unexpired, transition them to BOOKED, and create the Booking,
     * BookingSeat rows, a (stub) Payment, and a notification outbox row.
     */
    @Transactional
    public BookingResponse confirm(CreateBookingRequest request) {
        AppUser user = appUserRepository.findById(request.userId())
                .orElseThrow(() -> new UserNotFoundException(request.userId()));
        Show show = showRepository.findById(request.showId())
                .orElseThrow(() -> new ShowNotFoundException(request.showId()));

        List<ShowSeat> seats =
                showSeatRepository.lockSeatsByHoldToken(request.showId(), request.holdToken());
        if (seats.isEmpty()) {
            throw new HoldExpiredException("Hold token not found; the hold may have expired or been released");
        }

        Instant now = Instant.now();
        for (ShowSeat showSeat : seats) {
            boolean stillHeld = showSeat.getStatus() == SeatStatus.HELD
                    && showSeat.getHoldExpiresAt() != null
                    && !showSeat.getHoldExpiresAt().isBefore(now);
            if (!stillHeld) {
                throw new HoldExpiredException("Hold has expired for one or more seats");
            }
        }

        // Re-validate the discount under a row lock and price the booking. The lock lets us check
        // the redemption limit and increment it atomically, preventing over-redemption.
        DiscountCode discount = lockDiscount(request.discountCode());
        PriceBreakdown price = pricingService.price(show, seats, discount, now);
        if (discount != null) {
            discount.setTimesRedeemed(discount.getTimesRedeemed() + 1);
        }

        Booking booking = new Booking(user, show, BookingStatus.CONFIRMED, discount,
                price.subtotal(), price.discount(), price.tax(), price.total());
        bookingRepository.save(booking);

        List<Long> seatIds = seats.stream().map(ss -> ss.getSeat().getId()).toList();
        for (ShowSeat showSeat : seats) {
            showSeat.setStatus(SeatStatus.BOOKED);
            showSeat.setHoldToken(null);
            showSeat.setHoldExpiresAt(null);
            // The partial unique index booking_seat(show_seat_id) WHERE active is the DB backstop.
            bookingSeatRepository.save(new BookingSeat(booking, showSeat));
        }

        // Payment is a stub (no real gateway) — always succeeds.
        Payment payment = new Payment(booking, price.total(), PaymentStatus.SUCCESS,
                "STUB-" + UUID.randomUUID());
        paymentRepository.save(payment);

        // Outbox row written inside the booking transaction; a poller delivers it asynchronously.
        String payload = "Booking " + booking.getId() + " CONFIRMED for show " + show.getId()
                + ", seats " + seatIds + ", total " + price.total();
        notificationOutboxRepository.save(new NotificationOutbox(
                booking, NotificationType.BOOKING_CONFIRMATION, user.getUsername(), payload));

        return new BookingResponse(booking.getId(), show.getId(), user.getId(),
                booking.getStatus().name(), seatIds,
                booking.getSubtotalAmount(), booking.getDiscountAmount(),
                booking.getTaxAmount(), booking.getTotalAmount(), booking.getCreatedAt());
    }

    private DiscountCode lockDiscount(String discountCode) {
        if (!StringUtils.hasText(discountCode)) {
            return null;
        }
        return discountCodeRepository.lockByCode(discountCode.trim())
                .orElseThrow(() -> new InvalidDiscountException("Unknown discount code " + discountCode));
    }

    /**
     * Cancels a booking in one transaction: return its seats to AVAILABLE, deactivate the
     * booking_seat rows, mark the booking CANCELLED, compute the refund from the applicable
     * RefundPolicy, update the payment, and write a cancellation notification to the outbox.
     */
    @Transactional
    public CancelResponse cancel(Long bookingId, Long userId) {
        Booking booking = bookingRepository.findById(bookingId)
                .orElseThrow(() -> new BookingNotFoundException(bookingId));

        // Ownership guard (seam: slice 5 moves this to @PreAuthorize on the principal).
        if (!booking.getUser().getId().equals(userId)) {
            throw new BookingAccessDeniedException("You may only cancel your own booking");
        }
        if (booking.getStatus() == BookingStatus.CANCELLED) {
            throw new InvalidBookingStateException("Booking " + bookingId + " is already cancelled");
        }

        List<BookingSeat> bookingSeats = bookingSeatRepository.findByBooking_IdAndActiveTrue(bookingId);
        for (BookingSeat bookingSeat : bookingSeats) {
            ShowSeat showSeat = bookingSeat.getShowSeat();
            showSeat.setStatus(SeatStatus.AVAILABLE);
            showSeat.setHoldToken(null);
            showSeat.setHoldExpiresAt(null);
            bookingSeat.setActive(false);
        }

        Instant now = Instant.now();
        booking.setStatus(BookingStatus.CANCELLED);
        booking.setCancelledAt(now);

        RefundResult refund = refundService.computeRefund(booking, now);
        paymentRepository.findFirstByBooking_IdOrderByIdDesc(bookingId).ifPresent(payment -> {
            payment.setRefundedAmount(refund.amount());
            if (refund.amount().signum() > 0) {
                payment.setStatus(PaymentStatus.REFUNDED);
            }
        });

        String payload = "Booking " + bookingId + " CANCELLED; refund " + refund.amount()
                + " (" + refund.percentage() + "%)";
        notificationOutboxRepository.save(new NotificationOutbox(
                booking, NotificationType.BOOKING_CANCELLATION, booking.getUser().getUsername(), payload));

        return new CancelResponse(bookingId, booking.getStatus().name(),
                refund.amount(), refund.percentage(), now);
    }
}
