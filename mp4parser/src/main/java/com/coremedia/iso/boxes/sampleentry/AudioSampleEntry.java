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

package com.coremedia.iso.boxes.sampleentry;

import com.coremedia.iso.IsoTypeReader;
import com.coremedia.iso.IsoTypeWriter;
import com.coremedia.iso.boxes.Box;
import com.coremedia.iso.boxes.ContainerBox;

import java.nio.ByteBuffer;

/**
 * Contains basic information about the audio samples in this track. Format-specific information
 * is appened as boxes after the data described in ISO/IEC 14496-12 chapter 8.16.2.
 */
public class AudioSampleEntry extends SampleEntry implements ContainerBox {
    private int channelCount;
    private int sampleSize;
    private long sampleRate;
    private int soundVersion;
    private int compressionId;
    private int packetSize;
    private long samplesPerPacket;
    private long bytesPerPacket;
    private long bytesPerFrame;
    private long bytesPerSample;

    private int reserved1;
    private long reserved2;
    private byte[] soundVersion2Data;

    public AudioSampleEntry(String type) {
        super(type);
    }

    public int getChannelCount() {
        return channelCount;
    }

    public long getSampleRate() {
        return sampleRate;
    }

    public void setChannelCount(int channelCount) {
        this.channelCount = channelCount;
    }

    public void setSampleSize(int sampleSize) {
        this.sampleSize = sampleSize;
    }

    public void setSampleRate(long sampleRate) {
        this.sampleRate = sampleRate;
    }

    @Override
    public void _parseDetails(ByteBuffer content) {
        _parseReservedAndDataReferenceIndex(content);    //parses the six reserved bytes and dataReferenceIndex
        // 8 bytes already parsed
        //reserved bits - used by qt
        soundVersion = IsoTypeReader.readUInt16(content);

        //reserved
        reserved1 = IsoTypeReader.readUInt16(content);
        reserved2 = IsoTypeReader.readUInt32(content);

        channelCount = IsoTypeReader.readUInt16(content);
        sampleSize = IsoTypeReader.readUInt16(content);
        //reserved bits - used by qt
        compressionId = IsoTypeReader.readUInt16(content);
        //reserved bits - used by qt
        packetSize = IsoTypeReader.readUInt16(content);
        //sampleRate = in.readFixedPoint1616();
        sampleRate = IsoTypeReader.readUInt32(content);
        if (!type.equals("mlpa")) {
            sampleRate = sampleRate >>> 16;
        }

        //more qt stuff - see http://mp4v2.googlecode.com/svn-history/r388/trunk/src/atom_sound.cpp
        if (soundVersion > 0) {
            samplesPerPacket = IsoTypeReader.readUInt32(content);
            bytesPerPacket = IsoTypeReader.readUInt32(content);
            bytesPerFrame = IsoTypeReader.readUInt32(content);
            bytesPerSample = IsoTypeReader.readUInt32(content);
        }
        if (soundVersion == 2) {

            soundVersion2Data = new byte[20];
            content.get(20);
        }
        _parseChildBoxes(content);
    }

    @Override
    protected long getContentSize() {
        long contentSize = 28;
        contentSize += soundVersion > 0 ? 16 : 0;
        contentSize += soundVersion == 2 ? 20 : 0;
        for (Box boxe : boxes) {
            contentSize += boxe.getSize();
        }
        return contentSize;
    }

    @Override
    public String toString() {
        return "AudioSampleEntry{" +
                "bytesPerSample=" + bytesPerSample +
                ", bytesPerFrame=" + bytesPerFrame +
                ", bytesPerPacket=" + bytesPerPacket +
                ", samplesPerPacket=" + samplesPerPacket +
                ", packetSize=" + packetSize +
                ", compressionId=" + compressionId +
                ", soundVersion=" + soundVersion +
                ", sampleRate=" + sampleRate +
                ", sampleSize=" + sampleSize +
                ", channelCount=" + channelCount +
                ", boxes=" + getBoxes() +
                '}';
    }

    @Override
    protected void getContent(ByteBuffer byteBuffer) {
        _writeReservedAndDataReferenceIndex(byteBuffer);
        IsoTypeWriter.writeUInt16(byteBuffer, soundVersion);
        IsoTypeWriter.writeUInt16(byteBuffer, reserved1);
        IsoTypeWriter.writeUInt32(byteBuffer, reserved2);
        IsoTypeWriter.writeUInt16(byteBuffer, channelCount);
        IsoTypeWriter.writeUInt16(byteBuffer, sampleSize);
        IsoTypeWriter.writeUInt16(byteBuffer, compressionId);
        IsoTypeWriter.writeUInt16(byteBuffer, packetSize);
        if (type.equals("mlpa")) {
            IsoTypeWriter.writeUInt32(byteBuffer, getSampleRate());
        } else {
            IsoTypeWriter.writeUInt32(byteBuffer, getSampleRate() << 16);
        }

        if (soundVersion > 0) {
            IsoTypeWriter.writeUInt32(byteBuffer, samplesPerPacket);
            IsoTypeWriter.writeUInt32(byteBuffer, bytesPerPacket);
            IsoTypeWriter.writeUInt32(byteBuffer, bytesPerFrame);
            IsoTypeWriter.writeUInt32(byteBuffer, bytesPerSample);
        }

        if (soundVersion == 2) {
            byteBuffer.put(soundVersion2Data);
        }
        _writeChildBoxes(byteBuffer);
    }
}
