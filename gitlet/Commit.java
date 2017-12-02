package gitlet;

import java.io.File;
import java.io.Serializable;
import java.util.Date;
import java.util.HashMap;

public class Commit implements Serializable {

    private Date time;
    private String message;
    private String parentReference;
    public HashMap<String, String> files;
    public HashMap<String, String> staged;


    public Commit(String message, Date time, Commit parent) {
        if (message.length() == 0) {
            throw Utils.error("Please enter a commit message.");
        }
        this.time = time;
        this.message = message;
        this.parentReference = Utils.sha1(Utils.serialize(parent));
        staged = new HashMap<>();
        files = new HashMap<>();
        if (parent != null) {
            fillFileReferences(parent);
        }
    }

    private void fillFileReferences(Commit parent) {
        if (parent.staged.isEmpty()) {
            throw Utils.error("No changes added to the commit.");
        }
        for (String file: parent.files.keySet()) {
            files.put(file, parent.files.get(file));
        }
        for (String newFile: parent.staged.keySet()) {
            files.put(newFile, parent.staged.get(newFile));
        }
    }

    public void updateStaged(String fileName) {
        File file = new File(fileName);
        if (!file.exists()) {
            throw Utils.error("File does not exist.");
        }
        String content = Utils.readContentsAsString(file);
        String hash = Utils.sha1(content);
        staged.put(fileName, hash);
        if (!hash.equals(files.get(fileName))) {
            File newFile = new File(".gitlet/" + hash);
            Utils.writeContents(newFile, content);
        } else {
            staged.remove(fileName);
        }



    }

    public void storeCommit(String fileName) {
        File file = new File(".gitlet/" + fileName);
        Utils.writeObject(file, this);
    }

    public static Commit loadCommit(String fileName) {
        File file = new File(".gitlet/" + fileName);
        return Utils.readObject(file, Commit.class);
    }
}
