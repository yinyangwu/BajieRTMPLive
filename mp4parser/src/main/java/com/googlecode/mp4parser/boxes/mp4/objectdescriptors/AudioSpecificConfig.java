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
import com.coremedia.iso.IsoTypeWriter;

import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;


@Descriptor(tags = 0x5, objectTypeIndication = 0x40)
public class AudioSpecificConfig extends BaseDescriptor {
    private byte[] configBytes;

    public static Map<Integer, Integer> samplingFrequencyIndexMap = new HashMap<>();
    public static Map<Integer, String> audioObjectTypeMap = new HashMap<>();
    private int audioObjectType;
    private int samplingFrequencyIndex;
    private int samplingFrequency;
    private int channelConfiguration;
    private int extensionAudioObjectType;
    private int sbrPresentFlag;
    private int psPresentFlag;
    private int extensionSamplingFrequencyIndex;
    private int extensionSamplingFrequency;
    private int extensionChannelConfiguration;
    private int sacPayloadEmbedding;
    private int fillBits;
    private int epConfig;
    private int directMapping;
    private int syncExtensionType;

    //GASpecificConfig
    private int frameLengthFlag;
    private int dependsOnCoreCoder;
    private int coreCoderDelay;
    private int extensionFlag;
    private int layerNr;
    private int numOfSubFrame;
    private int layer_length;
    private int aacSectionDataResilienceFlag;
    private int aacScalefactorDataResilienceFlag;
    private int aacSpectralDataResilienceFlag;
    private int extensionFlag3;
    private boolean gaSpecificConfig;

    //ParametricSpecificConfig
    private int isBaseLayer;
    private int paraMode;
    private int paraExtensionFlag;
    private int hvxcVarMode;
    private int hvxcRateMode;
    private int erHvxcExtensionFlag;
    private int var_ScalableFlag;
    private int hilnQuantMode;
    private int hilnMaxNumLine;
    private int hilnSampleRateCode;
    private int hilnFrameLength;
    private int hilnContMode;
    private int hilnEnhaLayer;
    private int hilnEnhaQuantMode;
    private boolean parametricSpecificConfig;

    @Override
    public void parseDetail(ByteBuffer bb) {
        ByteBuffer configBytes = bb.slice();
        configBytes.limit(sizeOfInstance);
        bb.position(bb.position() + sizeOfInstance);

        this.configBytes = new byte[sizeOfInstance];
        configBytes.get(this.configBytes);
        configBytes.rewind();

        BitReaderBuffer bitReaderBuffer = new BitReaderBuffer(configBytes);
        audioObjectType = getAudioObjectType(bitReaderBuffer);
        samplingFrequencyIndex = bitReaderBuffer.readBits(4);

        if (samplingFrequencyIndex == 0xf) {
            samplingFrequency = bitReaderBuffer.readBits(24);
        }

        channelConfiguration = bitReaderBuffer.readBits(4);

        if (audioObjectType == 5 ||
                audioObjectType == 29) {
            extensionAudioObjectType = 5;
            sbrPresentFlag = 1;
            if (audioObjectType == 29) {
                psPresentFlag = 1;
            }
            extensionSamplingFrequencyIndex = bitReaderBuffer.readBits(4);
            if (extensionSamplingFrequencyIndex == 0xf)
                extensionSamplingFrequency = bitReaderBuffer.readBits(24);
            audioObjectType = getAudioObjectType(bitReaderBuffer);
            if (audioObjectType == 22)
                extensionChannelConfiguration = bitReaderBuffer.readBits(4);
        } else {
            extensionAudioObjectType = 0;
        }

        if (audioObjectType == 1 || audioObjectType == 2 || audioObjectType == 3 || audioObjectType == 4 || audioObjectType == 6 || audioObjectType == 7 || audioObjectType == 17 || audioObjectType == 19 || audioObjectType == 20 || audioObjectType == 21 || audioObjectType == 22 || audioObjectType == 23) {
            parseGaSpecificConfig(channelConfiguration, audioObjectType, bitReaderBuffer);
        } else if (audioObjectType == 8) {
            throw new UnsupportedOperationException("can't parse CelpSpecificConfig yet");
        } else if (audioObjectType == 9) {
            throw new UnsupportedOperationException("can't parse HvxcSpecificConfig yet");
        } else if (audioObjectType == 12) {
            throw new UnsupportedOperationException("can't parse TTSSpecificConfig yet");
        } else if (audioObjectType == 13 || audioObjectType == 14 || audioObjectType == 15 || audioObjectType == 16) {
            throw new UnsupportedOperationException("can't parse StructuredAudioSpecificConfig yet");
        } else if (audioObjectType == 24) {
            throw new UnsupportedOperationException("can't parse ErrorResilientCelpSpecificConfig yet");
        } else if (audioObjectType == 25) {
            throw new UnsupportedOperationException("can't parse ErrorResilientHvxcSpecificConfig yet");
        } else if (audioObjectType == 26 || audioObjectType == 27) {
            parseParametricSpecificConfig(bitReaderBuffer);
        } else if (audioObjectType == 28) {
            throw new UnsupportedOperationException("can't parse SSCSpecificConfig yet");
        } else if (audioObjectType == 30) {
            sacPayloadEmbedding = bitReaderBuffer.readBits(1);
            throw new UnsupportedOperationException("can't parse SpatialSpecificConfig yet");
        } else if (audioObjectType == 32 || audioObjectType == 33 || audioObjectType == 34) {
            throw new UnsupportedOperationException("can't parse MPEG_1_2_SpecificConfig yet");
        } else if (audioObjectType == 35) {
            throw new UnsupportedOperationException("can't parse DSTSpecificConfig yet");
        } else if (audioObjectType == 36) {
            fillBits = bitReaderBuffer.readBits(5);
            throw new UnsupportedOperationException("can't parse ALSSpecificConfig yet");
        } else if (audioObjectType == 37 || audioObjectType == 38) {
            throw new UnsupportedOperationException("can't parse SLSSpecificConfig yet");
        } else if (audioObjectType == 39) {
            throw new UnsupportedOperationException("can't parse ELDSpecificConfig yet");
        } else if (audioObjectType == 40 || audioObjectType == 41) {
            throw new UnsupportedOperationException("can't parse SymbolicMusicSpecificConfig yet");
        }

        if (audioObjectType == 17 || audioObjectType == 19 || audioObjectType == 20 || audioObjectType == 21 || audioObjectType == 22 || audioObjectType == 23 || audioObjectType == 24 || audioObjectType == 25 || audioObjectType == 26 || audioObjectType == 27 || audioObjectType == 39) {
            epConfig = bitReaderBuffer.readBits(2);
            if (epConfig == 2 || epConfig == 3) {
                throw new UnsupportedOperationException("can't parse ErrorProtectionSpecificConfig yet");
            }
        }

        if (extensionAudioObjectType != 5 && bitReaderBuffer.remainingBits() >= 16) {
            syncExtensionType = bitReaderBuffer.readBits(11);
            if (syncExtensionType == 0x2b7) {
                extensionAudioObjectType = getAudioObjectType(bitReaderBuffer);
                if (extensionAudioObjectType == 5) {
                    sbrPresentFlag = bitReaderBuffer.readBits(1);
                    if (sbrPresentFlag == 1) {
                        extensionSamplingFrequencyIndex = bitReaderBuffer.readBits(4);
                        if (extensionSamplingFrequencyIndex == 0xf) {
                            extensionSamplingFrequency = bitReaderBuffer.readBits(24);
                        }
                        if (bitReaderBuffer.remainingBits() >= 12) {
                            syncExtensionType = bitReaderBuffer.readBits(11); //10101001000
                            if (syncExtensionType == 0x548) {
                                psPresentFlag = bitReaderBuffer.readBits(1);
                            }
                        }
                    }
                }
                if (extensionAudioObjectType == 22) {
                    sbrPresentFlag = bitReaderBuffer.readBits(1);
                    if (sbrPresentFlag == 1) {
                        extensionSamplingFrequencyIndex = bitReaderBuffer.readBits(4);
                        if (extensionSamplingFrequencyIndex == 0xf) {
                            extensionSamplingFrequency = bitReaderBuffer.readBits(24);
                        }
                    }
                    extensionChannelConfiguration = bitReaderBuffer.readBits(4);
                }
            }
        }
    }

    private int gaSpecificConfigSize() {
        return 0;
    }

    public int serializedSize() {
        int out = 4;
        if (audioObjectType == 2) {
            out += gaSpecificConfigSize();
        } else {
            throw new UnsupportedOperationException("can't serialize that yet");
        }
        return out;
    }

    public ByteBuffer serialize() {
        ByteBuffer out = ByteBuffer.allocate(serializedSize());
        IsoTypeWriter.writeUInt8(out, 5);
        IsoTypeWriter.writeUInt8(out, serializedSize() - 2);
        BitWriterBuffer bwb = new BitWriterBuffer(out);
        bwb.writeBits(audioObjectType, 5);
        bwb.writeBits(samplingFrequencyIndex, 4);
        if (samplingFrequencyIndex == 0xf) {
            throw new UnsupportedOperationException("can't serialize that yet");
        }
        bwb.writeBits(channelConfiguration, 4);
        return out;
    }

    private int getAudioObjectType(BitReaderBuffer in) {
        int audioObjectType = in.readBits(5);
        if (audioObjectType == 31) {
            audioObjectType = 32 + in.readBits(6);
        }
        return audioObjectType;
    }

    private void parseGaSpecificConfig(int channelConfiguration, int audioObjectType, BitReaderBuffer in) {
        frameLengthFlag = in.readBits(1);
        dependsOnCoreCoder = in.readBits(1);
        if (dependsOnCoreCoder == 1) {
            coreCoderDelay = in.readBits(14);
        }
        extensionFlag = in.readBits(1);
        if (channelConfiguration == 0) {
            throw new UnsupportedOperationException("can't parse program_config_element yet");
        }
        if ((audioObjectType == 6) || (audioObjectType == 20)) {
            layerNr = in.readBits(3);
        }
        if (extensionFlag == 1) {
            if (audioObjectType == 22) {
                numOfSubFrame = in.readBits(5);
                layer_length = in.readBits(11);
            }
            if (audioObjectType == 17 || audioObjectType == 19 ||
                    audioObjectType == 20 || audioObjectType == 23) {
                aacSectionDataResilienceFlag = in.readBits(1);
                aacScalefactorDataResilienceFlag = in.readBits(1);
                aacSpectralDataResilienceFlag = in.readBits(1);
            }
            extensionFlag3 = in.readBits(1);
        }
        gaSpecificConfig = true;
    }

    private void parseParametricSpecificConfig(BitReaderBuffer in) {
        isBaseLayer = in.readBits(1);
        if (isBaseLayer == 1) {
            parseParaConfig(in);
        } else {
            parseHilnEnexConfig(in);
        }
    }

    private void parseParaConfig(BitReaderBuffer in) {
        paraMode = in.readBits(2);

        if (paraMode != 1) {
            parseErHvxcConfig(in);
        }
        if (paraMode != 0) {
            parseHilnConfig(in);
        }

        paraExtensionFlag = in.readBits(1);
        parametricSpecificConfig = true;
    }

    private void parseErHvxcConfig(BitReaderBuffer in) {
        hvxcVarMode = in.readBits(1);
        hvxcRateMode = in.readBits(2);
        erHvxcExtensionFlag = in.readBits(1);

        if (erHvxcExtensionFlag == 1) {
            var_ScalableFlag = in.readBits(1);
        }
    }

    private void parseHilnConfig(BitReaderBuffer in) {
        hilnQuantMode = in.readBits(1);
        hilnMaxNumLine = in.readBits(8);
        hilnSampleRateCode = in.readBits(4);
        hilnFrameLength = in.readBits(12);
        hilnContMode = in.readBits(2);
    }

    private void parseHilnEnexConfig(BitReaderBuffer in) {
        hilnEnhaLayer = in.readBits(1);
        if (hilnEnhaLayer == 1) {
            hilnEnhaQuantMode = in.readBits(2);
        }
    }

    public void setAudioObjectType(int audioObjectType) {
        this.audioObjectType = audioObjectType;
    }

    public void setSamplingFrequencyIndex(int samplingFrequencyIndex) {
        this.samplingFrequencyIndex = samplingFrequencyIndex;
    }

    public void setChannelConfiguration(int channelConfiguration) {
        this.channelConfiguration = channelConfiguration;
    }

    @Override
    public String toString() {
        final StringBuilder sb = new StringBuilder();
        sb.append("AudioSpecificConfig");
        sb.append("{configBytes=").append(Hex.encodeHex(configBytes));
        sb.append(", audioObjectType=").append(audioObjectType).append(" (").append(audioObjectTypeMap.get(audioObjectType)).append(")");
        sb.append(", samplingFrequencyIndex=").append(samplingFrequencyIndex).append(" (").append(samplingFrequencyIndexMap.get(samplingFrequencyIndex)).append(")");
        sb.append(", samplingFrequency=").append(samplingFrequency);
        sb.append(", channelConfiguration=").append(channelConfiguration);
        if (extensionAudioObjectType > 0) {
            sb.append(", extensionAudioObjectType=").append(extensionAudioObjectType).append(" (").append(audioObjectTypeMap.get(extensionAudioObjectType)).append(")");
            sb.append(", sbrPresentFlag=").append(sbrPresentFlag);
            sb.append(", psPresentFlag=").append(psPresentFlag);
            sb.append(", extensionSamplingFrequencyIndex=").append(extensionSamplingFrequencyIndex).append(" (").append(samplingFrequencyIndexMap.get(extensionSamplingFrequencyIndex)).append(")");
            sb.append(", extensionSamplingFrequency=").append(extensionSamplingFrequency);
            sb.append(", extensionChannelConfiguration=").append(extensionChannelConfiguration);
        }
        sb.append(", syncExtensionType=").append(syncExtensionType);
        if (gaSpecificConfig) {
            sb.append(", frameLengthFlag=").append(frameLengthFlag);
            sb.append(", dependsOnCoreCoder=").append(dependsOnCoreCoder);
            sb.append(", coreCoderDelay=").append(coreCoderDelay);
            sb.append(", extensionFlag=").append(extensionFlag);
            sb.append(", layerNr=").append(layerNr);
            sb.append(", numOfSubFrame=").append(numOfSubFrame);
            sb.append(", layer_length=").append(layer_length);
            sb.append(", aacSectionDataResilienceFlag=").append(aacSectionDataResilienceFlag);
            sb.append(", aacScalefactorDataResilienceFlag=").append(aacScalefactorDataResilienceFlag);
            sb.append(", aacSpectralDataResilienceFlag=").append(aacSpectralDataResilienceFlag);
            sb.append(", extensionFlag3=").append(extensionFlag3);
        }
        if (parametricSpecificConfig) {
            sb.append(", isBaseLayer=").append(isBaseLayer);
            sb.append(", paraMode=").append(paraMode);
            sb.append(", paraExtensionFlag=").append(paraExtensionFlag);
            sb.append(", hvxcVarMode=").append(hvxcVarMode);
            sb.append(", hvxcRateMode=").append(hvxcRateMode);
            sb.append(", erHvxcExtensionFlag=").append(erHvxcExtensionFlag);
            sb.append(", var_ScalableFlag=").append(var_ScalableFlag);
            sb.append(", hilnQuantMode=").append(hilnQuantMode);
            sb.append(", hilnMaxNumLine=").append(hilnMaxNumLine);
            sb.append(", hilnSampleRateCode=").append(hilnSampleRateCode);
            sb.append(", hilnFrameLength=").append(hilnFrameLength);
            sb.append(", hilnContMode=").append(hilnContMode);
            sb.append(", hilnEnhaLayer=").append(hilnEnhaLayer);
            sb.append(", hilnEnhaQuantMode=").append(hilnEnhaQuantMode);
        }
        sb.append('}');
        return sb.toString();
    }

    static {
        samplingFrequencyIndexMap.put(0x0, 96000);
        samplingFrequencyIndexMap.put(0x1, 88200);
        samplingFrequencyIndexMap.put(0x2, 64000);
        samplingFrequencyIndexMap.put(0x3, 48000);
        samplingFrequencyIndexMap.put(0x4, 44100);
        samplingFrequencyIndexMap.put(0x5, 32000);
        samplingFrequencyIndexMap.put(0x6, 24000);
        samplingFrequencyIndexMap.put(0x7, 22050);
        samplingFrequencyIndexMap.put(0x8, 16000);
        samplingFrequencyIndexMap.put(0x9, 12000);
        samplingFrequencyIndexMap.put(0xa, 11025);
        samplingFrequencyIndexMap.put(0xb, 8000);

        audioObjectTypeMap.put(1, "AAC main");
        audioObjectTypeMap.put(2, "AAC LC");
        audioObjectTypeMap.put(3, "AAC SSR");
        audioObjectTypeMap.put(4, "AAC LTP");
        audioObjectTypeMap.put(5, "SBR");
        audioObjectTypeMap.put(6, "AAC Scalable");
        audioObjectTypeMap.put(7, "TwinVQ");
        audioObjectTypeMap.put(8, "CELP");
        audioObjectTypeMap.put(9, "HVXC");
        audioObjectTypeMap.put(10, "(reserved)");
        audioObjectTypeMap.put(11, "(reserved)");
        audioObjectTypeMap.put(12, "TTSI");
        audioObjectTypeMap.put(13, "Main synthetic");
        audioObjectTypeMap.put(14, "Wavetable synthesis");
        audioObjectTypeMap.put(15, "General MIDI");
        audioObjectTypeMap.put(16, "Algorithmic Synthesis and Audio FX");
        audioObjectTypeMap.put(17, "ER AAC LC");
        audioObjectTypeMap.put(18, "(reserved)");
        audioObjectTypeMap.put(19, "ER AAC LTP");
        audioObjectTypeMap.put(20, "ER AAC Scalable");
        audioObjectTypeMap.put(21, "ER TwinVQ");
        audioObjectTypeMap.put(22, "ER BSAC");
        audioObjectTypeMap.put(23, "ER AAC LD");
        audioObjectTypeMap.put(24, "ER CELP");
        audioObjectTypeMap.put(25, "ER HVXC");
        audioObjectTypeMap.put(26, "ER HILN");
        audioObjectTypeMap.put(27, "ER Parametric");
        audioObjectTypeMap.put(28, "SSC");
        audioObjectTypeMap.put(29, "PS");
        audioObjectTypeMap.put(30, "MPEG Surround");
        audioObjectTypeMap.put(31, "(escape)");
        audioObjectTypeMap.put(32, "Layer-1");
        audioObjectTypeMap.put(33, "Layer-2");
        audioObjectTypeMap.put(34, "Layer-3");
        audioObjectTypeMap.put(35, "DST");
        audioObjectTypeMap.put(36, "ALS");
        audioObjectTypeMap.put(37, "SLS");
        audioObjectTypeMap.put(38, "SLS non-core");
        audioObjectTypeMap.put(39, "ER AAC ELD");
        audioObjectTypeMap.put(40, "SMR Simple");
        audioObjectTypeMap.put(41, "SMR Main");
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }

        AudioSpecificConfig that = (AudioSpecificConfig) o;

        if (aacScalefactorDataResilienceFlag != that.aacScalefactorDataResilienceFlag) {
            return false;
        }
        if (aacSectionDataResilienceFlag != that.aacSectionDataResilienceFlag) {
            return false;
        }
        if (aacSpectralDataResilienceFlag != that.aacSpectralDataResilienceFlag) {
            return false;
        }
        if (audioObjectType != that.audioObjectType) {
            return false;
        }
        if (channelConfiguration != that.channelConfiguration) {
            return false;
        }
        if (coreCoderDelay != that.coreCoderDelay) {
            return false;
        }
        if (dependsOnCoreCoder != that.dependsOnCoreCoder) {
            return false;
        }
        if (directMapping != that.directMapping) {
            return false;
        }
        if (epConfig != that.epConfig) {
            return false;
        }
        if (erHvxcExtensionFlag != that.erHvxcExtensionFlag) {
            return false;
        }
        if (extensionAudioObjectType != that.extensionAudioObjectType) {
            return false;
        }
        if (extensionChannelConfiguration != that.extensionChannelConfiguration) {
            return false;
        }
        if (extensionFlag != that.extensionFlag) {
            return false;
        }
        if (extensionFlag3 != that.extensionFlag3) {
            return false;
        }
        if (extensionSamplingFrequency != that.extensionSamplingFrequency) {
            return false;
        }
        if (extensionSamplingFrequencyIndex != that.extensionSamplingFrequencyIndex) {
            return false;
        }
        if (fillBits != that.fillBits) {
            return false;
        }
        if (frameLengthFlag != that.frameLengthFlag) {
            return false;
        }
        if (gaSpecificConfig != that.gaSpecificConfig) {
            return false;
        }
        if (hilnContMode != that.hilnContMode) {
            return false;
        }
        if (hilnEnhaLayer != that.hilnEnhaLayer) {
            return false;
        }
        if (hilnEnhaQuantMode != that.hilnEnhaQuantMode) {
            return false;
        }
        if (hilnFrameLength != that.hilnFrameLength) {
            return false;
        }
        if (hilnMaxNumLine != that.hilnMaxNumLine) {
            return false;
        }
        if (hilnQuantMode != that.hilnQuantMode) {
            return false;
        }
        if (hilnSampleRateCode != that.hilnSampleRateCode) {
            return false;
        }
        if (hvxcRateMode != that.hvxcRateMode) {
            return false;
        }
        if (hvxcVarMode != that.hvxcVarMode) {
            return false;
        }
        if (isBaseLayer != that.isBaseLayer) {
            return false;
        }
        if (layerNr != that.layerNr) {
            return false;
        }
        if (layer_length != that.layer_length) {
            return false;
        }
        if (numOfSubFrame != that.numOfSubFrame) {
            return false;
        }
        if (paraExtensionFlag != that.paraExtensionFlag) {
            return false;
        }
        if (paraMode != that.paraMode) {
            return false;
        }
        if (parametricSpecificConfig != that.parametricSpecificConfig) {
            return false;
        }
        if (psPresentFlag != that.psPresentFlag) {
            return false;
        }
        if (sacPayloadEmbedding != that.sacPayloadEmbedding) {
            return false;
        }
        if (samplingFrequency != that.samplingFrequency) {
            return false;
        }
        if (samplingFrequencyIndex != that.samplingFrequencyIndex) {
            return false;
        }
        if (sbrPresentFlag != that.sbrPresentFlag) {
            return false;
        }
        if (syncExtensionType != that.syncExtensionType) {
            return false;
        }
        if (var_ScalableFlag != that.var_ScalableFlag) {
            return false;
        }
        return Arrays.equals(configBytes, that.configBytes);
    }

    @Override
    public int hashCode() {
        int result = configBytes != null ? Arrays.hashCode(configBytes) : 0;
        result = 31 * result + audioObjectType;
        result = 31 * result + samplingFrequencyIndex;
        result = 31 * result + samplingFrequency;
        result = 31 * result + channelConfiguration;
        result = 31 * result + extensionAudioObjectType;
        result = 31 * result + sbrPresentFlag;
        result = 31 * result + psPresentFlag;
        result = 31 * result + extensionSamplingFrequencyIndex;
        result = 31 * result + extensionSamplingFrequency;
        result = 31 * result + extensionChannelConfiguration;
        result = 31 * result + sacPayloadEmbedding;
        result = 31 * result + fillBits;
        result = 31 * result + epConfig;
        result = 31 * result + directMapping;
        result = 31 * result + syncExtensionType;
        result = 31 * result + frameLengthFlag;
        result = 31 * result + dependsOnCoreCoder;
        result = 31 * result + coreCoderDelay;
        result = 31 * result + extensionFlag;
        result = 31 * result + layerNr;
        result = 31 * result + numOfSubFrame;
        result = 31 * result + layer_length;
        result = 31 * result + aacSectionDataResilienceFlag;
        result = 31 * result + aacScalefactorDataResilienceFlag;
        result = 31 * result + aacSpectralDataResilienceFlag;
        result = 31 * result + extensionFlag3;
        result = 31 * result + (gaSpecificConfig ? 1 : 0);
        result = 31 * result + isBaseLayer;
        result = 31 * result + paraMode;
        result = 31 * result + paraExtensionFlag;
        result = 31 * result + hvxcVarMode;
        result = 31 * result + hvxcRateMode;
        result = 31 * result + erHvxcExtensionFlag;
        result = 31 * result + var_ScalableFlag;
        result = 31 * result + hilnQuantMode;
        result = 31 * result + hilnMaxNumLine;
        result = 31 * result + hilnSampleRateCode;
        result = 31 * result + hilnFrameLength;
        result = 31 * result + hilnContMode;
        result = 31 * result + hilnEnhaLayer;
        result = 31 * result + hilnEnhaQuantMode;
        result = 31 * result + (parametricSpecificConfig ? 1 : 0);
        return result;
    }
}
