package gitlet;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Comparator;
import java.util.Scanner;

/** Driver class for Gitlet, the tiny stupid version-control system.
 *  @author
 */
public class Main {

    /** Usage: java gitlet.Main ARGS, where ARGS contains
     *  <COMMAND> <OPERAND> .... */
    public static void main(String... args) throws IOException {
        Controller controller = new Controller();
        Scanner input = new Scanner(System.in);
        while (true) {
            try {
                System.out.print("(gitlet) ");
                controller.parseLine(input.nextLine());
            } catch (GitletException e) {
                System.out.println(e.getMessage());
                Path dirPath = Paths.get( "./.gitlet" );
                Files.walk( dirPath )
                    .map( Path::toFile )
                    .sorted( Comparator.comparing( File::isDirectory ) )
                    .forEach( File::delete );                System.exit(0);
            }
        }
    }

}
