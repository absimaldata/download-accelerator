package App.service.task;

import App.entity.DownloadEntity;
import App.entity.DownloadPartialEntity;
import App.entity.DownloadResult;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.net.URL;
import java.net.URLConnection;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

@Slf4j
@AllArgsConstructor
public class DownloadEntityTask implements Runnable {

    private DownloadEntity downloadEntity;
    private int SPLIT_DOWNLOADS_COUNT;
    private boolean splitDownloads;
    private AtomicInteger parallelDownloadsLeft;

    @Override
    public void run() {
        download();
    }

    private void download() {
        if(!splitDownloads) {
            SPLIT_DOWNLOADS_COUNT = 1;
        }

        ExecutorService executorService = Executors.newFixedThreadPool(SPLIT_DOWNLOADS_COUNT);

        try {
            URLConnection urlConnection = new URL(downloadEntity.getUrl()).openConnection();
            if(SPLIT_DOWNLOADS_COUNT > 1) {
                if (urlConnection.getHeaderField("Accept-Ranges").equals("bytes")) {
                    AtomicInteger tasksLeft = new AtomicInteger(SPLIT_DOWNLOADS_COUNT);
                    DownloadResult downloadResult = new DownloadResult(new String[SPLIT_DOWNLOADS_COUNT], new double[SPLIT_DOWNLOADS_COUNT], new boolean[SPLIT_DOWNLOADS_COUNT], downloadEntity.getSaveFolder());
                    for (int i = 1; i <= SPLIT_DOWNLOADS_COUNT; i++) {
                        DownloadPartialEntity downloadPartialEntity = new DownloadPartialEntity();
                        downloadPartialEntity.setSaveFolder(downloadEntity.getSaveFolder());
                        downloadPartialEntity.setTaskNumber(i);
                        downloadPartialEntity.setUrl(downloadEntity.getUrl());
                        downloadPartialEntity.setSplitCount(SPLIT_DOWNLOADS_COUNT);
                        downloadPartialEntity.setContentLength(urlConnection.getContentLengthLong());
                        executorService.submit(new DownloadPartialRangeTask(downloadPartialEntity, downloadResult, tasksLeft, parallelDownloadsLeft));
                    }
                } else {
                    submitSingleTask(urlConnection, executorService);
                }
            } else {
                submitSingleTask(urlConnection, executorService);
            }
        } catch(Exception e) {
            log.error("Exception occurred: ", e);
        }
    }

    private void submitSingleTask(URLConnection urlConnection, ExecutorService executorService) {
        AtomicInteger tasksLeft = new AtomicInteger(1);
        DownloadResult downloadResult = new DownloadResult(new String[1], new double[1], new boolean[1], downloadEntity.getSaveFolder());
        DownloadPartialEntity downloadPartialEntity = new DownloadPartialEntity();
        downloadPartialEntity.setSaveFolder(downloadEntity.getSaveFolder());
        downloadPartialEntity.setTaskNumber(1);
        downloadPartialEntity.setSplitCount(1);
        downloadPartialEntity.setUrl(downloadEntity.getUrl());
        downloadPartialEntity.setContentLength(urlConnection.getContentLengthLong());
        executorService.submit(new DownloadPartialRangeTask(downloadPartialEntity, downloadResult, tasksLeft, parallelDownloadsLeft));
    }
}
