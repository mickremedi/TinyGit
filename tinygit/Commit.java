package tinygit;

import java.io.Serializable;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;

/**
 * This is a class to represent a commit.
 *
 * @author Michael Remediakis
 */
public class Commit implements Serializable {

    /**
     * The time the commit was made.
     */
    private Date _time;
    /**
     * The message given by the commit.
     */
    private String _message;
    /**
     * A reference to the parent of this commit.
     */
    private String _parentReference;
    /**
     * If this commit was caused by a merge, this is a reference to the other parent
     * commit.
     */
    private String _otherParentReference = null;
    /**
     * A map to all of the tracked files of this commit.
     */
    private HashMap<String, String> _tracked;
    /**
     * A map to all of the staged file for the next commit.
     */
    private HashMap<String, String> _staged;
    /**
     * A map to all of the files that are about to be removed in the next commit.
     */
    private ArrayList<String> _untracked;

    /**
     * Initializes a new commit with the given MESSAGE, TIME of commit, and a
     * reference to the PARENT commit.
     */
    public Commit(String message, Date time, String parent) {
        if (message.length() == 0) {
            throw Utils.error("Please enter a commit message.");
        }
        _time = time;
        _message = message;
        _parentReference = parent;
        _staged = new HashMap<>();
        _tracked = new HashMap<>();
        _untracked = new ArrayList<>();

        Commit parentCommit = getParent();
        if (parentCommit != null) {
            fillFileReferences(parentCommit);
        }
    }

    /**
     * Initializes a new commit caused by a merge with the given MESSAGE, TIME of
     * commit, and a reference to the PARENT commit as well as the OTHERPARENT of
     * this commit.
     */
    public Commit(String message, Date time, String parent, String otherParent) {
        this(message, time, parent);
        _otherParentReference = otherParent;
    }

    /**
     * Tracks any files tracked/staged from the PARENT commit and removes any files
     * marked to be removed.
     */
    private void fillFileReferences(Commit parent) {
        if (parent._staged.isEmpty() && parent.getUntracked().isEmpty()) {
            throw Utils.error("No changes added to the commit.");
        }
        for (String file : parent._tracked.keySet()) {
            if (!parent._untracked.contains(file)) {
                _tracked.put(file, parent._tracked.get(file));
            }
        }
        for (String newFile : parent._staged.keySet()) {
            _tracked.put(newFile, parent._staged.get(newFile));
        }
    }

    /**
     * Adds the given FILENAME to the stage for the next commit.
     */
    public void addToStage(String fileName) {
        TinyGitFile file = new TinyGitFile(fileName);
        if (!file.exists()) {
            throw Utils.error("File does not exist.");
        }
        if (_untracked.contains(fileName)) {
            _untracked.remove(fileName);
            return;
        }
        String content = Utils.readContentsAsString(file);
        String hash = Utils.sha1(content);
        _staged.put(fileName, hash);
        if (!hash.equals(_tracked.get(fileName))) {
            TinyGitFile newFile = new TinyGitFile(".tinygit/" + hash);
            Utils.writeContents(newFile, content);
        } else {
            _staged.remove(fileName);
        }
    }

    /**
     * Removes the given FILENAME from the stage and if it is currently being
     * tracked by the commit, marks it to be untracked in the next commit.
     */
    public void remove(String fileName) {
        if (!_staged.containsKey(fileName) && !_tracked.containsKey(fileName)) {
            throw Utils.error("No reason to remove the file.");
        }
        _staged.remove(fileName);
        if (_tracked.containsKey(fileName)) {
            untrack(fileName);
            Utils.restrictedDelete(fileName);
        }
    }

    /**
     * Marks a given FILENAME to be untracked.
     */
    public void untrack(String fileName) {
        _untracked.add(fileName);
    }

    /**
     * Stores the commit into the given FILENAME.
     */
    public void storeCommit(String fileName) {
        TinyGitFile file = new TinyGitFile(".tinygit/Commit/" + fileName);
        Utils.writeObject(file, this);
    }

    /**
     * Returns the commit from the given FILENAME.
     */
    public static Commit loadCommit(String fileName) {
        if (fileName.equals(Utils.sha1(Utils.serialize(null)))) {
            return null;
        }
        TinyGitFile file = null;
        boolean exists = false;
        if (fileName.length() < Utils.UID_LENGTH) {
            List<String> commits = Utils.plainFilenamesIn(".tinygit/Commit");
            for (String commitName : commits) {
                if (commitName.startsWith(fileName)) {
                    file = new TinyGitFile(".tinygit/Commit/" + commitName);
                    exists = true;
                }
            }

        } else {
            file = new TinyGitFile(".tinygit/Commit/" + fileName);
            exists = file.exists();
        }
        if (!exists) {
            throw Utils.error("No commit with that id exists.");
        }
        return Utils.readObject(file, Commit.class);
    }

    /**
     * Prints out the log of the commit, with the given HASH of the commit.
     *
     * @param hash
     */
    public void log(String hash) {
        System.out.println("commit " + hash);

        if (_otherParentReference != null) {
            System.out.println(
                    "Merge: " + _parentReference.substring(0, 7) + " " + _otherParentReference.substring(0, 7));
        }

        SimpleDateFormat formatter = new SimpleDateFormat("EEE MMM d HH:mm:ss yyyy Z");
        System.out.println("Date: " + formatter.format(_time));
        System.out.println(this._message);

    }

    /**
     * Returns the parent of the commit.
     */
    public Commit getParent() {
        return loadCommit(_parentReference);
    }

    /**
     * Returns the parent hash of the commit.
     */
    public String getParentHash() {
        return _parentReference;
    }

    /**
     * Returns the message of the commit.
     */
    public String getMessage() {
        return _message;
    }

    /**
     * Returns a map to all the tracked files of the commit.
     */
    public HashMap<String, String> getTracked() {
        return _tracked;
    }

    /**
     * Returns a map of all the files staged for the next commit.
     */
    public HashMap<String, String> getStaged() {
        return _staged;
    }

    /**
     * Returns a map of all the files marked to be removed for the next commit.
     */
    public ArrayList<String> getUntracked() {
        return _untracked;
    }
}
