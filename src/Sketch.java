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
     * <-------[rgb / hsb]------>  Type    <---------[general rules]--------->
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
     *      Has Ticked?
     *      Unused 1
     *      Unused 2
     *      Immovable;
     *      Indestructible from outside causes;
     *      Is falling?
     * 
     * additional notes:
     *      type -127 is reserved for cell type barrier floor
     *      cells of the same type can exchange places if they are going in opposite directions
     */

    public final long MASK_COLOR =             Long.parseUnsignedLong("11111111 11111111 11111111 00000000 00000000 00000000 00000000 00000000".replace(" ", ""), 2);
    public final long MASK_TYPE =              Long.parseUnsignedLong("00000000 00000000 00000000 11111111 00000000 00000000 00000000 00000000".replace(" ", ""), 2);
    public final long MASK_FALLING =           Long.parseUnsignedLong("00000000 00000000 00000000 00000000 10000000 00000000 00000000 00000000".replace(" ", ""), 2);
    public final long MASK_FALLING_DOWN =      Long.parseUnsignedLong("00000000 00000000 00000000 00000000 01000000 00000000 00000000 00000000".replace(" ", ""), 2);
    public final long MASK_METADATA =          Long.parseUnsignedLong("00000000 00000000 00000000 00000000 00111111 11111111 00000000 00000000".replace(" ", ""), 2);
    public final long MASK_TEMPERATURE =       Long.parseUnsignedLong("00000000 00000000 00000000 00000000 00000000 00000000 11111111 00000000".replace(" ", ""), 2);

    public final long MASK_SHUFFLE =           Long.parseUnsignedLong("00000000 00000000 00000000 00000000 00000000 00000000 00000000 10000000".replace(" ", ""), 2);
    public final long MASK_RAND_MOVE =         Long.parseUnsignedLong("00000000 00000000 00000000 00000000 00000000 00000000 00000000 01000000".replace(" ", ""), 2);
    public final long MASK_TICKED =            Long.parseUnsignedLong("00000000 00000000 00000000 00000000 00000000 00000000 00000000 00100000".replace(" ", ""), 2);
    public final long MASK_UNUSED_1 =          Long.parseUnsignedLong("00000000 00000000 00000000 00000000 00000000 00000000 00000000 00010000".replace(" ", ""), 2);
    public final long MASK_UNUSED_2 =          Long.parseUnsignedLong("00000000 00000000 00000000 00000000 00000000 00000000 00000000 00001000".replace(" ", ""), 2);
    public final long MASK_IMMOVABLE =         Long.parseUnsignedLong("00000000 00000000 00000000 00000000 00000000 00000000 00000000 00000100".replace(" ", ""), 2);
    public final long MASK_INDESTRUCTABLE =    Long.parseUnsignedLong("00000000 00000000 00000000 00000000 00000000 00000000 00000000 00000010".replace(" ", ""), 2);
    public final long MASK_UNUSED_3 =          Long.parseUnsignedLong("00000000 00000000 00000000 00000000 00000000 00000000 00000000 00000001".replace(" ", ""), 2);

    public int brushRadius = 2;
    public long[][] canvas;  // x then y

    public int canvasWidth = 100 * 2;
    public int canvasHeight = 60 * 2;
    public int sandSize = 5;

    // TODO: subjected for removal (temorary testing)
    public int count = 0;

    public final long CELL_EMPTY = 0;
    public final long CELL_BARRIER_FLOOR = encodeCellData(0, (byte)-128, (int)(MASK_IMMOVABLE | MASK_INDESTRUCTABLE));

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
        colorMode(HSB, 255, 100, 100);
    }

    boolean waitingRelease = false;
    @Override
    public void draw() {
        background(0);

        renderCells();
        // if (keyPressed && !waitingRelease) {
        //     waitingRelease = true;
        applyCellsRuleBehaviours();
        // } else if (!keyPressed && waitingRelease) {
        //     waitingRelease = false;
        // }

        // cell inspect
        if (keyPressed && !waitingRelease) {
            waitingRelease = true;
            String cell = Long.toBinaryString(getCellSafe(this.mouseX / sandSize, this.mouseY / sandSize));
            cell = " ".repeat(64 - cell.length()) + cell;
            
            for (int i = 0; i < 64; i += 8) {
                System.out.print(cell.substring(i, i + 8) + " ");
            }
            System.out.println();
        } else if (!keyPressed && waitingRelease) {
            waitingRelease = false;
        }
        
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
                    (int)(MASK_FALLING | MASK_FALLING_DOWN | MASK_SHUFFLE | MASK_FALLING)
                        ^ (int)(count % 2 == 0 ? MASK_FALLING_DOWN : 0)
                );
            }
        }
    }

    public void applyCellsRuleBehaviours() {
        for (int canvasX = 0; canvasX < canvasWidth; canvasX++) {
            for (int canvasY = 0; canvasY < canvasHeight; canvasY++) {
                int cellX = canvasX;
                int cellY = canvasY;
                long cell = canvas[cellX][cellY];

                if (cell == CELL_EMPTY || isBitEnabled(cell, MASK_TICKED)) continue;
                canvas[cellX][cellY] = cell |= MASK_TICKED;

                boolean isFallingDown = isBitEnabled(cell, MASK_FALLING_DOWN);
                int moveDirection = isFallingDown ? 1 : -1;
                long cellAhead = getCellSafe(cellX, cellY + moveDirection);

                if (
                    cellAhead != CELL_BARRIER_FLOOR
                    && (cellAhead == CELL_EMPTY || isBitEnabled(cellAhead, MASK_FALLING))
                ) {
                    canvas[cellX][cellY] = cell |= MASK_FALLING;  // start falling
                } else {
                    canvas[cellX][cellY] = cell &= ~MASK_FALLING;  // stop falling
                }

                boolean allowShuffle = !isBitEnabled(cell, MASK_FALLING)
                                        || !isBitEnabled(cellAhead, MASK_FALLING)
                                        || isBitEnabled(cellAhead, MASK_FALLING_DOWN) != isFallingDown;

                long updatedPos = cellCommonsMiscRules(cell, cellX, cellY, moveDirection, allowShuffle);
                cellX = (int)(updatedPos >>> 32);
                cellY = (int)(updatedPos & Integer.MAX_VALUE);
                cellAhead = getCellSafe(cellX, cellY + moveDirection);
                                                                                                            
                // we will know if it had shuffled if it moved on the x axis
                if (cellX == canvasX) {
                    if (isBitEnabled(cell, MASK_FALLING)) cellY += cellCommonsApplyGravity(isFallingDown, cellX, cellY);

                    // exchange rule: allow swapping places if the cell ahead is the same type and moving in the opposing direction
                    // only apply when we have moved vertically. in addition to the condition for the exchange rule the cell must
                    //      have not moved vertically yet and that the cell ahead is not a barrier floor
                    if (cellAhead != CELL_BARRIER_FLOOR && cellY == canvasY && isBitEnabled(cellAhead, MASK_FALLING) && isBitEnabled(cellAhead, MASK_FALLING_DOWN) != isFallingDown) {
                        long temp = canvas[cellX][cellY];
                        canvas[cellX][cellY] = canvas[cellX][cellY + moveDirection];
                        canvas[cellX][cellY + moveDirection] = temp;
                    }
                }

                // TODO: apply special behaviour
            }
        }

        // reset gravity flag and ticked flag
        // TODO: possible performance fix
        //       Instead of iterating this again we can just have a boolean to check whether
        //       or not this iteration should check for an on or off bit in MASK_TICKED
        //       and then inverting the boolean variable for the next iteration
        long resetFlag = ~MASK_TICKED;
        for (int x = 0; x < canvasWidth; x++) {
            for (int y = 0; y < canvasHeight; y++) {
                canvas[x][y] &= resetFlag;
            }
        }
    }

    /**
     * Applies Misc Rules. CAUTION: this function will update the canvas! NOTE: no cell is deleted in this process
     * @param cell cell data
     * @param x cell x on canvas
     * @param y cell y on canvas
     * @param moveDirection where the cell is moving towards according to gravity rule (see above for sector)
     * @param allowShuffle allow the cell to shuffle
     * @return updated x and y coordinates. 32bits on the left is the x coordinates as int, and 32bits on the right is y coordinates as int
     *         <br> Use a bitwise mask to access the value {@code x = (int)(res >>> 32);} {@code y = (int)(res & Integer.MAX_VALUE)}
     */
    public long cellCommonsMiscRules(long cell, int x, int y, int moveDirection, boolean allowShuffle) {
        if (allowShuffle && isBitEnabled(cell, MASK_SHUFFLE) && getCellSafe(x, y + moveDirection) != CELL_EMPTY) {  // Can Shuffle
            if (moveRelative(x, y, 1, moveDirection)) {
                y += moveDirection;
                x++;
            } else if (moveRelative(x, y, -1, moveDirection)) {
                y += moveDirection;
                x--;
            }
        }

        if (isBitEnabled(cell, MASK_RAND_MOVE) && random(3) == 0) {  // Random Movement; (1/3 chance to move every frame, equal chance to mvoe in either direction)
            boolean chooseLeft = random(2) == 0;
            if (chooseLeft && moveRelative(x, y, -1, 0)) {
                x--;
            } else if (moveRelative(x, y, 1, 0)) {
                x++;
            }
        }

        return ((long)x << 32) | y;
    }

    /**
     * Applies gravity to the affected cell. CAUTION: this function will update the canvas! NOTE: no cell is deleted in this process
     * @param isFallingDown true if gravity is heading down else false for going up
     * @param x cell x coords in the canvas
     * @param y cell y coords in the canvas
     * @return returns where the cell was offseted (up -1 or down 1)
     */
    public int cellCommonsApplyGravity(boolean isFallingDown, int x, int y) {
        int offset = isFallingDown ? 1 : -1;
        boolean moved = moveRelative(x, y, 0, offset);
        return moved ? offset : 0;
    }

    public boolean isBitEnabled(long value, long enabledFlags) {
        return (value & enabledFlags) == enabledFlags;
    }

    public boolean isYOutOfBounds(int y) {
        return y < 0 || y >= canvasHeight;
    }

    /**
     * Moves the cell from x, y to targetX, targetY. NOTE: function is aware of the canvas boundaries
     * @param x
     * @param y
     * @param targetX
     * @param targetY
     * @return true if successfully moved the cell, false otherwise
     */
    public boolean moveAbsolute(int x, int y, int targetX, int targetY) {
        if (getCellSafe(targetX, targetY) != CELL_EMPTY) return false;

        canvas[targetX][targetY] = canvas[x][y];
        canvas[x][y] = CELL_EMPTY;

        return true;
    }

    /**
     * Moves the cell relative to x, y given relX, relY. NOTE: function is aware of the canvas boundaries
     * @param x
     * @param y
     * @param relX
     * @param relY
     * @return true if successfully moved the cell, false otherwise
     */
    public boolean moveRelative(int x, int y, int relX, int relY) {
        return moveAbsolute(x, y, x + relX, y + relY);
    }

    /**
     * Get cell at x, y; if the coordinates are unreachable, {@link #CELL_BARRIER_FLOOR} will be return instead
     * @param x cell x position
     * @param y cell y position
     * @return the cell's value or {@link #CELL_BARRIER_FLOOR}
     */
    public long getCellSafe(int x, int y) {
        if (isYOutOfBounds(y)) return CELL_BARRIER_FLOOR;
        if (x < 0 || x >= canvasWidth) return CELL_BARRIER_FLOOR;
        return canvas[x][y];
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
        return (Integer.toUnsignedLong(color & 16777215) << 40)     // strip alpha channel from color (left side first 8 bits). shift 40 bits to the left
            | (Byte.toUnsignedLong(type) << 32)                     // shift type 32 bits left
            | Integer.toUnsignedLong(generalRules);                 // general rules and int uses 4 bytes (4 * 8 = 32; we used up the rest of the bits)
    }

    public int getCellColor(long val) {
        // -1099511627776 represents the rgb section of the cell value
        // we must add back the alpha section so that processing can recognise the color (append 8 on bits to the left)
        //      this value is -16777216

        return -16777216 | (int)((val & MASK_COLOR) >>> 40);
    }

    public byte getCellType(long val) {
        return (byte)((val & MASK_TYPE) >>> 32);
    }
}