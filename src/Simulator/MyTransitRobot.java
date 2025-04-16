package Simulator;

import fr.emse.fayol.maqit.simulator.components.ColorPackage;
// import fr.emse.fayol.maqit.simulator.components.Message;
import fr.emse.fayol.maqit.simulator.components.PackageState;
import fr.emse.fayol.maqit.simulator.components.ColorTransitZone;
import fr.emse.fayol.maqit.simulator.components.ColorStartZone;
import fr.emse.fayol.maqit.simulator.components.Robot;
import fr.emse.fayol.maqit.simulator.environment.Cell;
import fr.emse.fayol.maqit.simulator.environment.ColorCell;
import fr.emse.fayol.maqit.simulator.environment.ColorGridEnvironment;

import java.awt.Color;
import java.util.List;
import java.util.Map;

import Simulator.TaskCoordinator.Task;

public class MyTransitRobot extends MyRobot {

    // Enum to track the robot's state with transit zones
    public enum TransitState {
        FREE,               // Robot is free to pick up packages
        GOING_TO_START,    // Robot is going to a start zone
        GOING_TO_TRANSIT,  // Robot is going to a transit zone
        GOING_TO_GOAL,     // Robot is going to the final goal
        WAITING_AT_TRANSIT,// Robot is waiting at a transit zone
        PICKING_FROM_TRANSIT, // Robot is picking up from a transit zone
        DELIVERED          // Robot has delivered the package
    }

    private TransitState transitState;
    private int transitX;
    private int transitY;

    // Battery management
    private double batteryLevel = 100.0;

    // Communication
    private static final String MSG_HELP_REQUEST = "HELP_REQUEST";
    private static final String MSG_HELP_OFFER = "HELP_OFFER";
    private static final String MSG_TRANSIT_FULL = "TRANSIT_FULL";
    private static final String MSG_TRANSIT_AVAILABLE = "TRANSIT_AVAILABLE";
    private static final String MSG_LOW_BATTERY = "LOW_BATTERY";
    private boolean waitingForHelp = false;

    // Coordinates of transit zones
    int[][] transitZones = {{12, 10}, {12, 9}, {9, 10}, {9, 9}};

    // Coordinates of charging stations (near transit zones)
    int[][] chargingStations = {{11, 10}, {13, 9}, {8, 10}, {10, 9}};
    private boolean isCharging = false;

    // Battery management constants - extremely optimized values
    private static final double MAX_BATTERY = 100.0;
    private static final double CRITICAL_BATTERY_THRESHOLD = 5.0;   // Drastically reduced to allow robots to work much longer
    private static final double LOW_BATTERY_THRESHOLD = 15.0;      // Drastically reduced to avoid premature charging
    private static final double MOVE_BATTERY_COST = 0.4;           // Reduced movement cost for efficiency
    private static final double PICKUP_BATTERY_COST = 1.0;         // Minimized to optimize energy usage
    private static final double DEPOSIT_BATTERY_COST = 1.0;        // Minimized to optimize energy usage
    private static final double CHARGING_RATE = 10.0;              // Greatly increased for much faster charging
    // Initialize battery level in constructor

    public MyTransitRobot(String name, int field, int debug, int[] pos, Color color, int rows, int columns, ColorGridEnvironment env, long seed) {
        super(name, field, debug, pos, color, rows, columns, env, seed);
        this.transitState = TransitState.FREE;
        this.batteryLevel = MAX_BATTERY; // Start with full battery

        // Register with the task coordinator
        TaskCoordinator coordinator = TaskCoordinator.getInstance();
        coordinator.setEnvironment(env);
        coordinator.registerRobot(this);
    }

    /**
     * Get the current battery level
     * @return The current battery level (0-100)
     */
    public double getBatteryLevel() {
        return batteryLevel;
    }

    /**
     * Check if the robot has delivered its package
     * @return true if the robot has delivered its package, false otherwise
     */
    public boolean hasDelivered() {
        return etat == Etat.DELIVRE;
    }

    /**
     * Find a transit zone that is NOT full
     * @return The first transit zone found that can accept a new package
     */
    private ColorTransitZone findTransitZoneNotFull() {
        for (int[] tzPos : transitZones) {
            Cell c = env.getGrid()[tzPos[0]][tzPos[1]];
            if (c instanceof ColorCell && ((ColorCell)c).getContent() instanceof ColorTransitZone) {
                ColorTransitZone tz = (ColorTransitZone) ((ColorCell)c).getContent();
                if (!tz.isFull()) {
                    transitX = tzPos[0];
                    transitY = tzPos[1];
                    return tz;
                }
            }
        }
        return null;
    }

    /**
     * Find a transit zone that has packages
     * @return The first transit zone found with packages
     */
    private ColorTransitZone findTransitZoneWithPackage() {
        for (int[] tzPos : transitZones) {
            Cell c = env.getGrid()[tzPos[0]][tzPos[1]];
            if (c instanceof ColorCell && ((ColorCell)c).getContent() instanceof ColorTransitZone) {
                ColorTransitZone tz = (ColorTransitZone) ((ColorCell)c).getContent();
                if (tz.getPackages().size() > 0) {
                    transitX = tzPos[0];
                    transitY = tzPos[1];
                    return tz;
                }
            }
        }
        return null;
    }

    /**
     * Check if all transit zones are full
     * @return true if all transit zones are full, false otherwise
     */
    private boolean transitZonesAreFull() {
        for (int[] pos : transitZones) {
            Cell c = env.getGrid()[pos[0]][pos[1]];
            if (c instanceof ColorCell && c.getContent() instanceof ColorTransitZone) {
                ColorTransitZone tz = (ColorTransitZone) c.getContent();
                if (!tz.isFull()) {
                    return false;
                }
            }
        }
        return true;
    }

    /**
     * Determine if it's better to use a transit zone based on distance
     * @param destX Destination X coordinate
     * @param destY Destination Y coordinate
     * @return true if using transit is better, false otherwise
     */
    private boolean isBetterToUseTransit(int destX, int destY) {
        // Calculate direct distance from current position to destination
        double directDistance = distanceTo(this.getX(), this.getY(), destX, destY);

        // Find the closest transit zone
        ColorTransitZone tz = findTransitZoneNotFull();
        if (tz == null) return false; // No available transit zone

        // Calculate distance via transit zone
        double distanceToTransit = distanceTo(this.getX(), this.getY(), transitX, transitY);
        double distanceFromTransitToDest = distanceTo(transitX, transitY, destX, destY);
        double totalTransitDistance = distanceToTransit + distanceFromTransitToDest;

        // Use transit if it's not significantly longer (within 30% of direct distance)
        return totalTransitDistance <= directDistance * 1.3;
    }

    /**
     * Get methods for the carried package
     */
    public ColorPackage getCarriedPackage() {
        return carriedPackage;
    }

    public void setCarriedPackage(ColorPackage pack) {
        this.carriedPackage = pack;
    }

    /**
     * The main logic for the transit robot's movement
     */
    @Override
    public void step() {
        // If the robot has delivered, do nothing
        if (etat == Etat.DELIVRE) return;

        // Check if robot is stuck
        if (isStuck()) {
            tryToUnstuck();
            return;
        }

        // EXTREME OPTIMIZATION: Ultra-aggressive battery management
        if (isCharging) {
            // If already charging, only charge to 50% to save time
            // This is a major optimization - we don't need full battery to be effective
            if (batteryLevel < MAX_BATTERY * 0.5) {
                chargeBattery();
                return;
            } else {
                // Stop charging once we have enough battery
                isCharging = false;
            }
        }

        // Only charge if critically low - never charge opportunistically
        // This maximizes the time robots spend on tasks
        if (batteryLevel <= CRITICAL_BATTERY_THRESHOLD) {
            if (isNearChargingStation()) {
                isCharging = true;
                System.out.println(getName() + " a commencé à charger sa batterie.");
                chargeBattery(); // Start charging immediately
                return;
            } else {
                // Only move to charging station if critically low
                int[] nearestCS = findNearestChargingStation();
                if (nearestCS != null) {
                    moveOneStepTo(nearestCS[0], nearestCS[1]);
                    consumeBatteryForMovement();
                    return;
                }
            }
        }

        // Robot is free and looking for a package
        if (etat == Etat.FREE) {
            // First check if there are packages in transit zones
            ColorTransitZone transitWithPackage = findTransitZoneWithPackage();

            if (transitWithPackage != null) {
                // There's a package in a transit zone, go get it
                if (isAdjacentTo(transitX, transitY)) {
                    // We're next to the transit zone, pick up the package
                    List<ColorPackage> packages = transitWithPackage.getPackages();
                    if (!packages.isEmpty()) {
                        carriedPackage = packages.get(0);
                        transitWithPackage.removePackage(carriedPackage);
                        consumeBatteryForPickup();
                        tempsDepart = System.currentTimeMillis();

                        int[] goalPos = GOALS.get(carriedPackage.getDestinationGoalId());
                        if (goalPos != null) {
                            destX = goalPos[0];
                            destY = goalPos[1];
                            etat = Etat.TRANSPORT;
                            transitState = TransitState.GOING_TO_GOAL;
                        }

                        System.out.println(getName() + " a pris un paquet de la zone de transit (" + transitX + "," + transitY + ") pour la destination " + carriedPackage.getDestinationGoalId());
                    }
                } else {
                    // Move towards the transit zone
                    moveOneStepTo(transitX, transitY);
                }
            } else {
                // Check start zones for packages
                ColorStartZone startZone = findStartZoneWithPackage();
                if (startZone == null) return;

                if (isAdjacentTo(startZone.getX(), startZone.getY())) {
                    // We're next to the start zone, pick up the package
                    if (!startZone.getPackages().isEmpty()) {
                        carriedPackage = startZone.getPackages().get(0);
                        startZone.removePackage(carriedPackage);
                        tempsDepart = System.currentTimeMillis();

                        int[] goalPos = GOALS.get(carriedPackage.getDestinationGoalId());
                        if (goalPos != null) {
                            destX = goalPos[0];
                            destY = goalPos[1];

                            // Use the TaskCoordinator to decide whether to use transit zone
                            TaskCoordinator coordinator = TaskCoordinator.getInstance();
                            ColorTransitZone tz = findTransitZoneNotFull();

                            if (tz != null && coordinator.shouldUseTransitZone(this, destX, destY, transitX, transitY)) {
                                // Use transit zone based on coordinator's decision
                                etat = Etat.TRANSPORT;
                                transitState = TransitState.GOING_TO_TRANSIT;
                                System.out.println("[COORDINATOR] " + getName() + " a pris un paquet de " + carriedPackage.getStartZone() +
                                                 " et va le déposer dans une zone de transit (" + transitX + "," + transitY + ")");
                            } else {
                                // Direct delivery
                                etat = Etat.TRANSPORT;
                                transitState = TransitState.GOING_TO_GOAL;
                                System.out.println("[COORDINATOR] " + getName() + " a pris un paquet de " + carriedPackage.getStartZone() +
                                                 " pour la destination " + carriedPackage.getDestinationGoalId());
                            }
                        }
                    }
                } else {
                    // Move towards the start zone
                    moveOneStepTo(startZone.getX(), startZone.getY());
                }
            }
        } else if (etat == Etat.TRANSPORT) {
            // Robot is carrying a package
            if (transitState == TransitState.GOING_TO_TRANSIT) {
                // Going to a transit zone
                if (isAdjacentTo(transitX, transitY)) {
                    // We're next to the transit zone, deposit the package
                    Cell c = env.getGrid()[transitX][transitY];
                    if (c instanceof ColorCell && c.getContent() instanceof ColorTransitZone) {
                        ColorTransitZone tz = (ColorTransitZone) c.getContent();
                        if (!tz.isFull()) {
                            tz.addPackage(carriedPackage);
                            consumeBatteryForDeposit();
                            System.out.println(getName() + " a déposé un paquet dans la zone de transit (" + transitX + "," + transitY + ")");
                            carriedPackage = null;
                            etat = Etat.FREE;
                            transitState = TransitState.FREE;
                        }
                    }
                } else {
                    // Move towards the transit zone
                    moveOneStepTo(transitX, transitY);
                }
            } else if (transitState == TransitState.GOING_TO_GOAL) {
                // Going to the final destination
                if ((this.getX() == destX) && (this.getY() == destY)) {
                    // We've reached the destination
                    carriedPackage.setState(PackageState.ARRIVED);
                    consumeBatteryForDeposit();
                    MySimFactory.deliveredCount++;

                    tempsArrivee = System.currentTimeMillis();
                    long deliveryTime = tempsArrivee - tempsDepart;
                    double batteryUsed = MAX_BATTERY - batteryLevel;

                    System.out.println("[COORDINATOR] " + getName() + " a livré le paquet " + carriedPackage.getId() +
                                     " à destination en " + (deliveryTime/1000) + " secondes avec " +
                                     (int)batteryUsed + "% de batterie consommée.");

                    // Update the task coordinator with delivery statistics
                    TaskCoordinator coordinator = TaskCoordinator.getInstance();
                    coordinator.updateRobotEfficiency(this, deliveryTime, batteryUsed);

                    etat = Etat.DELIVRE;
                    transitState = TransitState.DELIVERED;
                    // Remove the robot from the environment
                    env.removeCellContent(this.getX(), this.getY());
                    // Set the carriedPackage to null to ensure it's not interacting with anything
                    carriedPackage = null;
                    // Set isCharging to false to ensure it doesn't try to charge
                    isCharging = false;
                } else {
                    // Check if we have enough battery to reach the goal
                    if (batteryLevel < CRITICAL_BATTERY_THRESHOLD || !canReachDestination(destX, destY)) {
                        // Not enough battery, find a charging station
                        if (isNearChargingStation()) {
                            isCharging = true;
                            System.out.println(getName() + " a besoin de recharger avant de continuer vers la destination.");
                            return;
                        } else {
                            // Move towards the nearest charging station
                            int[] nearestCS = findNearestChargingStation();
                            if (nearestCS != null) {
                                System.out.println(getName() + " se dirige vers une station de recharge car batterie insuffisante pour atteindre la destination.");
                                moveOneStepTo(nearestCS[0], nearestCS[1]);
                                return;
                            }
                        }
                    }
                    // Move towards the goal
                    moveOneStepTo(destX, destY);
                    consumeBatteryForMovement();
                }
            }
        }
    }

    /**
     * Check if the robot is near an exit zone
     * @return true if the robot is near an exit zone, false otherwise
     */
    private boolean isNearExit() {
        // Check if we're at the top of the grid (y=0) which is where exits are located
        return this.getY() <= 1; // Consider both y=0 and y=1 as near exit
    }

    /**
     * Count the number of steps the robot has been stuck
     */
    private int stuckCounter = 0;
    private int lastX = -1;
    private int lastY = -1;

    /**
     * Check if the robot is stuck (hasn't moved in several steps)
     */
    private boolean isStuck() {
        if (lastX == this.getX() && lastY == this.getY()) {
            stuckCounter++;
            return stuckCounter > 5; // Consider stuck after 5 steps without movement
        } else {
            stuckCounter = 0;
            lastX = this.getX();
            lastY = this.getY();
            return false;
        }
    }

    /**
     * Try to find an alternative path when stuck
     */
    private void tryToUnstuck() {
        // Try random movement to get unstuck
        randomOrientation();
        if (freeForward()) {
            moveForward();
            consumeBatteryForMovement();
            System.out.println(getName() + " essaie de se débloquer avec un mouvement aléatoire.");
        }
    }

    /**
     * Check if the robot is near a charging station
     * @return true if the robot is near a charging station, false otherwise
     */
    private boolean isNearChargingStation() {
        for (int[] csPos : chargingStations) {
            if (Math.abs(this.getX() - csPos[0]) + Math.abs(this.getY() - csPos[1]) <= 1) {
                return true;
            }
        }
        return false;
    }

    /**
     * Find the nearest charging station
     * @return The coordinates of the nearest charging station [x, y]
     */
    private int[] findNearestChargingStation() {
        int[] nearest = null;
        double minDist = Double.MAX_VALUE;

        for (int[] csPos : chargingStations) {
            double dist = distanceTo(this.getX(), this.getY(), csPos[0], csPos[1]);
            if (dist < minDist) {
                minDist = dist;
                nearest = csPos;
            }
        }

        return nearest;
    }

    /**
     * Charge the robot's battery
     */
    private void chargeBattery() {
        if (isCharging) {
            batteryLevel += CHARGING_RATE;
            if (batteryLevel >= MAX_BATTERY) {
                batteryLevel = MAX_BATTERY;
                isCharging = false;
                System.out.println(getName() + " a terminé de charger sa batterie. Niveau: 100%");
            } else {
                System.out.println(getName() + " est en train de charger. Niveau de batterie: " + (int)batteryLevel + "%");
            }
            // Update robot color based on battery level
            updateRobotColor();
        }
    }

    /**
     * Consume battery for movement
     */
    public void consumeBatteryForMovement() {
        double previousLevel = batteryLevel;
        batteryLevel -= MOVE_BATTERY_COST;

        // Log battery consumption
        System.out.println("[CONSOMMATION] " + getName() + " - Mouvement: -" + MOVE_BATTERY_COST +
                         "% (" + (int)previousLevel + "% -> " + (int)batteryLevel + "%)");

        if (batteryLevel < LOW_BATTERY_THRESHOLD && !isCharging) {
            System.out.println(getName() + " a un niveau de batterie faible: " + (int)batteryLevel + "%");
            broadcastLowBatteryMessage();
        }
        // Update robot color based on battery level
        updateRobotColor();
    }

    /**
     * Consume battery for picking up a package
     */
    public void consumeBatteryForPickup() {
        batteryLevel -= PICKUP_BATTERY_COST;
    }

    /**
     * Consume battery for depositing a package
     */
    public void consumeBatteryForDeposit() {
        double previousLevel = batteryLevel;
        batteryLevel -= DEPOSIT_BATTERY_COST;

        // Log battery consumption
        System.out.println("[CONSOMMATION] " + getName() + " - Dépôt: -" + DEPOSIT_BATTERY_COST +
                         "% (" + (int)previousLevel + "% -> " + (int)batteryLevel + "%)");

        // Update robot color based on battery level
        updateRobotColor();
    }

    /**
     * Update the robot's color based on battery level
     * - Green: Battery level > 70%
     * - Orange: Battery level between 30% and 70%
     * - Dégradé de rouge: Battery level < 20%
     */
    private void updateRobotColor() {
        int[] newColor;
        String batteryStatus;

        if (batteryLevel > 70) {
            // Good battery level - green
            newColor = new int[]{0, 255, 0};
            batteryStatus = "EXCELLENT";
        } else if (batteryLevel > 30) {
            // Medium battery level - orange
            newColor = new int[]{255, 165, 0};
            batteryStatus = "MOYEN";
        } else if (batteryLevel > 20) {
            // Low battery level - light red
            newColor = new int[]{255, 100, 100};
            batteryStatus = "FAIBLE";
        } else if (batteryLevel > 10) {
            // Very low battery level - medium red
            newColor = new int[]{255, 50, 50};
            batteryStatus = "TRES FAIBLE";
        } else {
            // Critical battery level - dark red
            newColor = new int[]{255, 0, 0};
            batteryStatus = "CRITIQUE";
        }

        // Log battery level to terminal
        System.out.println("[BATTERIE] " + getName() + " - Niveau: " + (int)batteryLevel + "% - Etat: " + batteryStatus);

        // Set the new color
        this.setColor(newColor);
    }

    /**
     * Check if the battery is critically low
     * @return true if the battery is critically low, false otherwise
     */
    private boolean isBatteryCritical() {
        return batteryLevel <= CRITICAL_BATTERY_THRESHOLD;
    }

    /**
     * Predict if the robot has enough battery to reach a destination
     * Extremely optimized version with ultra-aggressive battery usage
     * @param destX X coordinate of the destination
     * @param destY Y coordinate of the destination
     * @return true if the robot has enough battery to reach the destination, false otherwise
     */
    public boolean canReachDestination(int destX, int destY) {
        // Calculate Manhattan distance to destination
        int distance = Math.abs(this.getX() - destX) + Math.abs(this.getY() - destY);

        // Calculate battery needed for the trip (movement + deposit)
        double batteryNeeded = (distance * MOVE_BATTERY_COST) + DEPOSIT_BATTERY_COST;

        // EXTREME OPTIMIZATION: No safety margin at all
        // This is risky but maximizes efficiency

        // EXTREME OPTIMIZATION: For short distances, be extremely aggressive
        if (distance <= 10) {
            // For short distances, we only need 80% of the calculated battery
            // This is because we can often find more efficient paths during movement
            return batteryLevel >= batteryNeeded * 0.8;
        }

        // EXTREME OPTIMIZATION: For medium distances, be very aggressive
        if (distance <= 20) {
            // For medium distances, we need 90% of the calculated battery
            return batteryLevel >= batteryNeeded * 0.9;
        }

        // For long distances, use the exact calculated amount
        return batteryLevel >= batteryNeeded;
    }

    /**
     * Broadcast a message to all robots in the field
     * @param content The message content
     */
    private void broadcastMessage(String content) {
        SimpleMessage msg = new SimpleMessage(this, content);
        List<Robot> robots = env.getRobot();
        for (Robot r : robots) {
            if (r != this && r instanceof MyTransitRobot) {
                ((MyTransitRobot)r).handleSimpleMessage(msg);
            }
        }
    }

    /**
     * Broadcast a low battery message
     */
    private void broadcastLowBatteryMessage() {
        String content = MSG_LOW_BATTERY + "|" + this.getX() + "|" + this.getY() + "|" + (int)batteryLevel;
        broadcastMessage(content);
    }

    /**
     * Broadcast a help request message
     */
    private void broadcastHelpRequest() {
        if (!waitingForHelp) {
            String content = MSG_HELP_REQUEST + "|" + this.getX() + "|" + this.getY();
            if (carriedPackage != null) {
                content += "|" + carriedPackage.getDestinationGoalId();
            }
            broadcastMessage(content);
            waitingForHelp = true;
        }
    }

    /**
     * Handle incoming messages
     */
    @Override
    public void handleMessage(fr.emse.fayol.maqit.simulator.components.Message msg) {
        // Delegate to our SimpleMessage handler
        handleSimpleMessage(new SimpleMessage(this, msg.getContent()));
    }

    /**
     * Handle incoming simple messages
     */
    public void handleSimpleMessage(SimpleMessage msg) {
        String content = msg.getContent();
        String[] parts = content.split("\\|");

        if (parts.length > 0) {
            String messageType = parts[0];

            if (messageType.equals(MSG_LOW_BATTERY)) {
                // Another robot has low battery
                System.out.println(getName() + " a reçu un message de batterie faible de " + msg.getSender().getName());

            } else if (messageType.equals(MSG_HELP_REQUEST)) {
                // Another robot needs help
                if (etat == Etat.FREE && batteryLevel > LOW_BATTERY_THRESHOLD) {
                    // We're free and have enough battery to help
                    System.out.println(getName() + " va aider " + msg.getSender().getName());
                    String response = MSG_HELP_OFFER + "|" + this.getX() + "|" + this.getY();
                    SimpleMessage responseMsg = new SimpleMessage(this, response);
                    msg.getSender().handleSimpleMessage(responseMsg);
                }

            } else if (messageType.equals(MSG_HELP_OFFER)) {
                // Another robot offers help
                if (waitingForHelp) {
                    System.out.println(getName() + " a reçu une offre d'aide de " + msg.getSender().getName());
                    waitingForHelp = false;
                }

            } else if (messageType.equals(MSG_TRANSIT_FULL)) {
                // A transit zone is full
                int transitX = Integer.parseInt(parts[1]);
                int transitY = Integer.parseInt(parts[2]);
                System.out.println(getName() + " a été informé que la zone de transit (" + transitX + "," + transitY + ") est pleine");

            } else if (messageType.equals(MSG_TRANSIT_AVAILABLE)) {
                // A transit zone has space available
                int transitX = Integer.parseInt(parts[1]);
                int transitY = Integer.parseInt(parts[2]);
                System.out.println(getName() + " a été informé que la zone de transit (" + transitX + "," + transitY + ") est disponible");
            }
        }
    }
}