package com.backend.analysis.domain;

import com.backend.global.common.entity.BaseCreatedTimeEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Entity
@Table(
        name = "analysis_attempt",
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uk_analysis_attempt_result_no",
                        columnNames = {"analysis_result_id", "attempt_no"}
                )
        }
)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class AnalysisAttempt extends BaseCreatedTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "analysis_result_id", nullable = false)
    private AnalysisResult analysisResult;

    @Column(name = "attempt_no", nullable = false)
    private Integer attemptNo;

    @Enumerated(EnumType.STRING)
    @Column(name = "overall_level", nullable = false, length = 20)
    private OverallLevel overallLevel;

    @Enumerated(EnumType.STRING)
    @Column(name = "previous_overall_level", length = 20)
    private OverallLevel previousOverallLevel;

    @Column(name = "red_count", nullable = false)
    private Integer redCount;

    @Column(name = "yellow_count", nullable = false)
    private Integer yellowCount;

    @Column(name = "green_count", nullable = false)
    private Integer greenCount;

    @Column(name = "previous_red_count")
    private Integer previousRedCount;

    @Column(name = "previous_yellow_count")
    private Integer previousYellowCount;

    @Column(name = "previous_green_count")
    private Integer previousGreenCount;

    @Column(name = "red_diff", nullable = false)
    private Integer redDiff;

    @Column(name = "yellow_diff", nullable = false)
    private Integer yellowDiff;

    @Column(name = "green_diff", nullable = false)
    private Integer greenDiff;

    @Column(name = "summary_message", length = 100)
    private String summaryMessage;

    @Builder
    private AnalysisAttempt(
            AnalysisResult analysisResult,
            Integer attemptNo,
            OverallLevel overallLevel,
            OverallLevel previousOverallLevel,
            Integer redCount,
            Integer yellowCount,
            Integer greenCount,
            Integer previousRedCount,
            Integer previousYellowCount,
            Integer previousGreenCount,
            String summaryMessage
    ) {
        this.analysisResult = analysisResult;
        this.attemptNo = attemptNo;
        this.overallLevel = overallLevel;
        this.previousOverallLevel = previousOverallLevel;
        this.redCount = valueOrZero(redCount);
        this.yellowCount = valueOrZero(yellowCount);
        this.greenCount = valueOrZero(greenCount);
        this.previousRedCount = previousRedCount;
        this.previousYellowCount = previousYellowCount;
        this.previousGreenCount = previousGreenCount;
        this.redDiff = calculateDiff(this.redCount, previousRedCount);
        this.yellowDiff = calculateDiff(this.yellowCount, previousYellowCount);
        this.greenDiff = calculateDiff(this.greenCount, previousGreenCount);
        this.summaryMessage = summaryMessage;
    }

    public static AnalysisAttempt initial(AnalysisResult analysisResult) {
        return AnalysisAttempt.builder()
                .analysisResult(analysisResult)
                .attemptNo(1)
                .overallLevel(analysisResult.getOverallLevel())
                .redCount(analysisResult.getRedCount())
                .yellowCount(analysisResult.getYellowCount())
                .greenCount(analysisResult.getGreenCount())
                .summaryMessage("최초 분석")
                .build();
    }

    public static AnalysisAttempt reanalysis(
            AnalysisResult analysisResult,
            AnalysisAttempt previousAttempt,
            String summaryMessage
    ) {
        return AnalysisAttempt.builder()
                .analysisResult(analysisResult)
                .attemptNo(previousAttempt.getAttemptNo() + 1)
                .overallLevel(analysisResult.getOverallLevel())
                .previousOverallLevel(previousAttempt.getOverallLevel())
                .redCount(analysisResult.getRedCount())
                .yellowCount(analysisResult.getYellowCount())
                .greenCount(analysisResult.getGreenCount())
                .previousRedCount(previousAttempt.getRedCount())
                .previousYellowCount(previousAttempt.getYellowCount())
                .previousGreenCount(previousAttempt.getGreenCount())
                .summaryMessage(summaryMessage)
                .build();
    }

    private Integer calculateDiff(Integer currentCount, Integer previousCount) {
        if (previousCount == null) {
            return 0;
        }

        return currentCount - previousCount;
    }

    private Integer valueOrZero(Integer value) {
        return value != null ? value : 0;
    }
}
