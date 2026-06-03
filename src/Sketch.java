import processing.core.PApplet;
import processing.event.KeyEvent;
import processing.event.MouseEvent;

/**
 * Sand Physics Like Simulation
 * @author directconnections
 */
public class Sketch extends PApplet {
    public int brushRadius = 10;
    public boolean pauseRender = false;
    public byte[][] canvas;  // x then y

    public static void main(String[] args) {
        PApplet.main("Sketch");
    }

    @Override
    public void settings() {
        size(600, 400);

        canvas = new byte[60][40];
    }

    @Override
    public void setup() {
        noStroke();
        fill(255);
    }

    @Override
    public void draw() {
        background(0);
        if (pauseRender) {
            textAlign(LEFT, TOP);
            text("fps: " + frameRate, 0, 0);
            return;
        }

        background(0);
        scale(10);

        for (int x = 0; x < canvas.length; x++) {
            byte[] yAxis = canvas[x];

            for (int y = 0; y < yAxis.length; y++) {
                if (yAxis[y] == 1) {
                    fill(255);
                } else {
                    fill(0);
                }

                rect(x, y, 10, 10);
            }
        }

        fill(255);

        resetMatrix();

        textAlign(LEFT, TOP);
        text("fps: " + frameRate, 0, 0);
    }

    @Override
    public void mouseDragged(MouseEvent event) {
        // System.out.println(event.getX() + " " + event.getY());
        canvas[event.getX() / 10][event.getY() / 10] = 1;
    }

    @Override
    public void keyPressed(KeyEvent event) {
        pauseRender = !pauseRender;
    }

    /** Additional helper methods below */

}
