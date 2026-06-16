/*
 *  SPDX-License-Identifier: Apache-2.0
 *
 *  Copyright The original authors
 *
 *  Licensed under the Apache Software License version 2.0, available at http://www.apache.org/licenses/LICENSE-2.0
 */
package dev.hardwood.internal.reader;

import org.junit.jupiter.api.Test;

import dev.hardwood.internal.encoding.RleBitPackingHybridDecoder;

import static org.assertj.core.api.Assertions.assertThat;

/// Characterization tests for [Dictionary]'s `decodePage` index-decode path — the exact site
/// that allocates `new long[numValues]` / `new int[numValues]` straight from the page header
/// count (hardwood-hq/hardwood#610). `DictionaryTest` only covers `Dictionary.parse`; these
/// lock in the value-mapping behaviour so the bounded-decode refactor preserves it.
class DictionaryDecodePageTest {

    @Test
    void longDictionaryMapsIndicesToValues() {
        Dictionary dict = new Dictionary.LongDictionary(new long[] {100L, 200L, 300L});
        // bit-packed group of 8, bit width 2: indices 0,1,2,1,0,0,0,0
        // byte0 = 0 | (1<<2) | (2<<4) | (1<<6) = 0x64
        RleBitPackingHybridDecoder indices =
                new RleBitPackingHybridDecoder(new byte[] {0x03, 0x64, 0x00}, 2);

        Page page = dict.decodePage(indices, 4, null, null, 0);

        assertThat(((Page.LongPage) page).values()).containsExactly(100L, 200L, 300L, 200L);
    }

    @Test
    void intDictionaryMapsIndicesToValues() {
        Dictionary dict = new Dictionary.IntDictionary(new int[] {10, 20, 30});
        RleBitPackingHybridDecoder indices =
                new RleBitPackingHybridDecoder(new byte[] {0x03, 0x64, 0x00}, 2);

        Page page = dict.decodePage(indices, 4, null, null, 0);

        assertThat(((Page.IntPage) page).values()).containsExactly(10, 20, 30, 20);
    }

    @Test
    void byteArrayDictionaryMapsIndicesToValues() {
        byte[] a = {'a'};
        byte[] bb = {'b', 'b'};
        Dictionary dict = new Dictionary.ByteArrayDictionary(new byte[][] {a, bb});
        // bit-packed group of 8, bit width 1: indices 0,1,0,0,... -> 0b00000010 = 0x02
        RleBitPackingHybridDecoder indices =
                new RleBitPackingHybridDecoder(new byte[] {0x03, 0x02}, 1);

        Page page = dict.decodePage(indices, 3, null, null, 0);

        assertThat((Object[]) ((Page.ByteArrayPage) page).values()).containsExactly(a, bb, a);
    }

    @Test
    void bitWidthZeroMapsEveryRowToFirstEntry() {
        // The 42.parquet vector: one dictionary entry, index bit width 0, empty body.
        Dictionary dict = new Dictionary.LongDictionary(new long[] {42L});
        RleBitPackingHybridDecoder indices =
                new RleBitPackingHybridDecoder(new byte[0], 0, 0, 0);

        Page page = dict.decodePage(indices, 5, null, null, 0);

        assertThat(((Page.LongPage) page).values()).containsExactly(42L, 42L, 42L, 42L, 42L);
    }
}
