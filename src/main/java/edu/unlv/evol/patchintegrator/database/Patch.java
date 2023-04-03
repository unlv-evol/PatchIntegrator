package edu.unlv.evol.patchintegrator.database;

import org.javalite.activejdbc.Model;
import org.javalite.activejdbc.annotations.Table;

@Table("patch")
public class Patch extends Model {
    static {
        validatePresenceOf("number");
    }

    public Patch() {
    }

    public Patch(int number, boolean isConflicting, Project project) {
        set("number", number, "is_conflicting", isConflicting, "project_id", project.getId(), "is_done", false);
    }

    public int getProjectId() {
        return getInteger("project_id");
    }
    public int getNumber() {
        return getInteger("number");
    }
    public boolean getIsConflicting(){
        return getBoolean("is_conflicting");
    }
    public boolean isDone() {
        return getBoolean("is_done");
    }
    public void setDone() {
        setBoolean("is_done", true);
    }
}
