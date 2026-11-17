package facadepattern;

public class FlightBookingFacade {

    // Encapsulated subsystem references
    private final SeatInventorySystem seatSystem;
    private final BaggagePricingEngine baggageEngine;
    private final CurrencyConverter currencyConverter;
    private final PaymentProcessor paymentProcessor;
    private final TicketIssuer ticketIssuer;

    public FlightBookingFacade() {
        this.seatSystem = new SeatInventorySystem();
        this.baggageEngine = new BaggagePricingEngine();
        this.currencyConverter = new CurrencyConverter();
        this.paymentProcessor = new PaymentProcessor();
        this.ticketIssuer = new TicketIssuer();
    }

    /**
     * Phase 1 signature: Preserves backward compatibility by defaulting to "STANDARD".
     */
    public String bookFlight(String passengerName,
                             String flightCode,
                             String seatNumber,
                             double baseFareUSD,
                             int bagCount,
                             double bagWeightKg,
                             String currency,
                             String cardNumber) {
        return bookFlight(
                passengerName,
                flightCode,
                seatNumber,
                baseFareUSD,
                bagCount,
                bagWeightKg,
                currency,
                cardNumber,
                "STANDARD"
        );
    }

    /**
     * Phase 2 signature: Coordinates seat locking, loyalty baggage rules, currency conversion,
     * promo code application, payment execution, rollback on failure, and ticket issuance.
     */
    public String bookFlight(String passengerName,
                             String flightCode,
                             String seatNumber,
                             double baseFareUSD,
                             int bagCount,
                             double bagWeightKg,
                             String currency,
                             String cardNumber,
                             String loyaltyTier) {
        String lockToken = null;
        String tier = (loyaltyTier == null) ? "STANDARD" : loyaltyTier.trim().toUpperCase();

        try {
            // 1. Connect to seat server and lock the requested seat
            seatSystem.connectToReservationServer();
            lockToken = seatSystem.lockSeat(flightCode, seatNumber, passengerName);

            // 2. Compute baggage fee in USD (SILVER and GOLD bypass BaggagePricingEngine)
            double baggageFeeUSD = 0.0;
            if (!"SILVER".equals(tier) && !"GOLD".equals(tier)) {
                baggageFeeUSD = baggageEngine.calculateBaggageFeeUSD(bagCount, bagWeightKg);
            }
            double totalUSD = baseFareUSD + baggageFeeUSD;

            // 3. Convert total USD amount into target currency
            double convertedTotal = currencyConverter.convertFromUSD(totalUSD, currency);

            // 4. Initialize payment gateway, apply SILVER promo if eligible, and charge card
            paymentProcessor.initializeGateway();
            if ("SILVER".equals(tier)) {
                paymentProcessor.applyPromoCode("SILVER-FREE-BAG");
            }
            String receiptId = paymentProcessor.chargeCard(cardNumber, convertedTotal, currency);

            // 5. Generate and return the final e-ticket code
            return ticketIssuer.generateTicket(passengerName, flightCode, lockToken, receiptId);

        } catch (RuntimeException ex) {
            // 6. Rollback: release seat lock if it was acquired prior to failure
            if (lockToken != null) {
                seatSystem.releaseSeatLock(lockToken);
            }
            System.out.println("[FlightBookingFacade] Booking error: " + ex.getMessage());
            return "BOOKING_FAILED: " + ex.getMessage();
        } finally {
            // 7. Always disconnect from seat server
            seatSystem.disconnect();
        }
    }
}