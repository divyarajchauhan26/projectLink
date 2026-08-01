package CampusConnect.ui;

import CampusConnect.domain.Person;

import javax.swing.*;
import java.util.List;

/**
 * Controller for stepping through algorithm visualizations.
 */
public class AlgorithmVisualizer {

    private final NetworkCanvas canvas;
    private final JTextArea displayArea;
    private List<List<Person>> steps;
    private int currentStep = 0;
    private Timer timer;

    public AlgorithmVisualizer(NetworkCanvas canvas, JTextArea displayArea) {
        this.canvas = canvas;
        this.displayArea = displayArea;
        
        this.timer = new Timer(500, e -> nextStep());
    }

    public void startVisualization(List<List<Person>> steps, String algorithmName) {
        this.steps = steps;
        this.currentStep = 0;
        
        displayArea.setText("=== " + algorithmName + " Visualization ===\n");
        displayArea.append("Total steps: " + steps.size() + "\n\n");
        
        if (steps.isEmpty()) {
            displayArea.append("No steps to visualize.\n");
            return;
        }
        
        timer.start();
    }

    public void stopVisualization() {
        if (timer != null && timer.isRunning()) {
            timer.stop();
        }
        canvas.setVisualizationStep(null);
    }

    private void nextStep() {
        if (steps == null || currentStep >= steps.size()) {
            stopVisualization();
            displayArea.append("\nVisualization complete.\n");
            return;
        }

        List<Person> stepNodes = steps.get(currentStep);
        canvas.setVisualizationStep(stepNodes);
        
        displayArea.append("Step " + (currentStep + 1) + ": Visited " + stepNodes.size() + " nodes\n");
        // Auto scroll to bottom
        displayArea.setCaretPosition(displayArea.getDocument().getLength());
        
        currentStep++;
    }
}
