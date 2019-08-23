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

import java.io.IOException;
import java.lang.reflect.Modifier;
import java.nio.ByteBuffer;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;


public class ObjectDescriptorFactory {
    private static final boolean DEBUG = false;
    protected static Logger log = Logger.getLogger(ObjectDescriptorFactory.class.getName());

    private static Map<Integer, Map<Integer, Class<? extends BaseDescriptor>>> descriptorRegistry = new HashMap<>();

    static {
        Set<Class<? extends BaseDescriptor>> annotated = new HashSet<>();

        annotated.add(DecoderSpecificInfo.class);
        annotated.add(SLConfigDescriptor.class);
        annotated.add(BaseDescriptor.class);
        annotated.add(ExtensionDescriptor.class);
        annotated.add(ObjectDescriptorBase.class);
        annotated.add(ProfileLevelIndicationDescriptor.class);
        annotated.add(AudioSpecificConfig.class);
        annotated.add(ExtensionProfileLevelDescriptor.class);
        annotated.add(ESDescriptor.class);
        annotated.add(DecoderConfigDescriptor.class);

        for (Class<? extends BaseDescriptor> clazz : annotated) {
            final Descriptor descriptor = clazz.getAnnotation(Descriptor.class);
            final int[] tags = descriptor.tags();
            final int objectTypeInd = descriptor.objectTypeIndication();

            Map<Integer, Class<? extends BaseDescriptor>> tagMap = descriptorRegistry.get(objectTypeInd);
            if (tagMap == null) {
                tagMap = new HashMap<>();
            }
            for (int tag : tags) {
                tagMap.put(tag, clazz);
            }
            descriptorRegistry.put(objectTypeInd, tagMap);
        }
    }

    public static BaseDescriptor createFrom(int objectTypeIndication, ByteBuffer bb) throws IOException {
        int tag = IsoTypeReader.readUInt8(bb);

        Map<Integer, Class<? extends BaseDescriptor>> tagMap = descriptorRegistry.get(objectTypeIndication);
        if (tagMap == null) {
            tagMap = descriptorRegistry.get(-1);
        }
        Class<? extends BaseDescriptor> aClass = tagMap.get(tag);

        BaseDescriptor baseDescriptor;
        if (aClass == null || aClass.isInterface() || Modifier.isAbstract(aClass.getModifiers())) {
            if (DEBUG)
                log.warning("No ObjectDescriptor found for objectTypeIndication " + Integer.toHexString(objectTypeIndication) +
                        " and tag " + Integer.toHexString(tag) + " found: " + aClass);
            baseDescriptor = new UnknownDescriptor();
        } else {
            try {
                baseDescriptor = aClass.newInstance();
            } catch (Exception e) {
                if (DEBUG)
                    log.log(Level.SEVERE, "Couldn't instantiate BaseDescriptor class " + aClass + " for objectTypeIndication " + objectTypeIndication + " and tag " + tag, e);
                throw new RuntimeException(e);
            }
        }
        baseDescriptor.parse(tag, bb);
        return baseDescriptor;
    }
}
