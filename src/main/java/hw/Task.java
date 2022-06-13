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
    private static Long start = 0l;

    public static void main(String[] args) {
        start = System.currentTimeMillis();
        syncOldHttpURLConnection();
        printResult("SYNC HttpURLConnection", start);
        resetData();

        start = System.currentTimeMillis();
        syncNewHttpClient();
        printResult("SYNC HttpClient", start);
        resetData();

        start = System.currentTimeMillis();
        asyncNewHttpClient();
        printResult("ASYNC HttpClient", start);
        resetData();

        start = System.currentTimeMillis();
        asyncNewHttpClientInParallel();
        printResult("ASYNC HttpClient in parallel", start);
        resetData();

        start = System.currentTimeMillis();
        asyncOldHttpURLConnection();
        printResult("ASYNC HttpURLConnection", start);
        resetData();
    }


    @SneakyThrows
    public static void syncOldHttpURLConnection() {
        List<Photo> photos = getPhotos();

        for (Photo photo : photos) {
            HttpURLConnection connection = (HttpURLConnection) new URL(photo.getUrl()).openConnection();
            connection.connect();

            HttpURLConnection connection2 = (HttpURLConnection) new URL(connection.getHeaderFields().get("Location").get(0)).openConnection();
            connection2.connect();

            checkSaveMax(photo.getUrl(), connection2.getContentLength());
        }
    }


    @SneakyThrows
    public static void syncNewHttpClient() {
        for (Photo photo : getPhotos()) {
            HttpHeaders headers = HttpClient.newBuilder()
                    .followRedirects(HttpClient.Redirect.ALWAYS)
                    .build()
                    .send(HttpRequest.newBuilder(URI.create(photo.getUrl())).build(), HttpResponse.BodyHandlers.ofString())
                    .headers();
            checkSaveMax(photo.getUrl(), headers.firstValue("content-length").orElseGet(() -> "0"));
        }
    }

    @SneakyThrows
    public static void asyncNewHttpClient() {
        List<HttpResponse<String>> responses = getPhotos().stream()
                .map(Photo::getUrl)
                .map(url -> HttpClient.newBuilder()
                        .followRedirects(HttpClient.Redirect.ALWAYS)
                        .build()
                        .sendAsync(HttpRequest.newBuilder(URI.create(url)).build(),
                                HttpResponse.BodyHandlers.ofString())
                        .thenApply(t -> t))
                .toList().stream()
                .map(Task::getStringHttpResponse)
                .toList();

        for (HttpResponse<String> response : responses) {
            checkSaveMax(response.previousResponse().get().uri().toString(),
                    response.headers().firstValue("content-length").orElseGet(() -> "0"));
        }
    }

    @SneakyThrows
    public static void asyncNewHttpClientInParallel() {
        getPhotos().stream().parallel()
                .map(Photo::getUrl)
                .map(Task::getResponse)
                .map(httpResponse -> checkSaveMax(httpResponse.previousResponse().get().uri().toString(),
                        httpResponse.headers().firstValue("content-length").orElseGet(() -> "0")))
                .findAny()
                .orElseGet(() -> null);
    }

    @SneakyThrows
    public static void asyncOldHttpURLConnection() {
        List<Photo> photos = getPhotos();

        List<HttpURLConnection> connections = photos.stream().parallel()
                .map(photo -> getHttpURLConnection(photo.getUrl()))
                .map(connection -> getHttpURLConnection(connection.getHeaderFields().get("Location").get(0)))
                .toList();


        for (HttpURLConnection connection : connections) {
            checkSaveMax(connection.getURL().toString(), connection.getContentLength()); //TODO: not correct!! Have to return 1st link
        }
    }

    private static List<Photo> getPhotos() {
        RestTemplate restTemplate = new RestTemplate();
        return restTemplate.getForObject(url, Photos.class).getPhotos();
    }

    private static HttpResponse<String> getStringHttpResponse(CompletableFuture<HttpResponse<String>> cf) {
        HttpResponse<String> response = null;
        try {
            response = cf.get();
        } catch (InterruptedException | ExecutionException e) {
            e.printStackTrace();
        }
        return response;
    }

    private static HttpResponse<String> getResponse(String url) {
        HttpResponse<String> response = null;
        try {
            response = HttpClient.newBuilder()
                    .followRedirects(HttpClient.Redirect.ALWAYS)
                    .build()
                    .send(HttpRequest.newBuilder(URI.create(url)).build(),
                            HttpResponse.BodyHandlers.ofString());
        } catch (IOException | InterruptedException e) {
            e.printStackTrace();
        }
        return response;
    }

    private static HttpURLConnection getHttpURLConnection(String url) {
        HttpURLConnection connection = null;
        try {
            connection = (HttpURLConnection) new URL(url).openConnection();
            connection.connect();
        } catch (IOException e) {
            e.printStackTrace();
        }
        return connection;
    }

    private static boolean checkSaveMax(String url, String length) {
        int size = Integer.parseInt(length);
        checkSaveMax(url, size);
        return false;
    }

    private static boolean checkSaveMax(String url, int length) {
        if (length > maxSize) {
            maxSize = length;
            urlWithMaxContentSize = url;
            return true;
        }
        return false;
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
