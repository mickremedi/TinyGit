package gitlet;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.PrintStream;
import java.nio.file.FileVisitOption;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Comparator;

import static org.junit.Assert.*;

public class ControllerTest {

    private ByteArrayOutputStream outContent = new ByteArrayOutputStream();
    private String fileName = "testFile.txt";
    Controller c;

    @Before
    public void setUp() throws Exception {
        System.setOut(new PrintStream(outContent));
        File f = new File(fileName);
        Utils.writeContents(f, "hello there");
        c = new Controller();
        c.parseLine(array("init"));
    }

    @After
    public void tearDown() throws Exception {
        System.setOut(null);
        Utils.restrictedDelete(fileName);
        Path dirPath = Paths.get("./.gitlet");
        if (Files.exists(dirPath)) {
            Files.walk(dirPath, FileVisitOption.FOLLOW_LINKS)
                .sorted(Comparator.reverseOrder())
                .map(Path::toFile)
                .forEach(File::delete);
        }

    }

    public String[] array(String... operands) {
        return operands;
    }

    @Test
    public void init() throws Exception {
        File hidden = new File(".gitlet");
        assertEquals(true, hidden.exists() && hidden.isDirectory());

    }

    @Test
    public void getHead() throws Exception {
        Commit initial = c.getHead();
        assertEquals("initial commit", initial.getMessage());
    }

    @Test
    public void add() throws Exception {
        c.parseLine(array("add", fileName));

        Commit head = c.getHead();
        assertEquals(true, head.getStaged().containsKey(fileName));

        File trueFile = new File(fileName);

        String fileHash = head.getStaged().get(fileName);
        File hashedFile = new File(".gitlet/" + fileHash);
        assertEquals(true, hashedFile.exists());

        String trueFileContents = Utils.readContentsAsString(trueFile);
        String hashedFileContents = Utils.readContentsAsString(hashedFile);

        assertEquals(trueFileContents, hashedFileContents);


    }

    @Test
    public void commit() throws Exception {
        c.parseLine(array("add", fileName));
        c.parseLine(array("commit", "this is a test"));

        Commit head = c.getHead();
        assertEquals("this is a test", head.getMessage());
        assertEquals(true, head.getTracked().containsKey(fileName));
    }

    @Test
    public void remove() throws Exception {
        c.parseLine(array("add", fileName));
        c.parseLine(array("rm", fileName));

        Commit head = c.getHead();
        assertEquals(true, head.getStaged().isEmpty());

        c.parseLine(array("add", fileName));
        c.parseLine(array("commit", "test"));
        c.parseLine(array("rm", fileName));

        head = c.getHead();
        assertEquals(true, head.getStaged().isEmpty());
        assertEquals(false, head.getTracked().isEmpty());

        File f = new File(fileName);
        assertEquals(false, f.exists());

        c.parseLine(array("commit", "test2"));
        head = c.getHead();
        assertEquals(true, head.getTracked().isEmpty());
    }

    @Test
    public void find() throws Exception {
        c.parseLine(array("add", fileName));
        c.parseLine(array("commit", "test"));
        c.parseLine(array("add", "Makefile"));

        c.parseLine(array("commit", "test2"));
        c.parseLine(array("find", "test"));

        String[] commits = outContent.toString().split("\n");
        Commit current = Commit.loadCommit(commits[0]);
        assertEquals("test", current.getMessage());


    }

    @Test
    public void merge() throws Exception {
        File f = new File(fileName);

        c.parseLine(array("add", fileName));
        c.parseLine(array("commit", "test"));

        c.parseLine(array("branch", "newBranch"));

        Utils.writeContents(f, "wazzup");

        c.parseLine(array("add", fileName));
        c.parseLine(array("commit", "test2"));

        c.parseLine(array("checkout", "newBranch"));

        Utils.writeContents(f, "yoyoyo");

        c.parseLine(array("add", fileName));
        c.parseLine(array("commit", "test3"));
        c.parseLine(array("checkout", "master"));
        c.parseLine(array("merge", "newBranch"));

    }

}
