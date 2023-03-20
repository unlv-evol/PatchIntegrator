package edu.unlv.evol.patchintegrator.utils;

import org.kohsuke.github.GHIssueState;
import org.kohsuke.github.GHPullRequest;
import org.kohsuke.github.GHRepository;
import org.kohsuke.github.GitHub;

import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Properties;
import java.util.logging.Logger;

public class GitHubUtils {
    private static final String GITHUB_URL = "https://github.com/";
    private static final String BITBUCKET_URL = "https://bitbucket.org/";
    private static final Logger logger = Logger.getLogger(GitHubUtils.class.getName());
    private GitHub gitHub;

    public GitHubUtils() {

    }

    /**
     *
     * @param cloneURL
     * @return
     */
    private static String extractRepositoryName(String cloneURL) {
        int hostLength = 0;
        if (cloneURL.startsWith(GITHUB_URL)) {
            hostLength = GITHUB_URL.length();
        } else if (cloneURL.startsWith(BITBUCKET_URL)) {
            hostLength = BITBUCKET_URL.length();
        }
        int indexOfDotGit = cloneURL.length();
        if (cloneURL.endsWith(".git")) {
            indexOfDotGit = cloneURL.indexOf(".git");
        } else if (cloneURL.endsWith("/")) {
            indexOfDotGit = cloneURL.length() - 1;
        }
        return cloneURL.substring(hostLength, indexOfDotGit);
    }

    /**
     * establish connection to GitHub using OAuthToken
     * @return instance of GitHub connection
     */
    private GitHub connectToGitHub() {
        if (gitHub == null) {
            try {
                Properties prop = new Properties();
                InputStream input = new FileInputStream("github-oauth.properties");
                prop.load(input);
                String oAuthToken = prop.getProperty("OAuthToken");
                if (oAuthToken != null) {
                    gitHub = GitHub.connectUsingOAuth(oAuthToken);
                    if (gitHub.isCredentialValid()) {
                        logger.info("Connected to GitHub with OAuth token");
                    }
                } else {
                    gitHub = GitHub.connect();
                }
            } catch (FileNotFoundException e) {
                logger.warning("File github-oauth.properties was not found in RefactoringMiner's execution directory " + e);
            } catch (IOException ioe) {
                ioe.printStackTrace();
            }
        }
        return gitHub;
    }

    /**
     * extracts the all pull requests in a given repository for a specific status
     * @param cloneURL The GitHub repository
     * @param status pull request status, OPEN, CLOSED, etc
     * @return list of pull requests of a specific status (OPEN, CLOSED)
     * @throws IOException
     * @see GHPullRequest
     */
    public List<GHPullRequest> getPullRequestsInRepo(String cloneURL, GHIssueState status) throws IOException {

        GitHub gitHub = connectToGitHub();

        String repoName = extractRepositoryName(cloneURL);
        GHRepository repository = gitHub.getRepository(repoName);

        return repository.getPullRequests(status);
    }

    /**
     * extracts the merge commit sha of a given pull request
     * @param cloneURL the url of the repository to be cloned
     * @param pr pull request number
     * @return commit sha of a pull request
     * @throws IOException
     */
    public  String getMergeCommitSha( String cloneURL, int pr) throws IOException {
        GitHub gitHub = connectToGitHub();

        String repoName = extractRepositoryName(cloneURL);
        GHRepository repository = gitHub.getRepository(repoName);
        return repository.getPullRequest(pr).getMergeCommitSha();
    }

    /**
     * extracts the merge commit sha of list of pull request
     * @param cloneURL the url of the repository to be cloned
     * @param pr pull request number
     * @return ArrayList of merge commit sha
     * @throws IOException
     */
    public ArrayList<String> getMergeCommitShaList(String cloneURL, int...pr) throws IOException {
        ArrayList<String> mergeCommitSha = new ArrayList<>();
        String repoName = extractRepositoryName(cloneURL);
        GHRepository repository = gitHub.getRepository(repoName);
        Arrays.stream(pr).forEach(
                item -> {
                    try {
                        mergeCommitSha.add(repository.getPullRequest(item).getMergeCommitSha());
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                }
        );

        return mergeCommitSha;
    }
}
