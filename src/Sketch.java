import java.util.HashMap;

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
     *      See below for misc flags. start at {@link #MASK_CAN_SHUFFLE}
 * 
     * additional notes:
     *      cells of the same type can exchange places if they are going in opposite directions
     */

    public final long MASK_COLOR =             Long.parseUnsignedLong("11111111 11111111 11111111 00000000 00000000 00000000 00000000 00000000".replace(" ", ""), 2);
    public final long MASK_TYPE =              Long.parseUnsignedLong("00000000 00000000 00000000 11111111 00000000 00000000 00000000 00000000".replace(" ", ""), 2);
    public final long MASK_METADATA =          Long.parseUnsignedLong("00000000 00000000 00000000 00000000 11111111 11111111 11111111 00000000".replace(" ", ""), 2);

    /** has this cell ticked this iteration yet? */
    public final long MASK_TICKED =            Long.parseUnsignedLong("00000000 00000000 00000000 00000000 00000000 00000000 00000000 10000000".replace(" ", ""), 2);
    /** this cell can swap positions with other cells who ask */
    public final long MASK_CAN_SWAP_WITH_ANY = Long.parseUnsignedLong("00000000 00000000 00000000 00000000 00000000 00000000 00000000 01000000".replace(" ", ""), 2);
    /** this cell cant swap with a target cell. However a target cell CAN swap to this cell (i.e. this cell can't move on its own) */
    public final long MASK_SELF_CANT_SWAP =    Long.parseUnsignedLong("00000000 00000000 00000000 00000000 00000000 00000000 00000000 00100000".replace(" ", ""), 2);
    /** this cell wants to swap upwards (by default they will swap down) */
    public final long MASK_SWAP_UP =           Long.parseUnsignedLong("00000000 00000000 00000000 00000000 00000000 00000000 00000000 00010000".replace(" ", ""), 2);
    /** this cell cant be destroyed unless the user destroys it */
    public final long MASK_INDESTRUCTABLE =    Long.parseUnsignedLong("00000000 00000000 00000000 00000000 00000000 00000000 00000000 00001000".replace(" ", ""), 2);
    public final long MASK_UNSUED_1 =          Long.parseUnsignedLong("00000000 00000000 00000000 00000000 00000000 00000000 00000000 00000100".replace(" ", ""), 2);
    /** other cells can shuffle on this cell */
    public final long MASK_OTHERS_SHUFFLE_ON = Long.parseUnsignedLong("00000000 00000000 00000000 00000000 00000000 00000000 00000000 00000010".replace(" ", ""), 2);
    /** this cell can shuffle */
    public final long MASK_CAN_SHUFFLE =       Long.parseUnsignedLong("00000000 00000000 00000000 00000000 00000000 00000000 00000000 00000001".replace(" ", ""), 2);

    public final long MASKC_NATURALLY_IMMOVABLE = MASK_SELF_CANT_SWAP | MASK_OTHERS_SHUFFLE_ON;
    public final long MASKC_FLOATING_DISPLACEABLE = MASK_SELF_CANT_SWAP | MASK_CAN_SWAP_WITH_ANY;

    public int brushRadius = 2;
    public long[][] canvas;  // x then y

    public HashMap<Byte, String> specialBehaviours = new HashMap<>();

    public int canvasWidth = 100 * 2;
    public int canvasHeight = 60 * 2;
    public int sandSize = 5;

    // TODO: subjected for removal (temorary testing)
    public int count = 0;

    public final long CELL_AIR = cellEncodeData(color(0), (byte)0, 0, (byte)(MASKC_FLOATING_DISPLACEABLE));
    public final long CELL_BARRIER_FLOOR = cellEncodeData(color(0), (byte)-128, 0, (byte)(MASK_INDESTRUCTABLE | MASKC_NATURALLY_IMMOVABLE | MASK_TICKED));

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
                    (byte)(keyPressed && keyCode == CONTROL ? 2 : 1),
                    0,
                    (byte)((MASK_CAN_SHUFFLE)
                    | (count % 2 == 0 ? MASK_SWAP_UP : 0)
                    | (keyPressed && keyCode == CONTROL ? MASKC_NATURALLY_IMMOVABLE : 0)
                ));
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

        long resetFlag = ~MASK_TICKED;
        for (int x = 0; x < canvasWidth; x++) {
            for (int y = 0; y < canvasHeight; y++) {
                canvas[x][y] &= resetFlag;
            }
        }
    }

    public void cellTick(long cell, int x, int y, boolean canMove) {
        canvas[x][y] |= MASK_TICKED;

        if (canMove) {
            if (cellCommonsApplyGravity(cell, x, y)) {
                y++;
            } else if (cellIsFlagOn(cell, MASK_CAN_SHUFFLE)) {
                long updatedPos = cellApplyShuffling(cell, x, y);
                x = (int)(updatedPos >>> 32);
                y = (int)(updatedPos & Integer.MAX_VALUE);
            }
        }

        // TODO: apply special behaviour
    }

    /**
     * Apply cell shuffling. CAUTION: this function will update the canvas!
     * @param cell cell data
     * @param x cell x on canvas
     * @param y cell y on canvas
     * @param direction where the cell is moving towards according to gravity rule (see above for sector)
     * @return updated x and y coordinates. 32bits on the left is the x coordinates as int, and 32bits on the right is y coordinates as int
     *         <br> Use a bitwise mask to access the value {@code x = (int)(res >>> 32);} {@code y = (int)(res & Integer.MAX_VALUE)}
     */
    public long cellApplyShuffling(long cell, int x, int y) {
        int direction = cellSwapDirection(cell);
        if (direction == 0 || cellIsFlagOn(cell, MASK_SELF_CANT_SWAP)) return ((long)x << 32) | y;
        long cellAhead = cellAtXYSafe(x, y + direction);

        // shuffle rule:
        //      if the cell ahead hasnt ticked do it before attempting to shuffle
        //      you must shuffle on a surface that permits you to do so
        //      if you are unable to shuffle. you are prob on a still surface. permit others to shuffle on you
        //      you must yield to cells that are moving into your shuffling position
        //      the cell you are shuffling towards must be able to exchange with anyone
        //      cells that you must pass thru to get to your shuffled position (left or right, then up or down),
        //          must be of same type or can be freely swapped with

        if (!cellIsFlagOn(cellAhead, MASK_TICKED)) {
            cellTick(cellAhead, x, y + direction, true);
            cellAhead = cellAtXYSafe(x, y + direction);
        }

        if (cellIsFlagOn(cellAhead, MASK_OTHERS_SHUFFLE_ON)) {
            int targetY = y + direction;
            long cellLeft = cellAtXYSafe(x - 1, y);
            long cellRight = cellAtXYSafe(x + 1, y);

            // yield to cells that are already going towards your target
            if (
                cellIsFlagOn(cellAtXYSafe(x + 1, targetY), MASK_CAN_SWAP_WITH_ANY)
                && (cellIsFlagOn(cellRight, MASK_CAN_SWAP_WITH_ANY) || cellGetType(cell) == cellGetType(cellRight))
                && cellSwapDirection(cellRight) != direction
                && cellMoveRelative(x, y, 1, direction)
            ) {
                x++;
                y += direction;
            } else if (
                cellIsFlagOn(cellAtXYSafe(x - 1, targetY), MASK_CAN_SWAP_WITH_ANY)
                && (cellIsFlagOn(cellLeft, MASK_CAN_SWAP_WITH_ANY) || cellGetType(cell) == cellGetType(cellLeft))
                && cellSwapDirection(cellLeft) != direction
                && cellMoveRelative(x, y, -1, direction)
            ) {
                x--;
                y += direction;
            } else {
                canvas[x][y] |= MASK_OTHERS_SHUFFLE_ON;
            }
        } else {
            canvas[x][y] &= ~MASK_OTHERS_SHUFFLE_ON;
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
        int cellDirection = cellSwapDirection(cell);
        long cellBelow = cellAtXYSafe(x, y + 1);
        int cellBelowDirection = cellSwapDirection(cellBelow);
        int netDirection = cellDirection + cellBelowDirection;

        if (cellIsFlagOn(cell & cellBelow, MASK_SELF_CANT_SWAP)) return false;
        if (Math.abs(netDirection) == 2) return false;
        if (cellIsFlagOn(cellBelow, MASK_TICKED)) return false;
        
        boolean sameType = cellGetType(cell) == cellGetType(cellBelow);
        if (
            sameType
            && Math.abs(netDirection) == 0 && cellIsFlagOn(cellBelow, MASK_SWAP_UP)
            && !cellIsFlagOn(cell | cellBelow, MASK_SELF_CANT_SWAP)
        ) {
            return cellMoveRelative(x, y, 0, 1);
        } else if (
            Math.abs(netDirection) == 1
            && (
                (cellIsFlagOn(cell, MASK_CAN_SWAP_WITH_ANY) && cellBelowDirection == -1)
                || (cellDirection == 1 && cellIsFlagOn(cellBelow, MASK_CAN_SWAP_WITH_ANY))
            )
        ) {
            return cellMoveRelative(x, y, 0, 1);
        }

        return false;
    }

    /**
     * Removes the cell on the canvas (i.e replaces it with air). if {@link #MASK_INDESTRUCTABLE} is set then the cell cant be removed unless the user requested it to be destroyed
     * @param x cell x position
     * @param y cell y position
     * @param userRequested if true then the cell will be removed even with {@link #MASK_INDESTRUCTABLE}, else the cell can persist if that mask is set
     * @return true if it was removed successfully, otherwise false
     */
    public boolean cellRemove(int x, int y, boolean userRequested) {
        if (x < 0 || x >= canvasWidth || isYOutOfBounds(y)) return false;
        if (cellIsFlagOn(canvas[x][y], MASK_INDESTRUCTABLE) && !userRequested) return false;
        
        canvas[x][y] = CELL_AIR;
        return true;
    }

    /**
     * Get the direction that the cell wants to swap towards. If cell has {@link #MASK_SELF_CANT_SWAP} then return is 0
     * @param cell
     * @return 1 if going down, -1 is going up, 0 if {@link #MASK_SELF_CANT_SWAP} is enabled
     */
    public int cellSwapDirection(long cell) {
        if (cellIsFlagOn(cell, MASK_SELF_CANT_SWAP)) return 0;
        return cellIsFlagOn(cell, MASK_SWAP_UP) ? -1 : 1;
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
     * swap two cells. will fail if target has the following mask: {@link #MASK_OTHERS_CANT_SWAP}.
     *      <br> NOTE: this function will only check if the target exists. it assumes that (x, y) are valid coordinates
     * @param x
     * @param y
     * @param targetX
     * @param targetY
     * @return true if the swap was successful, false otherwise
     */
    public boolean cellMoveAbsolute(int x, int y, int targetX, int targetY) {
        long inital = canvas[x][y];
        long target = cellAtXYSafe(targetX, targetY);
        
        canvas[x][y] = target;
        canvas[targetX][targetY] = inital;

        if (!cellIsFlagOn(target, MASK_TICKED)) cellTick(target, x, y, false);

        return true;
    }

    public boolean cellMoveRelative(int x, int y, int relX, int relY) {
        return cellMoveAbsolute(x, y, x + relX, y + relY);
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