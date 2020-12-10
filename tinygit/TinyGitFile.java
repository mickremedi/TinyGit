package tinygit;

import java.io.File;

/**
 * Wrapper class that controls whether we are looking
 * at the local vs remote repository.
 * @author Michael Remediakis, Fatih AK
 */
public class TinyGitFile extends File {

    /**
     * Preceeding path to file.
     */
    private static String _remotePath = "";

    /**
     * Creates a path with given PATHNAME.
     */
    public TinyGitFile(String pathname) {
        super(_remotePath + pathname);
    }

    /**
     * Creates a path with given PARENT file and
     * CHILD path.
     */
    public TinyGitFile(File parent, String child) {
        super(parent, _remotePath + child);
    }

    /**
     * Sets the REMOTEPATH variable.
     */
    public static void setRemotePath(String remotePath) {
        TinyGitFile._remotePath = remotePath;
    }
}
