import java.io.*;
import java.text.*;
import java.util.*;
import java.util.stream.*;

// ---------- Custom Exceptions ----------

// Thrown when Aadhaar is not 12 digits
class InvalidAadhaarException extends Exception {
    public InvalidAadhaarException(String msg) { super(msg); }
}

// Thrown when journey date is in the past or has wrong format
class InvalidDateException extends Exception {
    public InvalidDateException(String msg) { super(msg); }
}

// Thrown when no confirmed / RAC / WL seat is available
class SeatNotAvailableException extends Exception {
    public SeatNotAvailableException(String msg) { super(msg); }
}

// Thrown when a PNR number is not found in the system
class InvalidPNRException extends Exception {
    public InvalidPNRException(String msg) { super(msg); }
}

// Thrown when a non-SL coach is chosen for a Passenger train
class InvalidCoachForTrainException extends Exception {
    public InvalidCoachForTrainException(String msg) { super(msg); }
}

// ---------- CoachType Enum ----------

// Each coach type holds its fare rate, total seats, and display name
enum CoachType {
    SL(1, 72, "Sleeper"),
    THREE_AC(2, 64, "AC 3 Tier"),
    TWO_AC(3, 54, "AC 2 Tier"),
    FIRST_AC(4, 26, "First AC");

    final int ratePerKm;
    final int totalSeats;
    final String displayName;

    CoachType(int ratePerKm, int totalSeats, String displayName) {
        this.ratePerKm   = ratePerKm;
        this.totalSeats  = totalSeats;
        this.displayName = displayName;
    }

    // Converts user input like "SL", "3A", "2A", "1A" to the enum constant
    public static CoachType fromInput(String input) {
        switch (input.trim().toUpperCase()) {
            case "SL":                return SL;
            case "3A": case "THREE_AC": return THREE_AC;
            case "2A": case "TWO_AC":   return TWO_AC;
            case "1A": case "FIRST_AC": return FIRST_AC;
            default:
                throw new IllegalArgumentException("Invalid coach type: " + input);
        }
    }

    // Short code used in menus
    public String shortCode() {
        switch (this) {
            case SL:       return "SL";
            case THREE_AC: return "3A";
            case TWO_AC:   return "2A";
            case FIRST_AC: return "1A";
            default:       return "??";
        }
    }
}

// ---------- Bookable Interface ----------

// Any class that manages bookings must implement these three methods
interface Bookable {
    void bookTicket();
    void cancelTicket();
    boolean checkAvailability(String coachType);
}

// ---------- Passenger ----------

// Stores passenger details; validates Aadhaar and gender on creation
class Passenger implements Serializable {
    private static final long serialVersionUID = 1L;

    String name;
    int    age;
    String gender;
    long   aadhaarNumber;

    public Passenger(String name, int age, String gender, long aadhaar)
            throws InvalidAadhaarException {
        this.name = name;
        this.age  = age;

        if (!gender.equalsIgnoreCase("Male") && !gender.equalsIgnoreCase("Female")) {
            throw new IllegalArgumentException("Invalid gender. Enter Male or Female.");
        }
        this.gender = gender;

        if (String.valueOf(aadhaar).length() != 12) {
            throw new InvalidAadhaarException("Aadhaar must be exactly 12 digits.");
        }
        this.aadhaarNumber = aadhaar;
    }

    public void displayPassengerInfo() {
        System.out.println("Name: " + name + "  Age: " + age
                + "  Gender: " + gender + "  Aadhaar: " + aadhaarNumber);
    }
}

// Extends Passenger with a disability type and a 75% fare concession
class DivyangPassenger extends Passenger {
    private static final long serialVersionUID = 1L;
    String disabilityType;

    public DivyangPassenger(String name, int age, String gender,
                             long aadhaar, String disabilityType)
            throws InvalidAadhaarException {
        super(name, age, gender, aadhaar);
        this.disabilityType = disabilityType;
    }

    @Override
    public void displayPassengerInfo() {
        super.displayPassengerInfo();
        System.out.println("  Disability: " + disabilityType + " (75% concession applied)");
    }
}

// ---------- Generic BookingQueue ----------

// A type-safe queue that only accepts Passenger objects or its subclasses
class BookingQueue<T extends Passenger> {
    private final Queue<T> queue = new LinkedList<>();

    public void add(T item)        { queue.add(item); }
    public T    remove()           { return queue.poll(); }
    public T    peek()             { return queue.peek(); }
    public boolean isEmpty()       { return queue.isEmpty(); }
    public int  size()             { return queue.size(); }
}

// ---------- Ticket ----------

// Represents a single booked ticket with PNR, fare breakdown, and status
class Ticket implements Serializable {
    private static final long serialVersionUID = 1L;

    static int ticketCounter = 1000;

    int       ticketNumber;
    String    pnr;
    String    status;        // CONFIRMED, RAC-N, or WL-N
    Passenger passenger;
    CoachType coachType;
    int       originalPrice; // base price before concessions
    int       finalPrice;    // price after concessions
    int       seatNumber;
    String    trainName;
    int       trainNumber;
    String    source;
    String    destination;
    String    journeyDate;

    public Ticket(Passenger passenger, CoachType coachType,
                  int originalPrice, int finalPrice, int seatNumber,
                  String trainName, int trainNumber,
                  String source, String destination, String journeyDate) {
        this.ticketNumber  = ++ticketCounter;
        this.pnr           = generatePNR();
        this.status        = "CONFIRMED";
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
    }

    // Builds a unique 10-digit PNR using current time and a random suffix
    private String generatePNR() {
        long timePart   = System.currentTimeMillis() % 100000L;
        long randomPart = new Random().nextInt(90000) + 10000;
        return String.format("%05d%05d", timePart, randomPart);
    }

    // Saves the ticket as PNR_<number>.txt in the working directory
    public void saveTicketToFile() {
        try (PrintWriter pw = new PrintWriter(new FileWriter("PNR_" + pnr + ".txt"))) {
            printLines(pw);
        } catch (IOException e) {
            System.out.println("Could not save ticket file: " + e.getMessage());
        }
    }

    // Deletes the ticket file when a booking is cancelled
    public void deleteTicketFile() {
        File f = new File("PNR_" + pnr + ".txt");
        if (f.exists()) f.delete();
    }

    // Prints ticket details to the console
    public void printTicket() {
        printLines(new PrintWriter(System.out, true));
    }

    // Shared helper used by both saveTicketToFile() and printTicket()
    private void printLines(PrintWriter pw) {
        pw.println("========== TICKET ==========");
        pw.println("PNR          : " + pnr);
        pw.println("Ticket No.   : " + ticketNumber);
        pw.println("Status       : " + status);
        pw.println("Train        : " + trainName + " (" + trainNumber + ")");
        pw.println("Journey      : " + source + " -> " + destination);
        pw.println("Date         : " + journeyDate);
        pw.println("Passenger    : " + passenger.name);
        pw.println("Age / Gender : " + passenger.age + " / " + passenger.gender);
        pw.println("Aadhaar      : " + passenger.aadhaarNumber);
        pw.println("Coach        : " + coachType.displayName);
        pw.println("Seat No.     : " + (seatNumber == 0 ? "N/A" : seatNumber));
        pw.println("Base Price   : Rs. " + originalPrice);
        pw.println("Final Price  : Rs. " + finalPrice);
        pw.println("============================");
    }
}

// ---------- TicketPriceCalculator ----------

// Calculates fare with or without passenger-specific concessions
class TicketPriceCalculator {

    // Basic fare: ratePerKm * distance * number of passengers
    public static int calculatePrice(CoachType coach, int numPassengers, int distance) {
        return coach.ratePerKm * distance * numPassengers;
    }

    // Overloaded: applies age, gender, and Divyang concessions for one passenger
    public static int calculatePrice(CoachType coach, int distance, Passenger passenger) {
        int base = coach.ratePerKm * distance;

        // Divyang passengers always get 75% off
        if (passenger instanceof DivyangPassenger) {
            return (int) (base * 0.25);
        }

        double discount = 0.0;
        if      (passenger.age < 5)   discount = 1.00; // infant travels free
        else if (passenger.age <= 11) discount = 0.50; // child 50% off
        else if (passenger.age >= 60) discount = 0.40; // senior citizen 40% off

        // Female passengers in 2A or 1A get an extra 10% off
        if (passenger.gender.equalsIgnoreCase("Female")
                && (coach == CoachType.TWO_AC || coach == CoachType.FIRST_AC)) {
            discount = Math.min(discount + 0.10, 1.0);
        }

        return (int) (base * (1.0 - discount));
    }
}

// ---------- SeatManager ----------

// Tracks available seats and manages RAC / waiting-list queues per coach
class SeatManager {

    private final Map<CoachType, Integer>       seats    = new HashMap<>();
    private final Map<CoachType, Queue<Ticket>> racQueue = new HashMap<>();
    private final Map<CoachType, Queue<Ticket>> wlQueue  = new HashMap<>();
    private final Random random = new Random();

    private static final int WL_CAP = 20; // max waiting-list entries per coach

    public SeatManager() {
        for (CoachType c : CoachType.values()) {
            seats.put(c, c.totalSeats);
            racQueue.put(c, new LinkedList<>());
            wlQueue.put(c,  new LinkedList<>());
        }
    }

    // RAC capacity is 10% of the coach's total seats
    private int racLimit(CoachType c) {
        return Math.max(1, c.totalSeats / 10);
    }

    // Returns "CONFIRMED", "RAC", "WL", or "FULL" — thread-safe
    public synchronized String bookSeats(CoachType coach, int count) {
        int available = seats.getOrDefault(coach, 0);
        if (available >= count) {
            seats.put(coach, available - count);
            return "CONFIRMED";
        }
        if (racQueue.get(coach).size() < racLimit(coach)) return "RAC";
        if (wlQueue.get(coach).size()  < WL_CAP)          return "WL";
        return "FULL";
    }

    // Cancels a ticket and auto-promotes WL -> RAC -> CONFIRMED — thread-safe
    public synchronized void cancelSeats(CoachType coach, Ticket cancelled,
                                          Map<String, Ticket> allTickets) {
        if ("CONFIRMED".equals(cancelled.status)) {
            Queue<Ticket> rac = racQueue.get(coach);
            if (!rac.isEmpty()) {
                // Move the first RAC passenger to CONFIRMED
                Ticket promoted = rac.poll();
                promoted.status = "CONFIRMED";
                promoted.saveTicketToFile();
                System.out.println(">> " + promoted.passenger.name + " moved from RAC to CONFIRMED.");

                // Fill the freed RAC slot from WL
                Queue<Ticket> wl = wlQueue.get(coach);
                if (!wl.isEmpty()) {
                    Ticket fromWL   = wl.poll();
                    fromWL.status   = "RAC-" + (rac.size() + 1);
                    rac.add(fromWL);
                    fromWL.saveTicketToFile();
                    System.out.println(">> " + fromWL.passenger.name + " moved from WL to RAC.");
                    renumberWL(coach);
                }
            } else {
                // No RAC to promote; simply free up one confirmed seat
                seats.put(coach, seats.get(coach) + 1);
            }

        } else if (cancelled.status.startsWith("RAC")) {
            racQueue.get(coach).remove(cancelled);
            renumberRAC(coach);

            // Promote the first WL entry to the freed RAC slot
            Queue<Ticket> wl = wlQueue.get(coach);
            if (!wl.isEmpty()) {
                Ticket fromWL = wl.poll();
                fromWL.status = "RAC-" + (racQueue.get(coach).size() + 1);
                racQueue.get(coach).add(fromWL);
                fromWL.saveTicketToFile();
                System.out.println(">> " + fromWL.passenger.name + " moved from WL to RAC.");
                renumberWL(coach);
            }

        } else if (cancelled.status.startsWith("WL")) {
            wlQueue.get(coach).remove(cancelled);
            renumberWL(coach);
        }
    }

    // Reassigns RAC-1, RAC-2, ... labels after a queue change
    private void renumberRAC(CoachType coach) {
        int n = 1;
        for (Ticket t : racQueue.get(coach)) {
            t.status = "RAC-" + n++;
            t.saveTicketToFile();
        }
    }

    // Reassigns WL-1, WL-2, ... labels after a queue change
    private void renumberWL(CoachType coach) {
        int n = 1;
        for (Ticket t : wlQueue.get(coach)) {
            t.status = "WL-" + n++;
            t.saveTicketToFile();
        }
    }

    // Adds a ticket to the RAC queue with the next available RAC number
    public void addToRAC(Ticket t, CoachType coach) {
        t.status = "RAC-" + (racQueue.get(coach).size() + 1);
        racQueue.get(coach).add(t);
    }

    // Adds a ticket to the waiting list with the next available WL number
    public void addToWL(Ticket t, CoachType coach) {
        t.status = "WL-" + (wlQueue.get(coach).size() + 1);
        wlQueue.get(coach).add(t);
    }

    // Assigns a random seat number from remaining seats in the coach
    public int assignSeat(CoachType coach) {
        int remaining = seats.getOrDefault(coach, 1);
        return random.nextInt(Math.max(remaining, 1)) + 1;
    }

    // Overloaded: returns a caller-specified preferred seat number
    public int assignSeat(CoachType coach, int preferredSeat) {
        return preferredSeat;
    }

    // Returns true if any seat (confirmed, RAC, or WL) is still open
    public boolean checkAvailability(CoachType coach) {
        return seats.getOrDefault(coach, 0) > 0
            || racQueue.get(coach).size() < racLimit(coach)
            || wlQueue.get(coach).size()  < WL_CAP;
    }

    public void displayRemainingSeats() {
        System.out.println("\n--- Remaining Seats ---");
        for (CoachType c : CoachType.values()) {
            System.out.printf("%-12s | Confirmed: %3d | RAC: %d/%d | WL: %d/%d%n",
                    c.displayName,
                    seats.get(c),
                    racQueue.get(c).size(), racLimit(c),
                    wlQueue.get(c).size(), WL_CAP);
        }
    }

    public Map<CoachType, Integer> getSeatsMap() {
        return seats;
    }
}

// ---------- Train Hierarchy ----------

// Abstract base for all train types; holds route info and distance calculation
abstract class Train {
    String trainName;
    final int    trainNumber;
    final String source;
    final String destination;
    final String trainType;

    // Ordered list of stations on the route
    static final List<String> ROUTE = List.of("Rajkot", "Ahmedabad", "Vadodara", "Surat", "Mumbai");

    // Distance in km between consecutive stations
    static final Map<String, Integer> SEGMENT_KM = Map.of(
        "Rajkot-Ahmedabad",   215,
        "Ahmedabad-Vadodara", 110,
        "Vadodara-Surat",     150,
        "Surat-Mumbai",       280
    );

    public Train(String trainName, int trainNumber,
                 String source, String destination, String trainType) {
        this.trainName   = trainName;
        this.trainNumber = trainNumber;
        this.source      = source;
        this.destination = destination;
        this.trainType   = trainType;
    }

    // Each subclass prints its own info line
    abstract void displayTrainInfo();

    // Sums up segment distances between source and destination
    public int calculateDistance() {
        if (source.equalsIgnoreCase("Rajkot") && destination.equalsIgnoreCase("Mumbai")) {
            return 755; // direct shortcut
        }
        int startIdx = ROUTE.indexOf(source);
        int endIdx   = ROUTE.indexOf(destination);
        if (startIdx < 0 || endIdx < 0 || startIdx >= endIdx) {
            throw new IllegalArgumentException("Invalid source or destination.");
        }
        int total = 0;
        for (int i = startIdx; i < endIdx; i++) {
            total += SEGMENT_KM.get(ROUTE.get(i) + "-" + ROUTE.get(i + 1));
        }
        return total;
    }

    // Subclasses override this to restrict coach options; default allows all
    public void validateCoach(CoachType coach) throws InvalidCoachForTrainException { }
}

// Standard express train — all coach types are allowed
class ExpressTrain extends Train {
    public ExpressTrain(String name, int number, String src, String dest) {
        super(name, number, src, dest, "Express");
    }

    @Override
    public void displayTrainInfo() {
        System.out.println("[Express]    " + trainName + " | #" + trainNumber
                + " | " + source + " -> " + destination);
    }
}

// Superfast train — faster service, all coach types allowed
class SuperfastTrain extends Train {
    public SuperfastTrain(String name, int number, String src, String dest) {
        super(name, number, src, dest, "Superfast");
    }

    @Override
    public void displayTrainInfo() {
        System.out.println("[Superfast]  " + trainName + " | #" + trainNumber
                + " | " + source + " -> " + destination);
    }
}

// Rajdhani train — adds a 20% luxury surcharge to all fares
class RajdhaniTrain extends Train {
    static final double LUXURY_SURCHARGE = 0.20;

    public RajdhaniTrain(String name, int number, String src, String dest) {
        super(name, number, src, dest, "Rajdhani");
    }

    @Override
    public void displayTrainInfo() {
        System.out.println("[Rajdhani]   " + trainName + " | #" + trainNumber
                + " | " + source + " -> " + destination + " (+20% surcharge)");
    }
}

// Local passenger train — only Sleeper (SL) coach is permitted
class PassengerTrain extends Train {
    public PassengerTrain(String name, int number, String src, String dest) {
        super(name, number, src, dest, "Passenger");
    }

    @Override
    public void displayTrainInfo() {
        System.out.println("[Passenger]  " + trainName + " | #" + trainNumber
                + " | " + source + " -> " + destination + " (SL only)");
    }

    @Override
    public void validateCoach(CoachType coach) throws InvalidCoachForTrainException {
        if (coach != CoachType.SL) {
            throw new InvalidCoachForTrainException(
                "Passenger trains only allow Sleeper (SL). You selected: " + coach.displayName);
        }
    }
}

// ---------- Background Threads ----------

// Prints the current date and time to the console every 5 seconds
class DateTimePrinter extends Thread {
    volatile boolean running = true;

    public void stopThread() { running = false; }

    @Override
    public void run() {
        SimpleDateFormat fmt = new SimpleDateFormat("dd-MM-yyyy HH:mm:ss");
        while (running) {
            System.out.println("\n[" + fmt.format(new Date()) + "]");
            try {
                Thread.sleep(5000);
            } catch (InterruptedException e) {
                break;
            }
        }
    }
}

// Checks every 30 seconds if any coach is below 10% seat capacity and prints an alert
class SeatAvailabilityMonitor extends Thread {
    private final SeatManager seatManager;
    volatile boolean running = true;

    public SeatAvailabilityMonitor(SeatManager sm) {
        this.seatManager = sm;
    }

    public void stopMonitor() { running = false; }

    @Override
    public void run() {
        while (running) {
            try {
                Thread.sleep(30000);
            } catch (InterruptedException e) {
                break;
            }
            for (CoachType c : CoachType.values()) {
                int remaining = seatManager.getSeatsMap().getOrDefault(c, 0);
                int threshold = Math.max(1, c.totalSeats / 10);
                if (remaining < threshold) {
                    System.out.println("*** ALERT: " + c.displayName
                            + " coach has only " + remaining + " seats left! ***");
                }
            }
        }
    }
}

// ---------- BookingManager ----------

// Implements Bookable; owns all ticket booking, cancellation, and reporting logic
class BookingManager implements Bookable {

    private final HashMap<String, Ticket> tickets = new HashMap<>();
    private final SeatManager seatManager;
    private final Scanner scanner;
    private Train selectedTrain;
    private int   distance;

    private static final String DATA_FILE = "bookings.dat";

    // Separate queue for Divyang passengers (demonstrates generics)
    private final BookingQueue<DivyangPassenger> divyangQueue = new BookingQueue<>();

    public BookingManager(SeatManager sm, Scanner sc) {
        this.seatManager = sm;
        this.scanner     = sc;
        loadBookings();
    }

    // ---- Bookable interface ----

    @Override
    public void bookTicket() {
        try {
            // Let user pick a train if not already chosen
            if (selectedTrain == null) selectTrain();
            if (selectedTrain == null) return;

            String journeyDate = askJourneyDate();

            System.out.print("Enter Coach Type (SL / 3A / 2A / 1A): ");
            CoachType coach;
            try {
                coach = CoachType.fromInput(scanner.nextLine().trim());
            } catch (IllegalArgumentException e) {
                System.out.println(e.getMessage());
                return;
            }

            // Passenger trains only allow SL
            try {
                selectedTrain.validateCoach(coach);
            } catch (InvalidCoachForTrainException e) {
                System.out.println(e.getMessage());
                return;
            }

            System.out.print("Is this a Divyang passenger? (yes/no): ");
            boolean isDivyang = scanner.nextLine().trim().equalsIgnoreCase("yes");

            System.out.print("Number of passengers: ");
            int numPassengers;
            try {
                numPassengers = Integer.parseInt(scanner.nextLine().trim());
            } catch (NumberFormatException e) {
                System.out.println("Invalid number.");
                return;
            }

            for (int i = 1; i <= numPassengers; i++) {
                System.out.println("\n--- Passenger " + i + " ---");
                Passenger passenger = readPassenger(isDivyang);
                if (passenger == null) { i--; continue; } // re-prompt on error

                // Calculate base price (without concessions) for display
                int basePrice = coach.ratePerKm * distance;
                if (selectedTrain instanceof RajdhaniTrain) {
                    basePrice = (int) (basePrice * (1 + RajdhaniTrain.LUXURY_SURCHARGE));
                }

                // Calculate final price after all concessions
                int finalPrice = TicketPriceCalculator.calculatePrice(coach, distance, passenger);
                if (selectedTrain instanceof RajdhaniTrain) {
                    finalPrice = (int) (finalPrice * (1 + RajdhaniTrain.LUXURY_SURCHARGE));
                }

                // Try to allocate a seat
                String alloc = seatManager.bookSeats(coach, 1);
                if ("FULL".equals(alloc)) {
                    System.out.println("No seats available (confirmed, RAC, or WL full).");
                    continue;
                }

                int seatNum = "CONFIRMED".equals(alloc) ? seatManager.assignSeat(coach) : 0;

                Ticket ticket = new Ticket(passenger, coach, basePrice, finalPrice, seatNum,
                        selectedTrain.trainName, selectedTrain.trainNumber,
                        selectedTrain.source, selectedTrain.destination, journeyDate);

                if ("RAC".equals(alloc))     seatManager.addToRAC(ticket, coach);
                else if ("WL".equals(alloc)) seatManager.addToWL(ticket, coach);

                tickets.put(ticket.pnr, ticket);
                ticket.saveTicketToFile();
                ticket.printTicket();
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
            Ticket ticket = getTicketByPNR(pnr);
            ticket.deleteTicketFile();
            tickets.remove(pnr);
            seatManager.cancelSeats(ticket.coachType, ticket, tickets);
            System.out.println("Ticket cancelled. PNR: " + pnr);
            saveBookings();
        } catch (InvalidPNRException e) {
            System.out.println(e.getMessage());
        }
    }

    @Override
    public boolean checkAvailability(String coachType) {
        try {
            return seatManager.checkAvailability(CoachType.fromInput(coachType));
        } catch (IllegalArgumentException e) {
            return false;
        }
    }

    // ---- PNR Status ----

    public void checkPNRStatus() {
        System.out.print("Enter PNR: ");
        String pnr = scanner.nextLine().trim();
        try {
            getTicketByPNR(pnr).printTicket();
        } catch (InvalidPNRException e) {
            System.out.println(e.getMessage());
        }
    }

    // ---- Train Search ----

    public static void searchTrains(Scanner sc) {
        System.out.print("Source     : ");
        String src  = sc.nextLine().trim();
        System.out.print("Destination: ");
        String dest = sc.nextLine().trim();

        System.out.println("\nTrains from " + src + " to " + dest + ":");
        boolean found = false;
        for (Train t : getAllTrains()) {
            if (t.source.equalsIgnoreCase(src) && t.destination.equalsIgnoreCase(dest)) {
                t.displayTrainInfo();
                found = true;
            }
        }
        if (!found) System.out.println("No direct trains found.");
    }

    // ---- All Passengers Sorted by Name ----

    public void viewAllPassengersSorted() {
        if (tickets.isEmpty()) {
            System.out.println("No passengers booked.");
            return;
        }
        System.out.println("\n--- All Passengers (A-Z by name) ---");
        tickets.values().stream()
            .map(t -> t.passenger)
            .sorted(Comparator.comparing(p -> p.name))
            .forEach(Passenger::displayPassengerInfo);
    }

    // ---- Reports using Streams & Lambdas ----

    public void showReports() {
        if (tickets.isEmpty()) {
            System.out.println("No tickets booked yet.");
            return;
        }

        System.out.println("\n========== REPORTS ==========");

        // Count tickets per coach type
        System.out.println("\n[Tickets by Coach]");
        for (CoachType c : CoachType.values()) {
            long count = tickets.values().stream()
                .filter(t -> t.coachType == c)
                .count();
            System.out.println("  " + c.displayName + ": " + count);
        }

        // Passengers sorted by age
        System.out.println("\n[Passengers by Age]");
        tickets.values().stream()
            .map(t -> t.passenger)
            .sorted(Comparator.comparingInt(p -> p.age))
            .forEach(p -> System.out.println("  " + p.name + " (age " + p.age + ")"));

        // Total revenue per coach type
        System.out.println("\n[Revenue by Coach]");
        tickets.values().stream()
            .collect(Collectors.groupingBy(
                t -> t.coachType,
                Collectors.summingInt(t -> t.finalPrice)))
            .forEach((c, r) -> System.out.println("  " + c.displayName + ": Rs. " + r));

        // Most expensive ticket
        System.out.println("\n[Most Expensive Ticket]");
        tickets.values().stream()
            .max(Comparator.comparingInt(t -> t.finalPrice))
            .ifPresent(t -> System.out.println("  PNR: " + t.pnr
                + "  Passenger: " + t.passenger.name
                + "  Price: Rs. " + t.finalPrice));
    }

    // ---- Admin Mode ----

    public void adminMode() {
        System.out.print("Admin password: ");
        String pwd = scanner.nextLine().trim();
        if (!"admin123".equals(pwd)) {
            System.out.println("Wrong password.");
            return;
        }
        System.out.println("\n===== ADMIN MODE =====");
        if (tickets.isEmpty()) {
            System.out.println("No bookings on record.");
        } else {
            System.out.println("All PNRs:");
            tickets.forEach((pnr, t) ->
                System.out.println("  PNR: " + pnr
                    + "  | " + t.passenger.name
                    + "  | " + t.coachType.displayName
                    + "  | Rs. " + t.finalPrice
                    + "  | " + t.status));
            int total = tickets.values().stream().mapToInt(t -> t.finalPrice).sum();
            System.out.println("Total Revenue  : Rs. " + total);
        }
        System.out.println("Divyang queue  : " + divyangQueue.size() + " passenger(s)");
    }

    // ---- Helper: Train Selection ----

    public void selectTrain() {
        List<Train> trains = getAllTrains();
        System.out.println("\n--- Select Train ---");
        for (int i = 0; i < trains.size(); i++) {
            System.out.print((i + 1) + ". ");
            trains.get(i).displayTrainInfo();
        }
        System.out.print("Choice (1-" + trains.size() + "): ");
        int ch;
        try {
            ch = Integer.parseInt(scanner.nextLine().trim());
        } catch (NumberFormatException e) {
            System.out.println("Invalid choice.");
            return;
        }
        if (ch < 1 || ch > trains.size()) {
            System.out.println("Invalid choice.");
            return;
        }
        selectedTrain = trains.get(ch - 1);
        try {
            distance = selectedTrain.calculateDistance();
        } catch (IllegalArgumentException e) {
            System.out.println("Route error: " + e.getMessage());
            selectedTrain = null;
        }
    }

    // ---- Helper: Journey Date ----

    private String askJourneyDate() throws InvalidDateException {
        SimpleDateFormat sdf = new SimpleDateFormat("dd-MM-yyyy");
        sdf.setLenient(false);
        while (true) {
            System.out.print("Journey Date (DD-MM-YYYY): ");
            String input = scanner.nextLine().trim();
            Date date;
            try {
                date = sdf.parse(input);
            } catch (ParseException e) {
                System.out.println("Invalid format. Use DD-MM-YYYY.");
                continue;
            }
            if (date.before(new Date())) {
                throw new InvalidDateException("Date is in the past: " + input);
            }
            return input;
        }
    }

    // ---- Helper: Read Passenger from Console ----

    private Passenger readPassenger(boolean isDivyang) {
        try {
            System.out.print("Name: ");
            String name = scanner.nextLine().trim();

            System.out.print("Age: ");
            int age = Integer.parseInt(scanner.nextLine().trim());

            System.out.print("Gender (Male/Female): ");
            String gender = scanner.nextLine().trim();

            System.out.print("Aadhaar (12 digits): ");
            long aadhaar = Long.parseLong(scanner.nextLine().trim());

            if (isDivyang) {
                System.out.print("Disability Type: ");
                String dtype = scanner.nextLine().trim();
                DivyangPassenger dp = new DivyangPassenger(name, age, gender, aadhaar, dtype);
                divyangQueue.add(dp);
                return dp;
            }
            return new Passenger(name, age, gender, aadhaar);

        } catch (NumberFormatException e) {
            System.out.println("Invalid number entered. Please try again.");
            return null;
        } catch (InvalidAadhaarException | IllegalArgumentException e) {
            System.out.println(e.getMessage() + " Please try again.");
            return null;
        }
    }

    // ---- Helper: PNR Lookup ----

    private Ticket getTicketByPNR(String pnr) throws InvalidPNRException {
        Ticket t = tickets.get(pnr);
        if (t == null) throw new InvalidPNRException("No ticket found for PNR: " + pnr);
        return t;
    }

    // ---- Train Catalogue ----

    public static List<Train> getAllTrains() {
        return List.of(
            new ExpressTrain  ("Saurashtra Janta Express", 19218, "Rajkot",    "Mumbai"),
            new ExpressTrain  ("Gujarat Express",          19011, "Ahmedabad", "Mumbai"),
            new SuperfastTrain("Duronto Express",          22955, "Rajkot",    "Mumbai"),
            new RajdhaniTrain ("Rajdhani Express",         12957, "Ahmedabad", "Mumbai"),
            new PassengerTrain("Passenger Local",          59201, "Rajkot",    "Ahmedabad")
        );
    }

    // ---- Serialization ----

    // Loads previously saved bookings from bookings.dat on startup
    @SuppressWarnings("unchecked")
    private void loadBookings() {
        File f = new File(DATA_FILE);
        if (!f.exists()) return;
        try (ObjectInputStream ois = new ObjectInputStream(new FileInputStream(f))) {
            HashMap<String, Ticket> saved = (HashMap<String, Ticket>) ois.readObject();
            tickets.putAll(saved);
            System.out.println("[Loaded " + tickets.size() + " booking(s) from " + DATA_FILE + "]");
        } catch (Exception e) {
            System.out.println("[Could not load " + DATA_FILE + ": " + e.getMessage() + "]");
        }
    }

    // Saves all current bookings to bookings.dat after every change
    private void saveBookings() {
        try (ObjectOutputStream oos = new ObjectOutputStream(new FileOutputStream(DATA_FILE))) {
            oos.writeObject(tickets);
        } catch (IOException e) {
            System.out.println("Could not save bookings: " + e.getMessage());
        }
    }
}

// ---------- Main Class ----------

// Application entry point — starts background threads and runs the menu loop
public class RailwayBookingSystem {

    public static void main(String[] args) {
        Scanner        scanner     = new Scanner(System.in);
        SeatManager    seatMgr     = new SeatManager();
        BookingManager bookingMgr  = new BookingManager(seatMgr, scanner);

        // DateTimePrinter prints current time every 5 seconds
        DateTimePrinter dtp = new DateTimePrinter();
        dtp.setDaemon(true);
        dtp.start();

        // SeatAvailabilityMonitor alerts when a coach is nearly full
        SeatAvailabilityMonitor sam = new SeatAvailabilityMonitor(seatMgr);
        sam.setDaemon(true);
        sam.start();

        System.out.println("=== Indian Railway Booking System ===");

        while (true) {
            System.out.println("\n--- MAIN MENU ---");
            System.out.println("1. Book Ticket");
            System.out.println("2. Cancel Ticket by PNR");
            System.out.println("3. Check PNR Status");
            System.out.println("4. View Remaining Seats");
            System.out.println("5. Search Trains by Source & Destination");
            System.out.println("6. View All Passengers (sorted by name)");
            System.out.println("7. Reports");
            System.out.println("8. Admin Mode");
            System.out.println("9. Exit");
            System.out.print("Choice: ");

            int choice;
            try {
                choice = Integer.parseInt(scanner.nextLine().trim());
            } catch (NumberFormatException e) {
                System.out.println("Please enter a number between 1 and 9.");
                continue;
            }

            switch (choice) {
                case 1: bookingMgr.bookTicket();                        break;
                case 2: bookingMgr.cancelTicket();                      break;
                case 3: bookingMgr.checkPNRStatus();                    break;
                case 4: seatMgr.displayRemainingSeats();                break;
                case 5: BookingManager.searchTrains(scanner);           break;
                case 6: bookingMgr.viewAllPassengersSorted();           break;
                case 7: bookingMgr.showReports();                       break;
                case 8: bookingMgr.adminMode();                         break;
                case 9:
                    System.out.println("Goodbye!");
                    dtp.stopThread();
                    sam.stopMonitor();
                    scanner.close();
                    System.exit(0);
                    break;
                default:
                    System.out.println("Invalid choice. Enter 1-9.");
            }
        }
    }
}
