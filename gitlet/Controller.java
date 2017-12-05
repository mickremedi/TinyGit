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
        commands.put("status", this::status);
        commands.put("checkout", this::checkout);
        commands.put("branch", this::branch);
        commands.put("rm-branch", this::removeBranch);
        commands.put("reset", this::reset);
//        commands.put("merge", this::merge);


    }

    public void parseLine(String[] command) throws GitletException {
        if (command.length < 1) {
            throw Utils.error("Please enter a command.");
        }

        if (!commands.containsKey(command[0])) {
            throw Utils.error("No command with that name exists.");
        }

        File gitlet = new File(".gitlet");
        if (!command[0].equals("init") && !gitlet.exists()) {
            throw Utils.error("Not in an initialized Gitlet directory.");
        }

        if (!command[0].equals("init")) {
            head = getHead();
        }

        commands.get(command[0]).accept(command);
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

        File branchDir = new File(".gitlet/Branch");
        branchDir.mkdir();

        File masterFile = new File(".gitlet/head");
        Utils.writeContents(masterFile, "master");

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
        for (String commit : commitNames) {
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
        for (String commit : commitNames) {
            String curr = Commit.loadCommit(commit).getMessage();
            if (curr.equals(message)) {
                System.out.println(commit);
                found = true;
            }
        }
        if (!found) {
            throw Utils.error("Found no commit with that message.");
        }

    }

    public void status(String[] unused) {
        if (unused.length != 1) {
            throw Utils.error("Incorrect operands.");
        }
        Commit head = getHead();

        List<String> branchNames = Utils.plainFilenamesIn(".gitlet/Branch");
        Collections.sort(branchNames);

        System.out.println("=== Branches ===");
        for (String name : branchNames) {
            if (getBranch().equals(name)) {
                System.out.print("*");
            }
            System.out.println(name);
        }
        System.out.println();

        Set<String> stagedSet = head.getStaged().keySet();
        List<String> stagedNames = new ArrayList<>(stagedSet);

        System.out.println("=== Staged Files ===");
        Collections.sort(stagedNames);
        for (String name : stagedNames) {
            System.out.println(name);
        }
        System.out.println();

        List<String> removedNames = head.getUntracked();
        Collections.sort(removedNames);

        System.out.println("=== Removed Files ===");
        for (String name : removedNames) {
            System.out.println(name);
        }
        System.out.println();

        Set<String> trackedSet = head.getTracked().keySet();
        List<String> trackedNames = new ArrayList<>(trackedSet);
        Collections.sort(trackedNames);

        List<String> modified = new ArrayList<>();
        System.out.println("=== Modifications Not Staged For Commit ===");

        for (String name : stagedNames) {
            File f = new File(name);
            if (!f.exists()) {
                modified.add(name + " (deleted)");
            } else {
                String fileContent = Utils.readContentsAsString(f);
                String hashed = Utils.sha1(fileContent);
                if (!hashed.equals(head.getStaged().get(name))) {
                    modified.add(name + " (modified)");
                }
            }
        }

        for (String name : trackedNames) {
            File f = new File(name);
            if (!f.exists() && !removedNames.contains(name)) {
                modified.add(name + " (deleted)");
            }
            if (f.exists()) {
                String fileContent = Utils.readContentsAsString(f);
                String hashed = Utils.sha1(fileContent);
                if (!stagedSet.contains(name) && !hashed.equals(head.getTracked().get(name))) {
                    modified.add(name + " (modified)");
                }
            }
        }

        Collections.sort(modified);

        for (String mod : modified) {
            System.out.println(mod);
        }

        System.out.println();

        List<String> directory = Utils.plainFilenamesIn(".");
        Collections.sort(directory);

        System.out.println("=== Untracked Files ===");
        for (String name : directory) {
            if (!stagedSet.contains(name) && !trackedSet.contains(name)) {
                System.out.println(name);
            } else if (removedNames.contains(name)) {
                System.out.println(name);
            }
        }
        System.out.println();


    }

    public void checkout(String[] operands) {
        Commit commit = null;
        String fileName = "";
        if (operands.length == 3 && operands[1].equals("--")) {
            commit = getHead();
            fileName = operands[2];
        } else if (operands.length == 4 && operands[2].equals("--")) {
            commit = Commit.loadCommit(operands[1]);
            fileName = operands[3];
        } else if (operands.length == 2) {
            File branch = new File(".gitlet/Branch/" + operands[1]);
            String commitHash = Utils.readContentsAsString(branch);
            if (!branch.exists()) {
                throw Utils.error("No such branch exists.");
            }
            if (operands[1].equals(getBranch())) {
                throw Utils.error("No need to checkout the current branch.");
            }

            Commit otherCommit = Commit.loadCommit(commitHash);
            checkUntracked(otherCommit);

            Commit head = getHead();
            for (String file: head.getTracked().keySet()) {
                Utils.restrictedDelete(file);
            }
            for (String file: otherCommit.getTracked().keySet()) {
                String hash = otherCommit.getTracked().get(file);
                File copy = new File(".gitlet/" + hash);
                String copyContents = Utils.readContentsAsString(copy);
                File newFile = new File(file);
                Utils.writeContents(newFile, copyContents);
            }
            updateBranch(operands[1]);
            return;

        } else {
            throw Utils.error("Incorrect operands.");
        }

        if (!commit.getTracked().containsKey(fileName)) {
            throw Utils.error("File does not exist in that commit.");
        }

        String hash = commit.getTracked().get(fileName);
        File copy = new File(".gitlet/" + hash);
        String copyContents = Utils.readContentsAsString(copy);

        File f = new File(fileName);

        if (f.exists()) {
            Utils.writeContents(f, copyContents);
        }

    }

    public void branch(String[] operands) {
        if (operands.length != 2) {
            throw Utils.error("Incorrect operands.");
        }

        List<String> branchNames = Utils.plainFilenamesIn(".gitlet/Commit");
        if (branchNames.contains(operands[1])) {
            throw Utils.error("Cannot remove the current branch.");
        }

        File newBranch = new File(".gitlet/Branch/" + operands[1]);
        String headhash = Utils.sha1(Utils.serialize(getHead()));
        Utils.writeContents(newBranch, headhash);


    }

    public void removeBranch(String[] operands) {
        if (operands.length != 2) {
            throw Utils.error("Incorrect operands.");
        }

        File branch = new File(".gitlet/Branch/" + operands[1]);

        if (!branch.exists()) {
            throw Utils.error("A branch with that name does not exist.");
        }

        if (getBranch().equals(operands[1])) {
            throw Utils.error("Cannot remove the current branch.");
        }

        branch.delete();

    }

    public void reset(String[] operands) {
        if (operands.length != 2) {
            throw Utils.error("Incorrect operands.");
        }
        String commitID = operands[1];
        Commit c = Commit.loadCommit(commitID);

        checkUntracked(c);

        for (String fileName: c.getTracked().keySet()) {
            String[] temp = { "checkout", commitID, "--", fileName};
            checkout(temp);
        }

    }

    public void merge(String[] operands) {

    }

    public void updateBranch(String newBranch) {
        File head = new File(".gitlet/head");
        Utils.writeContents(head, newBranch);
    }

    public String getBranch() {
        File head = new File(".gitlet/head");
        return Utils.readContentsAsString(head);
    }

    public Commit getHead() {
        File branch = new File(".gitlet/Branch/" + getBranch());
        String branchHash = Utils.readContentsAsString(branch);
        File actualHead = new File(".gitlet/Commit/" + branchHash);
        return Utils.readObject(actualHead, Commit.class);
    }

    public void committoFile(Commit head) {
        byte[] serialized = Utils.serialize(head);
        String hashed = Utils.sha1(serialized);
        head.storeCommit(hashed);
        File headFile = new File(".gitlet/Branch/" + getBranch());
        Utils.writeContents(headFile, hashed);
    }

    public void removeCommitFile(Commit head) {
        byte[] serialized = Utils.serialize(head);
        String hashed = Utils.sha1(serialized);
        File f = new File(".gitlet/Commit/" + hashed);
        f.delete();
    }

    public void checkUntracked(Commit other) {
        Commit head = getHead();

        List<String> directory = Utils.plainFilenamesIn(".");
        for (String file: directory) {
            File temp = new File(file);
            String hash = Utils.sha1(Utils.readContents(temp));
            if (!head.getTracked().containsKey(file) &&
                other.getTracked().containsKey(file) &&
                !hash.equals(other.getTracked().get(file))) {
                throw Utils.error("There is an untracked file in the way; delete it or add it first.");
            }
        }
    }
}
