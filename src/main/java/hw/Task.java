package hw;

import lombok.SneakyThrows;
import org.springframework.web.client.RestTemplate;

import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URL;
import java.net.http.HttpClient;
import java.net.http.HttpHeaders;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

public class Task {
    private static final String url = "https://api.nasa.gov/mars-photos/api/v1/rovers/curiosity/photos?sol=15&api_key=U2oXIN2fuIFK0aiXAoHXPYLvXihE6hY3CkSkvPV7";
    private static String urlWithMaxContentSize = "";
    private static Integer maxSize = 0;

    public static void main(String[] args) {
        syncOldHttpURLConnection();
        resetData();
        syncNewHttpClient();
        resetData();
        asyncNewHttpClient();
        resetData();
        asyncNewHttpClientInParallel();
        resetData();
        asyncOldHttpURLConnection();
        resetData();
    }


    @SneakyThrows
    public static void syncOldHttpURLConnection() {
        Long start = System.currentTimeMillis();
        List<Photo> photos = getPhotos();

        for (Photo photo : photos) {
            HttpURLConnection connection = (HttpURLConnection) new URL(photo.getUrl()).openConnection();
            connection.connect();

            HttpURLConnection connection2 = (HttpURLConnection) new URL(connection.getHeaderFields().get("Location").get(0)).openConnection();
            connection2.connect();

            checkSaveMax(photo.getUrl(), connection2.getContentLength());
        }
        printResult("SYNC HttpURLConnection", start);
    }


    @SneakyThrows
    public static void syncNewHttpClient() {
        Long start = System.currentTimeMillis();
        List<Photo> photos = getPhotos();

        for (Photo photo : photos) {
            HttpHeaders headers = HttpClient.newBuilder()
                    .followRedirects(HttpClient.Redirect.ALWAYS)
                    .build()
                    .send(HttpRequest.newBuilder(URI.create(photo.getUrl())).build(), HttpResponse.BodyHandlers.ofString())
                    .headers();
            checkSaveMax(photo.getUrl(), headers.firstValue("content-length").orElseGet(() -> "0"));
        }
        printResult("SYNC HttpClient", start);
    }

    @SneakyThrows
    public static void asyncNewHttpClient() {
        Long start = System.currentTimeMillis();
        List<Photo> photos = getPhotos();

        HttpClient httpClient = HttpClient.newBuilder()
                .followRedirects(HttpClient.Redirect.ALWAYS)
                .build();

        List<CompletableFuture<HttpResponse<String>>> responsesCF = photos.stream()
                .map(Photo::getUrl)
                .map(url -> httpClient
                        .sendAsync(HttpRequest.newBuilder(URI.create(url)).build(),
                                HttpResponse.BodyHandlers.ofString())
                        .thenApply(t -> t))
                .toList();

        List<HttpResponse<String>> responses = responsesCF.stream()
                .map(cf -> {
                    HttpResponse<String> response = null;
                    try {
                        response = cf.get();
                    } catch (InterruptedException | ExecutionException e) {
                        e.printStackTrace();
                    }
                    return response;
                })
                .toList();

        for (HttpResponse<String> response : responses) {
            checkSaveMax(response.previousResponse().get().uri().toString(),
                    response.headers().firstValue("content-length").orElseGet(() -> "0"));
        }

        printResult("ASYNC HttpClient", start);
    }

    @SneakyThrows
    public static void asyncNewHttpClientInParallel() {
        Long start = System.currentTimeMillis();
        List<Photo> photos = getPhotos();

        List<HttpResponse<String>> responses = photos.stream().parallel()
                .map(photo -> {
                    HttpResponse<String> response = null;
                    try {
                        response = HttpClient.newBuilder()
                                .followRedirects(HttpClient.Redirect.ALWAYS)
                                .build()
                                .send(HttpRequest.newBuilder(URI.create(photo.getUrl())).build(), HttpResponse.BodyHandlers.ofString());
                    } catch (IOException | InterruptedException e) {
                        e.printStackTrace();
                    }
                    return response;
                }).toList();

        for (HttpResponse<String> response : responses) {
            checkSaveMax(response.previousResponse().get().uri().toString(),
                    response.headers().firstValue("content-length").orElseGet(() -> "0"));
        }
        printResult("ASYNC HttpClient in parallel", start);
    }

    @SneakyThrows
    public static void asyncOldHttpURLConnection() {
        Long start = System.currentTimeMillis();
        List<Photo> photos = getPhotos();

        List<HttpURLConnection> connections = photos.stream().parallel()
                .map(photo -> {
                    HttpURLConnection connection = null;
                    try {
                        connection = (HttpURLConnection) new URL(photo.getUrl()).openConnection();
                        connection.connect();
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                    return connection;
                }).map(connection -> {
                    HttpURLConnection conn = null;
                    try {
                        conn = (HttpURLConnection) new URL(connection.getHeaderFields().get("Location").get(0)).openConnection();
                        conn.connect();
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                    return conn;
                }).toList();


        for (HttpURLConnection connection : connections) {
            checkSaveMax(connection.getURL().toString(), connection.getContentLength()); //TODO: not correct!! Have to return 1st link
        }

        printResult("ASYNC HttpURLConnection", start);
    }


    private static List<Photo> getPhotos() {
        RestTemplate restTemplate = new RestTemplate();
        return restTemplate.getForObject(url, Photos.class).getPhotos();
    }

    private static void checkSaveMax(String url, String length) {
        int size = Integer.parseInt(length);
        checkSaveMax(url, size);
    }

    private static void checkSaveMax(String url, int length) {
        if (length > maxSize) {
            maxSize = length;
            urlWithMaxContentSize = url;
        }
    }

    private static void printResult(String methodName, Long start) {
        System.out.println(urlWithMaxContentSize + " " + maxSize);
        System.out.println(methodName + ": " + (System.currentTimeMillis() - start));
    }

    private static void resetData() {
        urlWithMaxContentSize = "";
        maxSize = 0;
    }
}
