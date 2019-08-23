/*
 * Copyright 2008 CoreMedia AG, Hamburg
 *
 * Licensed under the Apache License, Version 2.0 (the License);
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an AS IS BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.coremedia.iso.boxes;

import com.coremedia.iso.IsoTypeReader;
import com.coremedia.iso.IsoTypeWriter;
import com.googlecode.mp4parser.AbstractFullBox;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static com.googlecode.mp4parser.util.CastUtils.l2i;

/**
 * Samples within the media data are grouped into chunks. Chunks can be of different sizes, and the
 * samples within a chunk can have different sizes. This table can be used to find the chunk that
 * contains a sample, its position, and the associated sample description. Defined in ISO/IEC 14496-12.
 */
public class SampleToChunkBox extends AbstractFullBox {
    private List<Entry> entries = Collections.emptyList();

    public static final String TYPE = "stsc";

    public SampleToChunkBox() {
        super(TYPE);
    }

    public List<Entry> getEntries() {
        return entries;
    }

    public void setEntries(List<Entry> entries) {
        this.entries = entries;
    }

    protected long getContentSize() {
        return entries.size() * 12 + 8;
    }

    @Override
    public void _parseDetails(ByteBuffer content) {
        parseVersionAndFlags(content);

        int entryCount = l2i(IsoTypeReader.readUInt32(content));
        entries = new ArrayList<>(entryCount);
        for (int i = 0; i < entryCount; i++) {
            entries.add(new Entry(
                    IsoTypeReader.readUInt32(content),
                    IsoTypeReader.readUInt32(content),
                    IsoTypeReader.readUInt32(content)));
        }
    }

    @Override
    protected void getContent(ByteBuffer byteBuffer) {
        writeVersionAndFlags(byteBuffer);
        IsoTypeWriter.writeUInt32(byteBuffer, entries.size());
        for (Entry entry : entries) {
            IsoTypeWriter.writeUInt32(byteBuffer, entry.getFirstChunk());
            IsoTypeWriter.writeUInt32(byteBuffer, entry.getSamplesPerChunk());
            IsoTypeWriter.writeUInt32(byteBuffer, entry.getSampleDescriptionIndex());
        }
    }

    public String toString() {
        return "SampleToChunkBox[entryCount=" + entries.size() + "]";
    }

    public static class Entry {
        long firstChunk;
        long samplesPerChunk;
        long sampleDescriptionIndex;

        public Entry(long firstChunk, long samplesPerChunk, long sampleDescriptionIndex) {
            this.firstChunk = firstChunk;
            this.samplesPerChunk = samplesPerChunk;
            this.sampleDescriptionIndex = sampleDescriptionIndex;
        }

        public long getFirstChunk() {
            return firstChunk;
        }

        public long getSamplesPerChunk() {
            return samplesPerChunk;
        }

        public long getSampleDescriptionIndex() {
            return sampleDescriptionIndex;
        }

        @Override
        public String toString() {
            return "Entry{" +
                    "firstChunk=" + firstChunk +
                    ", samplesPerChunk=" + samplesPerChunk +
                    ", sampleDescriptionIndex=" + sampleDescriptionIndex +
                    '}';
        }
    }
}
