
package parking;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.*;

/**
 * Beginner-friendly demonstration of a Campus Smart-Parking System.
 * Compile: javac -d out src/parking/CampusParkingSystem.java
 * Run:     java -cp out parking.CampusParkingSystem
 */
public class CampusParkingSystem {
    enum UserType { STUDENT, STAFF, VISITOR }

    static class User {
        private final String name;
        private final UserType type;
        private final boolean monthlyPass;
        private final boolean oneDayPass;

        User(String name, UserType type, boolean monthlyPass, boolean oneDayPass) {
            if (name == null || name.isBlank()) throw new IllegalArgumentException("Name is required");
            this.name = name;
            this.type = Objects.requireNonNull(type);
            this.monthlyPass = monthlyPass;
            this.oneDayPass = oneDayPass;
        }
        String getName() { return name; }
        UserType getType() { return type; }
        boolean hasFreePass() {
            return (type == UserType.STUDENT || type == UserType.STAFF) && monthlyPass
                    || type == UserType.VISITOR && oneDayPass;
        }
    }

    interface Chargeable {
        double chargingFee(int kWh);
        default String chargingMessage() { return "EV charging facility"; }
    }

    abstract static class Vehicle {
        private final String registrationNumber;
        Vehicle(String registrationNumber) {
            if (registrationNumber == null || registrationNumber.isBlank())
                throw new IllegalArgumentException("Registration number is required");
            this.registrationNumber = registrationNumber.toUpperCase(Locale.ROOT);
        }
        String getRegistrationNumber() { return registrationNumber; }
        protected abstract double ratePerHour();

        // Every started hour is charged in full: 1-60 min = 1 hour, 61-120 = 2 hours.
        public double calculateFee(int minutes) {
            if (minutes < 0) throw new IllegalArgumentException("Minutes cannot be negative");
            int billableHours = (minutes + 59) / 60;
            return billableHours * ratePerHour();
        }
        @Override public String toString() {
            return getClass().getSimpleName() + "[" + registrationNumber + "]";
        }
        @Override public boolean equals(Object other) {
            return other instanceof Vehicle
                    && registrationNumber.equals(((Vehicle) other).registrationNumber);
        }
        @Override public int hashCode() { return registrationNumber.hashCode(); }
    }

    static class TwoWheeler extends Vehicle {
        TwoWheeler(String reg) { super(reg); }
        @Override protected double ratePerHour() { return 15.0; }
    }
    static class Car extends Vehicle {
        Car(String reg) { super(reg); }
        @Override protected double ratePerHour() { return 45.0; }
    }
    static class ElectricCar extends Car implements Chargeable {
        ElectricCar(String reg) { super(reg); }
        @Override protected double ratePerHour() { return 45.0; }
        @Override public double chargingFee(int kWh) {
            if (kWh < 0) throw new IllegalArgumentException("kWh cannot be negative");
            return kWh * 12.0;
        }
    }
    static class EBike extends TwoWheeler implements Chargeable {
        EBike(String reg) { super(reg); }
        @Override protected double ratePerHour() { return 10.0; }
        @Override public double chargingFee(int kWh) {
            if (kWh < 0) throw new IllegalArgumentException("kWh cannot be negative");
            return kWh * 5.0;
        }
    }
    static class DeliveryVan extends Vehicle {
        DeliveryVan(String reg) { super(reg); }
        @Override protected double ratePerHour() { return 45.0; }
        double calculateTimeBasedFee(LocalDateTime entry, LocalDateTime exit) {
            if (exit.isBefore(entry)) throw new IllegalArgumentException("Exit before entry");
            long minutes = Duration.between(entry, exit).toMinutes();
            double normalFee = calculateFee((int) minutes);
            // Simplified rule: if entry time is between 09:00 and 16:59, double the fee.
            LocalTime t = entry.toLocalTime();
            return (!t.isBefore(LocalTime.of(9, 0)) && t.isBefore(LocalTime.of(17, 0)))
                    ? normalFee * 2 : normalFee;
        }
    }

    static class ParkingTicket {
        private static int nextTicketNumber = 1000;
        private final int ticketNumber;
        private final Vehicle vehicle;
        private final User user;
        private final int floor;
        private final int slot;
        private final LocalDateTime entryTime;

        ParkingTicket(Vehicle vehicle, User user, int floor, int slot) {
            this.ticketNumber = ++nextTicketNumber;
            this.vehicle = Objects.requireNonNull(vehicle);
            this.user = Objects.requireNonNull(user);
            this.floor = floor;
            this.slot = slot;
            this.entryTime = LocalDateTime.now();
        }
        int getTicketNumber() { return ticketNumber; }
        Vehicle getVehicle() { return vehicle; }
        User getUser() { return user; }
        int getFloor() { return floor; }
        int getSlot() { return slot; }
        LocalDateTime getEntryTime() { return entryTime; }
    }

    static class ParkingFacility {
        private final String[][] slots;
        private final Map<String, ParkingTicket> activeTickets = new HashMap<>();
        private static int totalOccupancy = 0;

        ParkingFacility(int[] slotsPerFloor) {
            slots = new String[slotsPerFloor.length][];
            for (int f = 0; f < slotsPerFloor.length; f++) {
                if (slotsPerFloor[f] < 0) throw new IllegalArgumentException("Negative slot count");
                slots[f] = new String[slotsPerFloor[f]];
            }
        }
        int[] allocate(String vehicleNo) {
            for (int f = 0; f < slots.length; f++) {
                for (int s = 0; s < slots[f].length; s++) {
                    if (slots[f][s] == null) {
                        slots[f][s] = vehicleNo;
                        totalOccupancy++;
                        return new int[]{f, s};
                    }
                }
            }
            return null;
        }
        boolean release(int floor, int slot) {
            if (floor < 0 || floor >= slots.length) return false;
            if (slot < 0 || slot >= slots[floor].length) return false;
            if (slots[floor][slot] == null) return false;
            slots[floor][slot] = null;
            totalOccupancy--;
            return true;
        }
        ParkingTicket park(Vehicle vehicle, User user) {
            String reg = vehicle.getRegistrationNumber();
            if (activeTickets.containsKey(reg)) throw new IllegalArgumentException("Duplicate registration");
            int[] position = allocate(reg);
            if (position == null) throw new IllegalStateException("Parking full");
            ParkingTicket ticket = new ParkingTicket(vehicle, user, position[0], position[1]);
            activeTickets.put(reg, ticket);
            return ticket;
        }
        double exit(String registrationNumber, int parkedMinutes, int chargingKWh) {
            ParkingTicket ticket = activeTickets.remove(registrationNumber.toUpperCase(Locale.ROOT));
            if (ticket == null) throw new IllegalArgumentException("No active ticket");
            Vehicle vehicle = ticket.getVehicle();
            double fee;
            if (ticket.getUser().hasFreePass()) fee = 0.0;
            else if (vehicle instanceof DeliveryVan) {
                fee = ((DeliveryVan) vehicle).calculateFee(parkedMinutes);
                // Time-window multiplier is demonstrated separately by calculateTimeBasedFee().
            } else fee = vehicle.calculateFee(parkedMinutes);
            if (vehicle instanceof Chargeable) fee += ((Chargeable) vehicle).chargingFee(chargingKWh);
            release(ticket.getFloor(), ticket.getSlot());
            return fee;
        }
        static int getTotalOccupancy() { return totalOccupancy; }
    }

    // Compile-time polymorphism: overloaded methods.
    static double estimateFee(Vehicle vehicle, int minutes) {
        return vehicle.calculateFee(minutes);
    }
    static double estimateFee(Vehicle vehicle, int minutes, boolean passHolder) {
        return passHolder ? 0.0 : vehicle.calculateFee(minutes);
    }

    public static void main(String[] args) {
        ParkingFacility facility = new ParkingFacility(new int[]{2, 3, 2});
        User visitor = new User("Arun", UserType.VISITOR, false, false);
        Vehicle car = new Car("TN01AB1234");
        ParkingTicket ticket = facility.park(car, visitor);
        System.out.println("Ticket: " + ticket.getTicketNumber());
        System.out.println("Vehicle: " + ticket.getVehicle());
        System.out.println("Slot: floor " + ticket.getFloor() + ", slot " + ticket.getSlot());
        System.out.println("Fee for 90 minutes: Rs " + facility.exit("TN01AB1234", 90, 0));
        System.out.println("Total occupancy: " + ParkingFacility.getTotalOccupancy());

        Vehicle ev = new ElectricCar("TN02CD5678");
        double evFee = estimateFee(ev, 130);
        if (ev instanceof Chargeable) evFee += ((Chargeable) ev).chargingFee(10);
        System.out.println("Electric car fee + charging: Rs " + evFee);
    }
}
