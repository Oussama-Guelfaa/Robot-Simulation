package Simulator;

import fr.emse.fayol.maqit.simulator.components.ColorInteractionRobot;
import fr.emse.fayol.maqit.simulator.components.ColorPackage;
import fr.emse.fayol.maqit.simulator.components.ColorStartZone;
import fr.emse.fayol.maqit.simulator.components.Message;
import fr.emse.fayol.maqit.simulator.components.Orientation;
import fr.emse.fayol.maqit.simulator.components.PackageState;
import fr.emse.fayol.maqit.simulator.environment.Cell;
import fr.emse.fayol.maqit.simulator.environment.ColorCell;
import fr.emse.fayol.maqit.simulator.environment.ColorGridEnvironment;
import fr.emse.fayol.maqit.simulator.environment.Location;

import java.awt.Color;
import java.util.HashMap;
import java.util.Map;


public class MyRobot extends ColorInteractionRobot {

    public enum Etat { FREE, TRANSPORT, DELIVRE }

    protected Etat etat;
    public ColorPackage carriedPackage;
    protected int destX;
	protected int destY;
    protected long tempsDepart;
    protected long tempsArrivee;
    protected ColorGridEnvironment env;

    /**
     *  definir la liste des goals (destination)
     */
    protected static final Map<Integer, int[]> GOALS = new HashMap<>();
    static {
        GOALS.put(1, new int[]{5, 0});   // Z1
        GOALS.put(2, new int[]{15, 0});  // Z2

    }
    //
    int[][] startZones = { {6, 19}, {9, 19}, {12, 19} };

    public MyRobot(String name, int field, int debug, int[] pos, Color color, int rows, int columns, ColorGridEnvironment env, long seed) {
        super(name, field, debug, pos, color, rows, columns,seed);
        this.env = env;
        this.etat = Etat.FREE;
        this.carriedPackage = null;
        randomOrientation();
    }


    /**
     *methodes pour trouver une zone de depart non vide
     * @return
     */
    protected ColorStartZone findStartZoneWithPackage() {
        for (int[] pos : startZones) {
            Cell c = env.getGrid()[pos[0]][pos[1]];
            if (c instanceof ColorCell && c.getContent() instanceof ColorStartZone) {
                ColorStartZone zone = (ColorStartZone) c.getContent();
                if (!zone.getPackages().isEmpty()) {
                    return zone;
                }
            }
        }
        return null;
    }

    /**
     * methode qui montre si lo robot touche une cellule  ou pas
     * @param row
     * @param col
     * @return
     */
    protected boolean isAdjacentTo(int row, int col) {
        return Math.abs(this.getX() - row) + Math.abs(this.getY() - col) == 1;
    }


    /**
     *  methode pour savoir si une cellule est libre
     * @param x
     * @param y
     * @return
     */
    private boolean isCellFree(int x, int y) {
        Cell c = env.getGrid()[x][y];
        return c == null || c.getContent() == null;
    }

    /**
     * methode pour calculer la distance euclidienne
     * @param x1
     * @param y1
     * @param x2
     * @param y2
     * @return
     */
    public double distanceTo(int x1, int y1, int x2, int y2) {
        return Math.sqrt(Math.pow(x2 - x1, 2) + Math.pow(y2 - y1, 2));
    }

    /**
     *  methode pour savoir si le robot est toujours active ou il a disparu
     * @return
     */
    public boolean isActive() {
        return etat != Etat.DELIVRE;
    }

    /**
     *  methode pour faire avancer le robot un pas vers une destinantion
     * @param targetX
     * @param targetY
     */
    protected void moveOneStepTo(int targetX, int targetY) {
        HashMap<String, Location> directions = getNextCoordinate();
        Location bestMove = null;
        double minDist = Double.MAX_VALUE;
        // chercher la meilleure position
        for (Map.Entry<String, Location> entry : directions.entrySet()) {
            Location loc = entry.getValue();
            if (loc.getX() < 0 || loc.getX() >= rows || loc.getY() < 0 || loc.getY() >= columns) continue;

            if (!isCellFree(loc.getX(), loc.getY())) continue;

            double dist = distanceTo(loc.getX(), loc.getY(), targetX, targetY);
            if (dist < minDist) {
                minDist = dist;
                bestMove = loc;
            }
        }
        // s'orienter vers la meilleure position
        if (bestMove != null) {
            if (bestMove.getX() == this.getX() - 1) setCurrentOrientation(Orientation.up);
            if (bestMove.getX() == this.getX() + 1) setCurrentOrientation(Orientation.down);
            if (bestMove.getY() == this.getY() - 1) setCurrentOrientation(Orientation.left);
            if (bestMove.getY() == this.getY() + 1) setCurrentOrientation(Orientation.right);

            moveForward();
            // If this is a MyTransitRobot, consume battery for movement
            if (this instanceof MyTransitRobot) {
                ((MyTransitRobot)this).consumeBatteryForMovement();
            }
        }
    }


    /**
     *  la logique de deplacemnet de robot
     */
    public void step() {
        if (etat == Etat.DELIVRE) return;

        if (etat == Etat.FREE) {
            ColorStartZone zone = findStartZoneWithPackage();
            if (zone == null) return;

            if (isAdjacentTo(zone.getX(), zone.getY())) {
                if (!zone.getPackages().isEmpty()) {
                    carriedPackage = zone.getPackages().get(0);// recupere le 1er paquet
                    zone.removePackage(carriedPackage);// supprimer le paquet apres le recuperer
                    tempsDepart = System.currentTimeMillis();
                    int[] goalPos = GOALS.get(carriedPackage.getDestinationGoalId());
                    if (goalPos != null) {
                        destX = goalPos[0];
                        destY = goalPos[1];
                        etat = Etat.TRANSPORT;
                    }

                    System.out.println(getName() + " a pris un paquet de " + carriedPackage.getStartZone() + " pour la destination "+ carriedPackage.getDestinationGoalId());

                }
            } else {
                moveOneStepTo(zone.getX(), zone.getY());
            }
        } else if (etat == Etat.TRANSPORT) {
            if ((this.getX() == destX) &(this.getY()== destY)) {
                carriedPackage.setState(PackageState.ARRIVED);

                MySimFactory.deliveredCount++;// incrementer le compteur pour savoir le nb de pas

                tempsArrivee = System.currentTimeMillis();
                long dureeLivraison = tempsArrivee - tempsDepart;
                etat = Etat.DELIVRE;
                env.removeCellContent(this.getX(), this.getY());// faire disparaitre le robot
            } else {
                moveOneStepTo(destX, destY);
            }
        }
    }

    @Override
    public void handleMessage(Message msg) {
        System.out.println(getName() + " a reçu un message: " + msg.getContent());
    }

    @Override
    public void move(int step) {
        step();
    }
}
