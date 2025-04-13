package Simulator;

import fr.emse.fayol.maqit.simulator.configuration.IniFile;


import fr.emse.fayol.maqit.simulator.configuration.SimProperties;
import java.awt.Color;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

import fr.emse.fayol.maqit.simulator.components.ColorExitZone;
import fr.emse.fayol.maqit.simulator.components.ColorObstacle;
import fr.emse.fayol.maqit.simulator.components.ColorPackage;
import fr.emse.fayol.maqit.simulator.components.ColorStartZone;
import fr.emse.fayol.maqit.simulator.components.ColorTransitZone;
import fr.emse.fayol.maqit.simulator.components.Robot;
import fr.emse.fayol.maqit.simulator.environment.Cell;
import fr.emse.fayol.maqit.simulator.environment.ColorCell;
import fr.emse.fayol.maqit.simulator.environment.ColorGoal;
import fr.emse.fayol.maqit.simulator.environment.ColorGridEnvironment;
/**
 * Cette classe permet de realiser la simulation
 */
public class MySimFactory extends SimFactory {

	private Map<String, ColorStartZone> startZonesMap = new HashMap<>();

	public static int deliveredCount = 0;// compteur pour calculer le nb de pas effectuées
	int nbPackages;
	int nbNotGeneratedPackets;
	int numberOfWorkers;
	Random rnd;
	int totalSteps= 0;


    public MySimFactory(SimProperties sp) {
        super(sp);
    }

    /**
     *  Créer l'environnement
     */
    @Override
    public void createEnvironment() {
        environment = new ColorGridEnvironment(sp.rows, sp.columns, sp.debug, sp.seed);
        environment.initializeGrid();
    }

    /**
     *  creer les obstacles
     */
    @Override
    public void createObstacle() {
        // Pour chaque position dans (environement.ini), on crée un obstacle coloré
        for (int[] pos : sp.obstaclePositions) {
            ColorObstacle obstacle = new ColorObstacle(pos, new int[]{
                sp.colorobstacle.getRed(),
                sp.colorobstacle.getGreen(),
                sp.colorobstacle.getBlue()
            });
            addNewComponent(obstacle);
        }
    }

    /**
     *  creer les zones d'arrivés sous forme d'un goal
     */
    @Override
    public void createGoal() {
        int[] z1Pos = sp.goalPositions.get(1);
        int[] z2Pos = sp.goalPositions.get(2);
        // Z1
        ((ColorCell) environment.getGrid()[z1Pos[0]][z1Pos[1]])
            .setGoal(new ColorGoal(
                1,// id de Z1
                new int[]{
                    sp.colorgoal.getRed(),
                    sp.colorgoal.getGreen(),
                    sp.colorgoal.getBlue()
                }
            ));
        //Z2
        ((ColorCell) environment.getGrid()[z2Pos[0]][z2Pos[1]])
            .setGoal(new ColorGoal(
                2,// id de Z2
                new int[]{
                    sp.colorgoal.getRed(),
                    sp.colorgoal.getGreen(),
                    sp.colorgoal.getBlue()
                }
            ));
    }


   /**
    * methodes pour creer les paquets
    */

    public void createPackages(int nbpackages) {
    	// Definir les zones de depart
        String[] startZones = { "A1", "A2", "A3" };


        for (int i = 0; i < nbpackages; i++) {
            int destinationId = rnd.nextInt(2)+1;
            int ts = 0; // temps de depart

            int randomStartZone = rnd.nextInt(startZones.length);
            String zone = startZones[randomStartZone];

            // les paquets ne seront pas physiquement dessinés, on leur attribue une position virtuelle
            int[] position = { -1, -1 };

            ColorPackage pack = new ColorPackage(
                position,
                new int[]{
                    sp.colorpackage.getRed(),
                    sp.colorpackage.getGreen(),
                    sp.colorpackage.getBlue()
                },
                destinationId,
                ts,
                zone
            );

            ColorStartZone startZone = getStartZoneById(zone);
            if (startZone != null) {
                startZone.addPackage(pack);
            } else {
                System.out.println("La zone de départ " + zone + " n'existe pas !");
            }
        }
    }

    /**
     *  Methode pour recupérer une zone de départ par son identifiant
     * @param id
     * @return
     */
    public ColorStartZone getStartZoneById(String id) {
        return startZonesMap.get(id);
    }

    /**
     * Creer les zones de depart
     */
    public void createStartZones() {
        for (Map.Entry<String, int[]> entry : sp.startZonePositions.entrySet()) {
            String zoneId = entry.getKey();
            int[] pos = entry.getValue();
            ColorStartZone zone = new ColorStartZone(
                pos,
                new int[]{
                    sp.colorstartzone.getRed(),
                    sp.colorstartzone.getGreen(),
                    sp.colorstartzone.getBlue()
                }
            );
            addNewComponent(zone);
            startZonesMap.put(zoneId, zone);
        }
    }

    /**
     * les zones de transit
     */
    public void createTransitZones() {
    	// dans environment.ini les zones de transit sont sous forme de des listes (x,y,capacité)
        for (int[] data : sp.transitZoneData) {
            int x = data[0];
            int y = data[1];
            int capacity = data[2];
            ColorTransitZone tz = new ColorTransitZone(
                new int[]{x, y},
                new int[]{
                    sp.colortransitzone.getRed(),
                    sp.colortransitzone.getGreen(),
                    sp.colortransitzone.getBlue()
                },
                capacity
            );
            addNewComponent(tz);
        }
    }

    /**
     * Creer les portes (en rouge)
     */
    public void createExitZones() {
        for (int[] pos : sp.exitZonePositions) {
            ColorExitZone exitZone = new ColorExitZone(
                pos,
                new int[]{
                    sp.colorexit.getRed(),
                    sp.colorexit.getGreen(),
                    sp.colorexit.getBlue()
                }
            );
            addNewComponent(exitZone);
        }
    }


    /**
     *  creer les employés (en jaune)
     */
    public void createWorker() {
        for (int i = 0; i < numberOfWorkers; i++) {
            int[] pos = environment.getPlace();
            Worker worker = new Worker(
                "Worker" + i,
                sp.field,
                sp.debug,
                pos,
                new Color(sp.colorother.getRed(), sp.colorother.getGreen(), sp.colorother.getBlue()),
                sp.rows,
                sp.columns,
                sp.seed
            );
            addNewComponent(worker);
        }
    }

    /**
     * Pour creer un robot normal utilise MyRobot
     * pour un robot transit utilise MyTransitRobot
     */
    @Override
    public void createRobot() {
        for (int i = 0; i < sp.nbrobot; i++) {
            int[] pos = environment.getPlace();
            MyRobot robot =  new MyTransitRobot(
            		//new MyRobot(
                "Robot" + i, sp.field, sp.debug, pos,
                new Color(sp.colorrobot.getRed(), sp.colorrobot.getGreen(), sp.colorrobot.getBlue()),
                sp.rows, sp.columns, (ColorGridEnvironment) environment, sp.seed
            );
            addNewComponent(robot);
        }
    }

    /**
     * Methode pour faire fonctionner le robot
     */
    @Override
    public void schedule() {
        List<Robot> robots = environment.getRobot();



        int currentNBPacket;
        for (int i = 0; i < sp.step; i++) {
        	totalSteps++;

        // packet creation
        if (nbNotGeneratedPackets > 0 && validGeneration()) {
        	if (nbNotGeneratedPackets > 2)
        		currentNBPacket = rnd.nextInt(nbNotGeneratedPackets/2+1);
        	else
        		currentNBPacket = 2;
        	 createPackages(currentNBPacket);
        	 nbNotGeneratedPackets -= currentNBPacket;
        }

        // activation des robots
        // Create a new list to store robots that have not delivered
        List<Robot> activeRobots = new ArrayList<>();

        for (Robot r : robots) {
            // Skip robots that have delivered their packages
            if (r instanceof MyTransitRobot && ((MyTransitRobot) r).hasDelivered()) {
                continue;
            }

            activeRobots.add(r);

            int[] prevPos = r.getLocation();
            Cell[][] perception = environment.getNeighbor(r.getX(), r.getY(), r.getField());
            r.updatePerception(perception);

            if (r instanceof MyTransitRobot) {
                ((MyTransitRobot) r).step();
            } else if (r instanceof MyRobot) {
                ((MyRobot)r).step();
            } else {
                r.move(1);
            }

            updateEnvironment(prevPos, r.getLocation());
        }

        // Replace the robots list with the active robots list
        robots = activeRobots;

            refreshGW();

            if (MySimFactory.deliveredCount >= nbPackages) {
                System.out.println("Tous les paquets sont livrés en " + totalSteps + " étapes.");
                break;
            }

            try {
                Thread.sleep(sp.waittime);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
    }


    private boolean validGeneration() {
		if (totalSteps % 10 == 0)
			return true;
		return false;
	}

	/**
     * le main principale
     * @param args
     * @throws Exception
     */
    public static void main(String[] args) throws Exception {
        // Charger le fichier principal et le fichier d'environnement
        IniFile ifile = new IniFile("parameters/configuration.ini");
        IniFile ifilenv = new IniFile("parameters/environment.ini");

        // instance pour les paramètres généraux (proptest.ini)
        SimProperties sp = new SimProperties(ifile);
        sp.simulationParams();
        sp.displayParams();

        // instance pour les paramètres d'environnement
        SimProperties envProp = new SimProperties(ifilenv);
        envProp.loadObstaclePositions();
        envProp.loadStartZonePositions();
        envProp.loadTransitZones();
        envProp.loadExitZonePositions();
        envProp.loadGoalPositions();


        sp.obstaclePositions = envProp.obstaclePositions;
        sp.startZonePositions = envProp.startZonePositions;
        sp.transitZoneData = envProp.transitZoneData;
        sp.exitZonePositions = envProp.exitZonePositions;
        sp.goalPositions = envProp.goalPositions;

        System.out.println("Environment size: " + sp.rows + "x" + sp.columns);

        MySimFactory sim = new MySimFactory(sp);

        // modifier
        sim.nbPackages = sp.nbrobot;
        sim.nbNotGeneratedPackets = sim.nbPackages;
        sim.numberOfWorkers = sp.nbobstacle / 2;
        sim.rnd = new Random(sp.seed);

        sim.createEnvironment();
        sim.createObstacle();
        sim.createGoal();
        sim.createStartZones();
        sim.createTransitZones();
        sim.createExitZones();
        sim.createWorker();
        sim.createRobot();

        sim.initializeGW();
        sim.schedule();
    }

}