package simulator;

import fr.emse.fayol.maqit.simulator.components.ColorInteractionRobot;
import fr.emse.fayol.maqit.simulator.components.ColorPackage;
import fr.emse.fayol.maqit.simulator.components.ColorStartZone;
import fr.emse.fayol.maqit.simulator.components.ColorTransitZone;
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
import java.util.ArrayList;
import java.util.List;


public class MyRobot extends ColorInteractionRobot {

    // CONSTANTES ET DÉFINITIONS D'ÉTAT

    protected enum Etat { FREE, TRANSPORT, DELIVRE, MOVING_AWAY, ROLE_NEGOTIATION, CHARGING, GOING_TO_CHARGE }

    protected Etat etat;
    public ColorPackage carriedPackage;
    protected int destX;
	protected int destY;
    protected long tempsDepart;
    protected long tempsArrivee;
    protected ColorGridEnvironment env;
    protected int waittime; // Référence au waittime de configuration

    // Variables pour le système de batterie
    protected int batteryLevel = 100;           // Niveau de batterie (0-100%)
    protected int maxBatteryLevel = 100;        // Capacité maximale
    protected int batteryConsumptionPerMove = 1; // Consommation par mouvement
    protected int lowBatteryThreshold = 20;     // Seuil critique (20%)
    protected int minBatteryForTask = 60;       // Seuil minimum pour prendre un colis (60%)
    protected boolean isCharging = false;       // En cours de charge
    protected int chargingRate = 5;             // Vitesse de charge par step
    protected String reservedChargingStation = null; // Station réservée
    protected int lastBatteryDisplayLevel = 100; // Dernier niveau affiché

    // Variables pour la communication décentralisée
    protected static int totalRobots = 0;
    protected static List<String> transitPackageNotifications = new ArrayList<>();

    // Variables pour la négociation des rôles
    protected int assignedRole = -1; // -1 = pas encore assigné, 0 = rôle start→transit, 1 = rôle transit→goal
    protected boolean roleNegotiationComplete = false;
    protected int negotiationStep = 0;
    protected List<String> receivedRoleMessages = new ArrayList<>();
    protected int knownRole0Count = 0;
    protected int knownTotalRobots = 0;
    protected boolean hasRequestedRole = false;
    protected long negotiationStartTime = 0;

    /**
     *  definir la liste des goals (destination)
     */
    protected static final Map<Integer, int[]> GOALS = new HashMap<>();
    static {
        GOALS.put(1, new int[]{5, 0});   // Z1
        GOALS.put(2, new int[]{15, 0});  // Z2
    }

    // Zones de départ et de transit
    int[][] startZones = { {6, 19}, {9, 19}, {12, 19} };
    int[][] transitZones = { {12, 10}, {12, 9}, {9, 10}, {9, 9} };

    // CONSTRUCTEUR ET INITIALISATION

    public MyRobot(String name, int field, int debug, int[] pos, Color color, int rows, int columns, ColorGridEnvironment env, long seed, int waittime) {
        super(name, field, debug, pos, color, rows, columns,seed);
        this.env = env;
        this.waittime = waittime; // Stocker le waittime pour les calculs de timeout
        this.etat = Etat.ROLE_NEGOTIATION; // Commencer par la négociation des rôles
        this.carriedPackage = null;
        totalRobots++;
        this.negotiationStartTime = System.currentTimeMillis();
        System.out.println(getName() + " créé - en attente d'attribution de rôle (Total robots: " + totalRobots + ")");

        // Annoncer sa présence aux autres robots
        broadcastMessage("ROBOT_ANNOUNCE:" + getName());
    }

    // MÉTHODES DE COMMUNICATION
    // Maintenant que les robots sont initialisés, ils doivent communiquer entre eux pour se coordonner...

    /**
     * Envoyer un message à tous les autres robots via l'environnement
     */
    private void broadcastMessage(String messageContent) {
        // Obtenir tous les robots de l'environnement et leur envoyer le message
        if (env != null && env.getRobot() != null) {
            for (fr.emse.fayol.maqit.simulator.components.Robot robot : env.getRobot()) {
                if (robot instanceof MyRobot && !robot.getName().equals(getName())) {
                    // Simuler l'envoi de message en appelant directement handleMessage
                    ((MyRobot) robot).receiveMessage(messageContent);
                }
            }
        }
        System.out.println(getName() + " diffuse: " + messageContent);
    }

    /**
     * Recevoir un message d'un autre robot (simulation de communication)
     */
    private void receiveMessage(String messageContent) {
        receivedRoleMessages.add(messageContent);
        System.out.println(getName() + " reçoit: " + messageContent);
    }

    @Override
    public void handleMessage(Message msg) {
        // Traiter le message reçu via le framework
        String content = msg.getContent();
        receiveMessage(content);
        System.out.println(getName() + " a reçu un message via handleMessage: " + content);
    }

    /**
     * Notifier les autres robots qu'un colis a été déposé en transit
     */
    private void notifyPackageInTransit(String packageId, int transitZoneX, int transitZoneY) {
        String notification = "PACKAGE_IN_TRANSIT:" + packageId + ":" + transitZoneX + ":" + transitZoneY + ":" + getName();
        broadcastMessage(notification);
        System.out.println(getName() + " notifie: colis " + packageId + " déposé en transit (" + transitZoneX + "," + transitZoneY + ")");
    }

    // MÉTHODES DE NÉGOCIATION DES RÔLES
    // Maintenant que les robots peuvent communiquer, ils doivent négocier leurs rôles

    /**
     * Négociation des rôles basée sur les messages
     * Utilise l'ordre lexicographique des noms pour éviter les conflits
     */
    private void negotiateRole() {
        // Traiter les messages reçus pour mettre à jour les connaissances
        processReceivedMessages();

        int targetRole0Count = totalRobots / 2;

        switch (negotiationStep) {
            case 0:
                // Étape 1: Attendre pour recevoir les annonces des autres robots
                // Attendre 4 fois le waittime pour permettre la communication
                if (System.currentTimeMillis() - negotiationStartTime > waittime * 4) {
                    knownTotalRobots = totalRobots; // Utiliser le total connu
                    negotiationStep++;
                }
                break;

            case 1:
                // Étape 2: Demander le rôle 0 basé sur l'ordre lexicographique
                if (!hasRequestedRole) {
                    // Compter combien de robots avec un nom "plus petit" devraient prendre le rôle 0
                    int robotsBeforeMe = 0;
                    for (int i = 0; i < totalRobots; i++) {
                        String otherRobotName = "Robot" + i;
                        if (otherRobotName.compareTo(getName()) < 0) {
                            robotsBeforeMe++;
                        }
                    }

                    // Si je suis parmi les premiers robots (ordre lexicographique), je prends le rôle 0
                    if (robotsBeforeMe < targetRole0Count) {
                        assignedRole = 0;
                        knownRole0Count++;
                        broadcastMessage("ROLE_ASSIGNED:0:" + getName() + ":count:" + knownRole0Count);
                        System.out.println(getName() + " s'attribue le rôle 0 (start→transit) - Position: " + robotsBeforeMe + "/" + targetRole0Count);
                    } else {
                        assignedRole = 1;
                        broadcastMessage("ROLE_ASSIGNED:1:" + getName());
                        System.out.println(getName() + " prend le rôle 1 (transit→goal) - Position: " + robotsBeforeMe + " >= " + targetRole0Count);
                    }
                    hasRequestedRole = true;
                    negotiationStep++;
                }
                break;

            case 2:
                // Étape 3: Attendre un peu puis terminer la négociation
                // Timeout après 9 fois le waittime pour finaliser la négociation
                if (System.currentTimeMillis() - negotiationStartTime > waittime * 9) {
                    roleNegotiationComplete = true;
                    if (assignedRole == 0) {
                        etat = Etat.FREE; // Robot rôle 0 commence à travailler
                        System.out.println(getName() + " commence le travail en tant que rôle 0");
                    } else {
                        etat = Etat.FREE; // Robot rôle 1 commence à travailler
                        System.out.println(getName() + " commence le travail en tant que rôle 1");
                    }
                }
                break;
        }
    }

    /**
     * Traiter les messages reçus pour la négociation des rôles et la communication
     */
    private void processReceivedMessages() {
        List<String> messagesToKeep = new ArrayList<>();

        for (String message : receivedRoleMessages) {
            String[] parts = message.split(":");
            if (parts.length >= 2) {
                String messageType = parts[0];

                switch (messageType) {
                    case "ROBOT_ANNOUNCE":
                        // Un nouveau robot s'annonce
                        if (parts.length >= 2) {
                            knownTotalRobots = Math.max(knownTotalRobots, totalRobots);
                        }
                        break;

                    case "ROLE_ASSIGNED":
                        // Un robot s'est assigné un rôle
                        if (parts.length >= 3 && parts[1].equals("0")) {
                            // Quelqu'un a pris le rôle 0
                            if (parts.length >= 4 && parts[2].startsWith("count")) {
                                try {
                                    int count = Integer.parseInt(parts[3]);
                                    knownRole0Count = Math.max(knownRole0Count, count);
                                } catch (NumberFormatException e) {
                                    // Ignorer si le parsing échoue
                                }
                            }
                        }
                        break;

                    case "PACKAGE_IN_TRANSIT":
                        // Un paquet a été déposé en transit - garder ce message pour les robots rôle 1
                        if (assignedRole == 1 || assignedRole == -1) {
                            messagesToKeep.add(message);
                        }
                        break;
                }
            }
        }

        // Remplacer la liste par les messages à garder (notifications de paquets)
        receivedRoleMessages.clear();
        receivedRoleMessages.addAll(messagesToKeep);
    }

    /**
     * Vérifier si la négociation des rôles est terminée pour ce robot
     */
    public boolean isRoleNegotiationComplete() {
        return roleNegotiationComplete;
    }

    // MÉTHODES DE COMPORTEMENT PRINCIPAL
    // Maintenant que les rôles sont négociés, les robots peuvent exécuter leur logique de comportement principal...

    /**
     *  la logique de deplacement selon le rôle attribué
     */
    public void step() {
        if (etat == Etat.ROLE_NEGOTIATION) {
            negotiateRole();
            return;
        }

        // PRIORITÉ 1: Gestion de la batterie
        if (etat == Etat.CHARGING) {
            handleChargingLogic();
            return;
        }

        if (etat == Etat.GOING_TO_CHARGE) {
            goToChargingStation();
            return;
        }

        // Vérifier si la batterie est critique et abandonner la tâche actuelle
        if (needsCharging() && etat != Etat.CHARGING && etat != Etat.GOING_TO_CHARGE) {
            // Abandonner la tâche actuelle
            if (carriedPackage != null) {
                System.out.println(getName() + " - Batterie critique! Colis perdu!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!");
                // Remettre le paquet à sa place si possible
                carriedPackage = null;
            }
            etat = Etat.GOING_TO_CHARGE;
            System.out.println(getName() + " - Batterie critique (" + batteryLevel + "%), recherche d'une station de chargement");
            return;
        }

        // Exécuter la logique selon le rôle attribué
        if (assignedRole == 0) {
            stepRole0(); // Logique rôle 0 (start → transit)
        } else if (assignedRole == 1) {
            stepRole1(); // Logique rôle 1 (transit → goal)
        }
    }

    @Override
    public void move(int step) {
        step();
    }

    /**
     * Logique pour les robots rôle 0 (start → transit)
     */
    private void stepRole0() {
        if (etat == Etat.MOVING_AWAY) {
            // S'éloigner de la zone de transit après dépôt et se diriger vers les zones de départ
            moveAwayFromTransitZones(true, 8.0, true);
            return;
        }

        if (etat == Etat.FREE) {
            // Vérifier d'abord si on a assez de batterie pour prendre un colis
            if (!hasSufficientBatteryForTask()) {
                System.out.println(getName() + " (rôle 0) - Batterie insuffisante (" + batteryLevel + "%), va se charger");
                etat = Etat.GOING_TO_CHARGE;
                return;
            }

            // Vérifier d'abord si on est déjà adjacent à une zone de départ
            ColorStartZone adjacentStartZone = findAdjacentStartZone();
            if (adjacentStartZone != null) {
                // On est adjacent à une zone de départ
                if (!adjacentStartZone.getPackages().isEmpty()) {
                    // Il y a un paquet, le prendre
                    carriedPackage = adjacentStartZone.getPackages().get(0);
                    adjacentStartZone.removePackage(carriedPackage);
                    tempsDepart = System.currentTimeMillis();

                    // Chercher une zone de transit libre
                    ColorTransitZone transitZone = findAvailableTransitZone();
                    if (transitZone != null) {
                        destX = transitZone.getX();
                        destY = transitZone.getY();
                        etat = Etat.TRANSPORT;
                        System.out.println(getName() + " (rôle 0) a pris un paquet de " + carriedPackage.getStartZone() + " vers transit (" + destX + "," + destY + ") - Batterie: " + batteryLevel + "%");
                    }
                } else {
                    // Zone de départ vide, s'éloigner un peu pour éviter l'encombrement
                    // puis chercher d'autres zones avec des paquets
                    ColorStartZone zoneWithPackage = findStartZoneWithPackage();
                    if (zoneWithPackage != null) {
                        // Il y a des paquets ailleurs, aller les chercher
                        moveOneStepTo(zoneWithPackage.getX(), zoneWithPackage.getY());
                        return;
                    } else {
                        // Pas de paquets disponibles, s'éloigner un peu de cette zone pour éviter l'encombrement
                        moveAwayFromStartZones();
                        System.out.println(getName() + " (rôle 0) s'éloigne temporairement de la zone de départ vide");
                        return;
                    }
                }
            } else {
                // Pas adjacent à une zone de départ, chercher une zone avec des paquets
                ColorStartZone zone = findStartZoneWithPackage();
                if (zone == null) {
                    // Pas de paquets disponibles, se diriger vers la zone de départ la plus proche pour s'y positionner
                    ColorStartZone closestStartZone = findClosestStartZone();
                    if (closestStartZone != null) {
                        moveOneStepTo(closestStartZone.getX(), closestStartZone.getY());
                    }
                    return;
                } else {
                    // Se diriger vers la zone avec des paquets
                    moveOneStepTo(zone.getX(), zone.getY());
                }
            }
        } else if (etat == Etat.TRANSPORT) {
            if (isAdjacentTo(destX, destY)) {
                // Déposer le colis en zone de transit
                Cell c = env.getGrid()[destX][destY];
                if (c instanceof ColorCell && c.getContent() instanceof ColorTransitZone) {
                    ColorTransitZone transitZone = (ColorTransitZone) c.getContent();
                    if (!transitZone.isFull()) {
                        transitZone.addPackage(carriedPackage);
                        notifyPackageInTransit(carriedPackage.getStartZone() + "_" + carriedPackage.getDestinationGoalId(), destX, destY);
                        carriedPackage = null;
                        etat = Etat.MOVING_AWAY;
                        System.out.println(getName() + " (rôle 0) a déposé un colis en transit (" + destX + "," + destY + ")");
                    }
                }
            } else {
                moveOneStepTo(destX, destY);
            }
        }
    }

    /**
     * Logique pour les robots rôle 1 (transit → goal)
     */
    private void stepRole1() {
        if (etat == Etat.MOVING_AWAY) {
            // S'éloigner de la zone de goal après livraison
            moveAwayFromGoalZones();
            return;
        }

        if (etat == Etat.FREE) {
            // Vérifier d'abord si on a assez de batterie pour prendre un colis
            if (!hasSufficientBatteryForTask()) {
                System.out.println(getName() + " (rôle 1) - Batterie insuffisante (" + batteryLevel + "%), va se charger");
                etat = Etat.GOING_TO_CHARGE;
                return;
            }

            ColorTransitZone zone = findTransitZoneWithPackage();
            if (zone == null) {
                // Pas de paquets en transit, s'éloigner des zones de transit pour éviter l'encombrement
                moveAwayFromTransitZones(false, 4.0, false);
                return;
            }

            if (isAdjacentTo(zone.getX(), zone.getY())) {
                if (!zone.getPackages().isEmpty()) {
                    carriedPackage = zone.getPackages().get(0);
                    zone.removePackage(carriedPackage);
                    tempsDepart = System.currentTimeMillis();

                    // Aller vers le goal final
                    int[] goalPos = GOALS.get(carriedPackage.getDestinationGoalId());
                    if (goalPos != null) {
                        destX = goalPos[0];
                        destY = goalPos[1];
                        etat = Etat.TRANSPORT;
                        System.out.println(getName() + " (rôle 1) a pris un paquet du transit vers goal " + carriedPackage.getDestinationGoalId() + " - Batterie: " + batteryLevel + "%");
                    }
                }
            } else {
                moveOneStepTo(zone.getX(), zone.getY());
            }
        } else if (etat == Etat.TRANSPORT) {
            if ((this.getX() == destX) && (this.getY() == destY)) {
                // Livrer le colis au goal
                carriedPackage.setState(PackageState.ARRIVED);
                MySimFactory.deliveredCount++;
                System.out.println(getName() + " (rôle 1) a livré un colis au goal " + carriedPackage.getDestinationGoalId() + " - Total livré: " + MySimFactory.deliveredCount);
                carriedPackage = null;
                etat = Etat.MOVING_AWAY;
            } else {
                moveOneStepTo(destX, destY);
            }
        }
    }

    // MÉTHODES DE RECHERCHE DE ZONES ET UTILITAIRES
    // Maintenant que les robots ont leurs comportements principaux, ils ont besoin de méthodes utilitaires pour trouver les zones et vérifier les conditions...

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
     * Trouver une zone de transit libre pour déposer un colis
     * @return
     */
    protected ColorTransitZone findAvailableTransitZone() {
        for (int[] pos : transitZones) {
            Cell c = env.getGrid()[pos[0]][pos[1]];
            if (c instanceof ColorCell && c.getContent() instanceof ColorTransitZone) {
                ColorTransitZone zone = (ColorTransitZone) c.getContent();
                if (!zone.isFull()) {
                    return zone;
                }
            }
        }
        return null;
    }

    /**
     * Trouver une zone de transit avec des colis (pour les robots rôle 1)
     * @return
     */
    protected ColorTransitZone findTransitZoneWithPackage() {
        for (int[] pos : transitZones) {
            Cell c = env.getGrid()[pos[0]][pos[1]];
            if (c instanceof ColorCell && c.getContent() instanceof ColorTransitZone) {
                ColorTransitZone zone = (ColorTransitZone) c.getContent();
                if (!zone.getPackages().isEmpty()) {
                    return zone;
                }
            }
        }
        return null;
    }

    /**
     * Trouve une zone de départ adjacente au robot (qu'elle soit vide ou non)
     * @return la zone de départ adjacente ou null si aucune
     */
    private ColorStartZone findAdjacentStartZone() {
        for (int[] pos : startZones) {
            if (isAdjacentTo(pos[0], pos[1])) {
                Cell c = env.getGrid()[pos[0]][pos[1]];
                if (c instanceof ColorCell && c.getContent() instanceof ColorStartZone) {
                    return (ColorStartZone) c.getContent();
                }
            }
        }
        return null;
    }

    /**
     * Trouve la zone de départ la moins encombrée (avec le moins de robots autour)
     * @return la zone de départ la moins encombrée
     */
    private ColorStartZone findClosestStartZone() {
        ColorStartZone best = null;
        double bestScore = Double.MAX_VALUE;

        for (int[] pos : startZones) {
            Cell c = env.getGrid()[pos[0]][pos[1]];
            if (c instanceof ColorCell && c.getContent() instanceof ColorStartZone) {
                // Calculer le nombre de robots dans un rayon de 3 cases autour de cette zone
                int robotsNearby = 0;
                for (fr.emse.fayol.maqit.simulator.components.Robot robot : env.getRobot()) {
                    if (!robot.getName().equals(getName())) {
                        double dist = distanceTo(robot.getX(), robot.getY(), pos[0], pos[1]);
                        if (dist <= 3.0) {
                            robotsNearby++;
                        }
                    }
                }

                // Calculer la distance à cette zone
                double distance = distanceTo(this.getX(), this.getY(), pos[0], pos[1]);

                // Score combiné : privilégier les zones moins encombrées et plus proches
                double score = distance + (robotsNearby * 5.0); // Pénaliser l'encombrement

                if (score < bestScore) {
                    bestScore = score;
                    best = (ColorStartZone) c.getContent();
                }
            }
        }
        return best;
    }

    // MÉTHODES DE MOUVEMENT ET POSITIONNEMENT
    // Maintenant que les robots peuvent trouver les zones, ils ont besoin de méthodes pour se déplacer et se positionner...

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
        if (bestMove != null && isCellFree(bestMove.getX(), bestMove.getY())) {
            if (bestMove.getX() == this.getX() - 1) setCurrentOrientation(Orientation.up);
            if (bestMove.getX() == this.getX() + 1) setCurrentOrientation(Orientation.down);
            if (bestMove.getY() == this.getY() - 1) setCurrentOrientation(Orientation.left);
            if (bestMove.getY() == this.getY() + 1) setCurrentOrientation(Orientation.right);

            moveForward();
        }
    }

    /**
     * Override de moveForward pour consommer la batterie à chaque mouvement réel
     */
    @Override
    public boolean moveForward() {
        boolean result = super.moveForward();
        if (result) {
            consumeBattery();
        }
        return result;
    }

    // MÉTHODES D'AIDE ET UTILITAIRES
    // Enfin, les robots ont besoin de méthodes utilitaires de base pour les calculs et vérifications...

    /**
     *  methode pour savoir si une cellule est libre
     * @param x
     * @param y
     * @return
     */
    protected boolean isCellFree(int x, int y) {
        if (x < 0 || x >= 20 || y < 0 || y >= 20) return false;

        Cell c = env.getGrid()[x][y];
        if (c == null) return true;

        // Vérifier que ce n'est pas une zone fixe (transit, start, goal)
        if (c instanceof ColorCell) {
            ColorCell colorCell = (ColorCell) c;

            // Interdire les cellules avec des zones de transit
            if (colorCell.getContent() instanceof ColorTransitZone) return false;

            // Interdire les cellules avec des zones de départ
            if (colorCell.getContent() instanceof ColorStartZone) return false;

            // Interdire les cellules avec des goals
            if (colorCell.getGoal() != null) return false;

            // Interdire les cellules avec d'autres robots
            if (colorCell.getContent() instanceof MyRobot) return false;
        }

        return c.getContent() == null;
    }

    /**
     * methode pour calculer la distance euclidienne
     * @param x1
     * @param y1
     * @param x2
     * @param y2
     * @return
     */
    protected double distanceTo(int x1, int y1, int x2, int y2) {
        return Math.sqrt(Math.pow(x2 - x1, 2) + Math.pow(y2 - y1, 2));
    }

    /**
     * S'éloigner des zones de transit avec différents comportements selon le contexte
     * @param targetStartZones Si true, se diriger vers les zones de départ (pour rôle 0)
     * @param minDistance Distance minimale à maintenir (4.0 pour évitement simple, 8.0 pour retour au travail)
     * @param changeStateWhenFar Si true, change l'état à FREE quand assez loin
     */
    private void moveAwayFromTransitZones(boolean targetStartZones, double minDistance, boolean changeStateWhenFar) {
        // Vérifier si on est trop proche des zones de transit
        double minDistToTransit = Double.MAX_VALUE;
        for (int[] transitPos : transitZones) {
            double dist = distanceTo(this.getX(), this.getY(), transitPos[0], transitPos[1]);
            minDistToTransit = Math.min(minDistToTransit, dist);
        }

        // Si on est à moins de la distance minimale, s'éloigner
        if (minDistToTransit < minDistance) {
            String successMessage = null;
            int[][] targetZones = null;

            if (targetStartZones) {
                targetZones = startZones;
                successMessage = getName() + " (rôle 0) s'est éloigné vers les zones de départ et retourne au travail";
            }

            moveAwayFromZones(transitZones, minDistance, targetZones, changeStateWhenFar, successMessage);
        } else if (changeStateWhenFar) {
            // Déjà assez loin, retourner au travail
            etat = Etat.FREE;
            if (targetStartZones) {
                System.out.println(getName() + " (rôle 0) s'est éloigné vers les zones de départ et retourne au travail");
            }
        }
    }

    /**
     * Méthode générique pour s'éloigner de certaines zones et optionnellement se rapprocher d'autres zones
     * @param avoidZones Zones à éviter (ex: transitZones, goalZones)
     * @param minDistance Distance minimale à maintenir des zones à éviter
     * @param targetZones Zones cibles à se rapprocher (optionnel, peut être null)
     * @param changeStateWhenFar Si true, change l'état à FREE quand assez loin
     * @param successMessage Message à afficher quand le robot est assez loin
     */
    private void moveAwayFromZones(int[][] avoidZones, double minDistance, int[][] targetZones,
                                  boolean changeStateWhenFar, String successMessage) {
        // Vérifier si on est déjà assez loin des zones à éviter
        double minDistToAvoid = Double.MAX_VALUE;
        for (int[] zonePos : avoidZones) {
            double dist = distanceTo(this.getX(), this.getY(), zonePos[0], zonePos[1]);
            minDistToAvoid = Math.min(minDistToAvoid, dist);
        }

        // Si on est assez loin, retourner à FREE
        if (minDistToAvoid >= minDistance) {
            if (changeStateWhenFar) {
                etat = Etat.FREE;
                if (successMessage != null) {
                    System.out.println(successMessage);
                }
            }
            return;
        }

        // Chercher la meilleure position
        int bestX = this.getX();
        int bestY = this.getY();
        double bestScore = -Double.MAX_VALUE;
        int searchRadius = targetZones != null ? 3 : 2;

        for (int x = Math.max(0, this.getX() - searchRadius); x <= Math.min(19, this.getX() + searchRadius); x++) {
            for (int y = Math.max(0, this.getY() - searchRadius); y <= Math.min(19, this.getY() + searchRadius); y++) {
                if (!isCellFree(x, y)) continue;
                if (x == this.getX() && y == this.getY()) continue; // Ne pas rester sur place

                // Calculer la distance minimale aux zones à éviter
                double minDistToAvoidFromPos = Double.MAX_VALUE;
                for (int[] zonePos : avoidZones) {
                    double dist = distanceTo(x, y, zonePos[0], zonePos[1]);
                    minDistToAvoidFromPos = Math.min(minDistToAvoidFromPos, dist);
                }

                // Si on a des zones cibles, privilégier les positions proches de ces zones
                double score = minDistToAvoidFromPos; // Plus on est loin des zones à éviter, mieux c'est

                if (targetZones != null) {
                    // Vérifier que cette position respecte la distance minimale des zones à éviter
                    if (minDistToAvoidFromPos < minDistance) continue;

                    // Calculer la distance aux zones cibles (plus proche = mieux)
                    double minDistToTarget = Double.MAX_VALUE;
                    for (int[] targetPos : targetZones) {
                        double dist = distanceTo(x, y, targetPos[0], targetPos[1]);
                        minDistToTarget = Math.min(minDistToTarget, dist);
                    }
                    score = minDistToAvoidFromPos - minDistToTarget; // Équilibrer éloignement et rapprochement
                }

                if (score > bestScore) {
                    bestScore = score;
                    bestX = x;
                    bestY = y;
                }
            }
        }

        // Se déplacer vers la meilleure position trouvée
        if ((bestX != this.getX() || bestY != this.getY()) && isCellFree(bestX, bestY)) {
            moveOneStepTo(bestX, bestY);
        } else if (changeStateWhenFar) {
            etat = Etat.FREE; // Si aucun mouvement possible, retourner au travail
        }
    }



    /**
     * S'éloigner des zones de départ pour éviter l'encombrement
     */
    private void moveAwayFromStartZones() {
        moveAwayFromZones(startZones, 2.0, null, false, null);
    }

    /**
     * S'éloigner des zones de goal après livraison
     */
    private void moveAwayFromGoalZones() {
        int[][] goalArray = GOALS.values().toArray(new int[0][]);
        moveAwayFromZones(goalArray, 2.0, null, true,
                         getName() + " (rôle 1) s'est éloigné et retourne au travail");
    }

    // MÉTHODES DE GESTION DE LA BATTERIE

    /**
     * Consomme de la batterie à chaque mouvement
     */
    protected void consumeBattery() {
        if (batteryLevel > 0) {
            batteryLevel -= batteryConsumptionPerMove;
            if (batteryLevel < 0) {
                batteryLevel = 0;
            }

            // Afficher le niveau de batterie tous les 10% et quand critique
            int currentDisplayLevel = (batteryLevel / 10) * 10;
            if (currentDisplayLevel != lastBatteryDisplayLevel || batteryLevel <= lowBatteryThreshold) {
                System.out.println(getName() + " - Niveau de batterie: " + batteryLevel + "%");
                lastBatteryDisplayLevel = currentDisplayLevel;
            }
        }
    }

    /**
     * Vérifie si la batterie est critique (≤ 20%)
     */
    protected boolean needsCharging() {
        return batteryLevel <= lowBatteryThreshold;
    }

    /**
     * Vérifie si la batterie est suffisante pour prendre un nouveau colis (≥ 60%)
     */
    protected boolean hasSufficientBatteryForTask() {
        return batteryLevel >= minBatteryForTask;
    }

    /**
     * Gère la logique de chargement
     */
    protected void handleChargingLogic() {
        if (isCharging) {
            // Charger la batterie
            batteryLevel += chargingRate;
            if (batteryLevel >= maxBatteryLevel) {
                batteryLevel = maxBatteryLevel;
                isCharging = false;
                etat = Etat.FREE;
                reservedChargingStation = null;
                System.out.println(getName() + " - Chargement terminé, retour au travail (100%)");
            } else {
                System.out.println(getName() + " - En charge: " + batteryLevel + "%");
            }
        }
    }

    /**
     * Se dirige vers une station de chargement
     */
    protected void goToChargingStation() {
        if (reservedChargingStation == null) {
            String nearestStation = findNearestChargingStation();
            if (nearestStation != null) {
                reservedChargingStation = nearestStation;
                System.out.println(getName() + " - Se dirige vers la station: " + nearestStation);
            } else {
                System.out.println(getName() + " - Aucune station de chargement disponible!");
                return;
            }
        }

        // Obtenir la position de la station réservée
        int[] stationPos = getChargingStationPosition(reservedChargingStation);
        if (stationPos != null) {
            if (isAdjacentTo(stationPos[0], stationPos[1])) {
                // Adjacent à la station, commencer le chargement
                etat = Etat.CHARGING;
                isCharging = true;
                System.out.println(getName() + " - Arrivé à la station, début du chargement");
            } else {
                // Se diriger vers la station
                moveOneStepTo(stationPos[0], stationPos[1]);
            }
        }
    }

    /**
     * Trouve la station de chargement la plus proche
     */
    protected String findNearestChargingStation() {
        String nearestStation = null;
        double minDistance = Double.MAX_VALUE;

        // Positions des stations de chargement (définies dans environment.ini)
        Map<String, int[]> chargingStations = new HashMap<>();
        chargingStations.put("station1", new int[]{2, 2});
        chargingStations.put("station2", new int[]{17, 2});
        chargingStations.put("station3", new int[]{2, 17});
        chargingStations.put("station4", new int[]{17, 17});

        for (Map.Entry<String, int[]> entry : chargingStations.entrySet()) {
            String stationName = entry.getKey();
            int[] pos = entry.getValue();

            if (isChargingStationFree(stationName)) {
                double distance = distanceTo(this.getX(), this.getY(), pos[0], pos[1]);
                if (distance < minDistance) {
                    minDistance = distance;
                    nearestStation = stationName;
                }
            }
        }

        return nearestStation;
    }

    /**
     * Trouve une station de chargement adjacente
     */
    protected String findAdjacentChargingStation() {
        Map<String, int[]> chargingStations = new HashMap<>();
        chargingStations.put("station1", new int[]{2, 2});
        chargingStations.put("station2", new int[]{17, 2});
        chargingStations.put("station3", new int[]{2, 17});
        chargingStations.put("station4", new int[]{17, 17});

        for (Map.Entry<String, int[]> entry : chargingStations.entrySet()) {
            String stationName = entry.getKey();
            int[] pos = entry.getValue();

            if (isAdjacentTo(pos[0], pos[1])) {
                return stationName;
            }
        }
        return null;
    }

    /**
     * Vérifie si une station de chargement est libre
     */
    protected boolean isChargingStationFree(String stationName) {
        int[] stationPos = getChargingStationPosition(stationName);
        if (stationPos == null) return false;

        // Vérifier si un autre robot est déjà adjacent à cette station
        for (fr.emse.fayol.maqit.simulator.components.Robot robot : env.getRobot()) {
            if (robot != this && robot instanceof MyRobot) {
                MyRobot otherRobot = (MyRobot) robot;
                if (stationName.equals(otherRobot.reservedChargingStation)) {
                    return false;
                }
            }
        }
        return true;
    }

    /**
     * Obtient la position d'une station de chargement
     */
    protected int[] getChargingStationPosition(String stationName) {
        Map<String, int[]> chargingStations = new HashMap<>();
        chargingStations.put("station1", new int[]{2, 2});
        chargingStations.put("station2", new int[]{17, 2});
        chargingStations.put("station3", new int[]{2, 17});
        chargingStations.put("station4", new int[]{17, 17});

        return chargingStations.get(stationName);
    }

}
