package gitlet;

import java.io.File;
import java.io.IOException;
import java.nio.file.FileVisitOption;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Comparator;

/**
 * Driver class for Gitlet, the tiny stupid version-control system.
 *
 * @author Michael Remediakis
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
//            Path dirPath = Paths.get("./.gitlet");
//            if (Files.exists(dirPath)) {
//                Files.walk(dirPath, FileVisitOption.FOLLOW_LINKS)
//                    .sorted(Comparator.reverseOrder())
//                    .map(Path::toFile)
//                    .forEach(File::delete);
//            }
            System.exit(0);
        }
    }

}
