import java.io.*;
import java.util.*;

// Interface (Separate File Bookable.java)
interface Bookable {
    void bookTicket();
}

// Custom Exception Class
class InvalidAadhaarException extends Exception {
    public InvalidAadhaarException(String message) {
        super(message);
    }
}

// Abstract Class Train
abstract class Train {
    String trainName;
    final int trainNumber;
    final String source;
    final String destination;
    final Map<String, Integer> routeDistance;

    public Train(String source, String destination) {
        this.trainName = "SAURASHTRA JANTA";
        this.trainNumber = 19218;
        this.source = source;
        this.destination = destination;
        this.routeDistance = Map.of(
            "Rajkot-Ahmedabad", 215,
            "Ahmedabad-Vadodara", 110,
            "Vadodara-Surat", 150,
            "Surat-Mumbai", 280
        );
    }

    abstract void displayTrainInfo();

    public int calculateDistance() {
        if (source.equalsIgnoreCase("Rajkot") && destination.equalsIgnoreCase("Mumbai")) {
            return 755;
        }
        int totalDistance = 0;
        List<String> route = List.of("Rajkot", "Ahmedabad", "Vadodara", "Surat", "Mumbai");
        int startIndex = route.indexOf(source);
        int endIndex = route.indexOf(destination);

        if (startIndex == -1 || endIndex == -1 || startIndex >= endIndex) {
            throw new IllegalArgumentException("Invalid source or destination.");
        }

        for (int i = startIndex; i < endIndex; i++) {
            totalDistance += routeDistance.get(route.get(i) + "-" + route.get(i + 1));
        }
        return totalDistance;
    }
}

// Concrete Class ExpressTrain extending Train
class ExpressTrain extends Train {
    public ExpressTrain(String source, String destination) {
        super(source, destination);
    }

    @Override
    void displayTrainInfo() {
        System.out.println("Train: " + trainName + " | Number: " + trainNumber + " | Source: " + source + " | Destination: " + destination);
    }
}

// Ticket Price Calculation Class
class TicketPriceCalculator {
    public static int calculatePrice(String coachType, int numPassengers, int distance) {
        int ratePerKm = switch (coachType) {
            case "SL" -> 1;
            case "3A" -> 2;
            case "2A" -> 3;
            case "1A" -> 4;
            default -> throw new IllegalArgumentException("Invalid coach type!");
        };
        return ratePerKm * distance * numPassengers;
    }
}

// Seat Manager Class
class SeatManager {
    private final Map<String, Integer> seats;
    private final Random random = new Random();

    public SeatManager() {
        seats = new HashMap<>();
        seats.put("SL", 72);
        seats.put("3A", 64);
        seats.put("2A", 54);
        seats.put("1A", 26);
    }

    public boolean bookSeats(String coachType, int numPassengers) {
        int available = seats.getOrDefault(coachType, 0);
        if (available >= numPassengers) {
            seats.put(coachType, available - numPassengers);
            return true;
        } else {
            System.out.println("Not enough seats available in " + coachType + " Coach.");
            return false;
        }
    }

    public int assignSeat(String coachType) {
        return random.nextInt(seats.get(coachType));
    }
    //method overloading
    public int assignSeat(String coachType, int preferredSeat) {
        return preferredSeat;
    }

    public void cancelSeats(String coachType, int numPassengers) {
        seats.put(coachType, seats.getOrDefault(coachType, 0) + numPassengers);
    }

    public void displayRemainingSeats() {
        System.out.println("\nRemaining Seats:");
        seats.forEach((coach, count) -> System.out.println(coach + " Coach: " + count + " seats left"));
    }
}

// Passenger Class
class Passenger {
    String name;
    int age;
    String gender;
    long aadhaarNumber;

    public Passenger(String name, int age, String gender, long aadhaarNumber) throws InvalidAadhaarException {
        this.name = name;
        this.age = age;

        if (!gender.equalsIgnoreCase("Male") && !gender.equalsIgnoreCase("Female")) {
            throw new IllegalArgumentException("Invalid gender! Enter 'Male' or 'Female'.");
        }
        this.gender = gender;

        String aadhaarStr = String.valueOf(aadhaarNumber);
        if (aadhaarStr.length() != 12) {
            throw new InvalidAadhaarException("Invalid Aadhaar Number! It must be a 12-digit number.");
        }

        this.aadhaarNumber = aadhaarNumber;
    }

    public void displayPassengerInfo() {
        System.out.println("Passenger: " + name + " | Age: " + age + " | Gender: " + gender + " | Aadhaar: " + aadhaarNumber);
    }
}

// Ticket Printing Class
class Ticket {
    static int ticketCounter = 1000;
    Passenger passenger;
    String coachType;
    int price;
    int seatNumber;
    int ticketNumber;

    public Ticket(Passenger passenger, String coachType, int price , int seatNumber) {
        this.ticketNumber = ++ticketCounter;
        this.passenger = passenger;
        this.coachType = coachType;
        this.price = price;
        this.seatNumber = seatNumber;
    }

    public void saveTicketToFile() {
        String fileName = passenger.aadhaarNumber + ".txt";
        try (StringWriter stringWriter = new StringWriter(); PrintWriter writer = new PrintWriter(stringWriter)) {
            writer.println("================= TICKET =================");
            writer.println("Ticket Number: " + ticketNumber);
            writer.println("Passenger Name: " + passenger.name);
            writer.println("Age: " + passenger.age);
            writer.println("Gender: " + passenger.gender);
            writer.println("Aadhaar: " + passenger.aadhaarNumber);
            writer.println("Coach Type: " + coachType);
            writer.println("Seat Number: " + seatNumber);
            writer.println("Ticket Price: Rs. " + price);
            writer.println("==========================================");
            
            try (FileWriter fileWriter = new FileWriter(fileName)) {
                fileWriter.write(stringWriter.toString());
            }
        } catch (IOException e) {
            System.out.println("Error saving ticket to file: " + e.getMessage());
        }
    }

    public void deleteTicketFile() {
        String fileName = passenger.aadhaarNumber + ".txt";
        File file = new File(fileName);
        if (file.exists()) {
            if (file.delete()) {
                System.out.println("Ticket file " + fileName + " deleted successfully.");
            } else {
                System.out.println("Failed to delete ticket file " + fileName);
            }
        } else {
            System.out.println("No ticket file found for Aadhaar " + passenger.aadhaarNumber);
        }
    }

    public void printTicket() {
        System.out.println("\n================= TICKET =================");
        System.out.println("Ticket Number: " + ticketNumber);
        System.out.println("Passenger Name: " + passenger.name);
        System.out.println("Age: " + passenger.age);
        System.out.println("Gender: " + passenger.gender);
        System.out.println("Aadhaar: " + passenger.aadhaarNumber);
        System.out.println("Coach Type: " + coachType);
        System.out.println("Seat Number: " + seatNumber);
        System.out.println("Ticket Price: Rs. " + price);
        System.out.println("==========================================");
    }
}

// Thread Class for Printing Date and Time
class DateTimePrinter extends Thread {
    boolean running = true;

    public void stopThread() {
        running = false;
    }


    // public void run() {
    //     SimpleDateFormat formatter = new SimpleDateFormat("dd-MM-yyyy HH:mm:ss");
    //     while (running) {
    //         System.out.println("\n[Current Date & Time: " + formatter.format(new Date()) + "]");
    //         try {
    //             Thread.sleep(5000);
    //         } catch (InterruptedException e) {
    //             System.out.println("DateTimePrinter interrupted.");
    //         }
    //     }
    // }
}

// Main Class
public class RailwayBookingSystem {
    public static void main(String[] args) {
        Scanner scanner = new Scanner(System.in);
        List<Ticket> bookedTickets = new ArrayList<>();
        SeatManager seatManager = new SeatManager();

        System.out.println("Select Train:");
        System.out.println("1. Saurashtra Janta Express (19218)");
        System.out.println("2. Gujarat Express (19011)");
        System.out.print("Enter choice (1 or 2): ");
        int trainChoice = scanner.nextInt();
        scanner.nextLine();

        String trainName;
        int trainNumber;

        switch (trainChoice) {
            case 1:
                trainName = "Saurashtra Janta Express";
                trainNumber = 19218;
                break;
            case 2:
                trainName = "Gujarat Express";
                trainNumber = 19011;
                break;
            default:
                System.out.println("Invalid choice! Exiting...");
                return;
        }

        
        System.out.print("Enter Source (Rajkot, Ahmedabad, Vadodara, Surat): ");
        String source = scanner.nextLine();
        System.out.print("Enter Destination (Ahmedabad, Vadodara, Surat, Mumbai): ");
        String destination = scanner.nextLine();

        ExpressTrain train;
        try {
            train = new ExpressTrain(source, destination);
            train.displayTrainInfo();
        } catch (IllegalArgumentException e) {
            System.out.println("Error: " + e.getMessage());
            return;
        }

        int distance = train.calculateDistance();
        System.out.println("Total Distance: " + distance + " km");

        DateTimePrinter dateTimePrinter = new DateTimePrinter();
        dateTimePrinter.start();

        while (true) {
            System.out.println("\n1. Book Ticket");
            System.out.println("2. Cancel Ticket");
            System.out.println("3. View Seat Remaining");
            System.out.println("4. Exit");
            System.out.print("Choose an option: ");
            int choice = scanner.nextInt();
            scanner.nextLine();

            switch (choice) {
                case 1:
                    System.out.print("Enter Coach Type (SL/3A/2A/1A): ");
                    String coachType = scanner.nextLine().toUpperCase();
                    System.out.print("Enter Number of Passengers: ");
                    int numPassengers = scanner.nextInt();
                    scanner.nextLine();

                    if (seatManager.bookSeats(coachType, numPassengers)) {
                        for (int i = 1; i <= numPassengers; i++) {
                            System.out.print("\nEnter Name: ");
                            String name = scanner.nextLine();
                            System.out.print("Enter Age: ");
                            int age = scanner.nextInt();
                            scanner.nextLine();
                            System.out.print("Enter Gender (Male/Female): ");
                            String gender = scanner.nextLine();
                            System.out.print("Enter Aadhaar Number (12 digits): ");
                            long aadhaarNumber = scanner.nextLong();
                            scanner.nextLine();

                            try {
                                Passenger passenger = new Passenger(name, age, gender, aadhaarNumber);
                                int price = TicketPriceCalculator.calculatePrice(coachType, 1, distance);
                                Ticket ticket = new Ticket(passenger, coachType, price , seatManager.assignSeat(coachType));
                                bookedTickets.add(ticket);
                                ticket.saveTicketToFile();
                                ticket.printTicket();
                            } catch (InvalidAadhaarException e) {
                                System.out.println("Error: " + e.getMessage());
                            }
                        }
                    }
                    break;

                    case 2: // Cancel Ticket
                    System.out.print("Enter aadhar  Number: ");
                    long aadhaarToCancel = scanner.nextLong();
                    Iterator<Ticket> iterator = bookedTickets.iterator();
                    boolean found = false;
                    while (iterator.hasNext()) {
                        Ticket ticket = iterator.next();
                        if (ticket.passenger.aadhaarNumber == aadhaarToCancel) {
                            seatManager.cancelSeats(ticket.coachType, 1);
                            ticket.deleteTicketFile();
                            iterator.remove();
                            found = true;
                        }
                    }
                    if (!found) {
                        System.out.println("No booked ticket found with Aadhaar " + aadhaarToCancel);
                    }

                    break;

                case 3:
                    seatManager.displayRemainingSeats();
                    break;

                case 4:
                    System.out.println("Exiting system.");
                    dateTimePrinter.stopThread();
                    scanner.close();
                    return;
            }
        }
    }
}
