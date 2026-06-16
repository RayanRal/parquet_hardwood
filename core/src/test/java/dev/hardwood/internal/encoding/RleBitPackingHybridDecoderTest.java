/*
 *  SPDX-License-Identifier: Apache-2.0
 *
 *  Copyright The original authors
 *
 *  Licensed under the Apache Software License version 2.0, available at http://www.apache.org/licenses/LICENSE-2.0
 */
package dev.hardwood.internal.encoding;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/// Characterization tests for [RleBitPackingHybridDecoder], locking in the behaviour the
/// bounded-decode work for hardwood-hq/hardwood#610 depends on:
///
/// - bit width 0 (the `42.parquet` vector) yields no data and leaves the caller's buffer
///   untouched (callers rely on the array being pre-zeroed);
/// - a `readInts` call may request fewer values than a run contains and **resume** from the
///   same decoder on the next call, producing the same result as one combined read — this is
///   the property that lets a page be decoded in bounded slices rather than all at once.
class RleBitPackingHybridDecoderTest {

    @Test
    void bitWidthZeroLeavesBufferUntouched() {
        // bit width 0 carries no body; the decoder must read nothing.
        RleBitPackingHybridDecoder decoder = new RleBitPackingHybridDecoder(new byte[0], 0, 0, 0);
        int[] buffer = {7, 7, 7, 7, 7};

        decoder.readInts(buffer, 0, 5);

        assertThat(buffer).containsExactly(7, 7, 7, 7, 7);
    }

    @Test
    void decodesRleRun() {
        // header = (10 << 1) | 0 = 20 (RLE, repeat 10); value = 1 (one byte, bit width 1)
        RleBitPackingHybridDecoder decoder = new RleBitPackingHybridDecoder(new byte[] {0x14, 0x01}, 1);
        int[] buffer = new int[10];

        decoder.readInts(buffer, 0, 10);

        assertThat(buffer).containsExactly(1, 1, 1, 1, 1, 1, 1, 1, 1, 1);
    }

    @Test
    void decodesBitPackedRun() {
        // header = (1 << 1) | 1 = 3 (bit-packed, 1 group of 8); 0x55 = 0b01010101 (LSB first)
        RleBitPackingHybridDecoder decoder = new RleBitPackingHybridDecoder(new byte[] {0x03, 0x55}, 1);
        int[] buffer = new int[8];

        decoder.readInts(buffer, 0, 8);

        assertThat(buffer).containsExactly(1, 0, 1, 0, 1, 0, 1, 0);
    }

    @Test
    void rleRunResumesAcrossPartialReads() {
        byte[] data = {0x14, 0x01}; // RLE, repeat 10, value 1
        int[] combined = new int[10];
        new RleBitPackingHybridDecoder(data, 1).readInts(combined, 0, 10);

        RleBitPackingHybridDecoder decoder = new RleBitPackingHybridDecoder(data, 1);
        int[] sliced = new int[10];
        decoder.readInts(sliced, 0, 4);
        decoder.readInts(sliced, 4, 6);

        assertThat(sliced).containsExactly(combined);
    }

    @Test
    void bitPackedRunResumesAcrossPartialReads() {
        byte[] data = {0x03, 0x55}; // bit-packed, 1 group of 8, 0b01010101
        int[] combined = new int[8];
        new RleBitPackingHybridDecoder(data, 1).readInts(combined, 0, 8);

        RleBitPackingHybridDecoder decoder = new RleBitPackingHybridDecoder(data, 1);
        int[] sliced = new int[8];
        decoder.readInts(sliced, 0, 3);
        decoder.readInts(sliced, 3, 5);

        assertThat(sliced).containsExactly(combined);
    }
}
