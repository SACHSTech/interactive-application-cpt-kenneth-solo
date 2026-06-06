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
     * <-------[rgb / hsb]------>  Type    <-------[metadata]------->  |<State and Rules (aka. flags)
     * 
     * Red, Green, Blue: Particle Color (mask is -1099511627776) (this channel can also support hsb)
     * Type: Particle Type (for special behaviour if applicable) (mask is 1095216660480)
     *      0 represents air
     *      -127 represents a barrier floor (basically cells outside the canvas range to prevent the cell from escaping)
     * Metadata (just borrowed the rest of the bits from the gravity byte, dont mind me)
     *      xx000000 00000000 00000000
     *      storing data about the cell i guess. could be useful for keeping track of stuff
     *      This value is represented as a byte
     * State and Rules (aka. flags) (the state of the cell and some rules that all cells probably have in common)
     *      See below for misc flags. start at {@link #MASK_SHUFFLE}
     * 
     * additional notes:
     *      cells of the same type can exchange places if they are going in opposite directions
     */

    public final long MASK_COLOR =            Long.parseUnsignedLong("11111111 11111111 11111111 00000000 00000000 00000000 00000000 00000000".replace(" ", ""), 2);
    public final long MASK_TYPE =             Long.parseUnsignedLong("00000000 00000000 00000000 11111111 00000000 00000000 00000000 00000000".replace(" ", ""), 2);
    public final long MASK_METADATA =         Long.parseUnsignedLong("00000000 00000000 00000000 00000000 11111111 11111111 11111111 00000000".replace(" ", ""), 2);

    public final long MASK_TICKED =           Long.parseUnsignedLong("00000000 00000000 00000000 00000000 00000000 00000000 00000000 10000000".replace(" ", ""), 2);
    public final long MASK_TARGET_ANY_SWAP =  Long.parseUnsignedLong("00000000 00000000 00000000 00000000 00000000 00000000 00000000 01000000".replace(" ", ""), 2);
    public final long MASK_UNSUED_1 =         Long.parseUnsignedLong("00000000 00000000 00000000 00000000 00000000 00000000 00000000 00100000".replace(" ", ""), 2);
    public final long MASK_FALLING_UP =       Long.parseUnsignedLong("00000000 00000000 00000000 00000000 00000000 00000000 00000000 00010000".replace(" ", ""), 2);
    public final long MASK_INDESTRUCTABLE =   Long.parseUnsignedLong("00000000 00000000 00000000 00000000 00000000 00000000 00000000 00001000".replace(" ", ""), 2);
    public final long MASK_CANT_FALL =        Long.parseUnsignedLong("00000000 00000000 00000000 00000000 00000000 00000000 00000000 00000100".replace(" ", ""), 2);
    public final long MASK_OTHERS_CANT_MOVE = Long.parseUnsignedLong("00000000 00000000 00000000 00000000 00000000 00000000 00000000 00000010".replace(" ", ""), 2);
    public final long MASK_SHUFFLE =          Long.parseUnsignedLong("00000000 00000000 00000000 00000000 00000000 00000000 00000000 00000001".replace(" ", ""), 2);

    public final long MASKC_NATURALLY_IMMOVABLE = MASK_CANT_FALL | MASK_OTHERS_CANT_MOVE;
    public final long MASKC_FLOATING_DISPLACEABLE = MASK_CANT_FALL | MASK_TARGET_ANY_SWAP;

    public int brushRadius = 2;
    public long[][] canvas;  // x then y

    public int canvasWidth = 100 * 2;
    public int canvasHeight = 60 * 2;
    public int sandSize = 5;

    // TODO: subjected for removal (temorary testing)
    public int count = 0;

    public final long CELL_AIR = cellEncodeData(color(0), (byte)0, 0, (byte)(MASKC_FLOATING_DISPLACEABLE));
    public final long CELL_BARRIER_FLOOR = cellEncodeData(color(0), (byte)-128, 0, (byte)(MASK_INDESTRUCTABLE | MASKC_NATURALLY_IMMOVABLE));

    public static void main(String[] args) {
        PApplet.main("Sketch");
    }

    @Override
    public void settings() {
        size(canvasWidth * sandSize, canvasHeight * sandSize);
        canvas = new long[canvasWidth][canvasHeight];
        for (int x = 0; x < canvasWidth; x++) {
            for (int y = 0; y < canvasHeight; y++) {
                canvas[x][y] = CELL_AIR;
            }
        }
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

        cellRender();
        cellApplyRuleBehaviours();

        // cell inspect
        if (keyPressed && !waitingRelease) {
            waitingRelease = true;
            String cell = Long.toBinaryString(cellAtXYSafe(this.mouseX / sandSize, this.mouseY / sandSize));
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
                canvas[Math.clamp(mouseX + xOffset, 0, canvasWidth - 1)][Math.clamp(mouseY + yOffset, 0, canvasHeight - 1)] = cellEncodeData(
                    color(hue, 100, 100),
                    (byte)1,
                    0,
                    (byte)((MASK_SHUFFLE)
                        ^ (count % 2 == 0 ? MASK_FALLING_UP : 0))
                );
            }
        }
    }

    public void cellApplyRuleBehaviours() {
        for (int x = 0; x < canvasWidth; x++) {
            for (int y = 0; y < canvasHeight; y++) {
                long cell = canvas[x][y];
                if (!cellIsFlagOn(cell, MASK_TICKED)) cellTick(cell, x, y, true);
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

    public void cellTick(long cell, int x, int y, boolean canMove) {
        canvas[x][y] |= MASK_TICKED;

        boolean isFallingUp = cellIsFlagOn(cell, MASK_FALLING_UP);
        int fallDirection = isFallingUp ? -1 : 1;
        // long cellAhead = cellAtXYSafe(x, y + fallDirection);

        if (canMove) {
            if (cellCommonsApplyGravity(cell, x, y)) y++;

            // long updatedPos = cellApplyShuffling(cell, x, y, fallDirection);
            // x = (int)(updatedPos >>> 32);
            // y = (int)(updatedPos & Integer.MAX_VALUE);
            // cellAhead = cellAtXYSafe(x, y + fallDirection);
        }

        // TODO: apply special behaviour
    }

    /**
     * Cells will "shuffle". i.e. Move out of the way or move down and over. CAUTION: this function will update the canvas!
     * @param cell cell data
     * @param x cell x on canvas
     * @param y cell y on canvas
     * @param direction where the cell is moving towards according to gravity rule (see above for sector)
     * @return updated x and y coordinates. 32bits on the left is the x coordinates as int, and 32bits on the right is y coordinates as int
     *         <br> Use a bitwise mask to access the value {@code x = (int)(res >>> 32);} {@code y = (int)(res & Integer.MAX_VALUE)}
     */
    public long cellApplyShuffling(long cell, int x, int y, int direction) {
        // boolean allowShuffle = !cellIsFlagOn(cell, MASK_CHAIN_FALLING_UP)
                                // || !cellIsFlagOn(cellAhead, MASK_CHAIN_FALLING_UP)
                                // || cellIsFlagOn(cellAhead, MASK_FALLING_UP) != isFallingUp;
        long cellAhead = cellAtXYSafe(x, y + direction);

        if (
            cellIsFlagOn(cell, MASK_SHUFFLE)
            && (
                !cellIsFlagOn(cell, MASK_CHAIN_FALLING_UP)
                || cellIsFlagOn(cellAhead, MASK_OTHERS_CANT_MOVE)
                || (cellIsFlagOn(cellAhead, MASK_FALLING_UP) ? -1 : 1) != direction
            )
        ) {
            if (cellMoveRelative(x, y, 1, direction, true)) {
                y += direction;
                x++;
            } else if (cellMoveRelative(x, y, -1, direction, true)) {
                y += direction;
                x--;
            }
        }

        return ((long)x << 32) | y;
    }

    /**
     * Applies gravity to the affected cell.
     * @param cell cell value at (x, y)
     * @param x cell x coords in the canvas
     * @param y cell y coords in the canvas
     * @return true when the cell moved down
     */
    public boolean cellCommonsApplyGravity(long cell, int x, int y) {
        boolean fallingUp = cellIsFlagOn(cell, MASK_FALLING_UP);
        int fallDirection = fallingUp ? -1 : 1;
        long cellAhead = cellAtXYSafe(x, y + fallDirection);

        // exchange rule: can swap place if
        //                  either one of them can fall
        //                  both have to be moveable by others
        //                  same type going in opposite directions OR either one has any swap
        if (cellIsFlagOn(cell & cellAhead, MASK_CANT_FALL)) return false;          // at least one has to fall
        if (cellIsFlagOn(cell | cellAhead, MASK_OTHERS_CANT_MOVE)) return false;   // both have to be moveable
        if (cellIsFlagOn(cellAhead, MASK_TICKED)) return false;                    // cannot move ticked cells
        
        // one or more is falling
        // both can be moved
        // they are either moving apart or moving towards
        // there is either 1 or no displaceables
        if (cellIsFlagOn(cell, MASKC_FLOATING_DISPLACEABLE) && cellIsFlagOn(cellAhead, MASK_FALLING_UP)) return cellMoveRelative(x, y, 0, 1, true);
        if (!cellIsFlagOn(cell, MASK_FALLING_UP) && cellIsFlagOn(cellAhead, MASKC_FLOATING_DISPLACEABLE)) return cellMoveRelative(x, y, 0, 1, true);
        boolean sameType = cellGetType(cell) == cellGetType(cellAhead);
        if (sameType && (!cellIsFlagOn(cell, MASK_FALLING_UP) && cellIsFlagOn(cellAhead, MASK_FALLING_UP))) return cellMoveRelative(x, y, 0, 1, true);

        return false;
    }

    public boolean cellIsFlagOn(long cell, long enabledFlags) {
        return (cell & enabledFlags) == enabledFlags;
    }

    public boolean isYOutOfBounds(int y) {
        return y < 0 || y >= canvasHeight;
    }

    /**
     * Get cell at x, y; if the coordinates are unreachable, {@link #CELL_BARRIER_FLOOR} will be return instead
     * @param x cell x position
     * @param y cell y position
     * @return the cell's value or {@link #CELL_BARRIER_FLOOR}
     */
    public long cellAtXYSafe(int x, int y) {
        if (isYOutOfBounds(y)) return CELL_BARRIER_FLOOR;
        if (x < 0 || x >= canvasWidth) return CELL_BARRIER_FLOOR;
        return canvas[x][y];
    }

    public void cellRender() {
        noStroke();
        for (int x = 0; x < canvasWidth; x++) {
            for (int y = 0; y < canvasHeight; y++) {
                fill(cellGetColor(canvas[x][y]));
                square(x * sandSize, y * sandSize, sandSize);
            }
        }
    }

    /**
     * swap two cells. will fail if target has the following mask: {@link #MASK_OTHERS_CANT_MOVE}.
     *      <br> NOTE: this function will only check if the target exists. it assumes that (x, y) are valid coordinates
     * @param x
     * @param y
     * @param targetX
     * @param targetY
     * @param onlyTickOnce if true it will tick if the target has not ticked yet. if false, will always tick target
     * @return true if the swap was successful, false otherwise
     */
    public boolean cellMoveAbsolute(int x, int y, int targetX, int targetY, boolean onlyTickOnce) {
        long inital = canvas[x][y];
        long target = cellAtXYSafe(targetX, targetY);
        if (cellIsFlagOn(target, MASK_OTHERS_CANT_MOVE)) return false;
        
        canvas[x][y] = target;
        canvas[targetX][targetY] = inital;

        if (onlyTickOnce) {
            if (!cellIsFlagOn(target, MASK_TICKED)) cellTick(target, x, y, false);
        } else {
            cellTick(target, x, y, false);
        }

        return true;
    }

    public boolean cellMoveRelative(int x, int y, int relX, int relY, boolean onlyTickOnce) {
        return cellMoveAbsolute(x, y, x + relX, y + relY, onlyTickOnce);
    }

    public boolean cellMoveAbsolute(int x, int y, int targetX, int targetY) {
        return cellMoveAbsolute(x, y, targetX, targetY, true);
    }

    public boolean cellMoveRelative(int x, int y, int relX, int relY) {
        return cellMoveAbsolute(x, y, x + relX, y + relY, true);
    }

    /** Additional helper methods below */
    public long cellEncodeData(int color, byte type, int metadata, byte generalRules) {
        // 16777215 represnts the first 3 bytes of the integer on the right.
        // in this case for color we do not care about the byte on the left because the alpha channel is meaningless to us
        // for metadata we do the same

        return Integer.toUnsignedLong(color & 16777215) << 40
            | Byte.toUnsignedLong(type) << 32
            | Integer.toUnsignedLong(metadata & 16777215) << 8
            | Byte.toUnsignedLong(generalRules);
    }

    public int cellGetColor(long val) {
        // -1099511627776 represents the rgb section of the cell value
        // we must add back the alpha section so that processing can recognise the color (append 8 on bits to the left)
        //      this value is -16777216

        return -16777216 | (int)((val & MASK_COLOR) >>> 40);
    }

    public byte cellGetType(long val) {
        return (byte)((val & MASK_TYPE) >>> 32);
    }

    public int getMetadata(long val) {
        return (int)((val & MASK_METADATA) >>> 24);
    }
}