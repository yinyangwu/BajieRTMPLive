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

import com.coremedia.iso.IsoTypeReader;
import com.coremedia.iso.IsoTypeWriter;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Logger;

@Descriptor(tags = {0x03})
public class ESDescriptor extends BaseDescriptor {
    private static final boolean DEBUG = false;
    private static Logger log = Logger.getLogger(ESDescriptor.class.getName());

    private int esId;
    private int streamDependenceFlag;
    private int URLFlag;
    private int oCRstreamFlag;
    private int streamPriority;

    private int URLLength = 0;
    private String URLString;

    private int dependsOnEsId;
    private int oCREsId;

    private DecoderConfigDescriptor decoderConfigDescriptor;
    private SLConfigDescriptor slConfigDescriptor;
    private List<BaseDescriptor> otherDescriptors = new ArrayList<>();

    @Override
    public void parseDetail(ByteBuffer bb) throws IOException {
        esId = IsoTypeReader.readUInt16(bb);

        int data = IsoTypeReader.readUInt8(bb);
        streamDependenceFlag = data >>> 7;
        URLFlag = (data >>> 6) & 0x1;
        oCRstreamFlag = (data >>> 5) & 0x1;
        streamPriority = data & 0x1f;

        if (streamDependenceFlag == 1) {
            dependsOnEsId = IsoTypeReader.readUInt16(bb);
        }
        if (URLFlag == 1) {
            URLLength = IsoTypeReader.readUInt8(bb);
            URLString = IsoTypeReader.readString(bb, URLLength);
        }
        if (oCRstreamFlag == 1) {
            oCREsId = IsoTypeReader.readUInt16(bb);
        }

        int baseSize = 1 /*tag*/ + getSizeBytes() + 2 + 1 + (streamDependenceFlag == 1 ? 2 : 0) + (URLFlag == 1 ? 1 + URLLength : 0) + (oCRstreamFlag == 1 ? 2 : 0);

        int begin = bb.position();
        if (getSize() > baseSize + 2) {
            BaseDescriptor descriptor = ObjectDescriptorFactory.createFrom(-1, bb);
            final long read = bb.position() - begin;
            if (DEBUG)
                log.finer(descriptor + " - ESDescriptor1 read: " + read + ", size: " + (descriptor != null ? descriptor.getSize() : null));
            if (descriptor != null) {
                final int size = descriptor.getSize();
                bb.position(begin + size);
                baseSize += size;
            } else {
                baseSize += read;
            }
            if (descriptor instanceof DecoderConfigDescriptor) {
                decoderConfigDescriptor = (DecoderConfigDescriptor) descriptor;
            }
        }

        begin = bb.position();
        if (getSize() > baseSize + 2) {
            BaseDescriptor descriptor = ObjectDescriptorFactory.createFrom(-1, bb);
            final long read = bb.position() - begin;
            if (DEBUG)
                log.finer(descriptor + " - ESDescriptor2 read: " + read + ", size: " + (descriptor != null ? descriptor.getSize() : null));
            if (descriptor != null) {
                final int size = descriptor.getSize();
                bb.position(begin + size);
                baseSize += size;
            } else {
                baseSize += read;
            }
            if (descriptor instanceof SLConfigDescriptor) {
                slConfigDescriptor = (SLConfigDescriptor) descriptor;
            }
        } else {
            if (DEBUG) log.warning("SLConfigDescriptor is missing!");
        }

        while (getSize() - baseSize > 2) {
            begin = bb.position();
            BaseDescriptor descriptor = ObjectDescriptorFactory.createFrom(-1, bb);
            final long read = bb.position() - begin;
            if (DEBUG)
                log.finer(descriptor + " - ESDescriptor3 read: " + read + ", size: " + (descriptor != null ? descriptor.getSize() : null));
            if (descriptor != null) {
                final int size = descriptor.getSize();
                bb.position(begin + size);
                baseSize += size;
            } else {
                baseSize += read;
            }
            otherDescriptors.add(descriptor);
        }
    }

    public int serializedSize() {
        int out = 5;
        if (streamDependenceFlag > 0) {
            out += 2;
        }
        if (URLFlag > 0) {
            out += 1 + URLLength;
        }
        if (oCRstreamFlag > 0) {
            out += 2;
        }

        out += decoderConfigDescriptor.serializedSize();
        out += slConfigDescriptor.serializedSize();

        return out;
    }

    public ByteBuffer serialize() {
        ByteBuffer out = ByteBuffer.allocate(serializedSize()); // Usually is around 30 bytes, so 200 should be enough...
        IsoTypeWriter.writeUInt8(out, 3);
        IsoTypeWriter.writeUInt8(out, serializedSize() - 2); // Not OK for longer sizes!
        IsoTypeWriter.writeUInt16(out, esId);
        int flags = (streamDependenceFlag << 7) | (URLFlag << 6) | (oCRstreamFlag << 5) | (streamPriority & 0x1f);
        IsoTypeWriter.writeUInt8(out, flags);
        if (streamDependenceFlag > 0) {
            IsoTypeWriter.writeUInt16(out, dependsOnEsId);
        }
        if (URLFlag > 0) {
            IsoTypeWriter.writeUInt8(out, URLLength);
            IsoTypeWriter.writeUtf8String(out, URLString);
        }
        if (oCRstreamFlag > 0) {
            IsoTypeWriter.writeUInt16(out, oCREsId);
        }

        ByteBuffer dec = decoderConfigDescriptor.serialize();
        ByteBuffer sl = slConfigDescriptor.serialize();
        out.put(dec.array());
        out.put(sl.array());

        return out;
    }

    public void setDecoderConfigDescriptor(DecoderConfigDescriptor decoderConfigDescriptor) {
        this.decoderConfigDescriptor = decoderConfigDescriptor;
    }

    public void setSlConfigDescriptor(SLConfigDescriptor slConfigDescriptor) {
        this.slConfigDescriptor = slConfigDescriptor;
    }

    public void setEsId(int esId) {
        this.esId = esId;
    }

    @Override
    public String toString() {
        final StringBuilder sb = new StringBuilder();
        sb.append("ESDescriptor");
        sb.append("{esId=").append(esId);
        sb.append(", streamDependenceFlag=").append(streamDependenceFlag);
        sb.append(", URLFlag=").append(URLFlag);
        sb.append(", oCRstreamFlag=").append(oCRstreamFlag);
        sb.append(", streamPriority=").append(streamPriority);
        sb.append(", URLLength=").append(URLLength);
        sb.append(", URLString='").append(URLString).append('\'');
        sb.append(", dependsOnEsId=").append(dependsOnEsId);
        sb.append(", oCREsId=").append(oCREsId);
        sb.append(", decoderConfigDescriptor=").append(decoderConfigDescriptor);
        sb.append(", slConfigDescriptor=").append(slConfigDescriptor);
        sb.append('}');
        return sb.toString();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        ESDescriptor that = (ESDescriptor) o;

        if (URLFlag != that.URLFlag) return false;
        if (URLLength != that.URLLength) return false;
        if (dependsOnEsId != that.dependsOnEsId) return false;
        if (esId != that.esId) return false;
        if (oCREsId != that.oCREsId) return false;
        if (oCRstreamFlag != that.oCRstreamFlag) return false;
        if (streamDependenceFlag != that.streamDependenceFlag) return false;
        if (streamPriority != that.streamPriority) return false;
        if (URLString != null ? !URLString.equals(that.URLString) : that.URLString != null)
            return false;
        if (decoderConfigDescriptor != null ? !decoderConfigDescriptor.equals(that.decoderConfigDescriptor) : that.decoderConfigDescriptor != null)
            return false;
        if (otherDescriptors != null ? !otherDescriptors.equals(that.otherDescriptors) : that.otherDescriptors != null)
            return false;
        return slConfigDescriptor != null ? slConfigDescriptor.equals(that.slConfigDescriptor) : that.slConfigDescriptor == null;
    }

    @Override
    public int hashCode() {
        int result = esId;
        result = 31 * result + streamDependenceFlag;
        result = 31 * result + URLFlag;
        result = 31 * result + oCRstreamFlag;
        result = 31 * result + streamPriority;
        result = 31 * result + URLLength;
        result = 31 * result + (URLString != null ? URLString.hashCode() : 0);
        result = 31 * result + dependsOnEsId;
        result = 31 * result + oCREsId;
        result = 31 * result + (decoderConfigDescriptor != null ? decoderConfigDescriptor.hashCode() : 0);
        result = 31 * result + (slConfigDescriptor != null ? slConfigDescriptor.hashCode() : 0);
        result = 31 * result + (otherDescriptors != null ? otherDescriptors.hashCode() : 0);
        return result;
    }
}
