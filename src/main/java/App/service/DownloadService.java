package App.service;

import App.entity.DownloadEntity;
import App.service.task.DownloadEntityTask;
import lombok.extern.slf4j.Slf4j;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.springframework.stereotype.Service;

import javax.annotation.PostConstruct;
import java.io.BufferedReader;
import java.io.FileReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.regex.Pattern;

@Service
@Slf4j
public class DownloadService {

    private static ExecutorService executorService = Executors.newFixedThreadPool(5);

    //@PostConstruct
    public void downloadFile() {
        String link = null;
        String pattern = null;
        String folderpath = null;
        try(BufferedReader bufferedReader = new BufferedReader(new FileReader("d:\\downloadlink.txt"))) {
            String line = bufferedReader.readLine();
            String[] tokens = line.split(";");
            link = tokens[0];
            pattern = tokens[1];
            folderpath = tokens[2];
            downloadAllLinksFromUrl(link, pattern, folderpath);
        } catch (Exception e) {
            log.error("Error in downloading.", e);
        }
    }

    public void downloadAllLinksFromUrl(String url, String pattern, String saveFolder) {
       try {

           List<String> links = getLinksFromURL(url, pattern, false);
           AtomicInteger parallelDownloads = new AtomicInteger(links.size());
           AtomicInteger parallelDownloads1 = new AtomicInteger(links.size());

           AtomicLong totalSize = new AtomicLong(0);
           CountDownLatch countDownLatch = new CountDownLatch(1);
           for(String link : links) {
               HttpURLConnection urlConnection = (HttpURLConnection) new URL(link).openConnection();
               CompletableFuture<Void> future = CompletableFuture.runAsync(() -> {
                   totalSize.addAndGet(urlConnection.getContentLengthLong());
                   if(parallelDownloads.decrementAndGet() == 0) {
                       log.info("Total length to be downloaded: " + totalSize.get() + " bytes");
                       countDownLatch.countDown();
                   }
               });
           }
           countDownLatch.await();
           for(String link: links) {
               downloadFile(link, saveFolder, parallelDownloads1);
           }

       } catch (Exception e) {
           log.error("Download failed.", e);
        }

    }

    public void downloadFile(String url, String saveFolder, AtomicInteger parallelDownloads) throws Exception {
        DownloadEntity downloadEntity = new DownloadEntity(url, saveFolder);
        executorService.submit(new DownloadEntityTask(downloadEntity, 10, false, parallelDownloads));
    }

    private List<String> getLinksFromURL(String url, String pattern, boolean images) throws Exception {
        Pattern pattern1 = Pattern.compile(pattern);
        List<String> downloadUrls = new ArrayList<>();
        Document document = Jsoup.connect(url).get();
        Elements linksOnPage;
        if(!images) {
            linksOnPage = document.select("a[href]");
        } else {
            linksOnPage = document.select("img[src]");
        }
        for (int i = 0; i < linksOnPage.size(); i++) {
            String attribute = images ? "src" : "href";
            String finalUrl = linksOnPage.get(i).absUrl(attribute);
            if(pattern1.matcher(finalUrl).find()) {
                downloadUrls.add(finalUrl);
            }
        }
        return downloadUrls;
    }

}
