import processing.core.PApplet;
import processing.event.MouseEvent;

/**
 * Sand Physics Like Simulation
 * @author directconnections
 */
public class Sketch extends PApplet {
    /**
     * Each array element (long) has data represented like this
     * 00000000 00000000 00000000 00000000 00000000 00000000 00000000 00000000
     *  Red      Green    Blue     Type    <---------[general rules]--------->
     * 
     * Red, Green, Blue: Particle Color (mask is -1099511627776) (this channel can also support hsb)
     * Type: Particle Type (for special behaviour if applicable) (mask is 1095216660480)
     *      0 means nothing
     * General Rules: general particle rules (common behaviour) (mask is 4294967295)
     *  there are 4 sections (each 8 bits as inidcated above). starting with the left most section:
     *   1: Gravity (sector -16777216)
     *      0 0000000
     *      First bit represents direction of gravity (bit representing a boolean)
     *          when true means down else up
     *      The rest of the bits represent the rate of gravity as a fraction represented as 1/x such that the particle will move once every x frames
     *   2: Metadata (sector 16711680)
     *      00000000
     *      storing data about the cell i guess. could be useful for keeping track of stuff
     *      This value is represented as a byte
     *   3: Temperature (cause why not?) (sector 65280)
     *      00000000
     *      This is a byte (primative type). use it literally
     *   4: Misc (other stuff that are just random or useful idk, ive gotta fill in the rest of the flags somehow) (starting with the left most bit (128)) (sector 255)
     *      Can Shuffle;
     *      Random Movement; (1/3 chance to move every frame, equal chance to mvoe in either direction)
     *      Instantaneous Disintegration upon contact;
     *      Destroy Adjacent; (depends on direction of movement)
     *      
     *      Triggerable;
     *      Immovable;
     *      Indestructible;
     *      Volitile; (randomly implodes and explodes)
     * 
     * Ticking order is as follows:
     *  Gravity
     *  Misc
     *  Special Behaviour
     */

    public int brushRadius = 3;
    public long[][] canvas;  // x then y

    public int canvasWidth = 100;
    public int canvasHeight = 60;
    public int sandSize = 5;

    // TODO: subjected for removal (temorary testing)
    public int count = 0;

    public static final int EMPTY_CELL = 0;

    public static void main(String[] args) {
        PApplet.main("Sketch");
    }

    @Override
    public void settings() {
        size(canvasWidth * sandSize, canvasHeight * sandSize);
        canvas = new long[canvasWidth][canvasHeight];
    }

    @Override
    public void setup() {
        fill(255);
        colorMode(HSB, 360, 100, 100);
        System.out.println(Integer.parseUnsignedInt("00000000 00000000 00000000 10000000".replace(" ", ""), 2));
    }

    @Override
    public void draw() {
        background(0);

        renderCells();
        applyCellsCommonRules();

        // update
        // for (int x = canvasWidth - 1; x >= 0; x--) {
        //     for (int y = canvasHeight - 1; y >= 0; y--) {
        //         if (canvas[x][y] != 0 && canvas[x][Math.clamp(y + 1, 0, canvasHeight - 1)] == 0) {  // falling rule
        //             canvas[x][y + 1] = canvas[x][y];
        //             canvas[x][y] = 0;
        //         } else if (canvas[x][y] != 0 && (canvas[x][Math.clamp(y - 1, 0, canvasHeight - 1)] == 0 || y == 0)) {  // shufflling rule
        //             if (y == canvasHeight - 1) continue;

        //             if (x != canvasWidth - 1 && canvas[Math.clamp(x + 1, 0, canvasWidth - 1)][Math.clamp(y + 1, 0, canvasHeight - 1)] == 0) { // shuffle right
        //                 canvas[x + 1][y + 1] = canvas[x][y];
        //                 canvas[x][y] = 0;
        //             } else if (x != 0 && canvas[Math.clamp(x - 1, 0, canvasWidth - 1)][Math.clamp(y + 1, 0, canvasHeight - 1)] == 0) {  // shuffle left
        //                 canvas[x - 1][y + 1] = canvas[x][y];
        //                 canvas[x][y] = 0;
        //             }
        //         }
        //     }
        // } 
        
        // fps
        fill(255);
        textAlign(LEFT, TOP);
        text(String.format("fps %d", (int)frameRate), 0, 0);
    }

    @Override
    public void mouseDragged(MouseEvent event) {
        int mouseX = Math.clamp(event.getX() / sandSize, 0, canvasWidth - 1);
        int mouseY = Math.clamp(event.getY() / sandSize, 0, canvasHeight - 1);
        
        // TODO: get rid of hue and replace with something else
        int hue = count++ % 360;
        
        for (int yOffset = -brushRadius; yOffset <= brushRadius; yOffset++) {
            for (int xOffset = -brushRadius; xOffset <= brushRadius; xOffset++) {
                canvas[Math.clamp(mouseX + xOffset, 0, canvasWidth - 1)][Math.clamp(mouseY + yOffset, 0, canvasHeight - 1)] = encodeCellData(
                    color(hue, 100, 100),
                    (byte)1,
                    0b10000001_00000000_00000000_00000000
                );
            }
        }
    }

    public void applyCellsCommonRules() {
        for (int x = 0; x < canvasWidth; x++) {
            for (int y = canvasHeight - 1; y >= 0; y--) {  // check from bottom of canvas to top
                long cell = canvas[x][y];
                if (getCellType(cell) == 0) continue;

                int rules = getCellGeneralRules(cell);
                
                cellCommonsApplyGravity((rules & -16777216) >>> 24, x, y);
                cellCommonsMiscRules((rules & 255), x, y);
            }
        }
    }

    public void cellCommonsMiscRules(int rule, int x, int y) {
    //     Can Shuffle;
    //  *      Random Movement; (1/3 chance to move every frame, equal chance to mvoe in either direction)
    //  *      Instantaneous Disintegration upon contact;
    //  *      Destroy Adjacent; (depends on direction of movement)
    //  *      
    //  *      Triggerable;
    //  *      Immovable;
    //  *      Indestructible;
    //  *      Volitile; (randomly implodes and explodes)
    }

    public void cellCommonsApplyGravity(int rule, int x, int y) {
        boolean isFalling = ((rule & 128) >>> 7) == 1 ? true : false;
        int rateOfChange = rule & 127;

        int offset;

        if (isFalling) {
            offset = y == canvasHeight - 1 ? 0 : rateOfChange;
        } else {  // going up
            offset = y == 0 ? 0 : rateOfChange;
        }

        if (offset == 0) return;

        if (frameCount % rateOfChange == 0 && canvas[x][y + offset] == 0) {
            canvas[x][y + offset] = canvas[x][y];
            canvas[x][y] = 0;
        }
    }

    public void renderCells() {
        noStroke();
        for (int x = 0; x < canvasWidth; x++) {
            for (int y = 0; y < canvasHeight; y++) {
                fill(getCellColor(canvas[x][y]));
                square(x * sandSize, y * sandSize, sandSize);
            }
        }
    }

    /** Additional helper methods below */
    public long encodeCellData(int color, byte type, int generalRules) {
        return ((color & 16777215L) << 40)              // strip alpha channel from color (left side first 8 bits). shift 40 bits to the left
            | ((type & 255L) << 32)                     // shift type 32 bits left
            | (generalRules & 4294967295L);             // general rules and int uses 4 bytes (4 * 8 = 32; we used up the rest of the bits)
    }

    public int getCellColor(long val) {
        // -1099511627776 represents the rgb section of the cell value
        // we must add back the alpha section so that processing can recognise the color (append 8 on bits to the left)
        //      this value is -16777216

        return -16777216 | (int)((val & -1099511627776L) >>> 40);
    }

    public byte getCellType(long val) {
        return (byte)((val & 1095216660480L) >>> 32);
    }

    public int getCellGeneralRules(long val) {
        return (int)(val & 4294967295L);
    }
}

// parsing test cases
//      System.out.println(Long.parseUnsignedLong("11111111 11111111 11111111 00000000 00000000 00000000 00000000 00000000".replace(" ", ""), 2));
//      System.out.println(Long.parseUnsignedLong("00000000 00000000 00000000 11111111 00000000 00000000 00000000 00000000".replace(" ", ""), 2));
//      System.out.println(Long.parseUnsignedLong("00000000 00000000 00000000 00000000 11111111 11111111 11111111 11111111".replace(" ", ""), 2));

//      System.out.println();

//      System.out.println("color");
//      System.out.println(Long.toBinaryString(encodeCellData(color(128, 128, 128), (byte)85, -1431655766)));
//      System.out.println(getCellColor(encodeCellData(color(128, 128, 128), (byte)85, -1431655766)));
//      System.out.println(color(128, 128, 128));

//      System.out.println();

//      System.out.println("type");
//      System.out.println(Long.toBinaryString(encodeCellData(color(128, 128, 128), (byte)85, -1431655766)));
//      System.out.println(getCellType(encodeCellData(color(128, 128, 128), (byte)85, -1431655766)));
//      System.out.println(85);

//      System.out.println();

//      System.out.println("general rules");
//      System.out.println(Long.toBinaryString(encodeCellData(color(128, 128, 128), (byte)85, -1431655766)));
//      System.out.println(getCellGeneralRules(encodeCellData(color(128, 128, 128), (byte)85, -1431655766)));
//      System.out.println(-1431655766);