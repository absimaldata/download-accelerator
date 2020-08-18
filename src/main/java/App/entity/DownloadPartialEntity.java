package App.entity;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.net.URLConnection;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class DownloadPartialEntity {
    private String url;
    private String saveFolder;
    private int taskNumber;
    private int splitCount;
    private long contentLength;
}
