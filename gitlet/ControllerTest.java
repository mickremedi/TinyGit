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
        c.parseLine("init");
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
        c.parseLine("add " + fileName);

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
        c.parseLine("add " + fileName);
        c.parseLine("commit \"this is a test\"");

        Commit head = c.getHead();
        assertEquals("this is a test", head.getMessage());
        assertEquals(true, head.getFiles().containsKey(fileName));
    }

    @Test
    public void remove() throws Exception {
        c.parseLine("add " + fileName);
        c.parseLine("rm " + fileName);

        Commit head = c.getHead();
        assertEquals(true, head.getStaged().isEmpty());

        c.parseLine("add " + fileName);
        c.parseLine("commit \"test\"");
        c.parseLine("rm " + fileName);

        head = c.getHead();
        assertEquals(true, head.getStaged().isEmpty());
        assertEquals(false, head.getFiles().isEmpty());

        File f = new File(fileName);
        assertEquals(false, f.exists());

        c.parseLine("commit \"test2\"");
        head = c.getHead();
        assertEquals(true, head.getFiles().isEmpty());
    }

    @Test
    public void log() throws Exception {
        c.parseLine("log");

        String result = outContent.toString();

        File f = new File("tests/logTest.txt");
        String expected = Utils.readContentsAsString(f);
        assertEquals(expected, result);
    }

    @Test
    public void find() throws Exception {
        c.parseLine("add " + fileName);
        c.parseLine("commit \"test\"");
        c.parseLine("add Makefile");

        Commit head = c.getHead();

        c.parseLine("commit \"test2\"");
        c.parseLine("find \"test\"");

        String hash = Utils.sha1(Utils.serialize(head));
        assertEquals(hash + "\n", outContent.toString());


    }

    @Test
    public void status() throws Exception {
    }

    @Test
    public void checkout() throws Exception {
    }

    @Test
    public void branch() throws Exception {
    }

    @Test
    public void removeBranch() throws Exception {
    }

    @Test
    public void reset() throws Exception {
    }

    @Test
    public void merge() throws Exception {
    }

    @Test
    public void committoFile() throws Exception {
    }

    @Test
    public void removeCommitFile() throws Exception {
    }

}
