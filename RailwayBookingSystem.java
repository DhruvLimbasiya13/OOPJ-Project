import java.io.*;
import java.text.*;
import java.util.*;
import java.util.stream.*;

// ─────────────────────────────────────────────────────────────────────────────
// CUSTOM EXCEPTIONS
// ─────────────────────────────────────────────────────────────────────────────

// Exception thrown when Aadhaar number is not exactly 12 digits
class InvalidAadhaarException extends Exception {
    public InvalidAadhaarException(String message) { super(message); }
}

// Exception thrown when journey date is in the past or has invalid format
class InvalidDateException extends Exception {
    public InvalidDateException(String message) { super(message); }
}

// Exception thrown when no confirmed, RAC, or waiting-list seats remain
class SeatNotAvailableException extends Exception {
    public SeatNotAvailableException(String message) { super(message); }
}

// Exception thrown when the supplied PNR does not match any stored ticket
class InvalidPNRException extends Exception {
    public InvalidPNRException(String message) { super(message); }
}

// Exception thrown when a Passenger train is booked with a non-SL coach
class InvalidCoachForTrainException extends Exception {
    public InvalidCoachForTrainException(String message) { super(message); }
}

// ─────────────────────────────────────────────────────────────────────────────
// ENUM: CoachType  –  replaces the old String-based coach constants
// ─────────────────────────────────────────────────────────────────────────────

// Enum representing each available coach type with its fare, capacity and label
enum CoachType {
    SL(1, 72, "Sleeper"),
    THREE_AC(2, 64, "AC 3 Tier"),
    TWO_AC(3, 54, "AC 2 Tier"),
    FIRST_AC(4, 26, "First AC");

    final int ratePerKm;
    final int totalSeats;
    final String displayName;

    CoachType(int ratePerKm, int totalSeats, String displayName) {
        this.ratePerKm  = ratePerKm;
        this.totalSeats = totalSeats;
        this.displayName = displayName;
    }

    // Parse user input like "SL", "3A", "2A", "1A" into the enum constant
    public static CoachType fromInput(String input) {
        return switch (input.trim().toUpperCase()) {
            case "SL"           -> SL;
            case "3A", "THREE_AC" -> THREE_AC;
            case "2A", "TWO_AC"   -> TWO_AC;
            case "1A", "FIRST_AC" -> FIRST_AC;
            default -> throw new IllegalArgumentException("Invalid coach type: " + input);
        };
    }

    // Short code used in menus and file names
    public String shortCode() {
        return switch (this) {
            case SL       -> "SL";
            case THREE_AC -> "3A";
            case TWO_AC   -> "2A";
            case FIRST_AC -> "1A";
        };
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// BOOKABLE INTERFACE
// ─────────────────────────────────────────────────────────────────────────────

// Interface that every booking manager must implement
interface Bookable {
    void bookTicket();
    void cancelTicket();
    boolean checkAvailability(String coachType);
}

// ─────────────────────────────────────────────────────────────────────────────
// PASSENGER
// ─────────────────────────────────────────────────────────────────────────────

// Represents a railway passenger with personal details and Aadhaar validation
class Passenger implements Serializable {
    private static final long serialVersionUID = 1L;
    String name;
    int age;
    String gender;
    long aadhaarNumber;

    public Passenger(String name, int age, String gender, long aadhaarNumber)
            throws InvalidAadhaarException {
        this.name = name;
        this.age  = age;

        if (!gender.equalsIgnoreCase("Male") && !gender.equalsIgnoreCase("Female"))
            throw new IllegalArgumentException("Invalid gender! Enter 'Male' or 'Female'.");
        this.gender = gender;

        String aadhaarStr = String.valueOf(aadhaarNumber);
        if (aadhaarStr.length() != 12)
            throw new InvalidAadhaarException("Invalid Aadhaar Number! It must be a 12-digit number.");
        this.aadhaarNumber = aadhaarNumber;
    }

    public void displayPassengerInfo() {
        System.out.println("Passenger: " + name + " | Age: " + age
                + " | Gender: " + gender + " | Aadhaar: " + aadhaarNumber);
    }
}

// Represents a passenger with a disability who gets a 75% fare concession
class DivyangPassenger extends Passenger {
    private static final long serialVersionUID = 1L;
    String disabilityType;

    public DivyangPassenger(String name, int age, String gender, long aadhaarNumber,
                             String disabilityType) throws InvalidAadhaarException {
        super(name, age, gender, aadhaarNumber);
        this.disabilityType = disabilityType;
    }

    @Override
    public void displayPassengerInfo() {
        super.displayPassengerInfo();
        System.out.println("Disability Type: " + disabilityType + " (75% concession applied)");
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// GENERIC BookingQueue<T extends Passenger>
// ─────────────────────────────────────────────────────────────────────────────

// Generic queue wrapper that restricts its element type to Passenger or its subclasses
class BookingQueue<T extends Passenger> {
    private final Queue<T> queue = new LinkedList<>();

    public void add(T item)    { queue.add(item); }
    public T    remove()       { return queue.poll(); }
    public T    peek()         { return queue.peek(); }
    public boolean isEmpty()   { return queue.isEmpty(); }
    public int  size()         { return queue.size(); }
}

// ─────────────────────────────────────────────────────────────────────────────
// TICKET
// ─────────────────────────────────────────────────────────────────────────────

// Represents a booked ticket including PNR, status, fare breakdown and journey date
class Ticket implements Serializable {
    private static final long serialVersionUID = 1L;

    static int ticketCounter = 1000;
    Passenger  passenger;
    CoachType  coachType;
    int        originalPrice;
    int        finalPrice;
    int        seatNumber;
    int        ticketNumber;
    String     pnr;
    String     status;        // "CONFIRMED", "RAC-N", "WL-N"
    String     trainName;
    int        trainNumber;
    String     source;
    String     destination;
    String     journeyDate;

    public Ticket(Passenger passenger, CoachType coachType,
                  int originalPrice, int finalPrice, int seatNumber,
                  String trainName, int trainNumber,
                  String source, String destination, String journeyDate) {
        this.ticketNumber  = ++ticketCounter;
        this.passenger     = passenger;
        this.coachType     = coachType;
        this.originalPrice = originalPrice;
        this.finalPrice    = finalPrice;
        this.seatNumber    = seatNumber;
        this.trainName     = trainName;
        this.trainNumber   = trainNumber;
        this.source        = source;
        this.destination   = destination;
        this.journeyDate   = journeyDate;
        this.status        = "CONFIRMED";
        this.pnr           = generatePNR();
    }

    // Generates a unique 10-digit PNR from current time and a random component
    private String generatePNR() {
        Random rnd = new Random();
        long ts  = System.currentTimeMillis() % 100000L;
        long rnd5 = (long)(rnd.nextInt(90000) + 10000);
        return String.format("%05d%05d", ts, rnd5);
    }

    public void saveTicketToFile() {
        String fileName = "PNR_" + pnr + ".txt";
        try (PrintWriter writer = new PrintWriter(new FileWriter(fileName))) {
            writer.println("================= TICKET =================");
            writer.println("PNR Number     : " + pnr);
            writer.println("Ticket Number  : " + ticketNumber);
            writer.println("Status         : " + status);
            writer.println("Train          : " + trainName + " (" + trainNumber + ")");
            writer.println("From           : " + source + "  -->  " + destination);
            writer.println("Journey Date   : " + journeyDate);
            writer.println("Passenger Name : " + passenger.name);
            writer.println("Age            : " + passenger.age);
            writer.println("Gender         : " + passenger.gender);
            writer.println("Aadhaar        : " + passenger.aadhaarNumber);
            writer.println("Coach Type     : " + coachType.displayName);
            writer.println("Seat Number    : " + seatNumber);
            writer.println("Original Price : Rs. " + originalPrice);
            writer.println("Final Price    : Rs. " + finalPrice);
            writer.println("==========================================");
        } catch (IOException e) {
            System.out.println("Error saving ticket: " + e.getMessage());
        }
    }

    public void deleteTicketFile() {
        File f = new File("PNR_" + pnr + ".txt");
        if (f.exists()) f.delete();
    }

    public void printTicket() {
        System.out.println("\n================= TICKET =================");
        System.out.println("PNR Number     : " + pnr);
        System.out.println("Ticket Number  : " + ticketNumber);
        System.out.println("Status         : " + status);
        System.out.println("Train          : " + trainName + " (" + trainNumber + ")");
        System.out.println("From           : " + source + "  -->  " + destination);
        System.out.println("Journey Date   : " + journeyDate);
        System.out.println("Passenger Name : " + passenger.name);
        System.out.println("Age            : " + passenger.age);
        System.out.println("Gender         : " + passenger.gender);
        System.out.println("Aadhaar        : " + passenger.aadhaarNumber);
        System.out.println("Coach Type     : " + coachType.displayName);
        System.out.println("Seat Number    : " + seatNumber);
        System.out.println("Original Price : Rs. " + originalPrice);
        System.out.println("Final Price    : Rs. " + finalPrice);
        System.out.println("==========================================");
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// TICKET PRICE CALCULATOR
// ─────────────────────────────────────────────────────────────────────────────

// Utility class that computes ticket fares, including all applicable concessions
class TicketPriceCalculator {

    // Basic fare calculation without passenger concessions
    public static int calculatePrice(CoachType coach, int numPassengers, int distance) {
        return coach.ratePerKm * distance * numPassengers;
    }

    // Overloaded method: applies age, gender, and Divyang concessions per passenger
    public static int calculatePrice(CoachType coach, int distance, Passenger passenger) {
        int base = coach.ratePerKm * distance;

        // Divyang passengers get 75% concession regardless of other rules
        if (passenger instanceof DivyangPassenger) {
            return (int)(base * 0.25);
        }

        double discount = 0.0;
        if      (passenger.age < 5)  discount = 1.0;   // free for infants
        else if (passenger.age <= 11) discount = 0.50;  // 50% for children
        else if (passenger.age >= 60) discount = 0.40;  // 40% for senior citizens

        // Extra 10% for female passengers in higher-class coaches
        if (passenger.gender.equalsIgnoreCase("Female")
                && (coach == CoachType.TWO_AC || coach == CoachType.FIRST_AC)) {
            discount = Math.min(discount + 0.10, 1.0);
        }

        return (int)(base * (1.0 - discount));
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// SEAT MANAGER  –  handles confirmed seats, RAC queue, and waiting list
// ─────────────────────────────────────────────────────────────────────────────

// Manages seat inventory, RAC, and waiting-list slots for every coach type
class SeatManager {
    private final Map<CoachType, Integer>        seats    = new HashMap<>();
    private final Map<CoachType, Queue<Ticket>>  racQ     = new HashMap<>();
    private final Map<CoachType, Queue<Ticket>>  waitingQ = new HashMap<>();
    private final Random random = new Random();

    // RAC capacity = 10% of coach seats; WL cap = 20 per coach
    private static final int WL_CAP = 20;

    public SeatManager() {
        for (CoachType c : CoachType.values()) {
            seats.put(c, c.totalSeats);
            racQ.put(c,     new LinkedList<>());
            waitingQ.put(c, new LinkedList<>());
        }
    }

    private int racCapacity(CoachType c) { return Math.max(1, c.totalSeats / 10); }

    // Thread-safe booking; returns "CONFIRMED", "RAC", "WL", or "FULL"
    public synchronized String bookSeats(CoachType coach, int numPassengers) {
        int avail = seats.getOrDefault(coach, 0);
        if (avail >= numPassengers) {
            seats.put(coach, avail - numPassengers);
            return "CONFIRMED";
        }
        if (racQ.get(coach).size() < racCapacity(coach)) return "RAC";
        if (waitingQ.get(coach).size() < WL_CAP)         return "WL";
        return "FULL";
    }

    // Thread-safe cancellation; auto-promotes WL→RAC→CONFIRMED
    public synchronized void cancelSeats(CoachType coach, Ticket cancelled,
                                          Map<String, Ticket> allTickets) {
        if ("CONFIRMED".equals(cancelled.status)) {
            // Promote first RAC ticket to CONFIRMED
            Queue<Ticket> rac = racQ.get(coach);
            if (!rac.isEmpty()) {
                Ticket promoted = rac.poll();
                promoted.status = "CONFIRMED";
                promoted.saveTicketToFile();
                System.out.println("RAC passenger " + promoted.passenger.name
                        + " promoted to CONFIRMED (PNR: " + promoted.pnr + ")");
                // Now a RAC slot opened – promote first WL to RAC
                Queue<Ticket> wl = waitingQ.get(coach);
                if (!wl.isEmpty()) {
                    Ticket wlTicket  = wl.poll();
                    int    racNum    = rac.size() + 1;
                    wlTicket.status  = "RAC-" + racNum;
                    rac.add(wlTicket);
                    wlTicket.saveTicketToFile();
                    System.out.println("WL passenger " + wlTicket.passenger.name
                            + " promoted to RAC (PNR: " + wlTicket.pnr + ")");
                    renumberWL(coach);
                }
            } else {
                seats.put(coach, seats.get(coach) + 1);
            }
        } else if (cancelled.status.startsWith("RAC")) {
            racQ.get(coach).remove(cancelled);
            renumberRAC(coach);
            // Promote WL to RAC
            Queue<Ticket> wl = waitingQ.get(coach);
            if (!wl.isEmpty()) {
                Ticket wlTicket = wl.poll();
                int    racNum   = racQ.get(coach).size() + 1;
                wlTicket.status = "RAC-" + racNum;
                racQ.get(coach).add(wlTicket);
                wlTicket.saveTicketToFile();
                System.out.println("WL passenger " + wlTicket.passenger.name
                        + " promoted to RAC (PNR: " + wlTicket.pnr + ")");
                renumberWL(coach);
            }
        } else if (cancelled.status.startsWith("WL")) {
            waitingQ.get(coach).remove(cancelled);
            renumberWL(coach);
        }
    }

    // Renumber RAC queue statuses sequentially after a change
    private void renumberRAC(CoachType coach) {
        int n = 1;
        for (Ticket t : racQ.get(coach)) { t.status = "RAC-" + n++; t.saveTicketToFile(); }
    }

    // Renumber WL queue statuses sequentially after a change
    private void renumberWL(CoachType coach) {
        int n = 1;
        for (Ticket t : waitingQ.get(coach)) { t.status = "WL-" + n++; t.saveTicketToFile(); }
    }

    // Add a ticket to the RAC queue with a proper RAC-N status label
    public void addToRAC(Ticket t, CoachType coach) {
        int n = racQ.get(coach).size() + 1;
        t.status = "RAC-" + n;
        racQ.get(coach).add(t);
    }

    // Add a ticket to the WL queue with a proper WL-N status label
    public void addToWL(Ticket t, CoachType coach) {
        int n = waitingQ.get(coach).size() + 1;
        t.status = "WL-" + n;
        waitingQ.get(coach).add(t);
    }

    public int assignSeat(CoachType coach) {
        int rem = seats.getOrDefault(coach, 1);
        return random.nextInt(Math.max(rem, 1)) + 1;
    }

    // Overloaded variant that honours a preferred seat number (method overloading)
    public int assignSeat(CoachType coach, int preferredSeat) { return preferredSeat; }

    public boolean checkAvailability(CoachType coach) {
        return seats.getOrDefault(coach, 0) > 0
                || racQ.get(coach).size() < racCapacity(coach)
                || waitingQ.get(coach).size() < WL_CAP;
    }

    public void displayRemainingSeats() {
        System.out.println("\n===== Remaining Seats =====");
        for (CoachType c : CoachType.values()) {
            System.out.printf("%-12s: %3d confirmed | RAC slots: %d/%d | WL: %d/%d%n",
                    c.displayName,
                    seats.get(c),
                    racQ.get(c).size(), racCapacity(c),
                    waitingQ.get(c).size(), WL_CAP);
        }
    }

    public Map<CoachType, Integer> getSeatsMap()     { return seats; }
}

// ─────────────────────────────────────────────────────────────────────────────
// TRAIN HIERARCHY
// ─────────────────────────────────────────────────────────────────────────────

// Abstract base class representing a train with route and distance calculation
abstract class Train {
    String trainName;
    final int    trainNumber;
    final String source;
    final String destination;
    final String trainType;
    final Map<String, Integer> routeDistance;

    static final List<String> ROUTE = List.of("Rajkot", "Ahmedabad", "Vadodara", "Surat", "Mumbai");
    static final Map<String, Integer> BASE_DISTANCES = Map.of(
        "Rajkot-Ahmedabad",   215,
        "Ahmedabad-Vadodara", 110,
        "Vadodara-Surat",     150,
        "Surat-Mumbai",       280
    );

    public Train(String trainName, int trainNumber, String source, String destination, String trainType) {
        this.trainName   = trainName;
        this.trainNumber = trainNumber;
        this.source      = source;
        this.destination = destination;
        this.trainType   = trainType;
        this.routeDistance = BASE_DISTANCES;
    }

    abstract void displayTrainInfo();

    public int calculateDistance() {
        if (source.equalsIgnoreCase("Rajkot") && destination.equalsIgnoreCase("Mumbai")) return 755;
        int total = 0;
        int si = ROUTE.indexOf(source), ei = ROUTE.indexOf(destination);
        if (si < 0 || ei < 0 || si >= ei)
            throw new IllegalArgumentException("Invalid source or destination.");
        for (int i = si; i < ei; i++)
            total += routeDistance.get(ROUTE.get(i) + "-" + ROUTE.get(i + 1));
        return total;
    }

    // Validate that a chosen coach is compatible with this train type
    public void validateCoach(CoachType coach) throws InvalidCoachForTrainException { }
}

// Concrete class for standard Express trains; supports all coach types
class ExpressTrain extends Train {
    public ExpressTrain(String trainName, int trainNumber, String source, String destination) {
        super(trainName, trainNumber, source, destination, "Express");
    }

    @Override
    public void displayTrainInfo() {
        System.out.println("[Express] " + trainName + " | #" + trainNumber
                + " | " + source + " -> " + destination);
    }
}

// Superfast train subclass offering higher-speed service on the same route
class SuperfastTrain extends Train {
    public SuperfastTrain(String trainName, int trainNumber, String source, String destination) {
        super(trainName, trainNumber, source, destination, "Superfast");
    }

    @Override
    public void displayTrainInfo() {
        System.out.println("[Superfast] " + trainName + " | #" + trainNumber
                + " | " + source + " -> " + destination + " (Superfast service)");
    }
}

// Rajdhani train subclass that applies a 20% luxury surcharge on all fares
class RajdhaniTrain extends Train {
    public static final double LUXURY_SURCHARGE = 0.20;

    public RajdhaniTrain(String trainName, int trainNumber, String source, String destination) {
        super(trainName, trainNumber, source, destination, "Rajdhani");
    }

    @Override
    public void displayTrainInfo() {
        System.out.println("[Rajdhani] " + trainName + " | #" + trainNumber
                + " | " + source + " -> " + destination
                + " (20% luxury surcharge applies)");
    }
}

// Passenger/local train subclass; only Sleeper class is allowed
class PassengerTrain extends Train {
    public PassengerTrain(String trainName, int trainNumber, String source, String destination) {
        super(trainName, trainNumber, source, destination, "Passenger");
    }

    @Override
    public void displayTrainInfo() {
        System.out.println("[Passenger] " + trainName + " | #" + trainNumber
                + " | " + source + " -> " + destination + " (SL only)");
    }

    @Override
    public void validateCoach(CoachType coach) throws InvalidCoachForTrainException {
        if (coach != CoachType.SL)
            throw new InvalidCoachForTrainException(
                    "Passenger trains only allow Sleeper (SL) coach. Selected: " + coach.displayName);
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// THREADS
// ─────────────────────────────────────────────────────────────────────────────

// Background thread that prints the current date and time every 5 seconds
class DateTimePrinter extends Thread {
    volatile boolean running = true;

    public void stopThread() { running = false; }

    @Override
    public void run() {
        SimpleDateFormat fmt = new SimpleDateFormat("dd-MM-yyyy HH:mm:ss");
        while (running) {
            System.out.println("\n[Current Date & Time: " + fmt.format(new Date()) + "]");
            try { Thread.sleep(5000); } catch (InterruptedException e) { break; }
        }
    }
}

// Background thread that alerts when any coach has fewer than 10% seats remaining
class SeatAvailabilityMonitor extends Thread {
    private final SeatManager seatManager;
    volatile boolean running = true;

    public SeatAvailabilityMonitor(SeatManager sm) { this.seatManager = sm; }

    public void stopMonitor() { running = false; }

    @Override
    public void run() {
        while (running) {
            try { Thread.sleep(30000); } catch (InterruptedException e) { break; }
            for (CoachType c : CoachType.values()) {
                int rem   = seatManager.getSeatsMap().getOrDefault(c, 0);
                int thresh = Math.max(1, c.totalSeats / 10);
                if (rem < thresh)
                    System.out.println("\n*** ALERT: " + c.displayName
                            + " coach has only " + rem + " seats left! ***");
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// BOOKING MANAGER  –  implements Bookable, owns all booking/cancel logic
// ─────────────────────────────────────────────────────────────────────────────

// Central class that handles ticket booking, cancellation, and PNR lookups
class BookingManager implements Bookable {
    private final HashMap<String, Ticket> tickets = new HashMap<>();
    private final SeatManager seatManager;
    private final Scanner scanner;
    private Train selectedTrain;
    private int   distance;
    private static final String DATA_FILE = "bookings.dat";

    // Separate queue for passengers with disabilities (uses generics)
    private final BookingQueue<DivyangPassenger> divyangQueue = new BookingQueue<>();

    public BookingManager(SeatManager sm, Scanner sc) {
        this.seatManager = sm;
        this.scanner     = sc;
        loadBookings();
    }

    public void setTrain(Train t, int dist) { this.selectedTrain = t; this.distance = dist; }

    // ── Bookable interface methods ──────────────────────────────────────────

    @Override
    public void bookTicket() {
        try {
            // Select train interactively if not already set
            if (selectedTrain == null) selectTrain();
            if (selectedTrain == null) return;

            // Journey date input and validation
            String journeyDate = askJourneyDate();

            System.out.print("Enter Coach Type (SL / 3A / 2A / 1A): ");
            String coachInput = scanner.nextLine().trim();
            CoachType coach;
            try {
                coach = CoachType.fromInput(coachInput);
            } catch (IllegalArgumentException e) {
                System.out.println("Invalid coach type. " + e.getMessage());
                return;
            }

            // Validate coach compatibility with train type
            try {
                selectedTrain.validateCoach(coach);
            } catch (InvalidCoachForTrainException e) {
                System.out.println("Error: " + e.getMessage());
                return;
            }

            System.out.print("Is this a Divyang passenger? (yes/no): ");
            boolean isDivyang = scanner.nextLine().trim().equalsIgnoreCase("yes");

            System.out.print("Enter Number of Passengers: ");
            int numPassengers;
            try { numPassengers = Integer.parseInt(scanner.nextLine().trim()); }
            catch (NumberFormatException e) { System.out.println("Invalid number."); return; }

            for (int i = 1; i <= numPassengers; i++) {
                System.out.println("\n--- Passenger " + i + " ---");
                System.out.print("Name: ");
                String name = scanner.nextLine().trim();
                System.out.print("Age: ");
                int age;
                try { age = Integer.parseInt(scanner.nextLine().trim()); }
                catch (NumberFormatException e) { System.out.println("Invalid age."); i--; continue; }
                System.out.print("Gender (Male/Female): ");
                String gender = scanner.nextLine().trim();
                System.out.print("Aadhaar Number (12 digits): ");
                long aadhaar;
                try { aadhaar = Long.parseLong(scanner.nextLine().trim()); }
                catch (NumberFormatException e) { System.out.println("Invalid Aadhaar."); i--; continue; }

                Passenger passenger;
                try {
                    if (isDivyang) {
                        System.out.print("Disability Type: ");
                        String dtype = scanner.nextLine().trim();
                        DivyangPassenger dp = new DivyangPassenger(name, age, gender, aadhaar, dtype);
                        divyangQueue.add(dp);
                        passenger = dp;
                    } else {
                        passenger = new Passenger(name, age, gender, aadhaar);
                    }
                } catch (InvalidAadhaarException | IllegalArgumentException e) {
                    System.out.println("Error: " + e.getMessage());
                    i--;
                    continue;
                }

                // Fare calculation
                int originalPrice = TicketPriceCalculator.calculatePrice(coach, distance, passenger);

                // Apply Rajdhani luxury surcharge
                if (selectedTrain instanceof RajdhaniTrain) {
                    originalPrice = (int)(originalPrice * (1 + RajdhaniTrain.LUXURY_SURCHARGE));
                }
                int finalPrice = originalPrice;

                // For display, compute base without concessions then show both
                int basePriceNoConcession = coach.ratePerKm * distance;
                if (selectedTrain instanceof RajdhaniTrain)
                    basePriceNoConcession = (int)(basePriceNoConcession * 1.20);

                // Attempt seat allocation
                String alloc;
                try {
                    alloc = seatManager.bookSeats(coach, 1);
                    if ("FULL".equals(alloc)) throw new SeatNotAvailableException(
                            "No confirmed, RAC or Waiting List seats available in " + coach.displayName);
                } catch (SeatNotAvailableException e) {
                    System.out.println("Error: " + e.getMessage());
                    continue;
                }

                int seatNum = "CONFIRMED".equals(alloc) ? seatManager.assignSeat(coach) : 0;

                Ticket ticket = new Ticket(passenger, coach,
                        basePriceNoConcession, finalPrice, seatNum,
                        selectedTrain.trainName, selectedTrain.trainNumber,
                        selectedTrain.source, selectedTrain.destination, journeyDate);

                if ("RAC".equals(alloc))      seatManager.addToRAC(ticket, coach);
                else if ("WL".equals(alloc))  seatManager.addToWL(ticket, coach);

                tickets.put(ticket.pnr, ticket);
                ticket.saveTicketToFile();
                ticket.printTicket();
                System.out.println("Original base price (no concession): Rs. " + basePriceNoConcession);
                System.out.println("Final price after concessions       : Rs. " + finalPrice);
            }

            saveBookings();

        } catch (Exception e) {
            System.out.println("Booking error: " + e.getMessage());
        }
    }

    @Override
    public void cancelTicket() {
        System.out.print("Enter PNR to cancel: ");
        String pnr = scanner.nextLine().trim();
        try {
            Ticket ticket = findByPNR(pnr);
            CoachType coach = ticket.coachType;
            ticket.deleteTicketFile();
            tickets.remove(pnr);
            seatManager.cancelSeats(coach, ticket, tickets);
            System.out.println("Ticket cancelled successfully. PNR: " + pnr);
            saveBookings();
        } catch (InvalidPNRException e) {
            System.out.println("Error: " + e.getMessage());
        }
    }

    @Override
    public boolean checkAvailability(String coachType) {
        try {
            CoachType c = CoachType.fromInput(coachType);
            return seatManager.checkAvailability(c);
        } catch (IllegalArgumentException e) {
            return false;
        }
    }

    public void checkPNRStatus() {
        System.out.print("Enter PNR: ");
        String pnr = scanner.nextLine().trim();
        try {
            Ticket t = findByPNR(pnr);
            t.printTicket();
        } catch (InvalidPNRException e) {
            System.out.println("Error: " + e.getMessage());
        }
    }

    private Ticket findByPNR(String pnr) throws InvalidPNRException {
        Ticket t = tickets.get(pnr);
        if (t == null) throw new InvalidPNRException("No ticket found for PNR: " + pnr);
        return t;
    }

    // ── Train search by source/destination ─────────────────────────────────

    public static void searchTrains(Scanner sc) {
        System.out.print("Enter Source     : ");
        String src  = sc.nextLine().trim();
        System.out.print("Enter Destination: ");
        String dest = sc.nextLine().trim();
        System.out.println("\nTrains from " + src + " to " + dest + ":");
        boolean found = false;
        for (Train t : getAllTrains()) {
            if (t.source.equalsIgnoreCase(src) && t.destination.equalsIgnoreCase(dest)) {
                t.displayTrainInfo();
                found = true;
            }
        }
        if (!found) System.out.println("No direct trains found for this route.");
    }

    // Returns the fixed catalogue of all available trains
    public static List<Train> getAllTrains() {
        return List.of(
            new ExpressTrain("Saurashtra Janta Express", 19218, "Rajkot",     "Mumbai"),
            new ExpressTrain("Gujarat Express",          19011, "Ahmedabad",  "Mumbai"),
            new SuperfastTrain("Duronto Express",        22955, "Rajkot",     "Mumbai"),
            new RajdhaniTrain("Rajdhani Express",        12957, "Ahmedabad",  "Mumbai"),
            new PassengerTrain("Passenger Local",        59201, "Rajkot",     "Ahmedabad")
        );
    }

    // ── Train selection menu ────────────────────────────────────────────────

    public void selectTrain() {
        List<Train> trains = getAllTrains();
        System.out.println("\n===== Select Train =====");
        for (int i = 0; i < trains.size(); i++) {
            System.out.print((i + 1) + ". ");
            trains.get(i).displayTrainInfo();
        }
        System.out.print("Choice (1-" + trains.size() + "): ");
        int ch;
        try { ch = Integer.parseInt(scanner.nextLine().trim()); }
        catch (NumberFormatException e) { System.out.println("Invalid choice."); return; }
        if (ch < 1 || ch > trains.size()) { System.out.println("Invalid choice."); return; }
        selectedTrain = trains.get(ch - 1);
        try {
            distance = selectedTrain.calculateDistance();
        } catch (IllegalArgumentException e) {
            System.out.println("Route error: " + e.getMessage());
            selectedTrain = null;
        }
    }

    // ── Journey date validation ─────────────────────────────────────────────

    private String askJourneyDate() throws InvalidDateException {
        SimpleDateFormat sdf = new SimpleDateFormat("dd-MM-yyyy");
        sdf.setLenient(false);
        while (true) {
            System.out.print("Enter Journey Date (DD-MM-YYYY): ");
            String input = scanner.nextLine().trim();
            Date journeyDate;
            try {
                journeyDate = sdf.parse(input);
            } catch (ParseException e) {
                System.out.println("Invalid date format. Please use DD-MM-YYYY.");
                continue;
            }
            if (journeyDate.before(new Date()))
                throw new InvalidDateException("Journey date cannot be in the past: " + input);
            return input;
        }
    }

    // ── All-passengers list sorted by name ─────────────────────────────────

    public void viewAllPassengersSorted() {
        if (tickets.isEmpty()) { System.out.println("No passengers booked."); return; }
        System.out.println("\n===== All Passengers (sorted by name) =====");
        tickets.values().stream()
            .map(t -> t.passenger)
            .sorted(Comparator.comparing(p -> p.name))
            .forEach(Passenger::displayPassengerInfo);
    }

    // ── Reports (Streams & Lambdas) ─────────────────────────────────────────

    public void showReports() {
        if (tickets.isEmpty()) { System.out.println("No tickets booked yet."); return; }

        System.out.println("\n========== REPORTS ==========");

        // Filter tickets by each coach type
        System.out.println("\n--- Tickets by Coach Type ---");
        for (CoachType c : CoachType.values()) {
            long count = tickets.values().stream()
                .filter(t -> t.coachType == c).count();
            System.out.println(c.displayName + ": " + count + " ticket(s)");
        }

        // Passengers sorted by age
        System.out.println("\n--- Passengers sorted by Age ---");
        tickets.values().stream()
            .map(t -> t.passenger)
            .sorted(Comparator.comparingInt(p -> p.age))
            .forEach(p -> System.out.println(p.name + " (age " + p.age + ")"));

        // Revenue per coach type
        System.out.println("\n--- Revenue per Coach Type ---");
        Map<CoachType, Integer> revenue = tickets.values().stream()
            .collect(Collectors.groupingBy(
                t -> t.coachType,
                Collectors.summingInt(t -> t.finalPrice)));
        revenue.forEach((c, r) -> System.out.println(c.displayName + ": Rs. " + r));

        // Most expensive ticket
        tickets.values().stream()
            .max(Comparator.comparingInt(t -> t.finalPrice))
            .ifPresent(t -> System.out.println(
                "\n--- Most Expensive Ticket ---"
                + "\nPNR: " + t.pnr + " | Passenger: " + t.passenger.name
                + " | Price: Rs. " + t.finalPrice));
    }

    // ── Admin Mode ──────────────────────────────────────────────────────────

    public void adminMode() {
        System.out.print("Enter admin password: ");
        String pwd = scanner.nextLine().trim();
        if (!"admin123".equals(pwd)) { System.out.println("Wrong password!"); return; }
        System.out.println("\n===== ADMIN MODE =====");
        System.out.println("All PNRs:");
        tickets.forEach((pnr, t) ->
            System.out.println("PNR: " + pnr + " | " + t.passenger.name
                    + " | " + t.coachType.displayName + " | Rs. " + t.finalPrice
                    + " | " + t.status));
        int total = tickets.values().stream().mapToInt(t -> t.finalPrice).sum();
        System.out.println("Total Revenue: Rs. " + total);
        System.out.println("Divyang queue size: " + divyangQueue.size());
    }

    // ── Serialization ───────────────────────────────────────────────────────

    @SuppressWarnings("unchecked")
    private void loadBookings() {
        File f = new File(DATA_FILE);
        if (!f.exists()) return;
        try (ObjectInputStream ois = new ObjectInputStream(new FileInputStream(f))) {
            HashMap<String, Ticket> loaded = (HashMap<String, Ticket>) ois.readObject();
            tickets.putAll(loaded);
            System.out.println("[Loaded " + tickets.size() + " booking(s) from " + DATA_FILE + "]");
        } catch (Exception e) {
            System.out.println("[Could not load bookings.dat: " + e.getMessage() + "]");
        }
    }

    private void saveBookings() {
        try (ObjectOutputStream oos = new ObjectOutputStream(new FileOutputStream(DATA_FILE))) {
            oos.writeObject(tickets);
        } catch (IOException e) {
            System.out.println("Error saving bookings: " + e.getMessage());
        }
    }

    public HashMap<String, Ticket> getTickets() { return tickets; }
}

// ─────────────────────────────────────────────────────────────────────────────
// MAIN CLASS
// ─────────────────────────────────────────────────────────────────────────────

// Entry point of the Railway Booking System; drives the interactive menu loop
public class RailwayBookingSystem {

    public static void main(String[] args) {
        Scanner      scanner      = new Scanner(System.in);
        SeatManager  seatManager  = new SeatManager();
        BookingManager bm         = new BookingManager(seatManager, scanner);

        // Start background threads (daemon so JVM can exit cleanly)
        DateTimePrinter dtp = new DateTimePrinter();
        dtp.setDaemon(true);
        dtp.start();

        SeatAvailabilityMonitor sam = new SeatAvailabilityMonitor(seatManager);
        sam.setDaemon(true);
        sam.start();

        System.out.println("\n╔══════════════════════════════════════════╗");
        System.out.println("║   Indian Railway Booking System v2.0     ║");
        System.out.println("╚══════════════════════════════════════════╝");

        while (true) {
            System.out.println("\n========== MAIN MENU ==========");
            System.out.println("1. Book Ticket");
            System.out.println("2. Cancel Ticket by PNR");
            System.out.println("3. Check PNR Status");
            System.out.println("4. View Remaining Seats");
            System.out.println("5. Search Trains by Source & Destination");
            System.out.println("6. View All Passengers (sorted by name)");
            System.out.println("7. Reports");
            System.out.println("8. Admin Mode");
            System.out.println("9. Exit");
            System.out.print("Choose an option: ");

            int choice;
            try {
                choice = Integer.parseInt(scanner.nextLine().trim());
            } catch (NumberFormatException e) {
                System.out.println("Invalid input. Please enter a number.");
                continue;
            }

            switch (choice) {
                case 1 -> bm.bookTicket();
                case 2 -> bm.cancelTicket();
                case 3 -> bm.checkPNRStatus();
                case 4 -> seatManager.displayRemainingSeats();
                case 5 -> BookingManager.searchTrains(scanner);
                case 6 -> bm.viewAllPassengersSorted();
                case 7 -> bm.showReports();
                case 8 -> bm.adminMode();
                case 9 -> {
                    System.out.println("Thank you for using Indian Railway Booking System. Goodbye!");
                    dtp.stopThread();
                    sam.stopMonitor();
                    scanner.close();
                    System.exit(0);
                }
                default -> System.out.println("Invalid option. Please choose 1-9.");
            }
        }
    }
}
