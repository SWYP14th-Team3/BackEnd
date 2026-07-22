package com.backend.analysis.client;

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
            // URL 채용공고 페이지에서 HTML 텍스트 추출
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
        // URL 도메인으로 채용 플랫폼 이름 구분
        if (jobUrl == null || jobUrl.isBlank()) {
            return "UNKNOWN";
        }

        String normalizedUrl = jobUrl.toLowerCase();

        if (normalizedUrl.contains("jobkorea.co.kr")) {
            return "JOBKOREA";
        }
        if (normalizedUrl.contains("saramin.co.kr")) {
            return "SARAMIN";
        }
        if (normalizedUrl.contains("wanted.co.kr")) {
            return "WANTED";
        }

        return "UNKNOWN";
    }
}
