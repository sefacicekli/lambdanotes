package com.lambdanotes;

public class GitHubRepo {
    private String name;
    private String clone_url;
    private String full_name;

    public String getName() { return name; }
    public String getCloneUrl() { return clone_url; }
    public String getFullName() { return full_name; }
    
    @Override
    public String toString() {
        return full_name;
    }
}
