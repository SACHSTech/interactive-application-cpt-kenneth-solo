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
     *   1: Gravity
     *      0 0
     *      First bit represents whether to apply gravity (bit representing a boolean)
     *      Second bit represents direction of gravity (bit representing a boolean)
     *          when true means down else up
     *   2: Metadata (just borrowed the rest of the bits from the gravity byte, dont mind me)
     *      xx000000 00000000
     *      storing data about the cell i guess. could be useful for keeping track of stuff
     *      This value is represented as a byte
     *   3: Temperature (cause why not?) (sector 65280 as int)
     *      00000000
     *      This is a byte (primative type). use it literally
     *   4: Misc (other stuff that are just random or useful idk, ive gotta fill in the rest of the flags somehow) (starting with the left most bit (128)) (sector 255 as int)
     *      Can Shuffle;
     *      Random Movement; (1/3 chance to move every frame, equal chance to mvoe in either direction)
     *      Already Applied Gravity?
     *      Has Ticked (to prevent special behaviour and certain misc from triggering again)
     *      Triggerable;
     *      Immovable;
     *      Indestructible from outside causes;
     *      Volitile; (randomly implodes and destroys itself)
     * 
     * Ticking order is as follows:
     *  Gravity
     *  Misc
     *  Special Behaviour
     */

    public int brushRadius = 3;
    public long[][] canvas;  // x then y
    public int[][][] canvasFloor; // x, y, (0 == floor, 1 == ceiling)

    public int canvasWidth = 100 * 2;
    public int canvasHeight = 60 * 2;
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
        // System.out.println(Long.parseUnsignedLong("00000000 00000000 00000000 00000000 01000000 00000000 00000000 00000000".replace(" ", ""), 2));
    }

    @Override
    public void draw() {
        background(0);

        renderCells();
        applyCellsRuleBehaviours();
        
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
                    0b11000000_00000000_00000000_10000000
                );
            }
        }
    }

    public void applyCellsRuleBehaviours() {
        for (int canvasX = 0; canvasX < canvasWidth; canvasX++) {
            boolean canShuffle = true;
            int lastLayer = -1;

            for (int canvasY = 0; canvasY < canvasHeight; canvasY++) {
                long cell = canvas[canvasX][canvasY];
                if (cell == EMPTY_CELL) {
                    if (lastLayer == -1) {
                        lastLayer = canvasY;
                        canvasY = canvasHeight - 1;
                    } else {
                        if (canShuffle) canShuffle = false;
                    }
                    
                    continue;
                }

                int cellX = canvasX;
                int cellY = canvasY;
                int gravityDirection = (int)((cell & 1073741824L) >>> 30);
                    
                if ((cell & 32) != 32 && (cell & 2147483648L) == 2147483648L) {  // check if gravity has already been applied
                    cell = canvas[cellX][cellY] |= 32;
                    cellY += cellCommonsApplyGravity(gravityDirection, cellX, cellY);
                }

                if ((cell & 16) != 16) {  // check if cell has already been ticked by misc
                    cell = canvas[cellX][cellY] |= 16;
                    int moveDirection = gravityDirection == 1 ? 1 : -1;
                    long updatedPos = cellCommonsMiscRules((int)(cell & 255), cellX, cellY, moveDirection, canShuffle);

                    cellX = (int)(updatedPos >>> 32);
                    cellY = (int)(updatedPos & Integer.MAX_VALUE);
                }

                if (canvas[cellX][cellY] == EMPTY_CELL) continue;
                // TODO: apply special behaviour
            }
        }

        // reset gravity flag and ticked flag
        for (int x = 0; x < canvasWidth; x++) {
            for (int y = 0; y < canvasHeight; y++) {
                long cell = canvas[x][y];
                if (cell == EMPTY_CELL) continue;
                
                canvas[x][y] ^= (cell & 48);
            }
        }
    }

    /**
     * Applies Misc Rules. CAUTION: this function will update the canvas!
     * @param rule misc rule see above for sector
     * @param x cell x on canvas
     * @param y cell y on canvas
     * @param moveDirection where the cell is moving towards according to gravity rule (see above for sector)
     * @param allowShuffle allow the cell to shuffle
     * @return updated x and y coordinates. 32bits on the left is the x coordinates as int, and 32bits on the right is y coordinates as int
     *         <br> Use a bitwise mask to access the value {@code x = (int)(res >>> 32);} {@code y = (int)(res & Integer.MAX_VALUE)}
     */
    public long cellCommonsMiscRules(int rule, int x, int y, int moveDirection, boolean allowShuffle) {
        if ((rule & 128) == 128 && allowShuffle) {  // Can Shuffle
            if (
                ((y + moveDirection) != -1 && (y + moveDirection) != canvasHeight)      // ensure it cannot move off the screen vertically
                && canvas[x][y + moveDirection] != EMPTY_CELL                           // only can shuffle if the space below or above occupied
            ) {
                if ((x + 1) != canvasWidth && canvas[x + 1][y + moveDirection] == EMPTY_CELL) {
                    moveRelative(x, y, 1, moveDirection);
    
                    y += moveDirection;
                    x++;
                } else if ((x - 1) != -1 && canvas[x - 1][y + moveDirection] == EMPTY_CELL) {
                    moveRelative(x, y, -1, moveDirection);

                    y += moveDirection;
                    x--;
                }
            }
        }

        if ((rule & 64) == 64 && random(3) == 0) {  // Random Movement; (1/3 chance to move every frame, equal chance to mvoe in either direction)
            boolean moveLeft = random(2) == 0;
            if (moveLeft && x != 0 && canvas[x - 1][y] == EMPTY_CELL) {
                moveRelative(x, y, -1, 0);
                x--;
            } else if (x != canvasWidth - 1 && canvas[x + 1][y] == EMPTY_CELL) {
                moveRelative(x, y, 1, 0);
                x++;
            }
        }

        if ((rule & 1) == 1 && random(1000) == 0) {  // volitile; (randomly implodes)
            canvas[x][y] = EMPTY_CELL;
        }

        return ((long)x << 32) | y;
    }

    /**
     * Applies gravity to the affected cell. CAUTION: this function will update the canvas!
     * @param rule gravity rule (only give me the direction of fall bit) see above for sector
     * @param x cell x coords in the canvas
     * @param y cell y coords in the canvas
     * @return returns where the cell was offseted (up -1 or down 1)
     */
    public int cellCommonsApplyGravity(int rule, int x, int y) {
        boolean isFalling = rule == 1 ? true : false;

        int offset;

        if (isFalling) {
            offset = ((y + 1) == canvasHeight) ? 0 : 1;
        } else {  // going up
            offset = ((y - 1) == -1) ? 0 : -1;
        }

        if (offset == 0) return 0;

        if (canvas[x][y + offset] == EMPTY_CELL) {
            moveRelative(x, y, 0, offset);
            return offset;
        }

        return 0;
    }

    public boolean moveAbsolute(int x, int y, int targetX, int targetY) {
        if (canvas[targetX][targetY] != EMPTY_CELL) return false;
        canvas[targetX][targetY] = canvas[x][y];
        canvas[x][y] = EMPTY_CELL;

        return true;
    }

    public boolean moveRelative(int x, int y, int relX, int relY) {
        return moveAbsolute(x, y, x + relX, y + relY);
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