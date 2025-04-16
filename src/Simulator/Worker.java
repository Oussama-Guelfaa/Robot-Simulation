package Simulator;

import java.awt.Color;

import fr.emse.fayol.maqit.simulator.components.ColorInteractionRobot;
import fr.emse.fayol.maqit.simulator.components.Message;
import fr.emse.fayol.maqit.simulator.components.Orientation;


/**
 * cette classe représente un travailleur mobile dans la simulation.
 * Il est considéré comme un obstacle mobile.
 */
public class Worker extends ColorInteractionRobot {


    public Worker(String name, int field, int debug, int[] pos, Color color, int rows, int columns, long seed) {
        super(name, field, debug, pos, color, rows, columns,seed);
        orientation =Orientation.up;
    }
    /**
     * le deplacement de worker - modifié pour rester fixe
     */
    @Override
    public void move(int step) {
        // Ne rien faire - les workers restent fixes
        // Cela empêche les obstacles blancs de bouger
    }

	@Override
	public void handleMessage(Message msg) {
		// TODO Auto-generated method stub

	}

}
