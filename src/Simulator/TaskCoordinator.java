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
    
    // Statistics for analysis
    private int totalTasksCompleted = 0;
    private long totalDeliveryTime = 0;
    private int totalBatteryUsed = 0;
    
    // Constants for utility calculation
    private static final double DISTANCE_WEIGHT = 0.4;
    private static final double BATTERY_WEIGHT = 0.3;
    private static final double EFFICIENCY_WEIGHT = 0.3;
    
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
     * Calculate priority based on package properties
     */
    private int calculatePriority(ColorPackage pack) {
        // Simple priority calculation - can be enhanced with more factors
        return 1; // Default priority
    }
    
    /**
     * Find the best task for a robot based on utility
     */
    public Task findBestTaskForRobot(MyTransitRobot robot) {
        if (robot.getCarriedPackage() != null) return null; // Robot already has a package
        
        double bestUtility = -1;
        Task bestTask = null;
        String zoneWithBestTask = null;
        
        // Check each zone for available tasks
        for (Map.Entry<String, List<Task>> entry : zoneTaskMap.entrySet()) {
            String zoneId = entry.getKey();
            List<Task> tasks = entry.getValue();
            
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
            System.out.println("[COORDINATOR] Assigned task " + bestTask.getId() + " to " + robot.getName() + 
                              " with utility " + bestUtility);
            
            // Remove the task from the zone's task list
            if (zoneWithBestTask != null) {
                zoneTaskMap.get(zoneWithBestTask).remove(bestTask);
            }
        }
        
        return bestTask;
    }
    
    /**
     * Calculate utility of a robot for a task
     */
    private double calculateUtility(MyTransitRobot robot, Task task) {
        // Distance component - closer is better
        double distance = calculateDistance(robot.getX(), robot.getY(), task.getStartX(), task.getStartY());
        double distanceUtility = 1.0 / (1.0 + distance); // Normalize to 0-1 range
        
        // Battery component - more battery is better
        double batteryLevel = robot.getBatteryLevel() / 100.0; // Normalize to 0-1 range
        
        // Efficiency component - based on past performance
        double efficiencyScore = robotEfficiencyScores.getOrDefault(robot.getName(), 1.0);
        
        // Combined utility with weights
        return (DISTANCE_WEIGHT * distanceUtility) + 
               (BATTERY_WEIGHT * batteryLevel) + 
               (EFFICIENCY_WEIGHT * efficiencyScore);
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
     */
    public boolean shouldUseTransitZone(MyTransitRobot robot, int destX, int destY, int transitX, int transitY) {
        // Calculate direct distance
        double directDistance = calculateDistance(robot.getX(), robot.getY(), destX, destY);
        
        // Calculate distance via transit
        double distanceToTransit = calculateDistance(robot.getX(), robot.getY(), transitX, transitY);
        double distanceFromTransitToDest = calculateDistance(transitX, transitY, destX, destY);
        double transitDistance = distanceToTransit + distanceFromTransitToDest;
        
        // Check if transit is significantly longer
        if (transitDistance > directDistance * 1.3) {
            return false;
        }
        
        // Check if robot has enough battery for direct route
        double batteryForDirect = directDistance * 0.5 + 2.0; // Movement + deposit
        if (robot.getBatteryLevel() < batteryForDirect * 1.2) { // With 20% safety margin
            // Not enough battery for direct route, check transit
            double batteryForTransit = distanceToTransit * 0.5 + 2.0; // To transit
            if (robot.getBatteryLevel() >= batteryForTransit * 1.2) {
                return true; // Use transit to save battery
            }
        }
        
        // Consider system load balancing - use transit if robot efficiency is low
        double efficiency = robotEfficiencyScores.getOrDefault(robot.getName(), 1.0);
        if (efficiency < 0.5 && transitDistance <= directDistance * 1.2) {
            return true;
        }
        
        // Default to direct delivery for high-efficiency robots
        return false;
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
