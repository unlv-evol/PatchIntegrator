# Patch Integrator (PI)
The goal of this project is to integrate missed patches from base repository to a divergent variant fork. It extends the work done in [RefactoringInMergeCommits](https://github.com/danielogen/RefactoringsInMergeCommits) project.
### Project Context
[LinkedIn](https://github.com/linkedin/kafka) is a clone-and-own variant of [Apache Kafka](https://github.com/apache/kafka) that was created by copying and adapting the existing code of Apache Kafka that was forked on `2011-08-15T18:06:16Z`. The two software systems kept on synchronizing their new updates until `2021-07-06T17:39:59Z`. Since `2021-07-06T17:39:59Z` (divergence date), the two projects do not share common commits yet actively evolve in parallel. Currently, ( as of `2022-10-01T15:01:39Z`), LinkedIn has _367_ individual commits, and Apache Kafka has _1,216_ individual commits. Development becomes redundant with the continued divergence, and maintenance efforts rapidly grow. For example, if a bug is discovered in a shared file and fixed in one variant, it is not easy to tell if it has been fixed in the other variant.

### System Requirements
- Linux or macOS
- git
- GitHub
- Java 11
- MySQL 5.7

### Dependencies
Project dependencies are easily managed using [gradle](https://gradle.org/) build tool.
```groovy
dependencies {
    testCompile group: 'junit', name: 'junit', version: '4.12'
    testImplementation 'org.junit.jupiter:junit-jupiter-engine:5.9.2'
    implementation 'com.github.tsantalis:refactoring-miner:2.3.2'
    implementation 'org.kohsuke:github-api:1.135'
    implementation 'commons-cli:commons-cli:1.2'
    implementation 'org.eclipse.jgit:org.eclipse.jgit:6.4.0.202211300538-r'
    implementation 'org.apache.logging.log4j:log4j-api:2.20.0'
    implementation 'org.apache.logging.log4j:log4j-core:2.20.0'
    implementation 'org.javalite:activejdbc:2.2'
    implementation 'org.javalite:activejdbc-instrumentation:2.3'
    implementation 'mysql:mysql-connector-java:8.0.32'
}
```
### Running Patch Integrator
#### 1. Database Setting
**PI** runs on `MySQL` database engine. Modify the `database.properties` file and add your MySQL `username` and `password`:
```properties
development.driver=com.mysql.cj.jdbc.Driver
development.username=USERNAME
development.password=PASSWORD
development.url=jdbc:mysql://localhost/Patch_Integrator
```
You can choose a different database name. To do this, simply edit `Patch_Integrator` in the `development.url` property above. Please make sure that another database with the same name doesn't exist, since it'll most likely have a different schema and the program will run into problems.

#### 2. Create Dataset
The program requires a text file consisting of the following: (i) GitHub repo of the `mainline` (SOURCE_REPO), (ii) GitHub repo of the `divergent fork` (DIVERGENT_REPO) and (iii) list of `patches` to be integrate from `mainline` to `divergent fork`. Each line in the text file should include the complete URL of repos in common separated format. We have included  a sample of the `reposList.txt` file.

#### 3. Build the Project
PI can be built using [gradle](https://gradle.org/) build tool:
```
.\gradlew build
```
Run the project using;
```
.\gradlew run
```
Note that the results will be stored in `MySQL` database configured in **step 1** above.

You can also run unit **tests**:
```
.\gradlew test
```  
To build a `jar` file run this command:

#### 4. Run the JAR
You can run the JAR file with the following command:
```
java -jar refactoring-analysis.jar [OPTIONS]
```
Note that none of the options are required. Here is a list of available options:

```
-c,--clonepath <file>        directory to temporarily download repositories (default=projects)
-d,--dbproperties <file>     database properties file (default=database.properties)
-h,--help                    print this message
-p,--parallelism <threads>   number of threads for parallel computing (default=1)
-r,--reposfile <file>        list of repositories to be analyzed (default=reposList.txt)
```
Here is an example command with all the options:
```commandline
 java -jar patch-integrator.jar -r reposList.txt -c projects -d mydb.properties -p 8 
```
#### 5. Analysis 

