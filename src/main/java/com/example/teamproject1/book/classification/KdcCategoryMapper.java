package com.example.teamproject1.book.classification;

import org.springframework.util.StringUtils;

import java.util.Map;

public final class KdcCategoryMapper {

    private static final Map<Character, String>
            CATEGORY_BY_FIRST_DIGIT =
            Map.of(
                    '0', "총류",
                    '1', "철학",
                    '2', "종교",
                    '3', "사회과학",
                    '4', "자연과학",
                    '5', "기술과학",
                    '6', "예술",
                    '7', "언어",
                    '8', "문학",
                    '9', "역사"
            );

    private KdcCategoryMapper() {
    }

    public static String resolve(
            String classNo,
            String className
    ) {
        String topClassName =
                extractTopClassName(className);

        if (StringUtils.hasText(topClassName)) {
            return topClassName;
        }

        String normalizedClassNo =
                normalizeClassNo(classNo);

        if (normalizedClassNo.isEmpty()) {
            return null;
        }

        return CATEGORY_BY_FIRST_DIGIT.get(
                normalizedClassNo.charAt(0)
        );
    }

    private static String extractTopClassName(
            String className
    ) {
        if (!StringUtils.hasText(className)) {
            return null;
        }

        String firstSegment =
                className
                        .split(">")[0]
                        .trim();

        if (
                firstSegment.isEmpty()
                        || "미분류".equals(firstSegment)
                        || "분류 정보 없음".equals(firstSegment)
        ) {
            return null;
        }

        return firstSegment;
    }

    private static String normalizeClassNo(
            String classNo
    ) {
        if (classNo == null) {
            return "";
        }

        return classNo
                .replaceAll("[^0-9.]", "")
                .trim();
    }
}
