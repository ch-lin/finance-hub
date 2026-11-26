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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import org.springframework.core.convert.converter.Converter;
import org.springframework.core.convert.converter.ConverterFactory;
import org.springframework.format.FormatterRegistry;

import ch.lin.finance.hub.backend.api.domain.model.config.scheduler.TransactionSource;

class WebMvcConfigTest {

    @Test
    @SuppressWarnings({ "unchecked", "rawtypes", "null" })
    void testAddFormatters() {
        WebMvcConfig config = new WebMvcConfig(
                mock(ExtensionSecurityInterceptor.class),
                mock(OwnerFilterInterceptor.class));
        FormatterRegistry registry = mock(FormatterRegistry.class);

        config.addFormatters(registry);

        ArgumentCaptor<ConverterFactory> captor = ArgumentCaptor.forClass(ConverterFactory.class);
        verify(registry).addConverterFactory(captor.capture());

        ConverterFactory factory = captor.getValue();
        Converter converter = factory.getConverter(TransactionSource.class);

        assertEquals(TransactionSource.AMAZON, converter.convert("amazon"));
        assertEquals(TransactionSource.CREDIT_CARD, converter.convert("credit-card"));
        assertNull(converter.convert(""));
        assertNull(converter.convert(null));

        assertNotNull(assertThrows(IllegalArgumentException.class, () -> converter.convert("non-existent")));
    }
}
