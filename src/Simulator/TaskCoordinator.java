package Simulator;

import java.util.*;
import fr.emse.fayol.maqit.simulator.components.Robot;
import fr.emse.fayol.maqit.simulator.components.ColorPackage;
import fr.emse.fayol.maqit.simulator.components.ColorStartZone;
import fr.emse.fayol.maqit.simulator.environment.ColorGridEnvironment;

/**
 * A decentralized task coordinator for optimizing package delivery
 * This class implements a market-based approach for task allocation
 */
public class TaskCoordinator {

    // Singleton instance
    private static TaskCoordinator instance;

    // Environment reference
    private ColorGridEnvironment environment;

    // Task allocation data structures
    private Map<String, List<Task>> zoneTaskMap = new HashMap<>();
    private Map<String, Double> robotUtilityScores = new HashMap<>();
    private Map<String, Integer> robotDeliveryCount = new HashMap<>();
    private Map<String, Double> robotEfficiencyScores = new HashMap<>();

    // Define the goals map for destination coordinates
    private static final Map<Integer, int[]> GOALS = new HashMap<>();
    static {
        GOALS.put(1, new int[]{5, 0});   // Z1
        GOALS.put(2, new int[]{15, 0});  // Z2
    }

    // Statistics for analysis
    private int totalTasksCompleted = 0;
    private long totalDeliveryTime = 0;
    private int totalBatteryUsed = 0;

    // Constants for utility calculation - extremely optimized weights
    private static final double DISTANCE_WEIGHT = 0.7;    // Dramatically increased to prioritize distance above all
    private static final double BATTERY_WEIGHT = 0.1;     // Minimized to allow for very aggressive task allocation
    private static final double EFFICIENCY_WEIGHT = 0.2;  // Reduced to focus on distance

    /**
     * Task class representing a delivery task
     */
    public static class Task {
        private String id;
        private int startX;
        private int startY;
        private int goalX;
        private int goalY;
        private int priority;
        private String assignedRobot;
        private long creationTime;
        private ColorPackage packageRef;

        public Task(String id, int startX, int startY, int goalX, int goalY, int priority, ColorPackage packageRef) {
            this.id = id;
            this.startX = startX;
            this.startY = startY;
            this.goalX = goalX;
            this.goalY = goalY;
            this.priority = priority;
            this.packageRef = packageRef;
            this.creationTime = System.currentTimeMillis();
        }

        // Getters
        public String getId() { return id; }
        public int getStartX() { return startX; }
        public int getStartY() { return startY; }
        public int getGoalX() { return goalX; }
        public int getGoalY() { return goalY; }
        public int getPriority() { return priority; }
        public String getAssignedRobot() { return assignedRobot; }
        public long getCreationTime() { return creationTime; }
        public ColorPackage getPackageRef() { return packageRef; }

        // Setters
        public void setAssignedRobot(String robotId) { this.assignedRobot = robotId; }
    }

    /**
     * Private constructor for singleton pattern
     */
    private TaskCoordinator() {
        // Initialize zone task maps for each start zone
        zoneTaskMap.put("A1", new ArrayList<>());
        zoneTaskMap.put("A2", new ArrayList<>());
        zoneTaskMap.put("A3", new ArrayList<>());
    }

    /**
     * Get the singleton instance
     */
    public static synchronized TaskCoordinator getInstance() {
        if (instance == null) {
            instance = new TaskCoordinator();
        }
        return instance;
    }

    /**
     * Set the environment reference
     */
    public void setEnvironment(ColorGridEnvironment env) {
        this.environment = env;
    }

    /**
     * Register a robot with the coordinator
     */
    public void registerRobot(MyTransitRobot robot) {
        robotUtilityScores.put(robot.getName(), 1.0);
        robotDeliveryCount.put(robot.getName(), 0);
        robotEfficiencyScores.put(robot.getName(), 1.0);
        System.out.println("[COORDINATOR] Robot " + robot.getName() + " registered with the coordinator");
    }

    /**
     * Create a new task from a package
     */
    public Task createTask(ColorPackage pack, ColorStartZone startZone, Map<Integer, int[]> goals) {
        int[] goalPos = goals.get(pack.getDestinationGoalId());
        if (goalPos == null) return null;

        String taskId = "Task-" + pack.getId();
        int priority = calculatePriority(pack);

        Task task = new Task(
            taskId,
            startZone.getX(),
            startZone.getY(),
            goalPos[0],
            goalPos[1],
            priority,
            pack
        );

        // Add task to the appropriate zone's task list
        String zoneId = pack.getStartZone();
        if (zoneTaskMap.containsKey(zoneId)) {
            zoneTaskMap.get(zoneId).add(task);
            System.out.println("[COORDINATOR] Created new task " + taskId + " at zone " + zoneId +
                              " with destination " + pack.getDestinationGoalId() + " and priority " + priority);
        }

        return task;
    }

    /**
     * Calculate priority based on package properties - optimized version
     */
    private int calculatePriority(ColorPackage pack) {
        // OPTIMIZATION: Prioritize packages based on destination and waiting time
        int priority = 1; // Base priority

        // Prioritize packages going to certain destinations
        // This helps balance the workload across destinations
        int destId = pack.getDestinationGoalId();
        if (destId == 1) {
            priority += 1; // Higher priority for destination 1
        }

        // Prioritize packages based on their ID (as a proxy for creation time)
        // Lower IDs were created earlier
        int packId = pack.getId();
        if (packId < 5) { // First few packages
            priority += 1; // Increase priority for older packages
        }

        return priority;
    }

    /**
     * Find the best task for a robot based on utility - optimized version
     */
    public Task findBestTaskForRobot(MyTransitRobot robot) {
        if (robot.getCarriedPackage() != null) return null; // Robot already has a package

        // OPTIMIZATION 1: Consider battery level before task assignment
        // Don't assign tasks to robots with very low battery
        if (robot.getBatteryLevel() < 15.0) {
            return null; // Robot should charge first
        }

        double bestUtility = -1;
        Task bestTask = null;
        String zoneWithBestTask = null;

        // OPTIMIZATION 2: Sort zones by distance to robot
        List<Map.Entry<String, List<Task>>> sortedZones = new ArrayList<>(zoneTaskMap.entrySet());
        sortedZones.sort((e1, e2) -> {
            // Get first task from each zone to get coordinates
            List<Task> tasks1 = e1.getValue();
            List<Task> tasks2 = e2.getValue();

            if (tasks1.isEmpty() || tasks2.isEmpty()) {
                return 0; // Can't compare empty lists
            }

            double dist1 = calculateDistance(robot.getX(), robot.getY(),
                                          tasks1.get(0).getStartX(), tasks1.get(0).getStartY());
            double dist2 = calculateDistance(robot.getX(), robot.getY(),
                                          tasks2.get(0).getStartX(), tasks2.get(0).getStartY());

            return Double.compare(dist1, dist2);
        });

        // OPTIMIZATION 3: Consider at most 2 closest zones to reduce computation
        int zonesToConsider = Math.min(2, sortedZones.size());
        for (int i = 0; i < zonesToConsider; i++) {
            Map.Entry<String, List<Task>> entry = sortedZones.get(i);
            String zoneId = entry.getKey();
            List<Task> tasks = entry.getValue();

            // OPTIMIZATION 4: Sort tasks by priority before evaluating
            tasks.sort((t1, t2) -> Integer.compare(t2.getPriority(), t1.getPriority()));

            for (Task task : tasks) {
                if (task.getAssignedRobot() != null) continue; // Skip already assigned tasks

                double utility = calculateUtility(robot, task);
                if (utility > bestUtility) {
                    bestUtility = utility;
                    bestTask = task;
                    zoneWithBestTask = zoneId;
                }
            }
        }

        if (bestTask != null) {
            bestTask.setAssignedRobot(robot.getName());

            // OPTIMIZATION 5: Update robot efficiency score immediately
            // This helps with load balancing in subsequent assignments
            int deliveryCount = robotDeliveryCount.getOrDefault(robot.getName(), 0) + 1;
            robotDeliveryCount.put(robot.getName(), deliveryCount);

            // OPTIMIZATION 6: Adjust efficiency score based on task difficulty
            double currentEfficiency = robotEfficiencyScores.getOrDefault(robot.getName(), 1.0);
            double taskDifficulty = calculateDistance(robot.getX(), robot.getY(),
                                                   bestTask.getStartX(), bestTask.getStartY()) / 20.0;
            double adjustedEfficiency = currentEfficiency * (1.0 - taskDifficulty * 0.1);
            robotEfficiencyScores.put(robot.getName(), Math.max(0.5, adjustedEfficiency));

            System.out.println("[COORDINATOR] Assigned task " + bestTask.getId() + " to " + robot.getName() +
                              " with utility " + bestUtility + " (efficiency: " +
                              String.format("%.2f", adjustedEfficiency) + ")");

            // Remove the task from the zone's task list
            if (zoneWithBestTask != null) {
                zoneTaskMap.get(zoneWithBestTask).remove(bestTask);
            }
        }

        return bestTask;
    }

    /**
     * Calculate utility of a robot for a task - extremely optimized version
     */
    private double calculateUtility(MyTransitRobot robot, Task task) {
        // EXTREME OPTIMIZATION 1: Much stronger distance preference
        // This makes closer robots overwhelmingly more likely to take nearby tasks
        double distance = calculateDistance(robot.getX(), robot.getY(), task.getStartX(), task.getStartY());

        // Use a much steeper exponential decay function
        double distanceUtility = Math.exp(-0.2 * distance); // Twice as steep as before

        // EXTREME OPTIMIZATION 2: Minimal battery consideration
        // Only care about battery if it's critically low
        double batteryLevel = robot.getBatteryLevel() / 100.0; // Normalize to 0-1 range
        double batteryUtility = (batteryLevel > 0.2) ? 1.0 : 0.0; // Binary decision - either has enough or doesn't

        // EXTREME OPTIMIZATION 3: Stronger load balancing
        double efficiencyScore = robotEfficiencyScores.getOrDefault(robot.getName(), 1.0);
        int deliveryCount = robotDeliveryCount.getOrDefault(robot.getName(), 0);

        // Much stronger bonus for robots with fewer deliveries
        double deliveryBonus = Math.max(0, 0.5 - (deliveryCount * 0.05));

        // EXTREME OPTIMIZATION 4: Higher priority impact
        double priorityBonus = task.getPriority() * 0.2; // Double the priority bonus

        // EXTREME OPTIMIZATION 5: Consider destination distance
        // Prefer tasks with closer destinations to minimize total travel
        // Use goalX and goalY directly from the task
        double destDistance = calculateDistance(task.getStartX(), task.getStartY(), task.getGoalX(), task.getGoalY());
        double destUtility = Math.exp(-0.05 * destDistance);
        distanceUtility = (distanceUtility + destUtility) / 2.0; // Average of start and destination utilities

        // Combined utility with weights and bonuses
        return (DISTANCE_WEIGHT * distanceUtility) +
               (BATTERY_WEIGHT * batteryUtility) +
               (EFFICIENCY_WEIGHT * efficiencyScore) +
               deliveryBonus + priorityBonus;
    }

    /**
     * Calculate Manhattan distance between two points
     */
    private double calculateDistance(int x1, int y1, int x2, int y2) {
        return Math.abs(x1 - x2) + Math.abs(y1 - y2);
    }

    /**
     * Update robot efficiency score after task completion
     */
    public void updateRobotEfficiency(MyTransitRobot robot, long deliveryTime, double batteryUsed) {
        String robotId = robot.getName();

        // Update delivery count
        int deliveryCount = robotDeliveryCount.getOrDefault(robotId, 0) + 1;
        robotDeliveryCount.put(robotId, deliveryCount);

        // Update efficiency score based on delivery time and battery usage
        double timeScore = 1.0 / (1.0 + (deliveryTime / 1000.0)); // Convert ms to seconds
        double batteryScore = 1.0 - (batteryUsed / 100.0); // Normalize to 0-1 range

        // Combined efficiency score
        double newEfficiency = (0.6 * timeScore) + (0.4 * batteryScore);

        // Update with exponential moving average
        double oldEfficiency = robotEfficiencyScores.getOrDefault(robotId, 1.0);
        double updatedEfficiency = (0.7 * newEfficiency) + (0.3 * oldEfficiency);

        robotEfficiencyScores.put(robotId, updatedEfficiency);

        // Update statistics
        totalTasksCompleted++;
        totalDeliveryTime += deliveryTime;
        totalBatteryUsed += batteryUsed;

        System.out.println("[COORDINATOR] Updated efficiency for " + robotId + ": " + updatedEfficiency +
                          " (deliveries: " + deliveryCount + ")");
    }

    /**
     * Check if a transit zone should be used based on current system state
     * Extremely optimized version to minimize total steps
     */
    public boolean shouldUseTransitZone(MyTransitRobot robot, int destX, int destY, int transitX, int transitY) {
        // Calculate direct distance
        double directDistance = calculateDistance(robot.getX(), robot.getY(), destX, destY);

        // Calculate distance via transit
        double distanceToTransit = calculateDistance(robot.getX(), robot.getY(), transitX, transitY);
        double distanceFromTransitToDest = calculateDistance(transitX, transitY, destX, destY);
        double transitDistance = distanceToTransit + distanceFromTransitToDest;

        // EXTREME OPTIMIZATION 1: Almost always use transit zones for long distances
        // This creates a hub-and-spoke model that's more efficient overall
        if (directDistance > 10) { // For any significant distance
            // Only avoid transit if it's a huge detour
            if (transitDistance <= directDistance * 1.3) {
                return true;
            }
        }

        // EXTREME OPTIMIZATION 2: For short distances, be very selective about transit use
        if (directDistance <= 10) {
            // Only use transit if it's almost directly on the path
            return transitDistance <= directDistance * 1.05;
        }

        // EXTREME OPTIMIZATION 3: Battery-based decision making
        // Much more aggressive battery management
        double batteryForDirect = directDistance * 0.5 + 1.5; // Reduced deposit cost
        if (robot.getBatteryLevel() < batteryForDirect) { // No safety margin at all
            // Not enough battery for direct route, check transit
            double batteryForTransit = distanceToTransit * 0.5 + 1.5;
            if (robot.getBatteryLevel() >= batteryForTransit) {
                return true; // Use transit to save battery
            }
        }

        // EXTREME OPTIMIZATION 4: Load balancing based on system state
        int activeRobots = countActiveRobots();
        int completedTasks = totalTasksCompleted;

        // If we have many robots and few completed tasks, use transit more aggressively
        if (activeRobots > 5 && completedTasks < 10) {
            return transitDistance <= directDistance * 1.4; // Much more lenient
        }

        // Default to direct delivery
        return false;
    }

    /**
     * Count the number of active robots (not delivered)
     */
    private int countActiveRobots() {
        int count = 0;
        for (String robotId : robotEfficiencyScores.keySet()) {
            if (!robotDeliveryCount.containsKey(robotId)) {
                count++;
            }
        }
        return Math.max(count, 1); // Ensure we don't return 0
    }

    /**
     * Get statistics for analysis
     */
    public Map<String, Object> getStatistics() {
        Map<String, Object> stats = new HashMap<>();
        stats.put("totalTasksCompleted", totalTasksCompleted);
        stats.put("averageDeliveryTime", totalTasksCompleted > 0 ? totalDeliveryTime / totalTasksCompleted : 0);
        stats.put("averageBatteryUsed", totalTasksCompleted > 0 ? totalBatteryUsed / totalTasksCompleted : 0);
        stats.put("robotEfficiencyScores", new HashMap<>(robotEfficiencyScores));
        stats.put("robotDeliveryCount", new HashMap<>(robotDeliveryCount));
        return stats;
    }
}
