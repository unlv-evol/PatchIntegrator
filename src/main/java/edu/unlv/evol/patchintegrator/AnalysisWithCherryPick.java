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
    private String sourceURL; //e.g Apache Kafka
    private String forkURL; // e.g linkedIn
    private int [] missedPatches; //11012
    private final String repoListFile;

    // --- added ---
//    public AnalysisWithCherryPick(String clonePath, String sourceURL, String forkURL, int [] missedPatches){
//        this.clonePath = clonePath;
//        this.sourceURL = sourceURL;
//        this.forkURL = forkURL;
//        this.missedPatches = missedPatches;
//    }

    public AnalysisWithCherryPick(String repoListFile, String clonePath){
        this.repoListFile = repoListFile;
        this.clonePath = clonePath;
    }

    public void start(int parallelism) {
        try {
            DatabaseUtils.createDatabase();
            runParallel(parallelism);
        } catch (Throwable e) {
            Utils.log(null, e);
            e.printStackTrace();
        }
    }

    /**
     *
     * @throws Exception Exception
     */
    public void start() throws Exception {
        DatabaseUtils.createDatabase();
        Base.open();
//        cloneAndAnalyzeProject(sourceURL, forkURL); //clone the variant fork project
        Base.close();
    }
    private void runParallel(int parallelism) throws Exception {
        List<String> projectURLs = Files.readAllLines(Paths.get(repoListFile));

        ForkJoinPool forkJoinPool = null;
        try {
            forkJoinPool = new ForkJoinPool(parallelism);
            forkJoinPool.submit(() ->
                    projectURLs.parallelStream().forEach(s -> {
                        String [] lineSpitted = s.split(",");
                        sourceURL = lineSpitted[0];
                        forkURL = lineSpitted[1];
                        missedPatches = Arrays.stream(lineSpitted, 2, (lineSpitted.length - 1)).mapToInt(Integer::parseInt).toArray();
                        Base.open();
                        cloneAndAnalyzeProject(sourceURL, forkURL, missedPatches);
                        Base.close();
                    })
            ).get();
        } catch (InterruptedException | ExecutionException e) {
            e.printStackTrace();
        } finally {
            if (forkJoinPool != null) {
                forkJoinPool.shutdown();
            }
        }
    }

    /**
     *
     * @param sourceURL GitHub repo of the source variant
     * @param forkURL GitHub repo of the target(divergent) variant
     */
    private void cloneAndAnalyzeProject(String sourceURL, String forkURL, int [] missedPatches) {
        String forkName = Utils.getProjectName(forkURL);
        String sourceName = Utils.getProjectName(sourceURL);

        Project project = Project.findFirst("fork_url = ?", forkURL);
        if (project == null) {
            project = new Project(sourceURL, sourceName, forkURL, forkName);
            project.saveIt();
        } else if (project.isDone()) {
            Utils.log(forkName, String.format("%s has been already analyzed, skipping...", forkName));
            return;
        }
        try {
            removeProject(forkName);
            cloneProject(forkURL);
            addRemoteRepo(sourceURL, forkURL);
            analyzeProject(project, missedPatches);
            project.setDone();
            project.saveIt();
            Utils.log(forkName, "Finished the analysis, removing the repository...");
            removeProject(forkName);
            Utils.log(forkName, "Done with " + forkName);
        } catch (JGitInternalException | GitAPIException | IOException | URISyntaxException e) {
            Utils.log(forkName, e);
            e.printStackTrace();
        }
    }

    /**
     * Clones the target variant repository to a local directory
     * specified by the clonePath variable
     * @param forkURL URL of the target (divergent fork) variant
     * @throws GitAPIException GitAPIException
     * @see GitAPIException
     */
    private void cloneProject(String forkURL) throws GitAPIException{
        String forkName = Utils.getProjectName(forkURL);
        Utils.log(forkName, String.format("Cloning %s...", forkName));
        Git.cloneRepository()
                .setURI(forkURL)
                .setDirectory(new File(clonePath, forkName))
                .call();

    }

    /**
     * Adds remote (source variant) to the current cloned target variant.
     * It also runs git fetch command to include the content of remote repo into target variant
     * @param sourceURL URL of the source variant
     * @param forkURL URL of the target (divergent fork) variant
     * @throws IOException
     * @throws URISyntaxException
     * @throws GitAPIException
     */
    private void addRemoteRepo(String sourceURL, String forkURL) throws IOException, URISyntaxException, GitAPIException {
        String forkName = Utils.getProjectName(forkURL);
        File file = new File(String.format("%s/%s", clonePath, forkName)); //projects/projectname
        if(file.isDirectory()){
            // project was cloned successfully, we can now add remote
            Git git = new Git(Git.open(file).getRepository());
            Utils.log(forkName, String.format("Adding remote (%s) repository to %s...", Utils.getProjectName(sourceURL), forkName));
            git.remoteAdd()
                    .setName(Utils.getProjectName(sourceURL))
                    .setUri(new URIish(String.format("%s.git", sourceURL))) //project-url.git
                    .call();

            Utils.log(forkName, String.format("Fetching remote (%s) content to %s...", Utils.getProjectName(sourceURL), forkName));
            git.fetch()
                    .setRemote(Utils.getProjectName(sourceURL))
                    .call();
        }
    }

    /**
     * Removes projects that were cloned in the project directory
     * @param forkName specifies the project name
     */
    private void removeProject(String forkName) {
        try {
            Files.walk(Paths.get(new File(clonePath, forkName).getAbsolutePath()))
                    .sorted(Comparator.reverseOrder())
                    .map(Path::toFile)
                    .forEach(File::delete);
        } catch (IOException e) {
            System.err.println(e.getMessage());
        }
    }

    /**
     *
     * @param project cloned repo of the target variant
     * @throws GitAPIException thrown when git operation fails
     * @throws IOException thrown when project directory is not available
     */
    private void analyzeProject(Project project, int [] missedPatches) throws GitAPIException, IOException {
        Utils.log(project.getName(), String.format("Analyzing %s's commits...", project.getName()));
        analyzeProjectCommits(project, missedPatches);

        Utils.log(project.getName(), String.format("Analyzing %s with RefMiner...", project.getName()));
        analyzeProjectWithRefMiner(project);
    }

    /**
     *
     * @param project cloned repo of the target variant
     * @throws GitAPIException thrown when git operation fails
     * @throws IOException thrown when project directory is not available
     */
    private void analyzeProjectCommits(Project project, int [] missedPatches) throws GitAPIException, IOException {
        GitUtils gitUtils = new GitUtils(new File(clonePath, project.getName()));

        //---edit this line of code, get the mergeCommit of the patch/pull request---
        for(int patch: missedPatches) {
            Utils.log(project.getName(), String.format("Analyzing Patch......%d", patch));
            Patch patchModel;
            String prMergeCommit = new GitHubUtils().getMergeCommitSha(sourceURL, patch);
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
                    boolean isConflicting = gitUtils.isConflictingCherryPick(mergeCommit, conflictingJavaFiles);

                    patchModel = new Patch(patch, isConflicting, project);
                    patchModel.saveIt();
                    patchModel.setDone();

                    mergeCommitModel = new MergeCommit(mergeCommit.getName(), isConflicting,
                            mergeParent.getName(), mergeCommit.getName(), project, patchModel,
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
    }

    /**
     *
     * @param gitUtils instance of the GitUtils class
     * @param mergeCommit instance of the MergeCommit class
     * @param conflictingJavaFiles stores conflicting java files in a map data structure
     * @see GitHubUtils
     * @see MergeCommit
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
     * @param project cloned repo of the target variant
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
     * @param conflictingRegionHistory instance of ConflictingRegionHistory
     * @return refactoringCommit
     * @see ConflictingRegionHistory
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
     * @param executor service executor object
     * @param refMinerUtils object refMinerUtils
     * @param project cloned repo of the target variant
     * @param conflictingRegionHistory instance of ConflictingRegionHistory
     * @param refactoringCommit object of RefactoringCommit class
     * @throws InterruptedException interrupt exception
     * @throws ExecutionException execution exception
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
     * @param refactorings list of refactorings
     * @param refactoringCommit object of RefactoringCommit class
     * @param refMinerUtils object refMinerUtils
     * @see RefactoringCommit
     * @see RefactoringMinerUtils
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
