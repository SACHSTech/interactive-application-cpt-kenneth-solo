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
    public int sandSize = 10;

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
            for (int y = canvasHeight - 1; y >= 0; y--) {
                if (canvas[x][y] != 0 && canvas[x][Math.clamp(y + 1, 0, canvasHeight - 1)] == 0) {  // falling rule
                    canvas[x][y + 1] = canvas[x][y];
                    canvas[x][y] = 0;
                } else if (canvas[x][y] != 0 && (canvas[x][Math.clamp(y - 1, 0, canvasHeight - 1)] == 0 || y == 0)) {  // shufflling rule
                    if (y == canvasHeight - 1) continue;

                    if (x != canvasHeight - 1 && canvas[Math.clamp(x + 1, 0, canvasWidth - 1)][Math.clamp(y + 1, 0, canvasHeight - 1)] == 0) { // shuffle right
                        canvas[x + 1][y + 1] = canvas[x][y];
                        canvas[x][y] = 0;
                    } else if (x != 0 && canvas[Math.clamp(x - 1, 0, canvasWidth - 1)][Math.clamp(y + 1, 0, canvasHeight - 1)] == 0) {  // shuffle left
                        canvas[x - 1][y + 1] = canvas[x][y];
                        canvas[x][y] = 0;
                    }
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
