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

import java.nio.ByteBuffer;

@Descriptor(tags = {0x06})
public class SLConfigDescriptor extends BaseDescriptor {
    private int predefined;

    public void setPredefined(int predefined) {
        this.predefined = predefined;
    }

    @Override
    public void parseDetail(ByteBuffer bb) {
        predefined = IsoTypeReader.readUInt8(bb);
    }

    public int serializedSize() {
        return 3;
    }

    public ByteBuffer serialize() {
        ByteBuffer out = ByteBuffer.allocate(3);
        IsoTypeWriter.writeUInt8(out, 6);
        IsoTypeWriter.writeUInt8(out, 1);
        IsoTypeWriter.writeUInt8(out, predefined);
        return out;
    }

    @Override
    public String toString() {
        final StringBuilder sb = new StringBuilder();
        sb.append("SLConfigDescriptor");
        sb.append("{predefined=").append(predefined);
        sb.append('}');
        return sb.toString();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }

        SLConfigDescriptor that = (SLConfigDescriptor) o;
        return predefined == that.predefined;
    }

    @Override
    public int hashCode() {
        return predefined;
    }
}
