/*
 * Copyright 2011 castLabs, Berlin
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

package com.googlecode.mp4parser.boxes.mp4.objectdescriptors;

import com.coremedia.iso.Hex;
import com.coremedia.iso.IsoTypeReader;
import com.coremedia.iso.IsoTypeWriter;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.logging.Logger;

@Descriptor(tags = {0x04})
public class DecoderConfigDescriptor extends BaseDescriptor {
    private static final boolean DEBUG = false;
    private static Logger log = Logger.getLogger(DecoderConfigDescriptor.class.getName());

    private int objectTypeIndication;
    private int streamType;
    private int upStream;
    private int bufferSizeDB;
    private long maxBitRate;
    private long avgBitRate;

    private DecoderSpecificInfo decoderSpecificInfo;
    private AudioSpecificConfig audioSpecificInfo;
    private List<ProfileLevelIndicationDescriptor> profileLevelIndicationDescriptors = new ArrayList<>();
    private byte[] configDescriptorDeadBytes;

    @Override
    public void parseDetail(ByteBuffer bb) throws IOException {
        objectTypeIndication = IsoTypeReader.readUInt8(bb);

        int data = IsoTypeReader.readUInt8(bb);
        streamType = data >>> 2;
        upStream = (data >> 1) & 0x1;

        bufferSizeDB = IsoTypeReader.readUInt24(bb);
        maxBitRate = IsoTypeReader.readUInt32(bb);
        avgBitRate = IsoTypeReader.readUInt32(bb);


        BaseDescriptor descriptor;
        if (bb.remaining() > 2) { //1byte tag + at least 1byte size
            final int begin = bb.position();
            descriptor = ObjectDescriptorFactory.createFrom(objectTypeIndication, bb);
            final int read = bb.position() - begin;
            if (DEBUG)
                log.finer(descriptor + " - DecoderConfigDescr1 read: " + read + ", size: " + (descriptor != null ? descriptor.getSize() : null));
            if (descriptor != null) {
                final int size = descriptor.getSize();
                if (read < size) {
                    //skip
                    configDescriptorDeadBytes = new byte[size - read];
                    bb.get(configDescriptorDeadBytes);
                }
            }
            if (descriptor instanceof DecoderSpecificInfo) {
                decoderSpecificInfo = (DecoderSpecificInfo) descriptor;
            }
            if (descriptor instanceof AudioSpecificConfig) {
                audioSpecificInfo = (AudioSpecificConfig) descriptor;
            }
        }

        while (bb.remaining() > 2) {
            final long begin = bb.position();
            descriptor = ObjectDescriptorFactory.createFrom(objectTypeIndication, bb);
            final long read = bb.position() - begin;
            if (DEBUG)
                log.finer(descriptor + " - DecoderConfigDescr2 read: " + read + ", size: " + (descriptor != null ? descriptor.getSize() : null));
            if (descriptor instanceof ProfileLevelIndicationDescriptor) {
                profileLevelIndicationDescriptors.add((ProfileLevelIndicationDescriptor) descriptor);
            }
        }
    }

    public int serializedSize() {
        return 15 + audioSpecificInfo.serializedSize();
    }

    public ByteBuffer serialize() {
        ByteBuffer out = ByteBuffer.allocate(serializedSize());
        IsoTypeWriter.writeUInt8(out, 4);
        IsoTypeWriter.writeUInt8(out, serializedSize() - 2);
        IsoTypeWriter.writeUInt8(out, objectTypeIndication);
        int flags = (streamType << 2) | (upStream << 1) | 1;
        IsoTypeWriter.writeUInt8(out, flags);
        IsoTypeWriter.writeUInt24(out, bufferSizeDB);
        IsoTypeWriter.writeUInt32(out, maxBitRate);
        IsoTypeWriter.writeUInt32(out, avgBitRate);
        out.put(audioSpecificInfo.serialize().array());
        return out;
    }

    public void setAudioSpecificInfo(AudioSpecificConfig audioSpecificInfo) {
        this.audioSpecificInfo = audioSpecificInfo;
    }

    public void setObjectTypeIndication(int objectTypeIndication) {
        this.objectTypeIndication = objectTypeIndication;
    }

    public void setStreamType(int streamType) {
        this.streamType = streamType;
    }

    public void setBufferSizeDB(int bufferSizeDB) {
        this.bufferSizeDB = bufferSizeDB;
    }

    public void setMaxBitRate(long maxBitRate) {
        this.maxBitRate = maxBitRate;
    }

    public void setAvgBitRate(long avgBitRate) {
        this.avgBitRate = avgBitRate;
    }

    @Override
    public String toString() {
        final StringBuilder sb = new StringBuilder();
        sb.append("DecoderConfigDescriptor");
        sb.append("{objectTypeIndication=").append(objectTypeIndication);
        sb.append(", streamType=").append(streamType);
        sb.append(", upStream=").append(upStream);
        sb.append(", bufferSizeDB=").append(bufferSizeDB);
        sb.append(", maxBitRate=").append(maxBitRate);
        sb.append(", avgBitRate=").append(avgBitRate);
        sb.append(", decoderSpecificInfo=").append(decoderSpecificInfo);
        sb.append(", audioSpecificInfo=").append(audioSpecificInfo);
        sb.append(", configDescriptorDeadBytes=").append(Hex.encodeHex(configDescriptorDeadBytes != null ? configDescriptorDeadBytes : new byte[]{}));
        sb.append(", profileLevelIndicationDescriptors=").append(profileLevelIndicationDescriptors == null ? "null" : Arrays.asList(profileLevelIndicationDescriptors).toString());
        sb.append('}');
        return sb.toString();
    }

}
