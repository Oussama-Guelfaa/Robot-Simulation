package Simulator;

import fr.emse.fayol.maqit.simulator.components.ColorPackage;
import fr.emse.fayol.maqit.simulator.components.ColorStartZone;
import fr.emse.fayol.maqit.simulator.components.ColorTransitZone;
import fr.emse.fayol.maqit.simulator.components.ComponentType;
import fr.emse.fayol.maqit.simulator.components.Message;
import fr.emse.fayol.maqit.simulator.components.PackageState;
import fr.emse.fayol.maqit.simulator.components.Orientation;
import fr.emse.fayol.maqit.simulator.environment.Cell;
import fr.emse.fayol.maqit.simulator.environment.ColorCell;
import fr.emse.fayol.maqit.simulator.environment.ColorGridEnvironment;
import fr.emse.fayol.maqit.simulator.environment.Location;

import java.awt.Color;
import java.util.HashMap;
import java.util.List;
import java.util.Map;


/**
 * MyTransitRobot:
 * Similar to MyRobot but enhanced to:
 *  - Potentially pick up packages from a transit zone
 *  - Possibly drop a carried package in a transit zone if beneficial
 */
public class MyTransitRobot extends MyRobot {

    // We'll reuse the same enum from MyRobot: { FREE, TRANSPORT, DELIVRE }
    // The logic is extended to handle transit zones, but states remain the same.

    // Positions of transit zones. 
    // In your environment.ini you have lines like:
    //   zone1 = 12,10,1
    //   zone2 = 12,9,1
    //   zone3 = 9,10,1
    //   zone4 = 9,9,1
    // We'll just store the x,y coords for the logic here:
    private int[][] transitZones = {
        {12, 10},
        {12, 9},
        {9, 10},
        {9, 9}
    };

    public MyTransitRobot(String name, int field, int debug, int[] pos, Color color,
                          int rows, int columns, ColorGridEnvironment env, long seed) {
        super(name, field, debug, pos, color, rows, columns, env, seed);
        // MyRobot sets etat = FREE initially
    }

    /**
     * Find a transit zone that is NOT full.
     * Returns the first zone found that can accept a new package.
     */
    private ColorTransitZone findTransitZoneNotFull() {
        for (int[] tzPos : transitZones) {
            Cell c = env.getGrid()[tzPos[0]][tzPos[1]];
            if (c instanceof ColorCell && ((ColorCell)c).getContent() instanceof ColorTransitZone) {
                ColorTransitZone tz = (ColorTransitZone) ((ColorCell)c).getContent();
                if (!tz.isFull()) {
                    return tz;
                }
            }
        }
        return null;
    }

    /**
     * Find a transit zone that has at least one package waiting.
     */
    private ColorTransitZone findTransitZoneWithPackage() {
        for (int[] tzPos : transitZones) {
            Cell c = env.getGrid()[tzPos[0]][tzPos[1]];
            if (c instanceof ColorCell && ((ColorCell)c).getContent() instanceof ColorTransitZone) {
                ColorTransitZone tz = (ColorTransitZone) ((ColorCell)c).getContent();
                List<ColorPackage> packages = tz.getPackages();
                if (packages != null && !packages.isEmpty()) {
                    return tz;
                }
            }
        }
        return null;
    }

    /**
     * Compare direct distance to final goal vs. going via a transit zone.
     * Return true if it's beneficial to drop at the transit zone, false if direct is better.
     * 
     * For simplicity, we do a naive comparison:
     *   distance(robot->transitZone) + distance(transitZone->goal) 
     *   vs. 
     *   distance(robot->goal)
     *
     * If the transit route is shorter, we say it's "better to use transit."
     * In a real scenario, you might consider more advanced metrics (battery, traffic, etc.).
     */
    private boolean isBetterToUseTransit(int destX, int destY, ColorTransitZone tz) {
        double directDist = distanceTo(getX(), getY(), destX, destY);
        double toTransitDist = distanceTo(getX(), getY(), tz.getX(), tz.getY());
        double tzToGoalDist = distanceTo(tz.getX(), tz.getY(), destX, destY);
        return (toTransitDist + tzToGoalDist < directDist);
    }

    /**
     * Overridden step() to incorporate intermediate zone logic.
     */
   
    @Override
    public void step() {
    	
        // If robot is already delivered, do nothing
        if (etat == Etat.DELIVRE) return;

        // If robot is transporting a package (etat=TRANSPORT)...
        if (etat == Etat.TRANSPORT && carriedPackage != null) {
            // Check if we've arrived at the final goal
            if (this.getX() == destX && this.getY() == destY) {
                // Mark the package as arrived
                carriedPackage.setState(PackageState.ARRIVED);
                MySimFactory.deliveredCount++;
                tempsArrivee = System.currentTimeMillis();
                etat = Etat.DELIVRE;
                env.removeCellContent(this.getX(), this.getY()); 
                System.out.println(getName() + " a livré le paquet " 
                    + carriedPackage.getId() + " à destination.");
            } else {
                // Possibly check if we still want to drop at a transit zone
                // E.g., if we find a not-full zone and it's beneficial
                ColorTransitZone tz = findTransitZoneNotFull();
                if (tz != null && isBetterToUseTransit(destX, destY, tz)) {
                    // Move to the transit zone, drop the package, become FREE
                    moveOneStepTo(tz.getX(), tz.getY());
                    // If we are adjacent, we can drop
                    if (isAdjacentTo(tz.getX(), tz.getY())) {
                        tz.addPackage(carriedPackage);
                        System.out.println(getName() + " a déposé le paquet " 
                            + carriedPackage.getId() + " dans la zone de transit.");
                        carriedPackage = null;
                        etat = Etat.FREE;
                    }
                } else {
                    // Move one step to the final destination
                    moveOneStepTo(destX, destY);
                }
            }
            return;
        }

        // If the robot is FREE (no package):
        if (etat == Etat.FREE) {
            // 1) Try picking up from a transit zone that already has a package
            ColorTransitZone tzWithPackage = findTransitZoneWithPackage();
            if (tzWithPackage != null) {
                // Move or pick from that transit zone
                int tx = tzWithPackage.getX();
                int ty = tzWithPackage.getY();
                if (isAdjacentTo(tx, ty)) {
                    // Remove first available package from the transit zone
                    List<ColorPackage> pcks = tzWithPackage.getPackages();
                    if (!pcks.isEmpty()) {
                        ColorPackage pkg = (ColorPackage) pcks.get(0);
                        tzWithPackage.removePackage(pkg);
                        carriedPackage = pkg;
                        // Determine final goal from the package
                        int[] goalPos = GOALS.get(pkg.getDestinationGoalId());
                        if (goalPos != null) {
                            destX = goalPos[0];
                            destY = goalPos[1];
                        }
                        etat = Etat.TRANSPORT;
                        tempsDepart = System.currentTimeMillis();
                        System.out.println(getName() 
                            + " a récupéré un paquet depuis une zone de transit pour la destination " 
                            + pkg.getDestinationGoalId());
                    }
                } else {
                    // Move closer to that zone
                    moveOneStepTo(tx, ty);
                }
                return;
            }

            // 2) If no transit zone has a package, pick up from a start zone
            ColorStartZone startZone = findStartZoneWithPackage();
            if (startZone == null) {
                // No start zone has a package => do nothing or roam
                return;
            }

            // If we are adjacent to that start zone, pick up the first package
            if (isAdjacentTo(startZone.getX(), startZone.getY())) {
                if (!startZone.getPackages().isEmpty()) {
                    carriedPackage = startZone.getPackages().get(0);
                    startZone.removePackage(carriedPackage);
                    tempsDepart = System.currentTimeMillis();
                    int[] goalPos = GOALS.get(carriedPackage.getDestinationGoalId());
                    if (goalPos != null) {
                        destX = goalPos[0];
                        destY = goalPos[1];
                        etat = Etat.TRANSPORT;
                    }
                    System.out.println(getName() + " a pris un paquet de " 
                        + carriedPackage.getStartZone() + " pour la destination " 
                        + carriedPackage.getDestinationGoalId());
                }
            } else {
                // Move one step closer to that start zone
                moveOneStepTo(startZone.getX(), startZone.getY());
            }
        }
    }

    // We reuse the rest of MyRobot's methods (moveOneStepTo, isAdjacentTo, distanceTo, etc.)

    @Override
    public void handleMessage(Message msg) {
        System.out.println(getName() + " a reçu un message: " + msg.getContent());
    }
    
    @Override
    public void move(int step) {
        step();
    }
}