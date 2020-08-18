package App.service;

import App.service.task.SiteScrapperTask;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import javax.annotation.PostConstruct;
import java.io.BufferedWriter;
import java.io.FileWriter;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

@Service
@Slf4j
public class SiteScrapper {

    private static ExecutorService scrappingExecutor = Executors.newFixedThreadPool(100);

    @PostConstruct
    public void init() {
        try {
            String url = "https://tests.stockfishchess.org/tests?page=";
            CountDownLatch taskCount = new CountDownLatch(3478);
            for (int i = 1; i <= 3478; i++) {
                String finalUrl = url + i;
                SiteScrapperTask siteScrapperTask = new SiteScrapperTask(finalUrl, i, taskCount, 0);
                scrappingExecutor.submit(siteScrapperTask);
            }
            taskCount.await();
        } catch (Exception e) {
            log.error("Error in scrapping");
        }
    }

}
