package tinygit;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;

/**
 * Manages the interpretation of commands given by the user.
 *
 * @author Michael Remediakis
 */
public class Controller {

    /**
     * A map of commands available to the user.
     */
    private HashMap<String, Consumer<String[]>> commands = new HashMap<>();
    /**
     * The head commit of the current branch.
     */
    private Commit head;

    /**
     * Initializes all available commands.
     */
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
        commands.put("add-remote", this::addRemote);
        commands.put("rm-remote", this::removeRemote);
        commands.put("push", this::push);
        commands.put("fetch", this::fetch);
        commands.put("pull", this::pull);
    }

    /**
     * Parses the COMMAND given by the user.
     */
    public void parseLine(String... command) throws TinyGitException {
        if (command.length < 1) {
            throw Utils.error("Please enter a command.");
        }

        if (!commands.containsKey(command[0])) {
            throw Utils.error("No command with that name exists.");
        }

        TinyGitFile gitlet = new TinyGitFile(".tinygit");
        if (!command[0].equals("init") && !gitlet.exists()) {
            throw Utils.error("Not in an initialized Gitlet directory.");
        }

        if (!command[0].equals("init")) {
            head = getHead();
        }

        commands.get(command[0]).accept(command);
    }

    /**
     * Initializes a tinygit repository.
     *
     * @param unused placeholder array for parseLine command
     */
    public void init(String... unused) {
        if (unused.length != 1) {
            throw Utils.error("Incorrect operands.");
        }
        TinyGitFile hiddenDir = new TinyGitFile(".tinygit");
        if (hiddenDir.isDirectory()) {
            throw Utils.error("A Gitlet version-control system "
                + "already exists in the current directory.");
        }
        hiddenDir.mkdir();

        TinyGitFile commitDir = new TinyGitFile(".tinygit/Commit");
        commitDir.mkdir();

        TinyGitFile branchDir = new TinyGitFile(".tinygit/Branch");
        branchDir.mkdir();

        TinyGitFile remoteDir = new TinyGitFile(".tinygit/Remote");
        remoteDir.mkdir();

        TinyGitFile masterFile = new TinyGitFile(".tinygit/head");
        Utils.writeContents(masterFile, "master");

        Date firstDay = new Date();
        firstDay.setTime(0);

        String nullHash = Utils.sha1(Utils.serialize(null));
        Commit firstHead = new Commit("initial commit", firstDay, nullHash);
        committoFile(firstHead);
    }

    /**
     * Adds the given file into the stage.
     *
     * @param operands contains given file
     */
    public void add(String... operands) {
        if (operands.length != 2) {
            throw Utils.error("Incorrect operands.");
        }
        String headHash = getHeadHash(getBranch());
        head.addToStage(operands[1]);
        updateCommitFile(headHash, head);
    }

    /**
     * Commits all staged/tracked files in the repository with a
     * given message.
     *
     * @param operands contains given message.
     */
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

    /**
     * Removes file from the stage. If it is currently being
     * tracked by the branch, marks it to be removed in the
     * next commit.
     *
     * @param operands given file
     */
    public void remove(String... operands) {
        if (operands.length != 2) {
            throw Utils.error("Incorrect operands.");
        }
        String headHash = getHeadHash();
        head.remove(operands[1]);
        updateCommitFile(headHash, head);
    }

    /**
     * Prints an ancestral list of commit logs starting from the
     * current branch.
     *
     * @param unused placeholder array for parseLine command
     */
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

    /**
     * Prints a log of all commits created.
     *
     * @param unused placeholder array for parseLine command
     */
    public void globalLog(String... unused) {
        if (unused.length != 1) {
            throw Utils.error("Incorrect operands.");
        }
        List<String> commitNames = Utils.plainFilenamesIn(".tinygit/Commit");
        for (String commit : commitNames) {
            System.out.println("===");
            Commit.loadCommit(commit).log(commit);
            System.out.println();
        }

    }

    /**
     * Prints all commit IDs that contain the given message.
     *
     * @param operands contains given message
     */
    public void find(String... operands) {
        if (operands.length != 2) {
            throw Utils.error("Incorrect operands.");
        }
        String message = operands[1].replaceAll("^\"|\"$", "");

        List<String> commitNames = Utils.plainFilenamesIn(".tinygit/Commit");
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

    /**
     * Prints current status of the repository.
     *
     * @param unused placeholder array for parseLine command
     */
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

    /**
     * Executes various checkout commands depending on input.
     *
     * @param operands given input
     */
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
            TinyGitFile branch = new TinyGitFile(".tinygit/Branch/" + operands[1]);
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
                TinyGitFile copy = new TinyGitFile(".tinygit/" + hash);
                String copyContents = Utils.readContentsAsString(copy);
                TinyGitFile newFile = new TinyGitFile(file);
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
        TinyGitFile copy = new TinyGitFile(".tinygit/" + hash);
        String copyContents = Utils.readContentsAsString(copy);

        TinyGitFile f = new TinyGitFile(fileName);

        Utils.writeContents(f, copyContents);

    }

    /**
     * Creates a new branch of the given name.
     *
     * @param operands contains given name
     */
    public void branch(String... operands) {
        if (operands.length != 2) {
            throw Utils.error("Incorrect operands.");
        }

        List<String> branchNames = Utils.plainFilenamesIn(".tinygit/Branch");
        if (branchNames.contains(operands[1])) {
            throw Utils.error("A branch with that name already exists.");
        }

        TinyGitFile newBranch = new TinyGitFile(".tinygit/Branch/" + operands[1]);
        String headhash = getHeadHash();
        Utils.writeContents(newBranch, headhash);


    }

    /**
     * Removes given branch from the repository.
     *
     * @param operands contains given branch
     */
    public void removeBranch(String... operands) {
        if (operands.length != 2) {
            throw Utils.error("Incorrect operands.");
        }

        TinyGitFile branch = new TinyGitFile(".tinygit/Branch/" + operands[1]);

        if (!branch.exists()) {
            throw Utils.error("A branch with that name does not exist.");
        }

        if (getBranch().equals(operands[1])) {
            throw Utils.error("Cannot remove the current branch.");
        }

        branch.delete();

    }

    /**
     * Resets repository to the state of the given commit ID.
     *
     * @param operands contains given commit ID.
     */
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

        for (String filename : c.getTracked().keySet()) {
            if (!head.getTracked().containsKey(filename)) {
                checkout("checkout", commitID, "--", filename);
            }
        }

        c.getStaged().clear();
        c.getUntracked().clear();
        updateCommitFile(commitID, c);


        TinyGitFile branch = new TinyGitFile(".tinygit/Branch/" + getBranch());
        Utils.writeContents(branch, commitID);

    }

    /**
     * Merges the current branch with the given branch.
     *
     * @param operands contains given branch
     */
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

        boolean foundConflict;

        foundConflict = mergeSplit(currentFiles, otherFiles, splitFiles);
        foundConflict = mergeOther(currentFiles,
            otherFiles, splitFiles, branchName)
            || foundConflict;

        String headHash = getHeadHash();
        if (foundConflict) {
            System.out.println("Encountered a merge conflict.");
        }
        Date current = new Date();
        String message = "Merged " + branchName + " into " + getBranch() + ".";
        Commit newCommit = new Commit(message, current,
            headHash, getHeadHash(branchName));
        committoFile(newCommit);
    }

    /**
     * Creates a connection to a remote repository.
     * @param operands contains given repository
     */
    public void addRemote(String... operands) {
        if (operands.length != 3) {
            throw Utils.error("Incorrect operands.");
        }

        String separator = TinyGitFile.separator;
        List<String> remoteNames = Utils.plainFilenamesIn(".tinygit/Remote");
        if (remoteNames.contains(operands[1])) {
            throw Utils.error("A remote with that name already exists.");
        }

        String filePath = operands[2].replaceAll("/", separator);

        TinyGitFile newBranch = new TinyGitFile(".tinygit/Remote/" + operands[1]);
        Utils.writeContents(newBranch, filePath);

    }

    /**
     * Removes a connection to a given repository.
     * @param operands contains given repository
     */
    public void removeRemote(String... operands) {
        if (operands.length != 2) {
            throw Utils.error("Incorrect operands.");
        }

        TinyGitFile branch = new TinyGitFile(".tinygit/Remote/" + operands[1]);

        if (!branch.exists()) {
            throw Utils.error("A remote with that name does not exist.");
        }

        branch.delete();

    }

    /**
     * Pushes all commits and blobs to remote repository.
     * @param operands given branch
     */
    public void push(String... operands) {
        if (operands.length != 3) {
            throw Utils.error("Incorrect operands.");
        }
        String remoteName = operands[1];
        String remoteBranch = operands[2];
        String remoteCommit = getRemoteBranch(remoteName, remoteBranch);

        List<String> ancestry = checkAncestry(remoteCommit);

        String remotePath = getRemotePath(remoteName);

        for (String commitHash : ancestry) {
            TinyGitFile commitFile = new TinyGitFile(
                ".tinygit/Commit/" + commitHash);
            Commit c = Utils.readObject(commitFile, Commit.class);
            TinyGitFile.setRemotePath(
                remotePath.substring(0, remotePath.length() - 7));
            c.storeCommit(commitHash);
            TinyGitFile.setRemotePath("");
        }
        List<String> fileNames = Utils.plainFilenamesIn(".tinygit");
        for (String fileName : fileNames) {
            if (!fileName.equals("head")) {
                TinyGitFile blobFile = new TinyGitFile(".tinygit/" + fileName);
                TinyGitFile remoteFile = new TinyGitFile(
                    getRemotePath(remoteName) + "/" + fileName);
                String fileContent = Utils.readContentsAsString(blobFile);
                Utils.writeContents(remoteFile, fileContent);
            }
        }
        String headHash = getHeadHash();
        TinyGitFile.setRemotePath(
            remotePath.substring(0, remotePath.length() - 7));
        TinyGitFile branchFile = new TinyGitFile(
            ".tinygit/Branch/" + remoteBranch);
        if (!branchFile.exists()) {
            branch("branch", remoteBranch);
        }
        TinyGitFile branch = new TinyGitFile(".tinygit/Branch/" + remoteBranch);
        String commitHash = Utils.readContentsAsString(branch);
        Commit otherCommit = Commit.loadCommit(commitHash);
        checkUntracked(otherCommit);
        for (String file : head.getTracked().keySet()) {
            Utils.restrictedDelete(file);
        }
        for (String file : otherCommit.getTracked().keySet()) {
            String hash = otherCommit.getTracked().get(file);
            TinyGitFile copy = new TinyGitFile(".tinygit/" + hash);
            String copyContents = Utils.readContentsAsString(copy);
            TinyGitFile newFile = new TinyGitFile(file);
            Utils.writeContents(newFile, copyContents);
        }
        otherCommit.getStaged().clear();
        updateCommitFile(commitHash, otherCommit);
        updateBranch(operands[1]);
        reset("reset", headHash);
        TinyGitFile.setRemotePath("");
    }

    /**
     * Fetches commits and blobs from remote repository.
     * @param operands given branch head
     */
    public void fetch(String... operands) {
        if (operands.length != 3) {
            throw Utils.error("Incorrect operands.");
        }
        String remoteName = operands[1];
        String remoteBranch = operands[2];

        String branchPath = getRemotePath(remoteName)
            + "/Branch/" + remoteBranch;
        TinyGitFile remoteGitlet = new TinyGitFile(branchPath);
        if (!remoteGitlet.exists()) {
            throw Utils.error("That remote does not have that branch.");
        }

        TinyGitFile dir = new TinyGitFile(".tinygit/Branch/" + remoteName);
        if (!dir.exists()) {
            dir.mkdir();
        }
        TinyGitFile localBranch = new TinyGitFile(
            ".tinygit/Branch/" + remoteName + "/" + remoteBranch);
        Utils.writeContents(localBranch,
            Utils.readContentsAsString(remoteGitlet));

        List<String> commits = Utils.plainFilenamesIn(
            getRemotePath(remoteName) + "/Commit");

        for (String commitHash : commits) {
            TinyGitFile remoteFile = new TinyGitFile(
                getRemotePath(remoteName) + "/Commit/" + commitHash);
            Commit c = Utils.readObject(remoteFile, Commit.class);
            c.storeCommit(commitHash);
        }

        List<String> fileNames = Utils.plainFilenamesIn(
            getRemotePath(remoteName));

        for (String fileName : fileNames) {
            if (!fileName.equals("head")) {
                TinyGitFile blobFile = new TinyGitFile(".tinygit/" + fileName);
                TinyGitFile remoteFile = new TinyGitFile(
                    getRemotePath(remoteName) + "/" + fileName);
                String fileContent = Utils.readContentsAsString(remoteFile);
                Utils.writeContents(blobFile, fileContent);
            }

        }
    }

    /**
     * Fetches from remote repository and merges with current.
     * @param operands given branch
     */
    public void pull(String... operands) {
        if (operands.length != 3) {
            throw Utils.error("Incorrect operands.");
        }
        String remoteName = operands[1];
        String remoteBranch = operands[2];
        fetch(operands);
        merge("merge", remoteName + "/" + remoteBranch);
    }



    /* ---------------------------------------------------- */
    /* ----------------- Helper Functions ----------------- */
    /* ---------------------------------------------------- */


    /**
     * Updates the current branch to be the NEWBRANCH.
     */
    public void updateBranch(String newBranch) {
        TinyGitFile headFile = new TinyGitFile(".tinygit/head");
        Utils.writeContents(headFile, newBranch);
    }

    /**
     * Returns the current branch name.
     */
    public String getBranch() {
        TinyGitFile headFile = new TinyGitFile(".tinygit/head");
        return Utils.readContentsAsString(headFile);
    }


    /**
     * Returns the hash of the head of the current branch.
     */
    public String getHeadHash() {
        TinyGitFile branch = new TinyGitFile(".tinygit/Branch/" + getBranch());
        return Utils.readContentsAsString(branch);

    }

    /**
     * Returns the hash of the head of the given BRANCHNAME.
     */
    public String getHeadHash(String branchName) {
        TinyGitFile branch = new TinyGitFile(".tinygit/Branch/" + branchName);
        if (!branch.exists()) {
            throw Utils.error("A branch with that name does not exist.");
        }
        return Utils.readContentsAsString(branch);

    }

    /**
     * Returns the head of the current branch.
     */
    public Commit getHead() {
        String headHash = getHeadHash(getBranch());
        TinyGitFile actualHead = new TinyGitFile(".tinygit/Commit/" + headHash);
        return Utils.readObject(actualHead, Commit.class);
    }

    /**
     * Returns the head of the given BRANCHNAME.
     */
    public Commit getHead(String branchName) {
        String headHash = getHeadHash(branchName);
        TinyGitFile actualHead = new TinyGitFile(".tinygit/Commit/" + headHash);
        return Utils.readObject(actualHead, Commit.class);
    }

    /**
     * Saves commit C a file in the .tinygit repository.
     */
    public void committoFile(Commit c) {
        byte[] serialized = Utils.serialize(c);
        String hashed = Utils.sha1(serialized);
        c.storeCommit(hashed);
        TinyGitFile headFile = new TinyGitFile(".tinygit/Branch/" + getBranch());
        Utils.writeContents(headFile, hashed);
    }

    /**
     * Updates the file with name HASH to contain the serialization of
     * the given commit C.
     */
    public void updateCommitFile(String hash, Commit c) {
        c.storeCommit(hash);
    }

    /**
     * Checks if there are any untracked files between the current branch
     * and the given OTHER commit.
     */
    public void checkUntracked(Commit other) {
        List<String> directory = Utils.plainFilenamesIn(".");
        for (String file : directory) {
            TinyGitFile temp = new TinyGitFile(file);
            String hash = Utils.sha1(Utils.readContents(temp));
            if (!head.getTracked().containsKey(file)
                && other.getTracked().containsKey(file)
                && !hash.equals(other.getTracked().get(file))) {
                throw Utils.error("There is an untracked file in the way;"
                    + " delete it or add it first.");
            }
        }
    }

    /**
     * Prints a list of all the existing branches, with an asterisk next to the
     * current branch.
     */
    public void branchesStatus() {
        List<String> branchNames = Utils.plainFilenamesIn(".tinygit/Branch");
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

    /**
     * Prints a list of all files in commit C staged to be added.
     */
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

    /**
     * Prints a list of all files in commit C staged to be removed.
     */
    public void removedStatus(Commit c) {
        List<String> removedNames = c.getUntracked();
        Collections.sort(removedNames);

        System.out.println("=== Removed Files ===");
        for (String name : removedNames) {
            System.out.println(name);
        }
        System.out.println();

    }

    /**
     * Prints a list of all files in commit C that have either
     * been removed or modified.
     */
    public void modifiedStatus(Commit c) {
        Set<String> stagedSet = c.getStaged().keySet();
        List<String> removedNames = c.getUntracked();
        Set<String> trackedSet = c.getTracked().keySet();
        List<String> trackedNames = new ArrayList<>(trackedSet);
        Collections.sort(trackedNames);

        List<String> modified = new ArrayList<>();
        System.out.println("=== Modifications Not Staged For Commit ===");

        for (String name : stagedSet) {
            TinyGitFile f = new TinyGitFile(name);
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
            TinyGitFile f = new TinyGitFile(name);
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

    /**
     * Prints a list of all files in the working directory that are
     * not being tracked by commit C.
     */
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

    /**
     * Returns the splitpoint between the current BRANCHNAME, using the
     * OTHERHEAD commit as a tracker.
     */
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
                    TinyGitFile f = new TinyGitFile(
                        ".tinygit/Branch/" + getBranch());
                    Utils.writeContents(f, getHeadHash(branchName));
                    throw Utils.error("Current branch fast-forwarded.");
                }
                break;
            }
            otherTracker = splitPoint;
        }
        return splitPoint;
    }

    /**
     * Clears the stage of the head.
     */
    public void clearStage() {
        head.getStaged().clear();
        head.getUntracked().clear();
        updateCommitFile(getHeadHash(), head);
    }

    /**
     * Fixes any merge conflicts in FILE, when merging from CURRENTFILES
     * and OTHERFILES.
     */
    public void fixMergeConflict(String file,
                                 HashMap<String, String> currentFiles,
                                 HashMap<String, String> otherFiles) {
        TinyGitFile currentFile = new TinyGitFile(
            ".tinygit/" + currentFiles.get(file));
        TinyGitFile otherFile = new TinyGitFile(
            ".tinygit/" + otherFiles.get(file));
        TinyGitFile realfile = new TinyGitFile(file);
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

    /**
     * Merges files tracked in CURRENTFILES with the files in SPLITFILES using
     * the OTHERFILES from the given commit for conditions. Returns whether
     * there are any conflicts during merging.
     */
    private boolean mergeSplit(HashMap<String, String> currentFiles,
                               HashMap<String, String> otherFiles,
                               HashMap<String, String> splitFiles) {
        boolean foundConflict = false;
        String headHash = getHeadHash();
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
            if (currentFiles.containsKey(file)
                && !otherFiles.containsKey(file)) {
                if (currentFiles.get(file).equals(splitFiles.get(file))) {
                    remove("rm", file);
                } else {
                    fixMergeConflict(file, currentFiles, otherFiles);
                    foundConflict = true;
                }
            }
            if (!currentFiles.containsKey(file)
                && otherFiles.containsKey(file)) {
                if (!otherFiles.get(file).equals(splitFiles.get(file))) {
                    fixMergeConflict(file, currentFiles, otherFiles);
                    foundConflict = true;
                }
            }

        }
        return foundConflict;
    }

    /**
     * Merges files tracked in OTHERFILES with the files in SPLITFILES using
     * the CURRENTFILES from the given commit for conditions. Returns whether
     * there are any conflicts during merging. Uses BRANCHNAME.
     */
    private boolean mergeOther(HashMap<String, String> currentFiles,
                               HashMap<String, String> otherFiles,
                               HashMap<String, String> splitFiles,
                               String branchName) {
        boolean foundConflict = false;
        for (String file : otherFiles.keySet()) {
            if (!currentFiles.containsKey(file)
                && !splitFiles.containsKey(file)) {
                checkout("checkout", getHeadHash(branchName), "--", file);
                add("add", file);
            }
            if (!splitFiles.containsKey(file)
                && currentFiles.containsKey(file)) {
                if (currentFiles.get(file).equals(otherFiles.get(file))) {
                    fixMergeConflict(file, currentFiles, otherFiles);
                    foundConflict = true;
                }
            }
        }
        return foundConflict;
    }

    /* ---------------------------------------------------- */
    /* ------------- Remote Helper Functions -------------- */
    /* ---------------------------------------------------- */


    /**
     * Returns the hash of the REMOTEBRANCH from the given REMOTE
     * repository.
     */
    public String getRemoteBranch(String remote, String remoteBranch) {
        String branchPath = getRemotePath(remote) + "/Branch/" + remoteBranch;
        TinyGitFile remoteGitlet = new TinyGitFile(branchPath);
        return Utils.readContentsAsString(remoteGitlet);

    }

    /**
     * Returns the path to the REMOTE branch.
     */
    public String getRemotePath(String remote) {
        TinyGitFile remoteFile = new TinyGitFile(".tinygit/Remote/" + remote);
        String remotePath = Utils.readContentsAsString(remoteFile);
        TinyGitFile remoteGitlet = new TinyGitFile(remotePath);
        if (!remoteGitlet.exists()) {
            throw Utils.error("Remote directory not found.");
        }
        return remotePath;

    }

    /**
     * Checks whether the REMOTECOMMIT exists in the ancestry of the local
     * head. Returns a list of future commits if so.
     */
    private List<String> checkAncestry(String remoteCommit) {
        String currentHash = getHeadHash();
        String nullHash = Utils.sha1(Utils.serialize(null));

        ArrayList<String> history = new ArrayList<>();

        while (!currentHash.equals(nullHash)) {
            if (remoteCommit.equals(currentHash)) {
                return history;
            }
            history.add(currentHash);
            Commit current = Commit.loadCommit(currentHash);
            currentHash = current.getParentHash();
        }
        throw Utils.error("Please pull down remote changes before pushing.");
    }
}
