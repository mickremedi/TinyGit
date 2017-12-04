package gitlet;

import java.io.File;
import java.io.Serializable;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;

public class Commit implements Serializable {

    private Date _time;
    private String _message;
    private String _parentReference;
    private HashMap<String, String> _files;
    private HashMap<String, String> _staged;
    private ArrayList<String> _untracked;


    public Commit(String message, Date time, Commit parent) {
        if (message.length() == 0) {
            throw Utils.error("Please enter a commit message.");
        }
        _time = time;
        _message = message;
        _parentReference = Utils.sha1(Utils.serialize(parent));
        _staged = new HashMap<>();
        _files = new HashMap<>();
        _untracked = new ArrayList<>();
        if (parent != null) {
            fillFileReferences(parent);
        }
    }

    private void fillFileReferences(Commit parent) {
        if (parent._staged.isEmpty() && parent.getUntracked().isEmpty()) {
            throw Utils.error("No changes added to the commit.");
        }
        for (String file: parent._files.keySet()) {
            if (!parent._untracked.contains(file)) {
                _files.put(file, parent._files.get(file));
            }
        }
        for (String newFile: parent._staged.keySet()) {
            _files.put(newFile, parent._staged.get(newFile));
        }
    }

    public void addToStage(String fileName) {
        File file = new File(fileName);
        if (!file.exists()) {
            throw Utils.error("File does not exist.");
        }
        String content = Utils.readContentsAsString(file);
        String hash = Utils.sha1(content);
        _staged.put(fileName, hash);
        if (!hash.equals(_files.get(fileName))) {
            File newFile = new File(".gitlet/" + hash);
            Utils.writeContents(newFile, content);
        } else {
            _staged.remove(fileName);
        }
    }

    public void remove(String fileName) {
        if (!_staged.containsKey(fileName) && !_files.containsKey(fileName)) {
            throw Utils.error("No reason to remove the file.");
        }
        _staged.remove(fileName);
        if (_files.containsKey(fileName)) {
            untrack(fileName);
            Utils.restrictedDelete(fileName);
        }
    }

    public void untrack(String fileName) {
        _untracked.add(fileName);
    }

    public void storeCommit(String fileName) {
        File file = new File(".gitlet/Commit/" + fileName);
        Utils.writeObject(file, this);
    }

    public static Commit loadCommit(String fileName) {
        if (fileName.equals(Utils.sha1(Utils.serialize(null)))) {
            return null;
        }
        File file = new File(".gitlet/Commit/" + fileName);
        return Utils.readObject(file, Commit.class);
    }

    public void log() {
        String hash = Utils.sha1(Utils.serialize(this));
        System.out.println("commit " + hash);

        SimpleDateFormat formatter = new SimpleDateFormat(
            "EEE MMM dd H:mm:ss yyyy Z"
        );
        System.out.println("Date: " + formatter.format(_time));
        System.out.println(this._message);


    }


    public Commit getParent() {
        return loadCommit(_parentReference);
    }

    public String getMessage() {
        return _message;
    }

    public HashMap<String, String> getFiles() {
        return _files;
    }

    public HashMap<String, String> getStaged() {
        return _staged;
    }

    public ArrayList<String> getUntracked() {
        return _untracked;
    }
}
