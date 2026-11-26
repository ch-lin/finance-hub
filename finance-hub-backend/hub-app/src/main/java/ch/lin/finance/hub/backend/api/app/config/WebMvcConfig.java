/*
 * Copyright (c) 2026 Che-Hung Lin. All rights reserved.
 *
 * This software and associated documentation files (the "Software") are the
 * proprietary and confidential property of Che-Hung Lin. Unauthorized copying,
 * modification, distribution, reverse engineering, or use of the Software,
 * via any medium, is strictly prohibited without prior written permission.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT.
 */
package ch.lin.finance.hub.backend.api.app.config;

import java.util.Objects;

import org.springframework.context.annotation.Configuration;
import org.springframework.core.convert.converter.Converter;
import org.springframework.core.convert.converter.ConverterFactory;
import org.springframework.format.FormatterRegistry;
import org.springframework.lang.NonNull;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Global Web MVC configuration for the application.
 */
@Configuration
public class WebMvcConfig implements WebMvcConfigurer {

    private final ExtensionSecurityInterceptor extensionSecurityInterceptor;
    private final OwnerFilterInterceptor ownerFilterInterceptor;

    public WebMvcConfig(ExtensionSecurityInterceptor extensionSecurityInterceptor,
            OwnerFilterInterceptor ownerFilterInterceptor) {
        this.extensionSecurityInterceptor = extensionSecurityInterceptor;
        this.ownerFilterInterceptor = ownerFilterInterceptor;
    }

    @Override
    public void addInterceptors(
            @NonNull org.springframework.web.servlet.config.annotation.InterceptorRegistry registry) {
        // Register ExtensionSecurityInterceptor first (Fail-fast security)
        registry.addInterceptor(Objects.requireNonNull(extensionSecurityInterceptor))
                .addPathPatterns("/*/extension/**", "/*/ai/**")
                .order(org.springframework.core.Ordered.HIGHEST_PRECEDENCE);

        // Register OwnerFilterInterceptor after security (DB filter switching)
        registry.addInterceptor(Objects.requireNonNull(ownerFilterInterceptor));
    }

    /**
     * Registers custom formatters and converters for the application.
     * <p>
     * This method adds a {@link KebabCaseEnumConverterFactory} which globally
     * transforms
     * incoming kebab-case string values (from @PathVariable or @RequestParam) into
     * the corresponding Java Enum values (which are typically
     * SCREAMING_SNAKE_CASE).
     *
     * @param registry The {@link FormatterRegistry} to which the converters are
     *                 added.
     */
    @Override
    public void addFormatters(@NonNull FormatterRegistry registry) {
        // Applies globally to all @PathVariable and @RequestParam mappings for ANY Enum
        registry.addConverterFactory(new KebabCaseEnumConverterFactory());
    }

    /**
     * Factory that generates a converter for converting kebab-case strings to Enum
     * constants.
     * <p>
     * Useful for building RESTful APIs where URL parameters are naturally
     * kebab-case,
     * but internal representation is a standard Java Enum.
     */
    private static class KebabCaseEnumConverterFactory implements ConverterFactory<String, Enum<?>> {

        /**
         * Retrieves the specific converter for the target Enum type.
         *
         * @param targetType The class of the Enum being converted into.
         * @param <T>        The type of the Enum.
         * @return A {@link Converter} capable of mapping a String to the specified
         *         Enum.
         */
        @Override
        @NonNull
        public <T extends Enum<?>> Converter<String, T> getConverter(@NonNull Class<T> targetType) {
            return source -> {
                if (source == null || source.isBlank()) {
                    return null;
                }
                String formatted = source.toUpperCase().replace("-", "_");
                for (T enumConstant : targetType.getEnumConstants()) {
                    if (enumConstant.name().equals(formatted)) {
                        return enumConstant;
                    }
                }
                throw new IllegalArgumentException(
                        "No enum constant " + targetType.getCanonicalName() + "." + formatted);
            };
        }
    }
}
