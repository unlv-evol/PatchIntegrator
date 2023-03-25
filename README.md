# Patch Integrator (PI)
The goal of this project is to integrated missed patches from base repository to a divergent variant fork. It utilizes the work done in [RefactoringInMergeCommits]() project.
### Project Context
[LinkedIn]() is a clone-and-own variant of [Apache Kafka]() that was created by copying and adapting the existing code of Apache Kafka that was forked on `2011-08-15T18:06:16Z`. The two software systems kept on synchronizing their new updates until `2021-07-06T17:39:59Z`. Since `2021-07-06T17:39:59Z` (divergence date), the two projects do not share common commits yet actively evolve in parallel. Currently, ( as of `2022-10-01T15:01:39Z`), LinkedIn has _367_ individual commits, and Apache Kafka has _1,216_ individual commits. Development becomes redundant with the continued divergence, and maintenance efforts rapidly grow. For example, if a bug is discovered in a shared file and fixed in one variant, it is not easy to tell if it has been fixed in the other variant.

### System Requirements
- Linux or macOS
- git
- GitHub
- Java 11
- MySQL 5.7

### Dependencies
Project dependencies are easily managed using [gradle]() build tool.
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
**PI** running on `MySQL` database engine. Modify the `database.properties` file and add your MySQL `username` and `password`:
```properties
development.driver=com.mysql.cj.jdbc.Driver
development.username=USERNAME
development.password=PASSWORD
development.url=jdbc:mysql://localhost/Patch_Integrator
```
You can choose a different name for the database. Simply edit `Patch_Integrator` in the `development.url` property above. Please make sure that another database with the same name doesn't exist, since it'll most likely have a different schema and the program will run into problems.

#### 2. Create Dataset



