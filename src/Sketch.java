import processing.core.PApplet;
import processing.event.MouseEvent;

/**
 * Sand Physics Like Simulation
 * @author directconnections
 */
public class Sketch extends PApplet {
    public int brushRadius = 10;
    public short[][] canvas;  // x then y

    public int canvasWidth = 60;
    public int canvasHeight = 40;
    public int sandSize = 5;

    public static void main(String[] args) {
        PApplet.main("Sketch");
    }

    @Override
    public void settings() {
        size(canvasWidth * sandSize, canvasHeight * sandSize);
        canvas = new short[canvasWidth][canvasHeight];
    }

    @Override
    public void setup() {
        fill(255);
    }

    @Override
    public void draw() {
        background(0);

        
        // render
        noStroke();
        for (int x = 0; x < canvasWidth; x++) {
            for (int y = 0; y < canvasHeight; y++) {
                fill((int)canvas[x][y]);
                square(x * sandSize, y * sandSize, sandSize);
            }
        }

        // update
        for (int x = canvasWidth - 1; x >= 0; x--) {
            for (int y = canvasHeight - 2; y >= 0; y--) {  // bottom particles do not need to keep falling
                if (canvas[x][y] != 0 && canvas[x][y + 1] == 0) {
                    canvas[x][y + 1] = canvas[x][y];
                    canvas[x][y] = 0;
                }
            }
        } 

        
        // fps
        fill(255);
        textAlign(LEFT, TOP);
        text(String.format("fps %d", (int)frameRate), 0, 0);
    }

    @Override
    public void mouseDragged(MouseEvent event) {
        // System.out.println(event.getX() + " " + event.getY());
        int x = Math.clamp(event.getX() / sandSize, 0, canvasWidth - 1);
        int y = Math.clamp(event.getY() / sandSize, 0, canvasHeight - 1);

        canvas[x][y] = 255;
    }

    /** Additional helper methods below */

}
