package com.ibasco.ucgdisplay.tools.service;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import com.ibasco.ucgdisplay.tools.beans.GithubFile;
import com.ibasco.ucgdisplay.tools.beans.GithubTree;
import com.ibasco.ucgdisplay.tools.beans.GithubTreeNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.lang.reflect.Type;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.List;
import java.util.stream.Collectors;

public class GithubService {

    private static final Logger log = LoggerFactory.getLogger(GithubService.class);

    private final HttpClient client = HttpClient.newBuilder().build();

    private final Gson gson = new Gson();

    public List<GithubTreeNode> getNodesFromTree(String path, String branch) throws IOException {
        try {
            HttpRequest request = buildGetRequest(String.format("https://api.github.com/repos/olikraus/u8g2/git/trees/%s?recursive=1", branch));
            String response = sendAndGetString(request);
            GithubTree tree = gson.fromJson(response, GithubTree.class);
            return tree.getTreeNodes().stream().filter(p -> p.getPath().startsWith(path)).collect(Collectors.toList());
        } catch (URISyntaxException e) {
            throw new IOException("Unable to extract contents from tree", e);
        }
    }

    public List<GithubFile> getPathContents(String path, String branch) throws IOException {
        try {
            HttpRequest request = buildGetRequest("https://api.github.com/repos/olikraus/u8g2/contents/%s?ref=%s", path, branch);
            log.debug("Retrieving response");
            String response = sendAndGetString(request);
            Type collectionType = new TypeToken<List<GithubFile>>() {
            }.getType();
            return gson.fromJson(response, collectionType);
        } catch (URISyntaxException | IOException e) {
            throw new IOException("Unable to extract contents", e);
        }
    }

    private String sendAndGetString(HttpRequest request) throws IOException {
        try {
            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
            return response.body();
        } catch (InterruptedException e) {
            log.error("Http send interrupted", e);
        }
        return null;
    }

    @SuppressWarnings("SameParameterValue")
    private HttpRequest buildGetRequest(String url, Object... args) throws URISyntaxException {
        return HttpRequest.newBuilder()
                .uri(new URI(String.format(url, args)))
                .GET()
                .build();
    }
}
