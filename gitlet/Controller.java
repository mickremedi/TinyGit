package gitlet;

import javax.rmi.CORBA.Util;
import java.io.File;
import java.util.Date;
import java.util.HashMap;
import java.util.function.Consumer;

public class Controller {

    HashMap<String, Consumer<String[]>> commands = new HashMap<>();

    public Controller() {
        commands.put("init", this::init);
        commands.put("add", this::add);
        commands.put("commit", this::commit);
//        commands.put("rm", this::remove);
//        commands.put("log", this::log);
//        commands.put("global-log", this::global_log);
//        commands.put("find", this::find);
//        commands.put("status", this::status);
//        commands.put("checkout", this::checkout);
//        commands.put("rm-branch", this::remove_branch);
//        commands.put("reset", this::reset);
//        commands.put("merge", this::merge);


    }

    public void parseLine(String command) throws GitletException {
        String[] operands = command.split(" ");

        if (operands.length < 1) {
            throw Utils.error("Please enter a command.");
        }

        if (!commands.containsKey(operands[0])) {
            throw Utils.error("No command with that name exists.");
        }

        File gitlet = new File(".gitlet");
        if (!operands[0].equals("init") && !gitlet.exists()) {
            throw Utils.error("Not in an initialized Gitlet directory.");
        }

        commands.get(operands[0]).accept(operands);
    }

    public void init(String[] unused) {
        if (unused.length != 1) {
            throw Utils.error("Incorrect operands.");
        }
        File hiddenDir = new File(".gitlet");
        if (hiddenDir.isDirectory()) {
            throw Utils.error("A Gitlet version-control system already exists in the current directory.");
        }
        hiddenDir.mkdir();
        Date firstDay = new Date();
        firstDay.setTime(0);
        Commit head = new Commit("initial commit", firstDay, null);
        committoFile(head);
    }

    public void add(String[] operands) {
        if (operands.length != 2) {
            throw Utils.error("Incorrect operands.");
        }
        Commit head = getHead();
        removeCommitFile(head);
        head.updateStaged(operands[1]);
        committoFile(head);
    }

    public void commit(String[] operands) {
        if (operands.length != 2) {
            throw Utils.error("Incorrect operands.");
        }
        Commit head = getHead();
        Date current = new Date();
        Commit newCommit = new Commit(operands[1], current, head);
        committoFile(newCommit);
    }

    public void remove(String[] operands) {

    }

    public void log(String[] unused) {

    }

    public void global_log(String[] unused) {

    }

    public void find(String[] operands) {

    }

    public void status(String[] unused) {

    }

    public void checkout(String[] unused) {

    }

    public void branch(String[] operands) {

    }

    public void remove_branch(String[] operands) {

    }

    public void reset(String[] unused) {

    }

    public void merge(String[] operands) {

    }

    public Commit getHead() {
        File head = new File(".gitlet/head");
        String headReference = Utils.readContentsAsString(head);
        File actualHead = new File(".gitlet/" + headReference);
        return Utils.readObject(actualHead, Commit.class);
    }

    public void committoFile(Commit head) {
        byte[] serialized = Utils.serialize(head);
        String hashed = Utils.sha1(serialized);
        head.storeCommit(hashed);
        File headFile = new File(".gitlet/head");
        Utils.writeContents(headFile, hashed);
    }

    public void removeCommitFile(Commit head) {
        byte[] serialized = Utils.serialize(head);
        String hashed = Utils.sha1(serialized);
        File f = new File(".gitlet/" + hashed);
        f.delete();
    }
}
