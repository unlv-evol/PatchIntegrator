package edu.unlv.evol.patchintegrator.database;

import org.javalite.activejdbc.Model;
import org.javalite.activejdbc.annotations.Table;

@Table("refactoring_region")
public class RefactoringRegion extends Model {

    public RefactoringRegion(){ }
    public RefactoringRegion(char type, String path, int startLine, int length, Refactoring refactoring) {
        set("type", String.valueOf(type).toLowerCase(), "path", path, "start_line", startLine, "length", length,
                "refactoring_id", refactoring.getId(),
                "refactoring_commit_id", refactoring.getRefactoringCommitId(),
                "commit_hash", refactoring.getCommitHash(),
                "project_id", refactoring.getProjectId());
    }
}
