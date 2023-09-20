package gitlet;

import java.io.File;
import java.lang.reflect.Array;
import java.nio.file.Files;
import java.util.HashMap;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

// TODO: any imports you need here

import static gitlet.Utils.*;
import static gitlet.myUtils.*;
import gitlet.Blob.*;
import gitlet.Commit.*;
import gitlet.Index.*;

/** Represents a gitlet repository.
 *  TODO: It's a good idea to give a description here of what else this Class
 *  does at a high level.
 *
 *  @author TODO
 */
public class Repository {
    /**
     * TODO: add instance variables here.
     *
     * List all instance variables of the Repository class here with a useful
     * comment above them describing what that variable represents and how that
     * variable is used. We've provided two examples for you.
     */

    /** The current working directory.
     *  .gitlet
     *    |--objects
     *      |--commits and blobs
     *    |--refs
     *      |--heads
     *        |--branch names
     *    |--HEAD
     *    |--index
     * */

    public static final File CWD = new File(System.getProperty("user.dir"));
    // The .gitlet directory
    public static final File GITLET_DIR = join(CWD, ".gitlet");
    // objects folder to store commits and blobs
    public static final File OBJECT_DIR = join(GITLET_DIR, "objects");
    // refs folder -> heads folder -> all branches,
    // each named by the branch name, content is SHA-1 ID for the head commit of each branch
    public static final File REFS_DIR = join(GITLET_DIR, "refs");
    public static final File HEADS_DIR = join(REFS_DIR, "heads");
    // HEAD file storing current commit ID
    public static final File HEAD = new File(GITLET_DIR, "HEAD");
    // index file storing staging area
    public static final File index = new File(GITLET_DIR, "index");
    public static Commit currCommit;

    /* TODO: fill in the rest of this class. */

    public static void setupPersistence() {
        GITLET_DIR.mkdir();
        OBJECT_DIR.mkdir();
        REFS_DIR.mkdir();
        HEADS_DIR.mkdir();
    }

    public static void initiateGitlet() {
        if (GITLET_DIR.exists()) {
            throw new GitletException(
                    "A Gitlet version-control system already exists in the current directory"
            );
        }
        setupPersistence();
        Commit currCommit = initCommit();
        currCommit.saveCommit();
        initHEAD();
        initOrUpdateHeads(currCommit);
        initIndex();
    }

    private static Commit initCommit() {
        currCommit = new Commit();
        return currCommit;
    }

    private static void initHEAD() {
        writeContents(HEAD, "master");
    }

    private static void initOrUpdateHeads(Commit currCommit) {
        File branchHead = new File(HEADS_DIR, getCurrBranch());
        writeContents(branchHead, currCommit.getCommitID());
    }

    private static void initIndex() {
        writeObject(index, new Index());
    }

    private static String getCurrBranch() {
        return readContentsAsString(HEAD);
    }

    private static String getCurrCommitID() {
        return readContentsAsString(join(HEADS_DIR, getCurrBranch()));
    }
    private static Commit getCurrCommit() {
        return getObjectbyID(getCurrCommitID(), Commit.class);
    }

    public static void addToStage(String fileName) {
        File f = join(CWD, fileName);
        if (!f.exists()) {
            throw new GitletException("File does not exist.");
        }

        Blob b = new Blob(f);
        b.saveBlob();
        Index stagedArea = readObject(index, Index.class);
        HashMap<String, String> commitFileMap = getCurrCommit().getBlobs();
        if (commitFileMap.containsKey(b.getFilePath()) && commitFileMap.get(b.getFilePath()).equals(b.getID())) {
            if (stagedArea.stagedToAddFiles.containsKey(b.getFilePath())) {
                stagedArea.stagedToAddFiles.remove(b.getFilePath());
            }
        } else {
            if (stagedArea.stagedToRemoveFiles.containsKey(b.getFilePath())) {
                stagedArea.stagedToRemoveFiles.remove(b.getFilePath());
            }
            stagedArea.addFile(b);
        }
        stagedArea.saveIndex();
        // own test
//        Index test1 = readObject(index, Index.class);
//        System.out.println(test1.stagedToAddFiles);
//        System.out.println(test1.stagedToRemoveFiles);
    }

    public static void newCommit(String commitMsg) {

        // abort if the staging area is clear
        Index stagedArea = readObject(index, Index.class);
        if (stagedArea.stagedToAddFiles.isEmpty() && stagedArea.stagedToRemoveFiles.isEmpty()) {
            throw new GitletException("No changes added to the commit.");
        } else if (commitMsg.isEmpty()) {
            // abort if the commit msg is blank
            throw new GitletException("Please enter a commit message.");
        }

        Commit c = new Commit(commitMsg, CalculateParents(), calculateBlobs());
        c.saveCommit();
        initOrUpdateHeads(c);
        clearStagedArea();
    }

    private static List<String> CalculateParents() {
        List<String> parents = new ArrayList<>();
        currCommit = getCurrCommit();
        parents.add(currCommit.getCommitID());
        return parents;
    }

    private static HashMap<String, String> calculateBlobs() {
        HashMap<String, String> blobs = getCurrCommit().getBlobs();
        Index stagedArea = readObject(index, Index.class); // get index file
        for (String i: stagedArea.stagedToAddFiles.keySet()) {
            blobs.put(i, stagedArea.stagedToAddFiles.get(i)); // update + add if any changes in staged
        }
        for (String j: stagedArea.stagedToRemoveFiles.keySet()) {
            blobs.remove(j); // remove files which are staged
        }
        return blobs;
    }

    private static void clearStagedArea() {
        Index stagedArea = readObject(Repository.index, Index.class);
        stagedArea.clearStagingArea();
        stagedArea.saveIndex();
    }

    public static void removeFile(String fileName) {
        File f = join(CWD, fileName);
        Index stagedArea = readObject(index, Index.class);
        currCommit = getCurrCommit();

        if (stagedArea.stagedToAddFiles.containsKey(f.getPath())) {
            stagedArea.stagedToAddFiles.remove(f.getPath());
        } else if (currCommit.getBlobs().containsKey(f.getPath())) {
            Blob b = readObject(myUtils.getObjectFilebyID(currCommit.getBlobs().get(f.getPath())), Blob.class);
            stagedArea.removeFile(b);
        } else {throw new GitletException("No reason to remove the file."); }
        stagedArea.saveIndex();
    }

    public static void displayLog() {
        currCommit = getCurrCommit();
        List<String> firstParents = currCommit.getParents();
        Commit commitToDisplay = currCommit;
        while (commitToDisplay != null) {
            System.out.println("===");
            System.out.printf("commit %s%n", commitToDisplay.getCommitID());
            System.out.printf("Date: %s%n", commitToDisplay.getCommitTime());
            System.out.println(commitToDisplay.getCommitMsg());
            System.out.println();
            if (commitToDisplay.getParents().isEmpty()) {commitToDisplay = null; }
            else {commitToDisplay = getObjectbyID(commitToDisplay.getParents().get(0), Commit.class);}
        }
    }

    public static void displayGlobalLog() {

    }

    public static void findCommitsWithMsg(String commitMsg) {

    }

    public static void displayStatus() {

    }

    public static void checkoutToFile(String fileName) {
        File f = join(CWD, fileName);
        checkFileExistInCommit(f, getCurrCommit());

        rewriteContentforCheckoutToFile(getCurrCommit(), f);
    }

    private static void checkFileExistInCommit(File f, Commit c) {
        if (!c.getBlobs().containsKey(f.getPath())) {
            throw new GitletException("File does not exist in that commit.");
        }
    }

    private static void checkCommitExistwithID(String ID) {
        if (!getObjectFilebyID(ID).exists()) {
            throw new GitletException("No commit with that id exists.");
        }
    }

    private static String getBlobIDbyFile(Commit c, File f) {
        return c.getBlobs().get(f.getPath());
    }

    public static void checkoutToCommitsFile(String ID, String fileName) {
        File f = join(CWD, fileName);
        checkCommitExistwithID(ID);
        Commit c = getObjectbyID(ID, Commit.class);
        checkFileExistInCommit(f, c);

        rewriteContentforCheckoutToFile(c, f);
    }

    private static void rewriteContentforCheckoutToFile(Commit c, File f) {
        Blob oldBlob = getObjectbyID(getBlobIDbyFile(c, f), Blob.class);
        writeContents(f, oldBlob.getContent());
    }

    public static void checkoutToBranch(String branchName) {
        File branch = join(HEADS_DIR, branchName);
        checkBranchExist(branch);
        checkBranchiscurrBranch(branchName);
    }

    private static void checkBranchExist(File branch) {
        if (!branch.exists()) {
            throw new GitletException("No such branch exists.");
        }
    }

    private static void checkBranchiscurrBranch(String branchName) {
        if (getCurrBranch().equals(branchName)) {
            throw new GitletException("No need to checkout the current branch.");
        }
    }

    public static void createNewBranch(String branchName) {

    }

    public static void removeBranch(String branchName) {

    }

    public static void resetToCommit(String commitID) {

    }

    public static void mergeToBranch(String branchName) {

    }
}
