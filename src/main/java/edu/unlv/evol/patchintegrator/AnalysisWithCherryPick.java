package edu.unlv.evol.patchintegrator;

import edu.unlv.evol.patchintegrator.database.*;
import edu.unlv.evol.patchintegrator.utils.GitHubUtils;
import edu.unlv.evol.patchintegrator.utils.GitUtils;
import edu.unlv.evol.patchintegrator.utils.RefactoringMinerUtils;
import edu.unlv.evol.patchintegrator.utils.Utils;
import gr.uom.java.xmi.diff.CodeRange;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.api.errors.JGitInternalException;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.transport.URIish;
import org.javalite.activejdbc.Base;
import org.refactoringminer.api.Refactoring;

import java.io.File;
import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.concurrent.*;

public class AnalysisWithCherryPick {
    private final String clonePath;
    private final String sourceURL; //e.g Apache Kafka
    private final String targetURL; // e.g linkedIn
    private final int missedPatches; //11012

    // --- added ---
    public AnalysisWithCherryPick(String clonePath, String sourceURL, String targetURL, int missedPatches){
        this.clonePath = clonePath;
        this.sourceURL = sourceURL;
        this.targetURL = targetURL;
        this.missedPatches = missedPatches;
    }

    /**
     *
     * @throws Exception
     */
    public void start() throws Exception {
        DatabaseUtils.createDatabase();
        Base.open();
        cloneAndAnalyzeProject(sourceURL, targetURL); //clone the variant fork project
        Base.close();
    }

    /**
     *
     * @param sourceURL
     * @param targetURL
     */
    private void cloneAndAnalyzeProject(String sourceURL, String targetURL) {
        String projectName = Utils.getProjectName(targetURL);

        Project project = Project.findFirst("url = ?", targetURL);
        if (project == null) {
            project = new Project(targetURL, projectName);
            project.saveIt();
        } else if (project.isDone()) {
            Utils.log(projectName, String.format("%s has been already analyzed, skipping...", projectName));
            return;
        }
        try {
            removeProject(projectName);
            cloneProject(targetURL);
            addRemoteRepo(sourceURL, targetURL);
            analyzeProject(project);
            project.setDone();
            project.saveIt();
            Utils.log(projectName, "Finished the analysis, removing the repository...");
            removeProject(projectName);
            Utils.log(projectName, "Done with " + projectName);
        } catch (JGitInternalException | GitAPIException | IOException | URISyntaxException e) {
            Utils.log(projectName, e);
            e.printStackTrace();
        }
    }

    /**
     * Clones the target variant repository to a local directory
     * specified by the clonePath variable
     * @param targetURL URL of the target (divergent fork) variant
     * @throws GitAPIException
     */
    private void cloneProject(String targetURL) throws GitAPIException{
        String projectName = Utils.getProjectName(targetURL);
        Utils.log(projectName, String.format("Cloning %s...", projectName));
        Git.cloneRepository()
                .setURI(targetURL)
                .setDirectory(new File(clonePath, projectName))
                .call();

    }

    /**
     * Adds remote (source variant) to the current cloned target variant.
     * It also runs git fetch command to include the content of remote repo into target variant
     * @param sourceURL URL of the source variant
     * @param targetURL URL of the target (divergent fork) variant
     * @throws IOException
     * @throws URISyntaxException
     * @throws GitAPIException
     */
    private void addRemoteRepo(String sourceURL, String targetURL) throws IOException, URISyntaxException, GitAPIException {
        String projectName = Utils.getProjectName(targetURL);
        File file = new File(String.format("%s/%s", clonePath, projectName)); //projects/projectname
        if(file.isDirectory()){
            // project was cloned successfully, we can now add remote
            Git git = new Git(Git.open(file).getRepository());
            Utils.log(projectName, String.format("Adding remote repo to %s...", projectName));
            git.remoteAdd()
                    .setName(Utils.getProjectName(sourceURL))
                    .setUri(new URIish(String.format("%s.git", sourceURL))) //project-url.git
                    .call();

            Utils.log(projectName, String.format("Fetching remote content to %s...", projectName));
            git.fetch()
                    .setRemote(Utils.getProjectName(sourceURL))
                    .call();
        }
    }

    /**
     * Removes projects that were cloned in the project directory
     * @param projectName specifies the project name
     */
    private void removeProject(String projectName) {
        try {
            Files.walk(Paths.get(new File(clonePath, projectName).getAbsolutePath()))
                    .sorted(Comparator.reverseOrder())
                    .map(Path::toFile)
                    .forEach(File::delete);
        } catch (IOException e) {
            System.err.println(e.getMessage());
        }
    }

    /**
     *
     * @param project
     * @throws GitAPIException
     * @throws IOException
     */
    private void analyzeProject(Project project) throws GitAPIException, IOException {
        Utils.log(project.getName(), String.format("Analyzing %s's commits...", project.getName()));
        analyzeProjectCommits(project);

        Utils.log(project.getName(), String.format("Analyzing %s with RefMiner...", project.getName()));
        analyzeProjectWithRefMiner(project);
    }

    /**
     *
     * @param project
     * @throws GitAPIException
     * @throws IOException
     */
    private void analyzeProjectCommits(Project project) throws GitAPIException, IOException {
        GitUtils gitUtils = new GitUtils(new File(clonePath, project.getName()));

        //---edit this line of code, get the mergeCommit of the patch/pull request---
        String prMergeCommit = new GitHubUtils().getMergeCommitSha(sourceURL, missedPatches);
        Iterable<RevCommit> mergeCommits = gitUtils.getMergeCommit(prMergeCommit);
        RevCommit mergeParent = gitUtils.getLastCommit();
        int mergeCommitIndex = 0;
        Map<String, String> conflictingJavaFiles = new HashMap<>();
        for (RevCommit mergeCommit : mergeCommits) {
            mergeCommitIndex++;
            Utils.log(project.getName(), String.format("Analyzing commit %.7s... (%d/?)", mergeCommit.getName(),
                    mergeCommitIndex));

            // Skip this commit if it already exists in the database.
            MergeCommit mergeCommitModel = MergeCommit.findFirst("commit_hash = ?", mergeCommit.getName());
            if (mergeCommitModel != null) {
                if (mergeCommitModel.isDone()) {
                    Utils.log(project.getName(), "Already analyzed, skipping...");
                    continue;
                }
                // Will cascade to dependent records because of foreign key constraints
                mergeCommitModel.delete();
            }

            try {
                conflictingJavaFiles.clear();
                boolean isConflicting = gitUtils.isConflicting(mergeCommit, conflictingJavaFiles);

                mergeCommitModel = new MergeCommit(mergeCommit.getName(), isConflicting,
                        mergeParent.getName(), mergeCommit.getName(), project,
                        mergeCommit.getAuthorIdent().getName(), mergeCommit.getAuthorIdent().getEmailAddress(),
                        mergeCommit.getCommitTime());

                mergeCommitModel.saveIt();
                extractConflictingRegions(gitUtils, mergeCommitModel, conflictingJavaFiles);
                mergeCommitModel.setDone();
                mergeCommitModel.saveIt();
            } catch (GitAPIException e) {
                Utils.log(project.getName(), e);
                e.printStackTrace();
            }
            conflictingJavaFiles.clear();
        }
    }

    /**
     *
     * @param gitUtils
     * @param mergeCommit
     * @param conflictingJavaFiles
     */
    private void extractConflictingRegions(GitUtils gitUtils, MergeCommit mergeCommit,
                                           Map<String, String> conflictingJavaFiles) {
        List<int[][]> conflictingRegions = new ArrayList<>();
        List<GitUtils.CodeRegionChange> leftConflictingRegionHistory = new ArrayList<>();
        List<GitUtils.CodeRegionChange> rightConflictingRegionHistory = new ArrayList<>();

        for (String path : conflictingJavaFiles.keySet()) {
            String conflictType = conflictingJavaFiles.get(path);
            ConflictingJavaFile conflictingJavaFile = new ConflictingJavaFile(path, conflictType, mergeCommit);
            conflictingJavaFile.saveIt();

            if (conflictType.equalsIgnoreCase("content") ||
                    conflictType.equalsIgnoreCase("add/add")) {
                String[] conflictingRegionPaths = new String[2];
                conflictingRegions.clear();
                gitUtils.getConflictingRegions(path, conflictingRegionPaths, conflictingRegions);

                for (int[][] conflictingLines : conflictingRegions) {
                    ConflictingRegion conflictingRegion = new ConflictingRegion(
                            conflictingLines[0][0], conflictingLines[0][1], conflictingRegionPaths[0],
                            conflictingLines[1][0], conflictingLines[1][1], conflictingRegionPaths[1],
                            conflictingJavaFile);
                    conflictingRegion.saveIt();

                    leftConflictingRegionHistory.clear();
                    rightConflictingRegionHistory.clear();
                    gitUtils.getConflictingRegionHistory(mergeCommit.getParent1(), mergeCommit.getParent2(),
                            path, conflictingLines[0], leftConflictingRegionHistory);
                    gitUtils.getConflictingRegionHistory(mergeCommit.getParent2(), mergeCommit.getParent1(),
                            path, conflictingLines[1], rightConflictingRegionHistory);

                    leftConflictingRegionHistory.forEach(codeRegionChange -> {
                        RevCommit commit = gitUtils.populateCommit(codeRegionChange.commitHash);
                        String authorName = commit == null ? null : commit.getAuthorIdent().getName();
                        String authorEmail = commit == null ? null : commit.getAuthorIdent().getEmailAddress();
                        int timestamp = commit == null ? 0 : commit.getCommitTime();
                        new ConflictingRegionHistory(
                                codeRegionChange.commitHash, 1,
                                codeRegionChange.oldStartLine, codeRegionChange.oldLength, codeRegionChange.oldPath,
                                codeRegionChange.newStartLine, codeRegionChange.newLength, codeRegionChange.newPath,
                                conflictingRegion, authorName, authorEmail, timestamp).saveIt();
                    });
                    rightConflictingRegionHistory.forEach(codeRegionChange -> {
                        RevCommit commit = gitUtils.populateCommit(codeRegionChange.commitHash);
                        String authorName = commit == null ? null : commit.getAuthorIdent().getName();
                        String authorEmail = commit == null ? null : commit.getAuthorIdent().getEmailAddress();
                        int timestamp = commit == null ? 0 : commit.getCommitTime();
                        new ConflictingRegionHistory(
                                codeRegionChange.commitHash, 2,
                                codeRegionChange.oldStartLine, codeRegionChange.oldLength, codeRegionChange.oldPath,
                                codeRegionChange.newStartLine, codeRegionChange.newLength, codeRegionChange.newPath,
                                conflictingRegion, authorName, authorEmail, timestamp).saveIt();
                    });
                }
            }
        }
        conflictingRegions.clear();
        leftConflictingRegionHistory.clear();
        rightConflictingRegionHistory.clear();
    }

    /**
     *
     * @param project
     */
    private void analyzeProjectWithRefMiner(Project project) {
        List<ConflictingRegionHistory> historyConfRegions =
                ConflictingRegionHistory.where("project_id = ?", project.getId());

        ExecutorService executor = Executors.newSingleThreadExecutor();
        try {
            File projectFile = new File(clonePath, project.getName());
            RefactoringMinerUtils refMinerUtils = new RefactoringMinerUtils(projectFile);

            for (int i = 0; i < historyConfRegions.size(); i++) {
                ConflictingRegionHistory conflictingRegionHistory = historyConfRegions.get(i);
                Utils.log(project.getName(), String.format("Analyzing commit %.7s with RefMiner... (%d/%d)",
                        conflictingRegionHistory.getCommitHash(), i + 1, historyConfRegions.size()));

                RefactoringCommit refactoringCommit = populateRefactoringCommit(conflictingRegionHistory);
                if (refactoringCommit == null) {
                    Utils.log(project.getName(), String.format("Already analyzed %.7s with RefMiner. Skipping...",
                            conflictingRegionHistory.getCommitHash()));
                    continue;
                }

                asyncRunRefMiner(executor, refMinerUtils, project, conflictingRegionHistory, refactoringCommit);
            }
        } catch (IOException | InterruptedException | ExecutionException e) {
            Utils.log(project.getName(), e);
            e.printStackTrace();
        }
        historyConfRegions.clear();
        executor.shutdownNow();
    }

    /**
     *
     * @param conflictingRegionHistory
     * @return
     */
    private RefactoringCommit populateRefactoringCommit(ConflictingRegionHistory conflictingRegionHistory) {
        RefactoringCommit refactoringCommit = RefactoringCommit.findFirst("commit_hash = ?",
                conflictingRegionHistory.getCommitHash());

        if (refactoringCommit == null) {
            refactoringCommit = new RefactoringCommit(conflictingRegionHistory.getCommitHash(),
                    conflictingRegionHistory.getProjectId());
            refactoringCommit.saveIt();
        } else if (refactoringCommit.isProcessed()) {
            return null;
        } else {
               edu.unlv.evol.patchintegrator.database.Refactoring.delete(
                    "refactoring_commit_id = ?", refactoringCommit.getID());
        }
        return refactoringCommit;
    }

    /**
     *
     * @param executor
     * @param refMinerUtils
     * @param project
     * @param conflictingRegionHistory
     * @param refactoringCommit
     * @throws InterruptedException
     * @throws ExecutionException
     */
    private void asyncRunRefMiner(ExecutorService executor, RefactoringMinerUtils refMinerUtils, Project project,
                                  ConflictingRegionHistory conflictingRegionHistory, RefactoringCommit refactoringCommit)
            throws InterruptedException, ExecutionException {
        List<Refactoring> refactorings = new ArrayList<>();
        Future futureRefMiner = executor.submit(() -> {
            try {
                refMinerUtils.detectAtCommit(conflictingRegionHistory.getCommitHash(), refactorings);
            } catch (Exception e) {
                Utils.log(project.getName(), e);
                e.printStackTrace();
            }
        });

        try {
            // Wait up to 4 minutes for RefactoringMiner to finish its analysis.
            futureRefMiner.get(4, TimeUnit.MINUTES);
            processRefactorings(refactorings, refactoringCommit, refMinerUtils);
            refactoringCommit.setDone();
            refactoringCommit.saveIt();

        } catch (TimeoutException e) {
            Utils.log(project.getName(), String.format("Commit %.7s timed out. Skipping...",
                    refactoringCommit.getCommitHash()));
            refactoringCommit.setTimedOut();
            refactoringCommit.saveIt();
        }
    }

    /**
     *
     * @param refactorings
     * @param refactoringCommit
     * @param refMinerUtils
     */
    private void processRefactorings(List<Refactoring> refactorings, RefactoringCommit refactoringCommit,
                                     RefactoringMinerUtils refMinerUtils) {
        for (Refactoring refactoring : refactorings) {
            edu.unlv.evol.patchintegrator.database.Refactoring refactoringModel =
                    new edu.unlv.evol.patchintegrator.database.Refactoring(
                            refactoring.getRefactoringType().getDisplayName(),
                            refactoring.toString(),
                            refactoringCommit);
            refactoringModel.saveIt();

            List<CodeRange> sourceCodeRanges = new ArrayList<>();
            List<CodeRange> destCodeRanges = new ArrayList<>();
            refMinerUtils.getRefactoringCodeRanges(refactoring, sourceCodeRanges, destCodeRanges);
            sourceCodeRanges.forEach(cr -> new RefactoringRegion('s', cr.getFilePath(), cr.getStartLine(),
                    cr.getEndLine() - cr.getStartLine(), refactoringModel).saveIt());
            destCodeRanges.forEach(cr -> new RefactoringRegion('d', cr.getFilePath(), cr.getStartLine(),
                    cr.getEndLine() - cr.getStartLine(), refactoringModel).saveIt());
        }
    }
}
