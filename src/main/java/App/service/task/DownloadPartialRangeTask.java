package App.service.task;

import App.entity.DownloadPartialEntity;
import App.entity.DownloadResult;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.StringJoiner;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

@Slf4j
@AllArgsConstructor
public class DownloadPartialRangeTask implements Runnable {

    private DownloadPartialEntity downloadPartialEntity;
    private DownloadResult downloadResult;
    private AtomicInteger tasksLeft;
    private AtomicInteger parallelDownloads;

    @Override
    public void run() {
        download();
        if(this.tasksLeft.get() == 0) {
            int downloadsLeft = parallelDownloads.decrementAndGet();
            if (downloadsLeft == 0) {
                log.info("All tasks are finished!");
            }
        }
    }

    private int download() {
        int retryCount = 0;
        HttpURLConnection urlConnection = null;
        BufferedInputStream br = null;
        BufferedOutputStream bw = null;

        while(retryCount < 3) {
            try {
                urlConnection = (HttpURLConnection) new URL(downloadPartialEntity.getUrl()).openConnection();
                if (downloadPartialEntity.getSplitCount() != 1) {
                    urlConnection.setRequestProperty("Range", "bytes=" + getStartByte() + "-" + getEndByte());
                }

                long contentLength = urlConnection.getContentLengthLong();
                String fileName = getFileName(downloadPartialEntity.getUrl(), urlConnection.getContentType());

                br = new BufferedInputStream(urlConnection.getInputStream());
                bw = new BufferedOutputStream(new FileOutputStream(downloadPartialEntity.getSaveFolder() + fileName));

                // set result filename
                downloadResult.getFileNameList()[downloadPartialEntity.getTaskNumber() - 1] = fileName;
                log.info("Content length: " + contentLength);
                long startTime = System.nanoTime();

                // Download the partial file
                long bytesDownloaded = downloadBytesFromStream(br, bw, startTime);

                log.info(bytesDownloaded + " bytes downloaded by task " + downloadPartialEntity.getTaskNumber());
                log.info("Total time taken for task " + downloadPartialEntity.getTaskNumber() + ": " + ((double) (System.nanoTime() - startTime)) / 1000000000d + " seconds.");

                // Close the streams and connections
                closeStreamsAndConnections(urlConnection, br, bw);

                // If this is the last task out of n tasks, n > 1 do the job of post processing.
                if (tasksLeft.decrementAndGet() == 0 && downloadPartialEntity.getSplitCount() > 1) {
                    postProcess();
                }

                return 1;
            } catch (Exception e) {
                log.error("Error downloading partial file. Retrying...", e);
                deleteFile(downloadResult.getFileNameList()[downloadPartialEntity.getTaskNumber() - 1]);
                closeStreamsAndConnections(urlConnection, br, bw);
                retryCount += 1;
            }
        }
        downloadResult.getIsError()[downloadPartialEntity.getTaskNumber() - 1] = true;
        return -1;
    }

    private long downloadBytesFromStream(BufferedInputStream br, BufferedOutputStream bw, long startTime) throws Exception {
        int retryCount = 0;
        long bytesDownloaded = 0;
        long readCount = 0;
        byte[] readBuffer = new byte[10000];
        int bytesRead = 0;
        while (bytesRead != -1 && retryCount < 3) {
            bytesRead = br.read(readBuffer, 0, 10000);
            if (bytesRead != -1) {
                readCount += 1;
                bw.write(readBuffer, 0, bytesRead);
                bytesDownloaded += bytesRead;
                if (readCount % 10 == 0) {
                    updateSpeed(bytesDownloaded, startTime);
                }
            } else {
                retryCount += 1;
            }
        }
        return bytesDownloaded;
    }

    private String getStartByte() {
        long splitSize = downloadPartialEntity.getContentLength() / downloadPartialEntity.getSplitCount();
        return String.valueOf((downloadPartialEntity.getTaskNumber() - 1) * splitSize);
    }

    private String getEndByte() {
        long splitSize = downloadPartialEntity.getContentLength() / downloadPartialEntity.getSplitCount();
        long endByte;
        if(downloadPartialEntity.getTaskNumber() == downloadPartialEntity.getSplitCount()) {
            return "";
        } else {
            return String.valueOf((downloadPartialEntity.getTaskNumber()) * splitSize - 1);
        }
    }

    private void updateSpeed(long bytesDownloaded, long startTime) {
        double totalTime = System.nanoTime() - startTime;
        double speed = bytesDownloaded / (totalTime / 1000000000L);
        speed = speed / (1024 * 1024);
        downloadResult.getLastKnownAverageSpeeds()[downloadPartialEntity.getTaskNumber() - 1] = speed;
    }

    private String getFileName(String url, String contentType) {
        String [] fileNameTokens = url.split("/");
        String fileName = fileNameTokens[fileNameTokens.length - 1].split("\\?")[0];
        if(fileName.lastIndexOf(".") == -1) {
            fileName = fileName.concat(".").concat(contentType.split("/")[1]);
        }
        if(downloadPartialEntity.getSplitCount() != 1) {
            return fileName + "." + UUID.randomUUID().toString().replaceAll("-", "") + "." + downloadPartialEntity.getTaskNumber();
        } else {
            return fileName;
        }
    }

    private void postProcess() {
        try {
            log.info("Doing post processing");
            String[] fileNames = downloadResult.getFileNameList();
            String finalFileName = downloadResult.getFolderName() + getFinalFileName(fileNames[0]);
            BufferedOutputStream bufferedOutputStream = new BufferedOutputStream(new FileOutputStream(finalFileName));
            byte[] buffer = new byte[100000];
            for (String fileName : fileNames) {
                BufferedInputStream bufferedInputStream = new BufferedInputStream(new FileInputStream(downloadResult.getFolderName() + fileName));
                int bytesRead = 0;
                while(true) {
                    bytesRead = bufferedInputStream.read(buffer, 0, 100000);
                    if(bytesRead != -1) {
                        bufferedOutputStream.write(buffer, 0, bytesRead);
                    } else {
                        break;
                    }
                }
                bufferedInputStream.close();
                deleteFile(downloadResult.getFolderName() + fileName);
            }
            bufferedOutputStream.close();
        } catch(Exception e) {
            log.error("Error writing the file to disk.", e);
        }
    }

    private String getFinalFileName(String fileName) {
        String[] tokens = fileName.split("\\.");
        StringJoiner stringJoiner = new StringJoiner(".");
        for(int i = 0; i <= tokens.length - 3; i++) {
            stringJoiner.add(tokens[i]);
        }
        return stringJoiner.toString();
    }

    private void deleteFile(String filePath) {
        File file = new File(filePath);
        if(file.delete()) {
            log.info("File: " + filePath + " deleted succcesfully");
        } else {
            log.error("File: " + filePath + " was not deleted.");
        }
    }

    private void closeStreamsAndConnections(HttpURLConnection connection, BufferedInputStream bi, BufferedOutputStream bo) {
        int retryCount = 0;
        while(retryCount < 3) {
            try {
                connection.disconnect();
                bi.close();
                bo.close();
                return;
            } catch (Exception e) {
                log.error("Error closing the streams and connections, retrying...", e);
                retryCount += 1;
            }
        }
    }
}
