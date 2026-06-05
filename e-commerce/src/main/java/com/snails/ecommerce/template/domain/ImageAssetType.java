package com.snails.ecommerce.template.domain;

/**
 * 图片资产类型。
 *
 * <p>模板使用该枚举定义默认图片组，图片版本下的 ImageAsset 也使用它表达资产用途。</p>
 */
public enum ImageAssetType {
    /** Amazon 白底主图。 */
    MAIN_IMAGE,
    /** 卖点信息图。 */
    INFOGRAPHIC,
    /** 使用场景图。 */
    LIFESTYLE,
    /** 尺寸、接口或规格图。 */
    DIMENSION,
    /** 车型、年份、系统或配件兼容图。 */
    COMPATIBILITY,
    /** 安装步骤或安装前后说明图。 */
    INSTALLATION,
    /** 包装清单图。 */
    PACKAGE_CONTENTS,
    /** A+ 模块图。 */
    A_PLUS_MODULE
}
