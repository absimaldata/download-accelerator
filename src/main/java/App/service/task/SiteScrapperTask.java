package App.service.task;


import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;

import java.io.BufferedWriter;
import java.io.FileWriter;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicInteger;

@AllArgsConstructor
@Slf4j
public class SiteScrapperTask implements Runnable {

    private String url;
    private int pageNo;
    private CountDownLatch taskCount;
    private int retryCount;

    @Override
    public void run() {
        String html = null;
        try {
            log.info("Scrapping site for pageNo: " + pageNo);
            Document document = Jsoup.connect(url).timeout(120000).get();
            html = document.text();
        } catch (Exception e){
            log.error("Exception in scrapping", e);
            if(retryCount <= 3) {
                log.info("Retrying...");
                retryCount++;
                run();
            }
        }
        saveWork(html);
        taskCount.countDown();
        if(taskCount.getCount() == 0) {
            log.info("All tasks finished");
        }
    }

    private void saveWork(String html) {
        if(html != null) {
            String fileName = pageNo + ".txt";
            log.info("Saving work for page no: " + pageNo);
            try(BufferedWriter bufferedWriter = new BufferedWriter(new FileWriter("D:\\TestScrap\\" + fileName))) {
                bufferedWriter.write(html);
            } catch (Exception e) {
                log.error("Exception in writing a file: " + pageNo);
            }
        } else {
            log.error("Cannot save for page no: " + pageNo);
        }
    }
}
