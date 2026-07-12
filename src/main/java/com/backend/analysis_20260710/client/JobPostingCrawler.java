package com.backend.analysis_20260710.client;

import com.backend.global.exception.CustomException;
import com.backend.global.exception.ErrorCode;
import java.io.IOException;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.springframework.stereotype.Component;

@Component
public class JobPostingCrawler {

    public String extractText(String jobUrl) {
        try {
            Document document = Jsoup.connect(jobUrl)
                    .userAgent("Mozilla/5.0")
                    .timeout(10_000)
                    .get();

            return document.text();
        } catch (IOException e) {
            throw new CustomException(ErrorCode.JOB_POSTING_CRAWL_ERROR);
        }
    }

    public String extractPlatform(String jobUrl) {
        if (jobUrl.contains("jobkorea.co.kr")) {
            return "JOBKOREA";
        }

        return "UNKNOWN";
    }
}
