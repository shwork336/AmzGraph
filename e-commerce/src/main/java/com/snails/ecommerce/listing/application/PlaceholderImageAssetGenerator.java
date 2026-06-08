package com.snails.ecommerce.listing.application;

import com.snails.ecommerce.common.error.BusinessException;
import com.snails.ecommerce.common.error.ErrorCode;
import com.snails.ecommerce.listing.domain.ImageAsset;
import com.snails.ecommerce.listing.domain.ImageVersion;
import com.snails.ecommerce.listing.domain.ListingBriefVersion;
import com.snails.ecommerce.template.domain.CategoryTemplate;
import com.snails.ecommerce.template.domain.ImageAssetType;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;
import tools.jackson.core.JacksonException;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

/**
 * 占位图片资产生成器。
 *
 * <p>该实现只用于跑通图片生成最小闭环。它根据类目模板中的默认图片类型和尺寸配置生成稳定可测试资产，
 * 后续接入真实图片模型后应由模型适配器替换。</p>
 */
@Component
public class PlaceholderImageAssetGenerator implements ImageAssetGenerator {

    private static final String PLACEHOLDER_PROVIDER = "PLACEHOLDER";
    private static final String DEFAULT_SIZE_PROFILE = "STANDARD_LISTING";

    private final ObjectMapper objectMapper;

    public PlaceholderImageAssetGenerator(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    /**
     * 基于模板默认图片类型生成占位图片资产。
     *
     * <p>生成结果不创建真实图片文件，只返回稳定占位 URL、Prompt、尺寸和合规字段。</p>
     */
    @Override
    public List<ImageAsset> generateImageAssets(
            ImageVersion imageVersion,
            ListingBriefVersion brief,
            CategoryTemplate template) {
        List<ImageAssetType> assetTypes = readAssetTypes(template.getDefaultImageAssetTypesJson());
        Map<String, SizeProfile> sizeProfiles = readSizeProfiles(template.getSizeProfilesJson());
        if (CollectionUtils.isEmpty(assetTypes)) {
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, "Category template has no image asset types");
        }

        return assetTypes.stream()
                .map(type -> buildAsset(imageVersion, brief, template, type, sizeProfiles))
                .toList();
    }

    /**
     * 创建单张占位资产。
     */
    private ImageAsset buildAsset(
            ImageVersion imageVersion,
            ListingBriefVersion brief,
            CategoryTemplate template,
            ImageAssetType type,
            Map<String, SizeProfile> sizeProfiles) {
        String sizeProfileName = resolveSizeProfileName(type);
        SizeProfile sizeProfile = sizeProfiles.getOrDefault(
                sizeProfileName,
                sizeProfiles.getOrDefault(DEFAULT_SIZE_PROFILE, new SizeProfile(2000, 2000)));
        String imageVersionId = StringUtils.hasText(imageVersion.getVersionId())
                ? imageVersion.getVersionId()
                : "draft";
        String prompt = buildPrompt(brief, template, type);

        ImageAsset asset = new ImageAsset();
        asset.setType(type);
        asset.setPrompt(prompt);
        asset.setRewrittenPrompt("Placeholder " + type.name() + " image for " + template.getCategoryName());
        asset.setGeneratedImageUrl("generated-images/" + imageVersionId + "/" + type.name() + ".png");
        asset.setSourceEditableFileUrl(null);
        asset.setSizeProfile(sizeProfileName);
        asset.setTargetWidth(sizeProfile.width());
        asset.setTargetHeight(sizeProfile.height());
        asset.setComplianceStatus("PASS");
        asset.setComplianceMethodsJson(writeStringList(List.of("PLACEHOLDER_RULE_CHECK")));
        asset.setComplianceIssuesJson(writeStringList(List.of()));
        return asset;
    }

    /**
     * 根据图片类型选择模板尺寸档位。
     */
    private String resolveSizeProfileName(ImageAssetType type) {
        if (type == ImageAssetType.MAIN_IMAGE) {
            return "MAIN_IMAGE";
        }
        if (type == ImageAssetType.A_PLUS_MODULE) {
            return "A_PLUS_STANDARD";
        }
        return DEFAULT_SIZE_PROFILE;
    }

    /**
     * 构造占位 Prompt，保留 Brief 目标受众和图片类型上下文。
     */
    private String buildPrompt(
            ListingBriefVersion brief,
            CategoryTemplate template,
            ImageAssetType type) {
        String audience = StringUtils.hasText(brief.getTargetAudience())
                ? brief.getTargetAudience()
                : "Amazon buyers";
        return "Generate " + type.name() + " image for "
                + template.getMarketplace() + " " + template.getCategoryName()
                + " listing, targeting " + audience + ".";
    }

    /**
     * 从模板 JSON 读取图片类型。
     */
    private List<ImageAssetType> readAssetTypes(String json) {
        try {
            List<String> names = objectMapper.readValue(
                    StringUtils.hasText(json) ? json : "[]",
                    new TypeReference<List<String>>() {
                    });
            return names.stream()
                    .filter(StringUtils::hasText)
                    .map(String::trim)
                    .map(ImageAssetType::valueOf)
                    .toList();
        } catch (IllegalArgumentException | JacksonException exception) {
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, "Failed to process image asset type JSON fields");
        }
    }

    /**
     * 从模板 JSON 读取尺寸配置。
     */
    private Map<String, SizeProfile> readSizeProfiles(String json) {
        try {
            return objectMapper.readValue(
                    StringUtils.hasText(json) ? json : "{}",
                    new TypeReference<Map<String, SizeProfile>>() {
                    });
        } catch (JacksonException exception) {
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, "Failed to process image size profile JSON fields");
        }
    }

    /**
     * 写入 JSON 字符串数组。
     */
    private String writeStringList(List<String> values) {
        try {
            return objectMapper.writeValueAsString(values == null ? List.of() : values);
        } catch (JacksonException exception) {
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, "Failed to process image asset JSON fields");
        }
    }

    /**
     * 模板尺寸配置。
     */
    private record SizeProfile(Integer width, Integer height) {
    }
}
