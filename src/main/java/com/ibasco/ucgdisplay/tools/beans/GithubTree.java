package com.ibasco.ucgdisplay.tools.beans;

import com.google.gson.annotations.Expose;
import com.google.gson.annotations.SerializedName;

import java.util.ArrayList;
import java.util.List;

public class GithubTree {

    @SerializedName("sha")
    @Expose
    private String sha;

    @SerializedName("url")
    @Expose
    private String url;

    @SerializedName("tree")
    @Expose
    private List<GithubTreeNode> treeNodes = new ArrayList<>();

    @SerializedName("truncated")
    @Expose
    private Boolean truncated;

    public String getSha() {
        return sha;
    }

    public void setSha(String sha) {
        this.sha = sha;
    }

    public String getUrl() {
        return url;
    }

    public void setUrl(String url) {
        this.url = url;
    }

    public List<GithubTreeNode> getTreeNodes() {
        return treeNodes;
    }

    public void setTreeNodes(List<GithubTreeNode> tree) {
        this.treeNodes = tree;
    }

    public Boolean getTruncated() {
        return truncated;
    }

    public void setTruncated(Boolean truncated) {
        this.truncated = truncated;
    }
}