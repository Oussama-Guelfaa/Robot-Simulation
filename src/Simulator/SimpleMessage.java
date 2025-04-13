package Simulator;

/**
 * A simple message class for robot communication
 */
public class SimpleMessage {
    private MyTransitRobot sender;
    private String content;
    
    /**
     * Create a new message
     * @param sender The robot sending the message
     * @param content The message content
     */
    public SimpleMessage(MyTransitRobot sender, String content) {
        this.sender = sender;
        this.content = content;
    }
    
    /**
     * Get the sender of the message
     * @return The robot that sent the message
     */
    public MyTransitRobot getSender() {
        return sender;
    }
    
    /**
     * Get the content of the message
     * @return The message content
     */
    public String getContent() {
        return content;
    }
}
