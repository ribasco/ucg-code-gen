package com.ibasco.ucgdisplay.tools.beans;

import com.google.gson.annotations.Expose;
import com.google.gson.annotations.SerializedName;

public class GithubFile {
    @SerializedName("name")
    @Expose
    private String name;
    @SerializedName("path")
    @Expose
    private String path;
    @SerializedName("sha")
    @Expose
    private String sha;
    @SerializedName("size")
    @Expose
    private Integer size;
    @SerializedName("url")
    @Expose
    private String url;
    @SerializedName("html_url")
    @Expose
    private String htmlUrl;
    @SerializedName("git_url")
    @Expose
    private String gitUrl;
    @SerializedName("download_url")
    @Expose
    private String downloadUrl;
    @SerializedName("type")
    @Expose
    private String type;
    @SerializedName("_links")
    @Expose
    private GithubLinks githubLinks;

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getPath() {
        return path;
    }

    public void setPath(String path) {
        this.path = path;
    }

    public String getSha() {
        return sha;
    }

    public void setSha(String sha) {
        this.sha = sha;
    }

    public Integer getSize() {
        return size;
    }

    public void setSize(Integer size) {
        this.size = size;
    }

    public String getUrl() {
        return url;
    }

    public void setUrl(String url) {
        this.url = url;
    }

    public String getHtmlUrl() {
        return htmlUrl;
    }

    public void setHtmlUrl(String htmlUrl) {
        this.htmlUrl = htmlUrl;
    }

    public String getGitUrl() {
        return gitUrl;
    }

    public void setGitUrl(String gitUrl) {
        this.gitUrl = gitUrl;
    }

    public String getDownloadUrl() {
        return downloadUrl;
    }

    public void setDownloadUrl(String downloadUrl) {
        this.downloadUrl = downloadUrl;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public GithubLinks getGithubLinks() {
        return githubLinks;
    }

    public void setGithubLinks(GithubLinks githubLinks) {
        this.githubLinks = githubLinks;
    }
}