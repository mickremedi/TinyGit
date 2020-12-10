package tinygit;

import org.junit.Test;
import org.junit.runner.JUnitCore;

/** The suite of all JUnit tests for the tinygit package.
 *  @author
 */

public class UnitTest {

    /** Run the JUnit tests in the loa package. Add xxxTest.class entries to
     *  the arguments of runClasses to run other JUnit tests. */
    public static void main(String[] ignored) {
        JUnitCore.runClasses(UnitTest.class, ControllerTest.class);
    }

    /** A dummy test to avoid complaint. */
    @Test
    public void placeholderTest() {
    }

}


