package gitlet;

import java.io.IOException;

/**
 * Driver class for Gitlet, the tiny stupid version-control system.
 *
 * @author Michael Remediakis
 * Worked with Mariam Abughosh, Gurpreet Singh, Fatih AK
 */
public class Main {

    /**
     * Usage: java gitlet.Main ARGS, where ARGS contains
     * <COMMAND> <OPERAND> ....
     */
    public static void main(String... args) throws IOException {
        Controller controller = new Controller();
        try {
            controller.parseLine(args);
        } catch (GitletException e) {
            System.out.println(e.getMessage());
            System.exit(0);
        }
    }

}
