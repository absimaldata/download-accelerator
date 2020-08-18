package App.entity;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class DownloadResult {
    private String[] fileNameList;
    private double[] lastKnownAverageSpeeds;
    private boolean[] isError;
    private String folderName;
}
