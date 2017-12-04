package gitlet;

import java.io.File;
import java.util.*;
import java.util.function.Consumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class Controller {

    HashMap<String, Consumer<String[]>> commands = new HashMap<>();
    Commit head;

    public Controller() {
        commands.put("init", this::init);
        commands.put("add", this::add);
        commands.put("commit", this::commit);
        commands.put("rm", this::remove);
        commands.put("log", this::log);
        commands.put("global-log", this::globalLog);
        commands.put("find", this::find);
//        commands.put("status", this::status);
//        commands.put("checkout", this::checkout);
//        commands.put("rm-branch", this::removeBranch);
//        commands.put("reset", this::reset);
//        commands.put("merge", this::merge);


    }

    public void parseLine(String command) throws GitletException {
        Pattern x = Pattern.compile("(([\\w.-]+)|(\"[\\w\\s.-]+\"))+");
        Matcher matcher = x.matcher(command);
        ArrayList<String> matches = new ArrayList<>();
        while (matcher.find()) {
            matches.add(matcher.group());
        }

        String[] operands = matches.toArray(new String[matches.size()]);

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

        if (!operands[0].equals("init")) {
            head = getHead();
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

        File commitDir = new File(".gitlet/Commit");
        commitDir.mkdir();

        Date firstDay = new Date();
        firstDay.setTime(0);
        Commit head = new Commit("initial commit", firstDay, null);
        committoFile(head);
    }

    public void add(String[] operands) {
        if (operands.length != 2) {
            throw Utils.error("Incorrect operands.");
        }
        removeCommitFile(head);
        head.addToStage(operands[1]);
        committoFile(head);
    }

    public void commit(String[] operands) {
        if (operands.length != 2) {
            throw Utils.error("Incorrect operands.");
        }
        Date current = new Date();
        String message = operands[1].replaceAll("^\"|\"$", "");
        Commit newCommit = new Commit(message, current, head);
        committoFile(newCommit);
    }

    public void remove(String[] operands) {
        if (operands.length != 2) {
            throw Utils.error("Incorrect operands.");
        }
        removeCommitFile(head);
        head.remove(operands[1]);
        committoFile(head);
    }

    public void log(String[] unused) {
        if (unused.length != 1) {
            throw Utils.error("Incorrect operands.");
        }
        Commit tracker = head;
        while (tracker != null) {
            System.out.println("===");
            tracker.log();
            System.out.println();
            tracker = tracker.getParent();
        }

    }

    public void globalLog(String[] unused) {
        if (unused.length != 1) {
            throw Utils.error("Incorrect operands.");
        }
        List<String> commitNames = Utils.plainFilenamesIn(".gitlet/Commit");
        for(String commit: commitNames) {
            System.out.println("===");
            Commit.loadCommit(commit).log();
            System.out.println();
        }

    }

    public void find(String[] operands) {
        if (operands.length != 2) {
            throw Utils.error("Incorrect operands.");
        }
        String message = operands[1].replaceAll("^\"|\"$", "");

        List<String> commitNames = Utils.plainFilenamesIn(".gitlet/Commit");
        boolean found = false;
        for(String commit: commitNames) {
            String curr = Commit.loadCommit(commit).getMessage();
            if (curr.equals(message)) {
                System.out.println(commit);
                found = true;
            }
        }
        if(!found) {
            throw Utils.error("Found no commit with that message.");
        }

    }

    public void status(String[] unused) {

    }

    public void checkout(String[] unused) {

    }

    public void branch(String[] operands) {

    }

    public void removeBranch(String[] operands) {

    }

    public void reset(String[] unused) {

    }

    public void merge(String[] operands) {

    }

    public Commit getHead() {
        File head = new File(".gitlet/head");
        String headReference = Utils.readContentsAsString(head);
        File actualHead = new File(".gitlet/Commit/" + headReference);
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
        File f = new File(".gitlet/Commit/" + hashed);
        f.delete();
    }
}
