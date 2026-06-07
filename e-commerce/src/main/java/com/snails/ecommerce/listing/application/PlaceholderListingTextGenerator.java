package com.snails.ecommerce.listing.application;

import com.snails.ecommerce.common.error.BusinessException;
import com.snails.ecommerce.common.error.ErrorCode;
import com.snails.ecommerce.listing.domain.ListingBriefVersion;
import com.snails.ecommerce.listing.domain.TextVersion;
import com.snails.ecommerce.template.domain.CategoryTemplate;
import java.util.List;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;
import tools.jackson.core.JacksonException;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

/**
 * 占位 Listing 文案生成器。
 *
 * <p>该实现只用于跑通文案生成最小闭环。它根据已批准 Brief 中的卖点和关键词生成稳定可测试文案，
 * 后续接入真实 Spring AI Alibaba 后应由模型适配器替换。</p>
 */
@Component
public class PlaceholderListingTextGenerator implements ListingTextGenerator {

    private final ObjectMapper objectMapper;

    public PlaceholderListingTextGenerator(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    /**
     * 基于 Brief 的结构化字段生成占位文案。
     *
     * <p>生成结果保持可读和确定性，便于后续服务测试验证版本保存与状态流转。</p>
     */
    @Override
    public TextVersion generateText(
            ListingBriefVersion brief,
            CategoryTemplate template,
            String iterationPrompt) {
        List<String> sellingPoints = readStringList(brief.getCoreSellingPointsJson());
        List<String> targetKeywords = readStringList(brief.getTargetKeywordsJson());
        String categoryName = StringUtils.hasText(template.getCategoryName())
                ? template.getCategoryName()
                : template.getCategoryCode();
        String primaryKeyword = CollectionUtils.isEmpty(targetKeywords)
                ? categoryName
                : targetKeywords.get(0);

        TextVersion version = new TextVersion();
        version.setIterationPrompt(iterationPrompt);
        version.setTitle("Amazon " + template.getMarketplace() + " " + primaryKeyword + " for " + categoryName);
        version.setBulletPointsJson(writeStringList(buildBulletPoints(sellingPoints, categoryName)));
        version.setDescription(buildDescription(brief, categoryName, sellingPoints));
        version.setBackendSearchTerms(String.join(" ", targetKeywords));
        version.setTargetKeywordsJson(writeStringList(targetKeywords));
        version.setComplianceWarningsJson(writeStringList(readStringList(brief.getComplianceNotesJson())));
        version.setQualityScore(80);
        version.setSelected(false);
        return version;
    }

    /**
     * 生成最多 5 条占位 Bullet，Brief 缺少卖点时给出类目级兜底文案。
     */
    private List<String> buildBulletPoints(List<String> sellingPoints, String categoryName) {
        if (CollectionUtils.isEmpty(sellingPoints)) {
            return List.of(
                    "Designed for Amazon shoppers comparing " + categoryName + " options",
                    "Highlights practical benefits without using unsupported claims",
                    "Ready for operator review before final publishing");
        }
        return sellingPoints.stream()
                .filter(StringUtils::hasText)
                .map(point -> "Built around " + point.trim())
                .limit(5)
                .toList();
    }

    /**
     * 生成占位描述，保留目标受众上下文。
     */
    private String buildDescription(
            ListingBriefVersion brief,
            String categoryName,
            List<String> sellingPoints) {
        String audience = StringUtils.hasText(brief.getTargetAudience())
                ? brief.getTargetAudience()
                : "Amazon buyers";
        String pointSummary = CollectionUtils.isEmpty(sellingPoints)
                ? "the approved positioning"
                : sellingPoints.stream()
                        .filter(StringUtils::hasText)
                        .limit(3)
                        .collect(Collectors.joining(", "));
        return "A review-ready " + categoryName + " listing for " + audience
                + ", focused on " + pointSummary + ".";
    }

    /**
     * 读取 JSON 字符串数组，空值按空数组处理。
     */
    private List<String> readStringList(String json) {
        try {
            return objectMapper.readValue(
                    StringUtils.hasText(json) ? json : "[]",
                    new TypeReference<List<String>>() {
                    });
        } catch (JacksonException exception) {
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, "Failed to process Brief JSON fields");
        }
    }

    /**
     * 写入 JSON 字符串数组。
     */
    private String writeStringList(List<String> values) {
        try {
            return objectMapper.writeValueAsString(values == null ? List.of() : values);
        } catch (JacksonException exception) {
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, "Failed to process text version JSON fields");
        }
    }
}
