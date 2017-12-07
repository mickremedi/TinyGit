package gitlet;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;

public class Controller {

    private HashMap<String, Consumer<String[]>> commands = new HashMap<>();
    private Commit head;

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
        commands.put("merge", this::merge);
    }

    public void parseLine(String... command) throws GitletException {
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

    public void init(String... unused) {
        if (unused.length != 1) {
            throw Utils.error("Incorrect operands.");
        }
        File hiddenDir = new File(".gitlet");
        if (hiddenDir.isDirectory()) {
            throw Utils.error("A Gitlet version-control system "
                + "already exists in the current directory.");
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

        String nullHash = Utils.sha1(Utils.serialize(null));
        Commit firstHead = new Commit("initial commit", firstDay, nullHash);
        committoFile(firstHead);
    }

    public void add(String... operands) {
        if (operands.length != 2) {
            throw Utils.error("Incorrect operands.");
        }
        String headHash = getHeadHash(getBranch());
        head.addToStage(operands[1]);
        updateCommitFile(headHash, head);
    }

    public void commit(String... operands) {
        if (operands.length != 2) {
            throw Utils.error("Incorrect operands.");
        }

        Date current = new Date();
        String message = operands[1];
        Commit newCommit = new Commit(message, current, getHeadHash());
        clearStage();
        committoFile(newCommit);
    }

    public void remove(String... operands) {
        if (operands.length != 2) {
            throw Utils.error("Incorrect operands.");
        }
        String headHash = getHeadHash();
        head.remove(operands[1]);
        updateCommitFile(headHash, head);
    }

    public void log(String... unused) {
        if (unused.length != 1) {
            throw Utils.error("Incorrect operands.");
        }
        Commit tracker = head;
        System.out.println("===");
        tracker.log(getHeadHash());
        System.out.println();
        String parentHash = tracker.getParentHash();
        tracker = tracker.getParent();
        while (tracker != null) {
            System.out.println("===");
            tracker.log(parentHash);
            System.out.println();
            parentHash = tracker.getParentHash();
            tracker = tracker.getParent();
        }

    }

    public void globalLog(String... unused) {
        if (unused.length != 1) {
            throw Utils.error("Incorrect operands.");
        }
        List<String> commitNames = Utils.plainFilenamesIn(".gitlet/Commit");
        for (String commit : commitNames) {
            System.out.println("===");
            Commit.loadCommit(commit).log(commit);
            System.out.println();
        }

    }

    public void find(String... operands) {
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

    public void status(String... unused) {
        if (unused.length != 1) {
            throw Utils.error("Incorrect operands.");
        }
        branchesStatus();
        stageStatus(head);
        removedStatus(head);
        modifiedStatus(head);
        untrackedStatus(head);

    }

    public void checkout(String... operands) {
        Commit commit;
        String fileName;
        if (operands.length == 3 && operands[1].equals("--")) {
            commit = getHead();
            fileName = operands[2];
        } else if (operands.length == 4 && operands[2].equals("--")) {
            commit = Commit.loadCommit(operands[1]);
            fileName = operands[3];
        } else if (operands.length == 2) {
            File branch = new File(".gitlet/Branch/" + operands[1]);
            if (!branch.exists()) {
                throw Utils.error("No such branch exists.");
            }
            if (operands[1].equals(getBranch())) {
                throw Utils.error("No need to checkout the current branch.");
            }

            String commitHash = Utils.readContentsAsString(branch);

            Commit otherCommit = Commit.loadCommit(commitHash);
            checkUntracked(otherCommit);

            for (String file : head.getTracked().keySet()) {
                Utils.restrictedDelete(file);
            }
            for (String file : otherCommit.getTracked().keySet()) {
                String hash = otherCommit.getTracked().get(file);
                File copy = new File(".gitlet/" + hash);
                String copyContents = Utils.readContentsAsString(copy);
                File newFile = new File(file);
                Utils.writeContents(newFile, copyContents);
            }

            otherCommit.getStaged().clear();
            updateCommitFile(commitHash, otherCommit);

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

        Utils.writeContents(f, copyContents);

    }

    public void branch(String... operands) {
        if (operands.length != 2) {
            throw Utils.error("Incorrect operands.");
        }

        List<String> branchNames = Utils.plainFilenamesIn(".gitlet/Branch");
        if (branchNames.contains(operands[1])) {
            throw Utils.error("A branch with that name already exists.");
        }

        File newBranch = new File(".gitlet/Branch/" + operands[1]);
        String headhash = getHeadHash();
        Utils.writeContents(newBranch, headhash);


    }

    public void removeBranch(String... operands) {
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

    public void reset(String... operands) {
        if (operands.length != 2) {
            throw Utils.error("Incorrect operands.");
        }
        String commitID = operands[1];
        Commit c = Commit.loadCommit(commitID);

        checkUntracked(c);

        for (String filename : head.getTracked().keySet()) {
            if (c.getTracked().containsKey(filename)) {
                checkout("checkout", commitID, "--", filename);
            } else {
                Utils.restrictedDelete(filename);
            }
        }

        for (String filename: c.getTracked().keySet()) {
            if (!head.getTracked().containsKey(filename)) {
                checkout("checkout", commitID, "--", filename);
            }
        }

        c.getStaged().clear();
        c.getUntracked().clear();
        updateCommitFile(commitID, c);


        File branch = new File(".gitlet/Branch/" + getBranch());
        Utils.writeContents(branch, commitID);

    }

    public void merge(String... operands) {
        if (operands.length != 2) {
            throw Utils.error("Incorrect operands.");
        }
        String branchName = operands[1];
        Commit currentHead = getHead();
        Commit otherHead = getHead(branchName);
        if (!currentHead.getStaged().isEmpty()
            || !currentHead.getUntracked().isEmpty()) {
            throw Utils.error("You have uncommitted changes.");
        }

        if (branchName.equals(getBranch())) {
            throw Utils.error("Cannot merge a branch with itself.");
        }
        checkUntracked(otherHead);
        Commit splitPoint = getSplit(branchName, otherHead);
        HashMap<String, String> currentFiles = currentHead.getTracked();
        HashMap<String, String> otherFiles = otherHead.getTracked();
        HashMap<String, String> splitFiles = splitPoint.getTracked();
        String headHash = getHeadHash();
        boolean foundConflict = false;
        for (String file : splitFiles.keySet()) {
            if (currentFiles.containsKey(file)
                && otherFiles.containsKey(file)) {
                if (!otherFiles.get(file).equals(splitFiles.get(file))) {
                    if (currentFiles.get(file).equals(splitFiles.get(file))) {
                        checkout("checkout", headHash, "--", file);
                        add("add", file);
                    } else {
                        fixMergeConflict(file, currentFiles, otherFiles);
                        foundConflict = true;
                    }
                }
            }
            if (currentFiles.containsKey(file) && !otherFiles.containsKey(file)) {
                if (currentFiles.get(file).equals(splitFiles.get(file))) {
                    remove("rm", file);
                } else {
                    fixMergeConflict(file, currentFiles, otherFiles);
                    foundConflict = true;
                }
            }
            if (!currentFiles.containsKey(file) && otherFiles.containsKey(file)) {
                if (!otherFiles.get(file).equals(splitFiles.get(file))) {
                    fixMergeConflict(file, currentFiles, otherFiles);
                    foundConflict = true;
                }
            }

        }
        for (String file : otherFiles.keySet()) {
            if (!currentFiles.containsKey(file)
                && !splitFiles.containsKey(file)) {
                checkout("checkout", getHeadHash(branchName), "--", file);
                add("add", file);
            }
            if (!splitFiles.containsKey(file) && currentFiles.containsKey(file)) {
                if (currentFiles.get(file).equals(otherFiles.get(file))) {
                    fixMergeConflict(file, currentFiles, otherFiles);
                    foundConflict = true;
                }
            }
        }
        if (foundConflict) {
            System.out.println("Encountered a merge conflict.");
        }
        Date current = new Date();
        String message = "Merged " + branchName + " into " + getBranch() + ".";
        Commit newCommit = new Commit(message, current,
            headHash, getHeadHash(branchName));
        committoFile(newCommit);
    }

    /* ---------------------------------------------------- */
    /* ----------------- Helper Functions ----------------- */
    /* ---------------------------------------------------- */

    public void updateBranch(String newBranch) {
        File headFile = new File(".gitlet/head");
        Utils.writeContents(headFile, newBranch);
    }

    public String getBranch() {
        File headFile = new File(".gitlet/head");
        return Utils.readContentsAsString(headFile);
    }

    public String getHeadHash() {
        File branch = new File(".gitlet/Branch/" + getBranch());
        return Utils.readContentsAsString(branch);

    }

    public String getHeadHash(String branchName) {
        File branch = new File(".gitlet/Branch/" + branchName);
        if (!branch.exists()) {
            throw Utils.error("A branch with that name does not exist.");
        }
        return Utils.readContentsAsString(branch);

    }

    public Commit getHead() {
        String headHash = getHeadHash(getBranch());
        File actualHead = new File(".gitlet/Commit/" + headHash);
        return Utils.readObject(actualHead, Commit.class);
    }

    public Commit getHead(String branchName) {
        String headHash = getHeadHash(branchName);
        File actualHead = new File(".gitlet/Commit/" + headHash);
        return Utils.readObject(actualHead, Commit.class);
    }

    public void committoFile(Commit c) {
        byte[] serialized = Utils.serialize(c);
        String hashed = Utils.sha1(serialized);
        c.storeCommit(hashed);
        File headFile = new File(".gitlet/Branch/" + getBranch());
        Utils.writeContents(headFile, hashed);
    }

    public void updateCommitFile(String hash, Commit c) {
        c.storeCommit(hash);
    }

    public void checkUntracked(Commit other) {
        List<String> directory = Utils.plainFilenamesIn(".");
        for (String file : directory) {
            File temp = new File(file);
            String hash = Utils.sha1(Utils.readContents(temp));
            if (!head.getTracked().containsKey(file)
                && other.getTracked().containsKey(file)
                && !hash.equals(other.getTracked().get(file))) {
                throw Utils.error("There is an untracked file in the way;"
                    + " delete it or add it first.");
            }
        }
    }

    public void branchesStatus() {
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
    }

    public void stageStatus(Commit c) {
        Set<String> stagedSet = c.getStaged().keySet();
        List<String> stagedNames = new ArrayList<>(stagedSet);

        System.out.println("=== Staged Files ===");
        Collections.sort(stagedNames);
        for (String name : stagedNames) {
            System.out.println(name);
        }
        System.out.println();

    }

    public void removedStatus(Commit c) {
        List<String> removedNames = c.getUntracked();
        Collections.sort(removedNames);

        System.out.println("=== Removed Files ===");
        for (String name : removedNames) {
            System.out.println(name);
        }
        System.out.println();

    }

    public void modifiedStatus(Commit c) {
        Set<String> stagedSet = c.getStaged().keySet();
        List<String> removedNames = c.getUntracked();
        Set<String> trackedSet = c.getTracked().keySet();
        List<String> trackedNames = new ArrayList<>(trackedSet);
        Collections.sort(trackedNames);

        List<String> modified = new ArrayList<>();
        System.out.println("=== Modifications Not Staged For Commit ===");

        for (String name : stagedSet) {
            File f = new File(name);
            if (!f.exists()) {
                modified.add(name + " (deleted)");
            } else {
                String fileContent = Utils.readContentsAsString(f);
                String hashed = Utils.sha1(fileContent);
                if (!hashed.equals(c.getStaged().get(name))) {
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
                if (!stagedSet.contains(name)
                    && !hashed.equals(c.getTracked().get(name))) {
                    modified.add(name + " (modified)");
                }
            }
        }

        Collections.sort(modified);

        for (String mod : modified) {
            System.out.println(mod);
        }

        System.out.println();

    }

    public void untrackedStatus(Commit c) {
        Set<String> stagedSet = c.getStaged().keySet();
        List<String> removedNames = c.getUntracked();
        Set<String> trackedSet = c.getTracked().keySet();

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

    public Commit getSplit(String branchName, Commit otherHead) {
        HashSet<String> ancestors = new HashSet<>();
        Commit currentTracker = head;
        Commit splitPoint = null;

        ancestors.add(getHeadHash());
        while (currentTracker != null) {
            ancestors.add(currentTracker.getParentHash());
            currentTracker = currentTracker.getParent();
        }

        Commit otherTracker = otherHead;

        if (ancestors.contains(getHeadHash(branchName))) {
            throw Utils.error("Given branch is an "
                + "ancestor of the current branch.");
        }

        while (otherTracker != null) {
            splitPoint = otherTracker.getParent();
            if (ancestors.contains(otherTracker.getParentHash())) {
                if (otherTracker.getParentHash().equals(getHeadHash())) {
                    File f = new File(".gitlet/Branch/" + getBranch());
                    Utils.writeContents(f, getHeadHash(branchName));
                    throw Utils.error("Current branch fast-forwarded.");
                }
                break;
            }
            otherTracker = splitPoint;
        }
        return splitPoint;
    }

    public void clearStage() {
        head.getStaged().clear();
        head.getUntracked().clear();
        updateCommitFile(getHeadHash(), head);
    }

    public void fixMergeConflict(String file,
                                 HashMap<String, String> currentFiles,
                                 HashMap<String, String> otherFiles) {
        File currentFile = new File(".gitlet/" + currentFiles.get(file));
        File otherFile = new File(".gitlet/" + otherFiles.get(file));
        File realfile = new File(file);
        if (!currentFile.exists()) {
            Utils.writeContents(currentFile, "");
        }
        if (!otherFile.exists()) {
            Utils.writeContents(otherFile, "");
        }
        String currentContents = Utils.readContentsAsString(currentFile);
        String otherContents = Utils.readContentsAsString(otherFile);
        Utils.writeContents(realfile, "<<<<<<< HEAD\n"
            , currentContents, "=======\n", otherContents, ">>>>>>>\n");
        add("add", file);

    }
}
