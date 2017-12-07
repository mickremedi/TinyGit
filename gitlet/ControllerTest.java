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
    private String fileOne = "testFile.txt";
    private String fileTwo = "otherFile.txt";
    Controller c;

    @Before
    public void setUp() throws Exception {
        System.setOut(new PrintStream(outContent));
        writeFile(fileOne, "Hello there");
        writeFile(fileTwo, "what is up");

        c = new Controller();
        c.parseLine("init");
    }

    @After
    public void tearDown() throws Exception {
        System.setOut(null);
        Utils.restrictedDelete(fileOne);
        Utils.restrictedDelete(fileTwo);
        Path dirPath = Paths.get("./.gitlet");
        if (Files.exists(dirPath)) {
            Files.walk(dirPath, FileVisitOption.FOLLOW_LINKS)
                .sorted(Comparator.reverseOrder())
                .map(Path::toFile)
                .forEach(File::delete);
        }

    }

    public void writeFile(String name, String content) {
        File f = new File(name);
        Utils.writeContents(f, content);
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
        c.parseLine("add", fileOne);

        Commit head = c.getHead();
        assertEquals(true, head.getStaged().containsKey(fileOne));

        File trueFile = new File(fileOne);

        String fileHash = head.getStaged().get(fileOne);
        File hashedFile = new File(".gitlet/" + fileHash);
        assertEquals(true, hashedFile.exists());

        String trueFileContents = Utils.readContentsAsString(trueFile);
        String hashedFileContents = Utils.readContentsAsString(hashedFile);

        assertEquals(trueFileContents, hashedFileContents);


    }

    @Test
    public void commit() throws Exception {
        c.parseLine("add", fileOne);
        c.parseLine("commit", "this is a test");

        Commit head = c.getHead();
        assertEquals("this is a test", head.getMessage());
        assertEquals(true, head.getTracked().containsKey(fileOne));
    }

    @Test
    public void remove() throws Exception {
        c.parseLine("add", fileOne);
        c.parseLine("rm", fileOne);

        Commit head = c.getHead();
        assertEquals(true, head.getStaged().isEmpty());

        c.parseLine("add", fileOne);
        c.parseLine("commit", "test");
        c.parseLine("rm", fileOne);

        head = c.getHead();
        assertEquals(true, head.getStaged().isEmpty());
        assertEquals(false, head.getTracked().isEmpty());

        File f = new File(fileOne);
        assertEquals(false, f.exists());

        c.parseLine("commit", "test2");
        head = c.getHead();
        assertEquals(true, head.getTracked().isEmpty());
    }

    @Test
    public void find() throws Exception {
        c.parseLine("add", fileOne);
        c.parseLine("commit", "test");
        c.parseLine("add", fileTwo);

        c.parseLine("commit", "test2");
        c.parseLine("find", "test");

        String[] commits = outContent.toString().split("\n");
        Commit current = Commit.loadCommit(commits[0]);
        assertEquals("test", current.getMessage());


    }

    @Test
    public void reset() throws Exception {
        c.parseLine("add", fileOne);
        c.parseLine("add", fileTwo);
        c.parseLine("commit", "two files");
        String secondHash = c.getHeadHash();

        c.parseLine("branch", "newBranch");

        File f3 = new File("h.txt");
        Utils.writeContents(f3, "h content");


        c.parseLine("add", "h.txt");
        c.parseLine("rm", fileOne);
        c.parseLine("commit", "added h removed fileone");

        c.parseLine("checkout", "newBranch");
        c.parseLine("rm", fileTwo);

        File f4 = new File("k.txt");
        Utils.writeContents(f4, "k content");

        c.parseLine("add", "k.txt");
        c.parseLine("commit", "added k removed filetwo");

        c.parseLine("checkout", "master");

        File f5 = new File("m.txt");
        Utils.writeContents(f5, "m content");

        c.parseLine("add", "m.txt");


        c.parseLine("reset", secondHash.substring(0, 6));

        c.parseLine("status");

        f3.delete();
        f4.delete();
        f5.delete();

    }

    @Test
    public void merge() throws Exception {
        File f = new File(fileOne);
        File f2 = new File(fileTwo);

        c.parseLine("add", fileOne);
        c.parseLine("add", fileTwo);
        c.parseLine("commit", "two files");

        c.parseLine("branch", "newBranch");

        File f3 = new File("h.txt");
        Utils.writeContents(f3, "h content");


        c.parseLine("add", "h.txt");
        c.parseLine("rm", fileOne);

        Utils.writeContents(f2, "i dont know");
        c.parseLine("add", fileTwo);

        c.parseLine("commit", "added h removed fileone, change filetwo");

        c.parseLine("checkout", "newBranch");

        Utils.writeContents(f2, "i dont know two");
        c.parseLine("add", fileTwo);

        File f4 = new File("k.txt");
        Utils.writeContents(f4, "k content");

        c.parseLine("add", "k.txt");
        c.parseLine("commit", "added k modified filetwo");

        c.parseLine("checkout", "master");
        c.parseLine("merge", "newBranch");
        c.parseLine("status");

        assertEquals(false, f.exists());
        assertEquals(true, f2.exists());
        assertEquals(true, f3.exists());
        assertEquals(true, f4.exists());

        f3.delete();
        f4.delete();
    }

    @Test
    public void checkout() throws Exception {
        File f = new File(fileOne);

        c.parseLine("add", fileOne);
        c.parseLine("commit", "version one");

        String version1 = c.getHeadHash().substring(0, 6);

        Utils.writeContents(f, "version 2");

        c.parseLine("add", fileOne);
        c.parseLine("commit", "version two");

        String version2 = c.getHeadHash().substring(0, 6);

        c.parseLine("checkout", version1, "--", fileOne);

        c.parseLine("checkout", version2, "--", fileOne);


    }

}
